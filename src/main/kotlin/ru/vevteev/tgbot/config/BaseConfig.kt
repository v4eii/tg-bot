package ru.vevteev.tgbot.config

import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.MessageSource
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.support.ReloadableResourceBundleMessageSource
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.config.ScheduledTaskRegistrar
import org.telegram.telegrambots.meta.TelegramBotsApi
import org.telegram.telegrambots.meta.generics.LongPollingBot
import org.telegram.telegrambots.starter.SpringWebhookBot
import org.telegram.telegrambots.starter.TelegramBotInitializer
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession


@Configuration
class BaseConfig {

    @Bean
    @ConditionalOnMissingBean(TelegramBotsApi::class)
    fun telegramBotsApi(): TelegramBotsApi = TelegramBotsApi(DefaultBotSession::class.java)

    @Bean
    @ConditionalOnMissingBean
    fun telegramBotInitializer(
        telegramBotsApi: TelegramBotsApi,
        longPollingBots: ObjectProvider<List<LongPollingBot>>,
        webHookBots: ObjectProvider<List<SpringWebhookBot>>,
    ): TelegramBotInitializer = TelegramBotInitializer(
        telegramBotsApi,
        longPollingBots.getIfAvailable { emptyList() },
        webHookBots.getIfAvailable { emptyList() }
    )

    @Bean
    fun messageSource(): MessageSource = ReloadableResourceBundleMessageSource().apply {
        setBasename("classpath:messages")
        setDefaultEncoding("UTF-8")
        setCacheSeconds(10)
        setUseCodeAsDefaultMessage(true)
    }

}