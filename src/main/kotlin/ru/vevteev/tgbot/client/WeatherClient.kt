package ru.vevteev.tgbot.client

import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import ru.vevteev.tgbot.dto.WeatherDTO
import ru.vevteev.tgbot.dto.WeatherForecastDTO

class WeatherClient(
    private val webClient: WebClient,
    private val apiKey: String
) {

    fun getCurrentWeatherInfo(coordinate: Pair<Double, Double>): WeatherDTO =
        webClient.get()
            .uri {
                it.path("/data/2.5/weather")
                    .queryParam("lat", coordinate.first)
                    .queryParam("lon", coordinate.second)
                    .queryParam("appid", apiKey)
                    .queryParam("units", "metric")
                    .build()
            }
            .retrieve()
            .bodyToMono<WeatherDTO>()
            .block() ?: throw RuntimeException("oops")

    fun getWeatherInfo(coordinate: Pair<Double, Double>, count: Int = 8): WeatherForecastDTO =
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
                            count > 38 -> 38
                            count <= 0 -> 1
                            else -> count
                        }
                    )
                    .build()
            }
            .retrieve()
            .bodyToMono<WeatherForecastDTO>()
            .block() ?: throw RuntimeException("oops")

}