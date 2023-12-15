package ru.vevteev.tgbot.bot.commands

import org.springframework.context.MessageSource
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageMedia
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton
import ru.vevteev.tgbot.bot.TelegramLongPollingBotExt
import ru.vevteev.tgbot.dto.ScheduleData
import ru.vevteev.tgbot.extension.chatId
import ru.vevteev.tgbot.extension.createEditMessage
import ru.vevteev.tgbot.extension.createSendMessage
import ru.vevteev.tgbot.extension.getMessage
import ru.vevteev.tgbot.extension.locale
import ru.vevteev.tgbot.repository.RedisDrinkDao
import ru.vevteev.tgbot.repository.RedisScheduleDao
import ru.vevteev.tgbot.schedule.DefaultScheduler
import java.util.*

@Component
class ScheduleCommandExecutor(
    private val messageSource: MessageSource,
    private val defaultScheduler: DefaultScheduler,
    private val redisDao: RedisScheduleDao
) : CommandCallbackExecutor {
    override fun commandName() = "schedule"

    override fun commandDescription(locale: Locale) = messageSource.getMessage("command.description.schedule", locale)

    override fun perform(update: Update, bot: TelegramLongPollingBotExt, arguments: List<String>) {
        update.run {
            val locale = locale(arguments)
            bot.execute(
                createSendMessage("/${commandName()}| Давай определимся что будем делать") {
                    replyMarkup = InlineKeyboardMarkup().apply {
                        keyboard = listOf(
                            listOf(
                                InlineKeyboardButton().apply {
                                    text = "Каждый день"
                                    callbackData = CallbackDayType.EVERY_DAY.toString()
                                },
                                InlineKeyboardButton().apply {
                                    text = "По будням"
                                    callbackData = CallbackDayType.WEEKDAYS.toString()
                                }
                            ),
                            listOf(
                                InlineKeyboardButton().apply {
                                    text = "Своя настройка"
                                    callbackData = CallbackDayType.CUSTOM.toString()
                                }
                            ),
                        )
                    }
                }
            )
        }
    }

    override fun processCallback(update: Update, bot: TelegramLongPollingBotExt, arguments: List<String>) {
        update.run {
            val msg = callbackQuery.data
            val locale = locale(arguments)
            when (msg) {
                CallbackDayType.EVERY_DAY.toString() -> {
                    redisDao.save(
                        callbackQuery.from.id.toString(),
                        ScheduleData(
                            locale = locale,
                            chatId = callbackQuery.message.chat.id.toString(),
                            cron = mutableMapOf(
                                "sec" to "0",
                                "min" to "?",
                                "hour" to "?",
                                "dayOfMonth" to "*",
                                "month" to "*",
                                "dayOfWeek" to "*"
                            ),
                            action = "none"
                        )
                    )
                }

                CallbackDayType.WEEKDAYS.toString() -> {
                    redisDao.save(
                        callbackQuery.from.id.toString(),
                        ScheduleData(
                            locale = locale,
                            chatId = callbackQuery.message.chat.id.toString(),
                            cron = mutableMapOf(
                                "sec" to "0",
                                "min" to "?",
                                "hour" to "?",
                                "dayOfMonth" to "*",
                                "month" to "*",
                                "dayOfWeek" to "0-5"
                            ),
                            action = "none"
                        )
                    )
                    bot.execute(
                        createEditMessage(
                            callbackQuery.message.messageId,
                            callbackQuery.message.chat.id.toString(),
                            "/${commandName()}| Хорошо, теперь определимся с временем"
                        ) {
                            replyMarkup = InlineKeyboardMarkup().apply {
                                keyboard = listOf(
                                    listOf(
                                        InlineKeyboardButton().apply {
                                            text = "Каждый час"
                                            callbackData = "everyHour"
                                        },
                                        InlineKeyboardButton().apply {
                                            text = "В 12 дня"
                                            callbackData = "12AM"
                                        }
                                    )
                                )
                            }
                        }
                    )
                }

                CallbackDayType.CUSTOM.toString() -> TODO("not implemented yet")

                "12AM" -> {
                    val data = redisDao.get(callbackQuery.from.id.toString())!!.apply {
                        cron["hour"] = "12"
                        cron["min"] = "0"
                    }
                    redisDao.save(
                        callbackQuery.from.id.toString(),
                        data
                    )
                    defaultScheduler.registerNewCronScheduleTask(data.cron.values.joinToString(" ")) {
                        bot.execute(createSendMessage("Тестим"))
                    }
                }

                else -> println()
            }
        }
    }

    enum class CallbackDayType {
        EVERY_DAY,
        WEEKDAYS,
        CUSTOM
    }

}