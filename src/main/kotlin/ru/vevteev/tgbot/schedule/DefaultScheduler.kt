package ru.vevteev.tgbot.schedule

import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.support.CronTrigger
import org.springframework.stereotype.Component
import java.time.ZoneId

@Component
@EnableScheduling
class DefaultScheduler(
    private val taskScheduler: TaskScheduler,
) {
    fun registerNewCronScheduleTask(cronExpression: String, some: () -> Unit) {
        taskScheduler.schedule(some, CronTrigger(cronExpression, ZoneId.of("Europe/Moscow")))
    }

}