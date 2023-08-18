package ru.vevteev.tgbot.bot.commands

import org.springframework.context.MessageSource
import org.springframework.stereotype.Component
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardRemove
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow
import ru.vevteev.tgbot.client.WeatherClient
import ru.vevteev.tgbot.dto.toShort
import ru.vevteev.tgbot.extension.bold
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
import ru.vevteev.tgbot.extension.userName
import ru.vevteev.tgbot.extension.valueOrAbsent
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.util.*
import kotlin.math.absoluteValue

@Component
class WeatherCommandExecutor(private val weatherClient: WeatherClient, private val messageSource: MessageSource) :
    CommandExecutor {
    override fun commandName(): String = "weather"

    override fun commandDescription(locale: Locale): String =
        messageSource.getMessage("command.description.weather", locale)

    override fun perform(update: Update, bot: TelegramLongPollingBot, arguments: List<String>) {
        update.run {
            val locale = locale(arguments)
            if (message.location != null) {
                if ("c" == (arguments.firstOrNull() ?: "")) {
                    val weather = weatherClient.getCurrentWeatherInfo(coordinatePair())
                    bot.execute(
                        createSendMessage(
                            messageSource.getMessage(
                                "msg.weather",
                                arrayOf(
                                    userName(),
                                    weather.name,
                                    weather.main.temp,
                                    weather.coord?.lat.valueOrAbsent(),
                                    weather.coord?.lon.valueOrAbsent(),
                                    weather.weather?.first()?.main.valueOrAbsent(),
                                    weather.weather?.first()?.description.valueOrAbsent(),
                                    weather.main.temp,
                                    weather.main.feelsLike,
                                    weather.main.humidity,
                                    weather.main.pressure,
                                    weather.wind?.speed.valueOrAbsent(),
                                    weather.clouds?.all.valueOrAbsent(),
                                    weather.visibility,
                                    Instant.ofEpochSecond((weather.sys?.sunset?.toLong() ?: 0) + weather.timezone),
                                    Instant.ofEpochSecond((weather.sys?.sunrise?.toLong() ?: 0) + weather.timezone),
                                    weather.dt.toString(),
                                    Instant.ofEpochSecond(weather.dt.toLong()),
                                    Instant.ofEpochSecond(weather.dt.toLong() + weather.timezone)
                                ),
                                locale
                            )
                        ) {
                            enableMarkdown(true)
                            replyMarkup = ReplyKeyboardRemove(true)
                        }
                    )
                } else {
                    val weatherForecast =
                        weatherClient.getWeatherInfo(coordinatePair(), arguments.getOrElse(0) { "8" }.toIntOrNull() ?: 8)
                    bot.execute(
                        createSendMessage(
                            weatherForecast.city.name?.bold()?.space(2) +
                                    weatherForecast.list
                                        .map { it.toShort() }
                                        .groupBy { it.dt.toLocalDate(weatherForecast.city.timezone.toZoneId()) }
                                        .map {
                                            it.key.toString().bold().space(2) +
                                                    it.value.joinToString("\n\n") { dto ->
                                                        dto.dt
                                                            .toLocalDateTime(weatherForecast.city.timezone.toZoneId())
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
                        ) {
                            enableMarkdown(true)
                            replyMarkup = ReplyKeyboardRemove(true)
                        }
                    )
                }
                bot.execute(createDeleteMessage(messageId()))
                bot.execute(createDeleteMessage(replyMessageId()))
            } else {
                bot.execute(
                    createSendMessage(
                        messageSource.getMessage("msg.weather-location-request", arrayOf(text()), locale)
                    ) {
                        replyMarkup = ReplyKeyboardMarkup().apply {
                            keyboard = listOf(
                                KeyboardRow(
                                    listOf(
                                        KeyboardButton("Send your location").apply { requestLocation = true }
                                    )
                                )
                            )
                            resizeKeyboard = true
                            oneTimeKeyboard = true
                        }
                    }
                )
            }
        }
    }

    fun Int.toZoneId(): ZoneId =
        ZoneId.of(
            if (this > 0) {
                "+" + LocalTime.ofSecondOfDay(this.toLong()).toString()
            } else {
                "-" + LocalTime.ofSecondOfDay(this.absoluteValue.toLong()).toString()
            }
        )

}