package ru.vevteev.tgbot.bot.commands

import org.springframework.context.MessageSource
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.methods.send.SendAnimation
import org.telegram.telegrambots.meta.api.methods.send.SendDocument
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto
import org.telegram.telegrambots.meta.api.objects.InputFile
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton
import ru.vevteev.tgbot.bot.TelegramLongPollingBotExt
import ru.vevteev.tgbot.bot.commands.ScheduleCommandExecutor.CallbackActionType.NONE
import ru.vevteev.tgbot.bot.commands.ScheduleCommandExecutor.CallbackActionType.RANDOM_CAT
import ru.vevteev.tgbot.bot.commands.ScheduleCommandExecutor.CallbackActionType.RANDOM_CONTENT
import ru.vevteev.tgbot.bot.commands.ScheduleCommandExecutor.CallbackActionType.valueOf
import ru.vevteev.tgbot.bot.commands.ScheduleCommandExecutor.CallbackDayType.CUSTOM
import ru.vevteev.tgbot.bot.commands.ScheduleCommandExecutor.CallbackDayType.EVERY_DAY
import ru.vevteev.tgbot.bot.commands.ScheduleCommandExecutor.CallbackDayType.WEEKDAYS
import ru.vevteev.tgbot.bot.commands.ScheduleCommandExecutor.CallbackTimeType.AM_12
import ru.vevteev.tgbot.bot.commands.ScheduleCommandExecutor.CallbackTimeType.EVERY_HOUR
import ru.vevteev.tgbot.bot.commands.ScheduleCommandExecutor.CallbackTimeType.EVERY_MINUTE
import ru.vevteev.tgbot.bot.commands.ScheduleCommandExecutor.CallbackTimeType.WORK_TIME
import ru.vevteev.tgbot.client.CatClient
import ru.vevteev.tgbot.dto.CronData
import ru.vevteev.tgbot.dto.ScheduleData
import ru.vevteev.tgbot.extension.createDeleteMessage
import ru.vevteev.tgbot.extension.createEditMessage
import ru.vevteev.tgbot.extension.createSendMessage
import ru.vevteev.tgbot.extension.getMessage
import ru.vevteev.tgbot.extension.isReply
import ru.vevteev.tgbot.extension.isReplyCommand
import ru.vevteev.tgbot.extension.locale
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
    override fun commandName() = "schedule"

    override fun commandDescription(locale: Locale) = messageSource.getMessage("command.description.schedule", locale)

    override fun perform(update: Update, bot: TelegramLongPollingBotExt, arguments: List<String>) {
        update.run {
            val locale = locale(arguments) // TODO
            if (isReply() && isReplyCommand() && message.replyToMessage?.replyMarkup != null) {
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
            val msg = callbackQuery.data
            val locale = locale(arguments)
            val callbackUserId = callbackQuery.from.id.toString()
            val callbackMessageId = callbackQuery.message.messageId
            val callbackMessageChatId = callbackQuery.message.chat.id.toString()
            when (msg) {
                in CallbackDayType.values().map { it.toString() } -> {
                    when (CallbackDayType.valueOf(msg)) {
                        EVERY_DAY -> redisDao.save(
                            callbackUserId,
                            ScheduleData(
                                locale = locale,
                                chatId = callbackMessageChatId,
                                cron = CronData(minute = "?", hour = "?")
                            )
                        )
                        WEEKDAYS -> redisDao.save(
                            callbackUserId,
                            ScheduleData(
                                locale = locale,
                                chatId = callbackMessageChatId,
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
                    timeStepEdit(bot)
                }

                in CallbackTimeType.values().map { it.toString() } -> {
                    val (hour, minute) = when (CallbackTimeType.valueOf(msg)) {
                        AM_12 -> "12" to "0"
                        EVERY_HOUR -> "*" to "0"
                        EVERY_MINUTE -> "*" to "*"
                        WORK_TIME -> "9-21" to "0"
                    }
                    redisDao.run {
                        get(callbackUserId)?.apply {
                            cron.hour = hour
                            cron.minute = minute
                        }?.also { save(callbackUserId, it) }
                    }
                    actionStepEdit(bot)
                }

                in CallbackActionType.values().map { it.toString() } -> {
                    when (valueOf(msg)) {
                        RANDOM_CAT -> {
                            redisDao.run {
                                get(callbackUserId)?.apply {
                                    action = RANDOM_CAT
                                    scheduleComplete = true
                                }?.also {
                                    save(callbackUserId, it)
                                    defaultScheduler.registerNewCronScheduleTask(it.cron.toString()) {
                                        val cat = catClient.getRandomCat()
                                        bot.execute(
                                            SendPhoto(
                                                it.chatId,
                                                InputFile(URL(cat.url).openStream(), "random_cat_${cat.id}")
                                            )
                                        )
                                    }
                                    bot.execute(
                                        createEditMessage(
                                            callbackMessageId,
                                            callbackMessageChatId,
                                            "Окей! Вот твоя задача ${it.action} ${it.cron}"
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
                                    save(callbackUserId, it)
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
            }
            return
        }
    }

    private fun Update.replyProcess(bot: TelegramLongPollingBotExt) {
        if (message.replyToMessage?.replyMarkup?.keyboard?.firstOrNull()
                ?.firstOrNull()?.callbackData == "random_content_waiting"
        ) {
            redisDao.run {
                get(message.from.id.toString())?.apply {
                    val (type, s) = if (message.hasAnimation()) {
                        "anim" to message.animation.fileId
                    } else if (message.hasDocument()) {
                        "doc" to message.document.fileId
                    } else if (message.hasPhoto()) {
                        "photo" to message.photo.first().fileId
                    } else {
                        "text" to message.text
                    }
                    actionDescription = "$type:$s"
                    scheduleComplete = true
                }?.also {
                    save(it.chatId, it)
                    defaultScheduler.registerNewCronScheduleTask(it.cron.toString()) {
                        val (type, fileId) = it.actionDescription.split(":")
                        when (type) {
                            "anim" -> bot.execute(SendAnimation(it.chatId, InputFile(fileId)))
                            "doc" -> bot.execute(SendDocument(it.chatId, InputFile(fileId)))
                            "photo" -> bot.execute(SendPhoto(it.chatId, InputFile(fileId)))
                            else -> bot.execute(SendMessage(it.chatId, fileId))
                        }
                    }
                    bot.sendMsg(
                        message.replyToMessage.chat.id.toString(),
                        "Окей! Вот твоя задача ${it.action} ${it.cron} ${
                            it.actionDescription.split(":").first()
                        }"
                    )
                    bot.execute(createDeleteMessage(message.replyToMessage.messageId))
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

    enum class CallbackActionType {
        RANDOM_CAT,
        RANDOM_CONTENT,
        NONE
    }

}