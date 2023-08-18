package ru.vevteev.tgbot.config.weather

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("client.weather")
data class WeatherProperties(
    val baseUrl: String,
    val apiKey: String
)