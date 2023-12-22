package ru.vevteev.tgbot.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.NestedConfigurationProperty
import java.time.Duration

@ConfigurationProperties("bot.command")
data class CommandProperties(@NestedConfigurationProperty val exchange: Exchange) {
    data class Exchange(val exchangeCachePeriod: Duration)
}