package ru.vevteev.tgbot.bot.commands

import org.springframework.context.MessageSource
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow
import ru.vevteev.tgbot.bot.TelegramLongPollingBotExt
import ru.vevteev.tgbot.client.WeatherClient
import ru.vevteev.tgbot.dto.WeatherDTO
import ru.vevteev.tgbot.dto.WeatherForecastDTO
import ru.vevteev.tgbot.dto.toShort
import ru.vevteev.tgbot.extension.bold
import ru.vevteev.tgbot.extension.buildDefaultKeyboard
import ru.vevteev.tgbot.extension.coordinatePair
import ru.vevteev.tgbot.extension.createDeleteMessage
import ru.vevteev.tgbot.extension.createSendMessage
import ru.vevteev.tgbot.extension.getMessage
import ru.vevteev.tgbot.extension.locale
import ru.vevteev.tgbot.extension.messageId
import ru.vevteev.tgbot.extension.replyMessageId
import ru.vevteev.tgbot.extension.space
import ru.vevteev.tgbot.extension.text
import ru.vevteev.tgbot.extension.toLocalDate
import ru.vevteev.tgbot.extension.toLocalDateTime
import ru.vevteev.tgbot.extension.toZoneId
import ru.vevteev.tgbot.extension.userName
import ru.vevteev.tgbot.extension.valueOrAbsent
import java.time.Instant
import java.util.*

@Component
class WeatherCommandExecutor(private val weatherClient: WeatherClient, private val messageSource: MessageSource) :
    CommandExecutor {
    override fun commandName(): String = "weather"

    override fun commandDescription(locale: Locale): String =
        messageSource.getMessage("command.description.weather", locale)

    override fun perform(update: Update, bot: TelegramLongPollingBotExt, arguments: List<String>) {
        update.run {
            val locale = locale(arguments)
            if (message.location != null) {
                sendWeatherMessage(arguments, bot, locale)
            } else {
                sendRequestLocationMessage(bot, locale)
            }
        }
    }

    private fun Update.sendRequestLocationMessage(
        bot: TelegramLongPollingBotExt,
        locale: Locale
    ) {
        bot.execute(
            createSendMessage(
                messageSource.getMessage("msg.weather-location-request", arrayOf(text()), locale)
            ) {
                replyMarkup = ReplyKeyboardMarkup().apply {
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
        )
    }

    private fun Update.sendWeatherMessage(
        arguments: List<String>,
        bot: TelegramLongPollingBotExt,
        locale: Locale
    ) {
        if ("c" == (arguments.firstOrNull() ?: "")) {
            val weather = weatherClient.getCurrentWeatherInfo(coordinatePair())
            bot.execute(
                createSendMessage(
                    messageSource.getMessage("msg.weather", weather.buildArrayParameters(userName()), locale)
                ) {
                    enableMarkdown(true)
                    replyMarkup = bot.buildDefaultKeyboard()
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
                    replyMarkup = bot.buildDefaultKeyboard()
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

}