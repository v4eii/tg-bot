package ru.vevteev.tgbot.bot.commands

import org.springframework.context.MessageSource
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow
import ru.vevteev.tgbot.bot.TelegramLongPollingBotExt
import ru.vevteev.tgbot.bot.commands.WeatherCommandExecutor.CallbackWeatherMode.CUSTOM
import ru.vevteev.tgbot.bot.commands.WeatherCommandExecutor.CallbackWeatherMode.FIVE_DAY
import ru.vevteev.tgbot.bot.commands.WeatherCommandExecutor.CallbackWeatherMode.FOURTH_DAY
import ru.vevteev.tgbot.bot.commands.WeatherCommandExecutor.CallbackWeatherMode.FULL
import ru.vevteev.tgbot.bot.commands.WeatherCommandExecutor.CallbackWeatherMode.ONE_DAY
import ru.vevteev.tgbot.bot.commands.WeatherCommandExecutor.CallbackWeatherMode.THREE_DAY
import ru.vevteev.tgbot.bot.commands.WeatherCommandExecutor.CallbackWeatherMode.TWO_DAY
import ru.vevteev.tgbot.client.WeatherClient
import ru.vevteev.tgbot.dto.WeatherDTO
import ru.vevteev.tgbot.dto.WeatherForecastDTO
import ru.vevteev.tgbot.dto.toShort
import ru.vevteev.tgbot.extension.CANCEL_DATA
import ru.vevteev.tgbot.extension.bold
import ru.vevteev.tgbot.extension.buildDefaultCommandKeyboard
import ru.vevteev.tgbot.extension.callbackButton
import ru.vevteev.tgbot.extension.callbackQueryData
import ru.vevteev.tgbot.extension.callbackQueryMessageId
import ru.vevteev.tgbot.extension.cancelHandler
import ru.vevteev.tgbot.extension.commandMarker
import ru.vevteev.tgbot.extension.coordinatePair
import ru.vevteev.tgbot.extension.createDeleteMessage
import ru.vevteev.tgbot.extension.createSendMessage
import ru.vevteev.tgbot.extension.getMessage
import ru.vevteev.tgbot.extension.isGroupOrSuperGroupSafe
import ru.vevteev.tgbot.extension.isSuperGroupMessage
import ru.vevteev.tgbot.extension.isSuperGroupMessageSafe
import ru.vevteev.tgbot.extension.locale
import ru.vevteev.tgbot.extension.messageId
import ru.vevteev.tgbot.extension.messageUserName
import ru.vevteev.tgbot.extension.replyMessageId
import ru.vevteev.tgbot.extension.space
import ru.vevteev.tgbot.extension.toLocalDate
import ru.vevteev.tgbot.extension.toLocalDateTime
import ru.vevteev.tgbot.extension.toZoneId
import ru.vevteev.tgbot.extension.valueOrAbsent
import ru.vevteev.tgbot.extension.withCancelButton
import java.time.Instant
import java.util.*

@Component
class WeatherCommandExecutor(
    private val weatherClient: WeatherClient,
    private val messageSource: MessageSource
) : CommandReplyExecutor, CommandCallbackExecutor {
    override fun commandName(): String = "weather"

    override fun commandDescription(locale: Locale): String =
        messageSource.getMessage("command.description.weather", locale)

    override fun perform(update: Update, bot: TelegramLongPollingBotExt, arguments: List<String>) {
        update.run {
            bot.execute(
                createSendMessage("${commandName().commandMarker(arguments)} Какой прогноз интересует?") {
                    replyMarkup = InlineKeyboardMarkup(
                        listOf(
                            listOf(
                                callbackButton("Один день", ONE_DAY),
                                callbackButton("Два дня", TWO_DAY),
                                callbackButton("Три дня", THREE_DAY),
                            ),
                            listOf(
                                callbackButton("Четыре дня", FOURTH_DAY),
                                callbackButton("Пять дней", FIVE_DAY),
//                                callbackButton("Свой период", CUSTOM) TODO
                            ),
                            listOf(
                                callbackButton("Полные текущие данные", FULL)
                            )
                        )
                    ).withCancelButton()
                }
            )
        }
    }

    override fun processReply(update: Update, bot: TelegramLongPollingBotExt, arguments: List<String>) {
        update.run {
            if (message.location != null) {
                sendWeatherMessage(arguments, bot, locale(arguments))
            }
        }
    }

    override fun processCallback(update: Update, bot: TelegramLongPollingBotExt, arguments: List<String>) {
        update.run {
            val data = callbackQueryData()
            val locale = locale(arguments)

            when (data) {
                CANCEL_DATA -> cancelHandler(bot)
                in CallbackWeatherMode.values().map { it.toString() } -> {
                    val mode = CallbackWeatherMode.valueOf(data)
                    if (mode == CUSTOM) {
                        bot.execute(
                            createSendMessage("Ответь на это сообщение количеством дней")
                        )
                    } else {
                        sendRequestLocation(bot, mode.argument, locale)
                    }
                }

                else -> {} // do nothing
            }
        }
    }

    private fun Update.sendRequestLocation(
        bot: TelegramLongPollingBotExt,
        argString: String,
        locale: Locale
    ) {
        bot.execute(createDeleteMessage(callbackQueryMessageId()))
        bot.execute(
            createSendMessage(
                messageSource.getMessage(
                    "msg.weather-location-request",
                    arrayOf(commandName().commandMarker(listOf(argString))),
                    locale
                )
            ) {
                replyMarkup = if (isGroupOrSuperGroupSafe()) {
                    null
                } else {
                    ReplyKeyboardMarkup().apply {
                        keyboard = listOf(
                            KeyboardRow(
                                listOf(
                                    KeyboardButton(
                                        messageSource.getMessage(
                                            "button.weather-location-request",
                                            locale
                                        )
                                    ).apply { requestLocation = true }
                                )
                            )
                        )
                        resizeKeyboard = true
                        oneTimeKeyboard = true
                    }
                }
            }
        )
    }

    private fun Update.sendWeatherMessage(
        arguments: List<String>,
        bot: TelegramLongPollingBotExt,
        locale: Locale
    ) {
        if ("full" == (arguments.firstOrNull() ?: "")) {
            val weather = weatherClient.getCurrentWeatherInfo(coordinatePair())
            bot.execute(
                createSendMessage(
                    messageSource.getMessage("msg.weather", weather.buildArrayParameters(messageUserName()), locale)
                ) {
                    enableMarkdown(true)
                    replyMarkup = bot.buildDefaultCommandKeyboard()
                }
            )
        } else {
            val weatherForecast = weatherClient.getWeatherInfo(
                coordinatePair(),
                arguments.getOrElse(0) { "8" }.toIntOrNull() ?: 8
            )
            bot.execute(
                createSendMessage(weatherForecast.buildTextMessage(locale)) {
                    enableMarkdown(true)
                    replyMarkup = bot.buildDefaultCommandKeyboard()
                }
            )
        }
        bot.execute(createDeleteMessage(messageId()))
        bot.execute(createDeleteMessage(replyMessageId()))
    }

    private fun WeatherForecastDTO.buildTextMessage(locale: Locale) = city.name?.bold()?.space(2) +
            list.map { it.toShort() }
                .groupBy { it.dt.toLocalDate(city.timezone.toZoneId()) }
                .map {
                    it.key.toString().bold().space(2) +
                            it.value.joinToString("\n\n") { dto ->
                                dto.dt
                                    .toLocalDateTime(city.timezone.toZoneId())
                                    .toLocalTime()
                                    .toString()
                                    .bold().space(1) +
                                        messageSource.getMessage(
                                            "msg.weather-short",
                                            arrayOf(
                                                dto.temp,
                                                dto.feelsLike,
                                                dto.weatherDescription?.replaceFirstChar { ch -> ch.uppercaseChar() },
                                                dto.pressure,
                                                dto.humidity
                                            ),
                                            locale
                                        )
                            }
                }.joinToString("\n\n")

    private fun WeatherDTO.buildArrayParameters(userName: String) = arrayOf(
        userName,
        name,
        main.temp,
        coord?.lat.valueOrAbsent(),
        coord?.lon.valueOrAbsent(),
        weather?.first()?.main.valueOrAbsent(),
        weather?.first()?.description.valueOrAbsent(),
        main.temp,
        main.feelsLike,
        main.humidity,
        main.pressure,
        wind?.speed.valueOrAbsent(),
        clouds?.all.valueOrAbsent(),
        visibility,
        Instant.ofEpochSecond((sys?.sunset?.toLong() ?: 0) + timezone),
        Instant.ofEpochSecond((sys?.sunrise?.toLong() ?: 0) + timezone),
        dt.toString(),
        Instant.ofEpochSecond(dt.toLong()),
        Instant.ofEpochSecond(dt.toLong() + timezone)
    )

    enum class CallbackWeatherMode(internal val argument: String) {
        FULL("full"),
        ONE_DAY(ONE_DAY_CHUNKS.toString()),
        TWO_DAY(ONE_DAY_CHUNKS.times(2).toString()),
        THREE_DAY(ONE_DAY_CHUNKS.times(3).toString()),
        FOURTH_DAY(ONE_DAY_CHUNKS.times(4).toString()),
        FIVE_DAY("38"), // cause max
        CUSTOM("custom")
    }

    companion object {
        const val ONE_DAY_CHUNKS = 8
    }

}