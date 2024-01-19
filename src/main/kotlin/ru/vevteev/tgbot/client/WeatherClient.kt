package ru.vevteev.tgbot.client

import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import ru.vevteev.tgbot.dto.WeatherDTO
import ru.vevteev.tgbot.dto.WeatherForecastDTO
import java.util.*

class WeatherClient(
    private val webClient: WebClient,
    private val apiKey: String
) {

    fun getCurrentWeatherInfo(coordinate: Pair<Double, Double>, locale: Locale): WeatherDTO =
        webClient.get()
            .uri {
                it.path("/data/2.5/weather")
                    .queryParam("lat", coordinate.first)
                    .queryParam("lon", coordinate.second)
                    .queryParam("appid", apiKey)
                    .queryParam("units", "metric")
                    .queryParam("lang", locale.language)
                    .build()
            }
            .retrieve()
            .bodyToMono<WeatherDTO>()
            .block() ?: throw RuntimeException("oops")

    fun getWeatherInfo(coordinate: Pair<Double, Double>, count: Int = 8, locale: Locale): WeatherForecastDTO =
        webClient.get()
            .uri {
                it.path("/data/2.5/forecast")
                    .queryParam("lat", coordinate.first)
                    .queryParam("lon", coordinate.second)
                    .queryParam("appid", apiKey)
                    .queryParam("units", "metric")
                    .queryParam(
                        "cnt",
                        when {
                            count <= 0 -> 1
                            else -> count
                        }
                    )
                    .queryParam("lang", locale.language)
                    .build()
            }
            .retrieve()
            .bodyToMono<WeatherForecastDTO>()
            .block() ?: throw RuntimeException("oops")

}