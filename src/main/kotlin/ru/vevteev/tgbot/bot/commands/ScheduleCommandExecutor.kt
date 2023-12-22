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
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton
import ru.vevteev.tgbot.bot.TelegramLongPollingBotExt
import ru.vevteev.tgbot.bot.commands.ScheduleCommandExecutor.CallbackActionType.NONE
import ru.vevteev.tgbot.bot.commands.ScheduleCommandExecutor.CallbackActionType.RANDOM_CAT
import ru.vevteev.tgbot.bot.commands.ScheduleCommandExecutor.CallbackActionType.RANDOM_CONTENT
import ru.vevteev.tgbot.bot.commands.ScheduleCommandExecutor.CallbackDayType.CUSTOM
import ru.vevteev.tgbot.bot.commands.ScheduleCommandExecutor.CallbackDayType.EVERY_DAY
import ru.vevteev.tgbot.bot.commands.ScheduleCommandExecutor.CallbackDayType.WEEKDAYS
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
import ru.vevteev.tgbot.extension.callbackQueryFromUserId
import ru.vevteev.tgbot.extension.callbackQueryData
import ru.vevteev.tgbot.extension.callbackQueryMessageChatId
import ru.vevteev.tgbot.extension.callbackQueryMessageId
import ru.vevteev.tgbot.extension.createDeleteMessage
import ru.vevteev.tgbot.extension.createEditMessage
import ru.vevteev.tgbot.extension.createSendMessage
import ru.vevteev.tgbot.extension.getMessage
import ru.vevteev.tgbot.extension.isReply
import ru.vevteev.tgbot.extension.isReplyMessageCommand
import ru.vevteev.tgbot.extension.isReplyMessageWithInlineMarkup
import ru.vevteev.tgbot.extension.locale
import ru.vevteev.tgbot.extension.messageUserId
import ru.vevteev.tgbot.extension.replyMessageId
import ru.vevteev.tgbot.repository.RedisScheduleDao
import ru.vevteev.tgbot.schedule.DefaultScheduler
import java.net.URL
import java.util.*

@Component
class ScheduleCommandExecutor(
    private val messageSource: MessageSource,
    private val defaultScheduler: DefaultScheduler,
    private val redisDao: RedisScheduleDao,
    private val catClient: CatClient, // TODO move to collection
) : CommandCallbackExecutor {
    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun commandName() = "schedule"

    override fun commandDescription(locale: Locale) = messageSource.getMessage("command.description.schedule", locale)

    override fun init(bot: TelegramLongPollingBotExt) {
        redisDao.getAll("*").forEach {
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
        logger.info("Init all schedules")
    }

    override fun perform(update: Update, bot: TelegramLongPollingBotExt, arguments: List<String>) {
        update.run {
            val locale = locale(arguments) // TODO
            if ("-d" in arguments) {
                val all = redisDao.getAll("${messageUserId()}*")
                if (all.isEmpty()) {
                    bot.execute(
                        createSendMessage("У тебя нет ни одной созданной задачи")
                    )
                } else {
                    bot.execute(
                        createSendMessage("/${commandName()}| Выбери какую задачу хочешь удалить") {
                            replyMarkup = InlineKeyboardMarkup(
                                all.map {
                                    listOf(
                                        callbackButton(
                                            "${it.action.description} - ${it.cron}",
                                            "remove:${it.keyValue()}"
                                        )
                                    )
                                }
                            )
                        }
                    )
                }
            } else if (isReply() && isReplyMessageCommand() && isReplyMessageWithInlineMarkup()) {
                replyProcess(bot)
            } else {
                bot.execute(
                    createSendMessage("/${commandName()}| Давай определимся что будем делать") {
                        replyMarkup = buildInitInlineKeyboard()
                    }
                )
            }
            return
        }
    }

    override fun processCallback(update: Update, bot: TelegramLongPollingBotExt, arguments: List<String>) {
        update.run {
            val data = callbackQueryData()
            val locale = locale(arguments)
            val callbackUserId = callbackQueryFromUserId().toString()
            val callbackMessageId = callbackQueryMessageId()
            val callbackMessageChatId = callbackQueryMessageChatId().toString()
            when {
                data in CallbackDayType.values().map { it.toString() } -> {
                    processCallbackDayType(
                        locale,
                        CallbackDayType.valueOf(data),
                        callbackUserId,
                        callbackMessageId,
                        callbackMessageChatId,
                        bot
                    )
                    timeStepEdit(bot)
                }

                data in CallbackTimeType.values().map { it.toString() } -> {
                    processCallbackTimeType(CallbackTimeType.valueOf(data), callbackUserId)
                    actionStepEdit(bot)
                }

                data in CallbackActionType.values().map { it.toString() } -> {
                    processCallbackActionType(
                        CallbackActionType.valueOf(data),
                        callbackUserId,
                        callbackMessageId,
                        callbackMessageChatId,
                        bot
                    )
                }

                data.contains("remove:.*".toRegex()) -> {
                    val key = data.split(":", limit = 2)[1]
                    redisDao.run {
                        get(key)?.also {
                            delete(key)
                            defaultScheduler.removeCronSchedule(it.cron.toString(), it.userId)
                        }
                    }
                    bot.execute(
                        createEditMessage(callbackMessageId, callbackMessageChatId, "Готово!")
                    )
                }
            }
            return
        }
    }

    private fun processCallbackActionType(
        actionType: CallbackActionType,
        callbackUserId: String,
        callbackMessageId: Int,
        callbackMessageChatId: String,
        bot: TelegramLongPollingBotExt
    ) {
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
                                callbackMessageId,
                                callbackMessageChatId,
                                "Окей! Вот твоя задача ${it.cron} ${it.action.description}"
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
                                callbackMessageId,
                                callbackMessageChatId,
                                "/${commandName()}| Окей, ответь на это сообщение тем самым контентом"
                            ) {
                                replyMarkup = InlineKeyboardMarkup(
                                    listOf(
                                        listOf(
                                            callbackButton("Я жду", "random_content_waiting")
                                        )
                                    )
                                )
                            }
                        )
                    }
                }
            }

            NONE -> {} // do nothing
        }
    }

    private fun processCallbackTimeType(timeType: CallbackTimeType, callbackUserId: String) {
        val (hour, minute) = when (timeType) {
            AM_12 -> "12" to "0"
            EVERY_HOUR -> "*" to "0"
            EVERY_MINUTE -> "*" to "*"
            WORK_TIME -> "9-21" to "0"
        }
        redisDao.run {
            get(callbackUserId)?.apply {
                cron.hour = hour
                cron.minute = minute
            }?.also { save(it.userId, it) }
        }
    }

    private fun Update.processCallbackDayType(
        locale: Locale,
        dayType: CallbackDayType,
        callbackUserId: String,
        callbackMessageId: Int,
        callbackMessageChatId: String,
        bot: TelegramLongPollingBotExt
    ) {
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

            CUSTOM -> bot.execute( // TODO tmp
                createEditMessage(
                    callbackMessageId,
                    callbackMessageChatId,
                    callbackQuery.message.text + " (Ну реально не работает)"
                ) {
                    replyMarkup = buildInitInlineKeyboard()
                }
            )
        }
    }

    private fun Update.replyProcess(bot: TelegramLongPollingBotExt) {
        if (message.replyToMessage?.replyMarkup?.keyboard?.firstOrNull()
                ?.firstOrNull()?.callbackData == "random_content_waiting"
        ) {
            redisDao.run {
                get(message.from.id.toString())?.apply {
                    val (type, s) = if (message.hasAnimation()) {
                        ANIMATION to message.animation.fileId
                    } else if (message.hasDocument()) {
                        DOCUMENT to message.document.fileId
                    } else if (message.hasPhoto()) {
                        PHOTO to message.photo.first().fileId
                    } else if (message.hasSticker()) {
                        STICKER to message.sticker.fileId
                    } else {
                        TEXT to message.text
                    }
                    actionDescription = "$type:$s"
                    scheduleComplete = true
                }?.also {
                    delete(it.userId)
                    save(it.keyValue(), it)
                    defaultScheduler.registerNewCronScheduleTask(it.cron.toString(), it.userId) {
                        val (type, fileId) = it.actionDescription.split(":")
                        randomContent(RandomContentType.valueOf(type), fileId, it.chatId, bot)
                    }
                    bot.sendMsg(
                        message.replyToMessage.chat.id.toString(),
                        "Окей! Вот твоя задача ${it.cron} ${it.action.description} ${
                            it.actionDescription.split(":").first()
                        }"
                    )
                    bot.execute(createDeleteMessage(replyMessageId()))
                }
            }
        }
    }

    private fun buildInitInlineKeyboard() = InlineKeyboardMarkup(
        listOf(
            listOf(
                callbackButton("Каждый день", EVERY_DAY),
                callbackButton("По будням", WEEKDAYS)
            ),
            listOf(
                callbackButton("Своя настройка (пока не работает)", CUSTOM)
            ),
        )
    )

    private fun Update.actionStepEdit(bot: TelegramLongPollingBotExt) =
        bot.execute(
            createEditMessage(
                callbackQuery.message.messageId,
                callbackQuery.message.chat.id.toString(),
                "/${commandName()}| Что будем делать?"
            ) {
                replyMarkup = InlineKeyboardMarkup(
                    listOf(
                        listOf(
                            callbackButton("Пикча рандомного кота", RANDOM_CAT),
                        ),
                        listOf(
                            callbackButton("Отправлять контент (gif, img, text)", RANDOM_CONTENT)
                        )
                    )
                )
            }
        )

    private fun Update.timeStepEdit(bot: TelegramLongPollingBotExt) =
        bot.execute(
            createEditMessage(
                callbackQuery.message.messageId,
                callbackQuery.message.chat.id.toString(),
                "/${commandName()}| Хорошо, теперь определимся с временем"
            ) {
                replyMarkup = InlineKeyboardMarkup(
                    listOf(
                        listOf(
                            callbackButton("Каждый час", EVERY_HOUR),
                            callbackButton("Каждую минуту", EVERY_MINUTE)
                        ),
                        listOf(
                            callbackButton("В 12 дня", AM_12),
                            callbackButton("С 9 утра до 9 вечера", WORK_TIME)
                        )
                    )
                )
            }
        )

    private fun callbackButton(text: String, callbackData: Enum<*>) =
        InlineKeyboardButton(text).apply { this.callbackData = callbackData.toString() }

    private fun callbackButton(text: String, callbackData: String) =
        InlineKeyboardButton(text).apply { this.callbackData = callbackData }

    fun catAction(chatId: String, catClient: CatClient, bot: TelegramLongPollingBotExt) {
        val cat = catClient.getRandomCat()
        bot.execute(
            SendPhoto(
                chatId,
                InputFile(URL(cat.url).openStream(), "random_cat_${cat.id}")
            )
        )
    }

    fun randomContent(type: RandomContentType, fileId: String, chatId: String, bot: TelegramLongPollingBotExt) {
        when (type) {
            ANIMATION -> bot.execute(SendAnimation(chatId, InputFile(fileId)))
            DOCUMENT -> bot.execute(SendDocument(chatId, InputFile(fileId)))
            PHOTO -> bot.execute(SendPhoto(chatId, InputFile(fileId)))
            STICKER -> bot.execute(SendSticker(chatId, InputFile(fileId)))
            TEXT -> bot.execute(SendMessage(chatId, fileId))
        }
    }

    enum class CallbackDayType {
        EVERY_DAY,
        WEEKDAYS,
        CUSTOM
    }

    enum class CallbackTimeType {
        EVERY_HOUR,
        EVERY_MINUTE,
        AM_12,
        WORK_TIME
    }

    enum class CallbackActionType(val description: String) {
        RANDOM_CAT("Пикча кота"),
        RANDOM_CONTENT("Собственный контент"),
        NONE("Нет действия")
    }

    enum class RandomContentType {
        ANIMATION,
        DOCUMENT,
        PHOTO,
        STICKER,
        TEXT
    }

}