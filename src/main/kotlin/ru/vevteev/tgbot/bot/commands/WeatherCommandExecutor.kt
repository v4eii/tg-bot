package ru.vevteev.tgbot.bot.commands

import org.springframework.context.MessageSource
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow
import ru.vevteev.tgbot.bot.TelegramLongPollingBotExt
import ru.vevteev.tgbot.bot.commands.WeatherCommandExecutor.CallbackWeatherMode.FIVE_DAY
import ru.vevteev.tgbot.bot.commands.WeatherCommandExecutor.CallbackWeatherMode.FOURTH_DAY
import ru.vevteev.tgbot.bot.commands.WeatherCommandExecutor.CallbackWeatherMode.FULL
import ru.vevteev.tgbot.bot.commands.WeatherCommandExecutor.CallbackWeatherMode.ONE_DAY
import ru.vevteev.tgbot.bot.commands.WeatherCommandExecutor.CallbackWeatherMode.THREE_DAY
import ru.vevteev.tgbot.bot.commands.WeatherCommandExecutor.CallbackWeatherMode.TWO_DAY
import ru.vevteev.tgbot.client.WeatherClient
import ru.vevteev.tgbot.dto.WeatherDTO
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
import ru.vevteev.tgbot.extension.withCommandMarker
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
            val locale = locale(arguments)
            bot.execute(
                createSendMessage(
                    messageSource.getMessage("command.weather.button.forecast-text", locale(arguments))
                        .withCommandMarker(commandName(), arguments)
                ) {
                    replyMarkup = InlineKeyboardMarkup(
                        listOf(
                            listOf(
                                callbackButton(
                                    messageSource.getMessage("command.weather.button.forecast-one", locale),
                                    ONE_DAY
                                ),
                                callbackButton(
                                    messageSource.getMessage("command.weather.button.forecast-two", locale),
                                    TWO_DAY
                                ),
                                callbackButton(
                                    messageSource.getMessage("command.weather.button.forecast-three", locale),
                                    THREE_DAY
                                ),
                            ),
                            listOf(
                                callbackButton(
                                    messageSource.getMessage("command.weather.button.forecast-fourth", locale),
                                    FOURTH_DAY
                                ),
                                callbackButton(
                                    messageSource.getMessage("command.weather.button.forecast-five", locale),
                                    FIVE_DAY
                                )
                            ),
                            listOf(
                                callbackButton(
                                    messageSource.getMessage("command.weather.button.forecast-full", locale),
                                    FULL
                                )
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
                    sendRequestLocation(
                        bot,
                        listOf(
                            CallbackWeatherMode.valueOf(data).argument,
                            locale.language
                        ),
                        locale
                    )
                }

                else -> {} // do nothing
            }
        }
    }

    private fun Update.sendRequestLocation(
        bot: TelegramLongPollingBotExt,
        arguments: List<String>,
        locale: Locale
    ) {
        bot.execute(createDeleteMessage(callbackQueryMessageId()))
        bot.execute(
            createSendMessage(
                messageSource.getMessage(
                    "msg.weather-location-request",
                    arrayOf(commandName().commandMarker(arguments)),
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
                                            "command.weather.button.location-request",
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
            val weather = weatherClient.getCurrentWeatherInfo(coordinatePair(), locale)
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
                arguments.getOrElse(0) { "8" }.toIntOrNull() ?: 8,
                locale
            )
            weatherForecast.list
                .chunked(30)
                .forEach {
                    bot.execute(
                        createSendMessage(
                            it.buildTextMessage(
                                weatherForecast.city.name,
                                weatherForecast.city.timezone,
                                locale
                            )
                        ) {
                            enableMarkdown(true)
                            replyMarkup = bot.buildDefaultCommandKeyboard()
                        }
                    )
                }
        }
        bot.execute(createDeleteMessage(messageId()))
        bot.execute(createDeleteMessage(replyMessageId()))
    }

    private fun List<WeatherDTO>.buildTextMessage(
        cityName: String?,
        cityTimeZone: Int,
        locale: Locale
    ) = cityName?.bold()?.space(2) +
            this.map { it.toShort() }
                .groupBy { it.dt.toLocalDate(cityTimeZone.toZoneId()) }
                .map {
                    it.key.toString().bold().space(2) +
                            it.value.joinToString("\n\n") { dto ->
                                dto.dt
                                    .toLocalDateTime(cityTimeZone.toZoneId())
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
        FIVE_DAY(ONE_DAY_CHUNKS.times(5).toString())
    }

    companion object {
        const val ONE_DAY_CHUNKS = 8
    }

}