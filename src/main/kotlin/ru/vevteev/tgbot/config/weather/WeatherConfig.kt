package ru.vevteev.tgbot.config.weather

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient
import ru.vevteev.tgbot.client.WeatherClient

@Configuration
@EnableConfigurationProperties(WeatherProperties::class)
class WeatherConfig(
    private val weatherProperties: WeatherProperties
) {

    @Bean
    fun weatherClient() = weatherProperties.run {
        WeatherClient(
            webClient = WebClient.create(baseUrl),
            apiKey = apiKey
        )
    }

}