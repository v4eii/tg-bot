package ru.vevteev.tgbot.dto

import org.springframework.scheduling.support.CronExpression
import java.util.*


data class ScheduleData(
    val locale: Locale,
    val chatId: String,
    val cron: MutableMap<String, String>,
    var action: String
)