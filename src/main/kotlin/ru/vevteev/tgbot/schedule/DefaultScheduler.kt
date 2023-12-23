package ru.vevteev.tgbot.schedule

import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.support.CronTrigger
import org.springframework.scheduling.support.PeriodicTrigger
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledFuture

@Component
@EnableScheduling
class DefaultScheduler(private val taskScheduler: TaskScheduler) {
    fun registerNewCronScheduleTask(cronExpression: String, userId: String, action: () -> Unit) {
        taskScheduler.schedule(action, CronTrigger(cronExpression, ZoneId.of("Europe/Moscow")))?.also {
            schedules["$cronExpression:$userId"] = it
        }
    }

    fun registerNewFixedScheduleTask(period: Duration, userId: String, actionName: String, action: () -> Unit) {
        taskScheduler.schedule(action, PeriodicTrigger(period))?.also {
            schedules["$actionName:$userId"] = it
        }
    }

    fun removeCronSchedule(cronExpression: String, userId: String) {
        removeSchedule("$cronExpression:$userId")
    }

    fun removeFixedSchedule(actionName: String, userId: String) {
        removeSchedule("$actionName:$userId")
    }

    fun removeSchedule(key: String) {
        schedules[key]?.cancel(true)
        schedules.remove(key)
    }

    companion object {
        private val schedules: ConcurrentHashMap<String, ScheduledFuture<*>> = ConcurrentHashMap()
    }

}