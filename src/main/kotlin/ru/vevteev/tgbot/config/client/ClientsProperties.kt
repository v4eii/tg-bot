package ru.vevteev.tgbot.config.client

import org.springframework.boot.context.properties.ConfigurationProperties


@ConfigurationProperties("client.cbr")
data class CbrProperties(
    val baseUrl: String
)

@ConfigurationProperties("client.weather")
data class WeatherProperties(
    val baseUrl: String,
    val apiKey: String
)

@ConfigurationProperties("client.cat-api")
data class RandomCatProperties(
    val baseUrl: String,
)