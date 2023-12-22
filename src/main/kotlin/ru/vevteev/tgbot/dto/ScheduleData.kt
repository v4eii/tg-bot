package ru.vevteev.tgbot.dto

import ru.vevteev.tgbot.bot.commands.ScheduleCommandExecutor
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.*


data class ScheduleData(
    val locale: Locale,
    val chatId: String,
    val userId: String,
    val cron: CronData,
    var action: ScheduleCommandExecutor.CallbackActionType = ScheduleCommandExecutor.CallbackActionType.NONE,
    var actionDescription: String = "",
    var scheduleComplete: Boolean = false,
    val createDate: OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC)
) {
    fun keyValue() = "$userId:$action:$createDate"
}

data class CronData(
    var second: String = "0",
    var minute: String = "*",
    var hour: String = "*",
    var dayOfMonth: String = "*",
    var month: String = "*",
    var dayOfWeek: String = "*"
) {
    override fun toString(): String = "$second $minute $hour $dayOfMonth $month $dayOfWeek"
}