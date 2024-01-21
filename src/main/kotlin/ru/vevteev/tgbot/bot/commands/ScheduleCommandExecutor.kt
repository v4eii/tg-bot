package ru.vevteev.tgbot.bot.commands

import org.slf4j.LoggerFactory
import org.springframework.context.MessageSource
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.methods.send.SendAnimation
import org.telegram.telegrambots.meta.api.methods.send.SendDocument
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto
import org.telegram.telegrambots.meta.api.methods.send.SendSticker
import org.telegram.telegrambots.meta.api.objects.InputFile
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import ru.vevteev.tgbot.bot.TelegramLongPollingBotExt
import ru.vevteev.tgbot.bot.commands.ScheduleCommandExecutor.CallbackActionType.NONE
import ru.vevteev.tgbot.bot.commands.ScheduleCommandExecutor.CallbackActionType.RANDOM_CAT
import ru.vevteev.tgbot.bot.commands.ScheduleCommandExecutor.CallbackActionType.RANDOM_CONTENT
import ru.vevteev.tgbot.bot.commands.ScheduleCommandExecutor.CallbackDayType.CUSTOM
import ru.vevteev.tgbot.bot.commands.ScheduleCommandExecutor.CallbackDayType.EVERY_DAY
import ru.vevteev.tgbot.bot.commands.ScheduleCommandExecutor.CallbackDayType.WEEKDAYS
import ru.vevteev.tgbot.bot.commands.ScheduleCommandExecutor.CallbackModeType.CREATE
import ru.vevteev.tgbot.bot.commands.ScheduleCommandExecutor.CallbackModeType.DELETE
import ru.vevteev.tgbot.bot.commands.ScheduleCommandExecutor.CallbackTimeType.AM_12
import ru.vevteev.tgbot.bot.commands.ScheduleCommandExecutor.CallbackTimeType.EVERY_HOUR
import ru.vevteev.tgbot.bot.commands.ScheduleCommandExecutor.CallbackTimeType.EVERY_MINUTE
import ru.vevteev.tgbot.bot.commands.ScheduleCommandExecutor.CallbackTimeType.WORK_TIME
import ru.vevteev.tgbot.bot.commands.ScheduleCommandExecutor.RandomContentType.ANIMATION
import ru.vevteev.tgbot.bot.commands.ScheduleCommandExecutor.RandomContentType.DOCUMENT
import ru.vevteev.tgbot.bot.commands.ScheduleCommandExecutor.RandomContentType.PHOTO
import ru.vevteev.tgbot.bot.commands.ScheduleCommandExecutor.RandomContentType.STICKER
import ru.vevteev.tgbot.bot.commands.ScheduleCommandExecutor.RandomContentType.TEXT
import ru.vevteev.tgbot.client.CatClient
import ru.vevteev.tgbot.dto.CronData
import ru.vevteev.tgbot.dto.ScheduleData
import ru.vevteev.tgbot.extension.CANCEL_DATA
import ru.vevteev.tgbot.extension.callbackButton
import ru.vevteev.tgbot.extension.callbackQueryData
import ru.vevteev.tgbot.extension.callbackQueryFromUserId
import ru.vevteev.tgbot.extension.callbackQueryMessageChatId
import ru.vevteev.tgbot.extension.callbackQueryMessageId
import ru.vevteev.tgbot.extension.cancelHandler
import ru.vevteev.tgbot.extension.createDeleteMessage
import ru.vevteev.tgbot.extension.createEditMessage
import ru.vevteev.tgbot.extension.createSendMessage
import ru.vevteev.tgbot.extension.get
import ru.vevteev.tgbot.extension.isReplyMessageWithInlineMarkup
import ru.vevteev.tgbot.extension.locale
import ru.vevteev.tgbot.extension.messageText
import ru.vevteev.tgbot.extension.messageUserIdSafe
import ru.vevteev.tgbot.extension.oneButtonInlineKeyboard
import ru.vevteev.tgbot.extension.replyMessageId
import ru.vevteev.tgbot.extension.withCancelButton
import ru.vevteev.tgbot.extension.withCommandMarker
import ru.vevteev.tgbot.repository.RedisScheduleDao
import ru.vevteev.tgbot.schedule.DefaultScheduler
import java.net.URL
import java.time.Duration
import java.time.OffsetDateTime
import java.util.*

@Component
class ScheduleCommandExecutor(
    private val messageSource: MessageSource,
    private val defaultScheduler: DefaultScheduler,
    private val redisDao: RedisScheduleDao,
    private val catClient: CatClient, // TODO move to collection
) : CommandCallbackExecutor, CommandReplyExecutor {
    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun commandName() = "schedule"

    override fun commandDescription(locale: Locale) = messageSource.get("command.description.schedule", locale)

    override fun init(bot: TelegramLongPollingBotExt) {
        fun initSchedules() {
            val all = redisDao.getAll("*")
            all.filter { it.scheduleComplete }
                .forEach {
                    defaultScheduler.registerNewCronScheduleTask(
                        it.cron.toString(),
                        it.userId,
                        when (it.action) {
                            RANDOM_CAT -> {
                                { catAction(it.chatId, catClient, bot) }
                            }

                            RANDOM_CONTENT -> {
                                val (type, fileId) = it.actionDescription.split(":");
                                { randomContent(RandomContentType.valueOf(type), fileId, it.chatId, bot) }
                            }

                            NONE -> {
                                {}
                            }
                        }
                    )
                }
                .also { logger.info("Init all schedules"); }
        }

        fun removeOutdatedTasks() {
            val all = redisDao.getAll("*")
            all.filter { it.scheduleComplete.not() && it.createDate.plusDays(1).isBefore(OffsetDateTime.now()) }
                .forEach { redisDao.delete(it.keyValue()) }
                .also { logger.info("All outdated schedules deleted") }
        }

        defaultScheduler.registerNewFixedScheduleTask(Duration.ofHours(1), "ADMIN", "refreshTasks") {
            initSchedules()
            removeOutdatedTasks()
            logger.info("Tasks successfully refreshed")
        }
    }

    override fun perform(update: Update, bot: TelegramLongPollingBotExt, arguments: List<String>) {
        update.run {
            val locale = locale(arguments)
            bot.execute(
                createSendMessage(
                    messageSource.get("command.schedule.button.init-text", locale)
                        .withCommandMarker(commandName(), arguments)
                ) {
                    replyMarkup = buildStartInlineKeyboard(locale)
                        .withCancelButton(messageSource.get("command.button.cancel.description", locale))
                }
            )
            return
        }
    }

    override fun processCallback(update: Update, bot: TelegramLongPollingBotExt, arguments: List<String>) {
        update.run {
            val data = callbackQueryData()
            val locale = locale(arguments)
            when {
                data == CANCEL_DATA -> cancelHandler(bot, messageSource.get("msg.cancele", locale))

                data in CallbackModeType.values().map { it.toString() } -> {
                    when (CallbackModeType.valueOf(data)) {
                        CREATE -> bot.execute(
                            createEditMessage(
                                callbackQueryMessageId(),
                                messageSource.get("command.schedule.button.date-choose-text", locale)
                                    .withCommandMarker(commandName(), arguments)
                            ) {
                                replyMarkup = buildInitScheduleInlineKeyboard(locale)
                                    .withCancelButton(messageSource.get("command.button.cancel.description", locale))
                            }
                        )

                        DELETE -> deleteSchedule(bot, arguments)
                    }
                }

                data in CallbackDayType.values().map { it.toString() } -> {
                    processCallbackDayType(locale, CallbackDayType.valueOf(data), bot, arguments)
                }

                data in CallbackTimeType.values().map { it.toString() } -> {
                    processCallbackTimeType(CallbackTimeType.valueOf(data), bot, arguments)
                }

                data in CallbackActionType.values().map { it.toString() } -> {
                    processCallbackActionType(CallbackActionType.valueOf(data), bot, arguments)
                }

                data.contains("remove:.*".toRegex()) -> {
                    val key = data.split(":", limit = 2)[1]
                    redisDao.run {
                        get(key)?.also {
                            delete(key)
                            defaultScheduler.removeCronSchedule(it.cron.toString(), it.userId)
                        }
                    }
                    bot.execute(createDeleteMessage(callbackQueryMessageId()))
                    bot.execute(createSendMessage(messageSource.get("msg.done", locale)))
                }
            }
            return
        }
    }

    override fun processReply(update: Update, bot: TelegramLongPollingBotExt, arguments: List<String>) {
        update.run {
            if (isReplyMessageWithInlineMarkup()) {
                val replyMarkup = message.replyToMessage.replyMarkup
                when (replyMarkup?.keyboard?.firstOrNull()?.firstOrNull()?.callbackData) {
                    "random_content_waiting" -> processRandomContentWaitingReply(bot, locale(arguments))
                    "custom_cron_waiting" -> processCustomCronWaitingReply(bot, arguments)
                }
            }
        }
    }

    private fun Update.processCustomCronWaitingReply(
        bot: TelegramLongPollingBotExt,
        arguments: List<String>
    ) {
        val locale = locale(arguments)
        if (message.hasText()) {
            val inputCron = messageText()!!.split(" ")
            if (inputCron.size !in 5..6) {
                bot.execute(
                    createSendMessage(messageSource.get("command.schedule.button.incorrect-cron-text", locale))
                )
            } else {
                val updCron = when (inputCron.size) {
                    5 -> {
                        CronData(
                            second = "0",
                            minute = inputCron[0],
                            hour = inputCron[1],
                            dayOfMonth = inputCron[2],
                            month = inputCron[3],
                            dayOfWeek = inputCron[4]
                        )
                    }

                    6 -> {
                        CronData(
                            second = inputCron[0],
                            minute = inputCron[1],
                            hour = inputCron[2],
                            dayOfMonth = inputCron[3],
                            month = inputCron[4],
                            dayOfWeek = inputCron[5]
                        )
                    }

                    else -> CronData()
                }
                redisDao.get(message.from.id.toString())?.apply {
                    cron.second = updCron.second
                    cron.minute = updCron.minute
                    cron.hour = updCron.hour
                    cron.dayOfMonth = updCron.dayOfMonth
                    cron.month = updCron.month
                    cron.dayOfWeek = updCron.dayOfWeek
                }?.also {
                    redisDao.save(message.from.id.toString(), it)
                    actionStepEdit(bot, arguments, message.replyToMessage.messageId)
                }
            }
        } else {
            bot.execute(createSendMessage(messageSource.get("command.schedule.button.incorrect-cron-format", locale)))
        }
    }

    private fun Update.processRandomContentWaitingReply(bot: TelegramLongPollingBotExt, locale: Locale) {
        redisDao.get(message.from.id.toString())?.apply {
            val (type, s) = when {
                message.hasAnimation() -> ANIMATION to message.animation.fileId
                message.hasDocument() -> DOCUMENT to message.document.fileId
                message.hasPhoto() -> PHOTO to message.photo.first().fileId
                message.hasSticker() -> STICKER to message.sticker.fileId
                else -> TEXT to message.text
            }
            actionDescription = "$type:$s"
            scheduleComplete = true
        }?.also {
            redisDao.delete(it.userId)
            redisDao.save(it.keyValue(), it)
            defaultScheduler.registerNewCronScheduleTask(it.cron.toString(), it.userId) {
                val (type, fileId) = it.actionDescription.split(":")
                randomContent(RandomContentType.valueOf(type), fileId, it.chatId, bot)
            }
            bot.execute(createDeleteMessage(replyMessageId()))
            bot.execute(
                createSendMessage(
                    messageSource.getMessage(
                        "msg.done-schedule",
                        arrayOf(
                            "${it.cron} ${it.action} ${it.actionDescription.split(":").first()}"
                        ),
                        locale
                    )
                )
            )
        }
    }

    private fun Update.processCallbackActionType(
        actionType: CallbackActionType,
        bot: TelegramLongPollingBotExt,
        arguments: List<String>
    ) {
        val locale = locale(arguments)
        val callbackUserId = callbackQueryFromUserId().toString()
        val callbackMessageChatId = callbackQueryMessageChatId().toString()
        when (actionType) {
            RANDOM_CAT -> {
                redisDao.run {
                    get(callbackUserId)?.apply {
                        action = RANDOM_CAT
                        scheduleComplete = true
                    }?.also {
                        delete(it.userId)
                        save(it.keyValue(), it)
                        defaultScheduler.registerNewCronScheduleTask(it.cron.toString(), it.userId) {
                            catAction(it.chatId, catClient, bot)
                        }
                        bot.execute(
                            createEditMessage(
                                callbackQueryMessageId(),
                                messageSource.getMessage(
                                    "msg.done-schedule",
                                    arrayOf("${it.cron} ${it.action}"),
                                    locale
                                )
                            )
                        )
                    }
                }
            }

            RANDOM_CONTENT -> {
                redisDao.run {
                    get(callbackUserId)?.apply {
                        action = RANDOM_CONTENT
                    }?.also {
                        save(it.userId, it)
                        bot.execute(
                            createEditMessage(
                                callbackQueryMessageId(),
                                messageSource.get("command.schedule.button.random-content-waiting", locale)
                                    .withCommandMarker(commandName(), arguments),
                                callbackMessageChatId,
                            ) {
                                replyMarkup = oneButtonInlineKeyboard(
                                    messageSource.get("msg.waiting", locale),
                                    "random_content_waiting"
                                )
                            }
                        )
                    }
                }
            }

            NONE -> {} // do nothing
        }
    }

    private fun Update.processCallbackTimeType(
        timeType: CallbackTimeType,
        bot: TelegramLongPollingBotExt,
        arguments: List<String>
    ) {
        val (hour, minute) = when (timeType) {
            AM_12 -> "12" to "0"
            EVERY_HOUR -> "*" to "0"
            EVERY_MINUTE -> "*" to "*"
            WORK_TIME -> "9-21" to "0"
        }
        redisDao.run {
            get(callbackQueryFromUserId().toString())?.apply {
                cron.hour = hour
                cron.minute = minute
            }?.also { save(it.userId, it) }
        }
        actionStepEdit(bot, arguments)
    }

    private fun Update.processCallbackDayType(
        locale: Locale,
        dayType: CallbackDayType,
        bot: TelegramLongPollingBotExt,
        arguments: List<String>,
    ) {
        val callbackUserId = callbackQueryFromUserId().toString()
        val callbackMessageChatId = callbackQueryMessageChatId().toString()
        when (dayType) {
            EVERY_DAY -> redisDao.save(
                callbackUserId,
                ScheduleData(
                    locale = locale,
                    chatId = callbackMessageChatId,
                    userId = callbackUserId,
                    cron = CronData(minute = "?", hour = "?")
                )
            )

            WEEKDAYS -> redisDao.save(
                callbackUserId,
                ScheduleData(
                    locale = locale,
                    chatId = callbackMessageChatId,
                    userId = callbackUserId,
                    cron = CronData(minute = "?", hour = "?", dayOfWeek = "0-5")
                )
            )

            CUSTOM -> {
                redisDao.save(
                    callbackUserId,
                    ScheduleData(
                        locale = locale,
                        chatId = callbackMessageChatId,
                        userId = callbackUserId,
                        cron = CronData()
                    )
                )
                bot.execute(
                    createEditMessage(
                        callbackQueryMessageId(),
                        messageSource.get("command.schedule.button.custom-cron-text", locale)
                            .trimIndent()
                            .withCommandMarker(commandName(), arguments),
                        callbackMessageChatId,
                    ) {
                        replyMarkup =
                            oneButtonInlineKeyboard(messageSource.get("msg.waiting", locale), "custom_cron_waiting")
                    }
                )
            }
        }

        if (dayType != CUSTOM) {
            timeStepEdit(bot, arguments)
        }
    }

    private fun Update.deleteSchedule(bot: TelegramLongPollingBotExt, arguments: List<String>) {
        val locale = locale(arguments)
        val all = redisDao.getAll("${messageUserIdSafe()}*").filter { it.scheduleComplete }
        if (all.isEmpty()) {
            bot.execute(
                createEditMessage(
                    callbackQueryMessageId(),
                    messageSource.get("msg.schedule-list-empty", locale)
                )
            )
        } else {
            bot.execute(
                createEditMessage(
                    callbackQueryMessageId(),
                    messageSource.get("command.schedule.button.delete-task-text", locale)
                        .withCommandMarker(commandName(), arguments)
                ) {
                    replyMarkup = InlineKeyboardMarkup(
                        all.map {
                            listOf(
                                callbackButton(
                                    "${it.action} - ${it.cron}",
                                    "remove:${it.keyValue()}"
                                )
                            )
                        }
                    ).withCancelButton(messageSource.get("command.button.cancel.description", locale))
                }
            )
        }
    }

    private fun buildInitScheduleInlineKeyboard(locale: Locale) = InlineKeyboardMarkup(
        listOf(
            listOf(
                callbackButton(messageSource.get("command.schedule.button.every-day", locale), EVERY_DAY),
                callbackButton(messageSource.get("command.schedule.button.weekdays", locale), WEEKDAYS)
            ),
            listOf(
                callbackButton(messageSource.get("command.schedule.button.custom", locale), CUSTOM)
            )
        )
    )

    private fun buildStartInlineKeyboard(locale: Locale) = InlineKeyboardMarkup(
        listOf(
            listOf(
                callbackButton(messageSource.get("command.schedule.button.create", locale), CREATE),
                callbackButton(messageSource.get("command.schedule.button.delete", locale), DELETE)
            )
        )
    )

    private fun Update.actionStepEdit(
        bot: TelegramLongPollingBotExt,
        arguments: List<String>,
        messageId: Int = callbackQueryMessageId()
    ) {
        val locale = locale(arguments)
        bot.execute(
            createEditMessage(
                messageId,
                messageSource.get("command.schedule.button.action-text", locale)
                    .withCommandMarker(commandName(), arguments)
            ) {
                replyMarkup = InlineKeyboardMarkup(
                    listOf(
                        listOf(
                            callbackButton(messageSource.get("command.schedule.button.random-cat", locale), RANDOM_CAT),
                        ),
                        listOf(
                            callbackButton(
                                messageSource.get("command.schedule.button.custom-content", locale),
                                RANDOM_CONTENT
                            )
                        )
                    )
                )
            }
        )
    }

    private fun Update.timeStepEdit(bot: TelegramLongPollingBotExt, arguments: List<String>) {
        val locale = locale(arguments)
        bot.execute(
            createEditMessage(
                callbackQuery.message.messageId,
                messageSource.get("command.schedule.button.time-text", locale)
                    .withCommandMarker(commandName(), arguments)
            ) {
                replyMarkup = InlineKeyboardMarkup(
                    listOf(
                        listOf(
                            callbackButton(messageSource.get("command.schedule.button.every-hour", locale), EVERY_HOUR),
                            callbackButton(messageSource.get("command.schedule.button.every-minute", locale), EVERY_MINUTE)
                        ),
                        listOf(
                            callbackButton(messageSource.get("command.schedule.button.in-12-am", locale), AM_12),
                            callbackButton(messageSource.get("command.schedule.button.work-time", locale), WORK_TIME)
                        )
                    )
                ).withCancelButton(messageSource.get("command.button.cancel.description", locale))
            }
        )
    }

    private fun catAction(chatId: String, catClient: CatClient, bot: TelegramLongPollingBotExt) {
        val cat = catClient.getRandomCat()
        bot.execute(
            SendPhoto(
                chatId,
                InputFile(URL(cat.url).openStream(), "random_cat_${cat.id}")
            )
        )
    }

    private fun randomContent(type: RandomContentType, fileId: String, chatId: String, bot: TelegramLongPollingBotExt) {
        when (type) {
            ANIMATION -> bot.execute(SendAnimation(chatId, InputFile(fileId)))
            DOCUMENT -> bot.execute(SendDocument(chatId, InputFile(fileId)))
            PHOTO -> bot.execute(SendPhoto(chatId, InputFile(fileId)))
            STICKER -> bot.execute(SendSticker(chatId, InputFile(fileId)))
            TEXT -> bot.execute(SendMessage(chatId, fileId))
        }
    }


    private enum class CallbackModeType {
        CREATE,
        DELETE
    }

    private enum class CallbackDayType {
        EVERY_DAY,
        WEEKDAYS,
        CUSTOM
    }

    private enum class CallbackTimeType {
        EVERY_HOUR,
        EVERY_MINUTE,
        AM_12,
        WORK_TIME
    }

    enum class CallbackActionType {
        RANDOM_CAT,
        RANDOM_CONTENT,
        NONE
    }

    private enum class RandomContentType {
        ANIMATION,
        DOCUMENT,
        PHOTO,
        STICKER,
        TEXT
    }

}