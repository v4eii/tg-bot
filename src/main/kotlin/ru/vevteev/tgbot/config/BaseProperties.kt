package ru.vevteev.tgbot.config

import org.springframework.boot.context.properties.ConfigurationProperties


@ConfigurationProperties("bot")
data class BaseProperties(val token: String, val name: String)