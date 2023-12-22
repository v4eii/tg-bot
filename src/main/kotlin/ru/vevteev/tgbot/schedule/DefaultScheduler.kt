package ru.vevteev.tgbot.schedule

import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.support.CronTrigger
import org.springframework.stereotype.Component
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledFuture

@Component
@EnableScheduling
class DefaultScheduler(
    private val taskScheduler: TaskScheduler,
) {
    fun registerNewCronScheduleTask(cronExpression: String, userId: String, action: () -> Unit) {
        taskScheduler.schedule(action, CronTrigger(cronExpression, ZoneId.of("Europe/Moscow")))?.also {
            schedules["$cronExpression:$userId"] = it
        }
    }

    fun removeCronSchedule(cronExpression: String, userId: String) {
        val key = "$cronExpression:$userId"
        schedules[key]?.cancel(false)
        schedules.remove(key)
    }

    companion object {
        private val schedules: ConcurrentHashMap<String, ScheduledFuture<*>> = ConcurrentHashMap()
    }

}