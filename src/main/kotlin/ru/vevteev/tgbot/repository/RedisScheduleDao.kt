package ru.vevteev.tgbot.repository

import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component
import ru.vevteev.tgbot.dto.ScheduleData


@Component
class RedisScheduleDao(redisTemplate: RedisTemplate<String, ScheduleData>) :
    AbstractStringKeyRedisDao<ScheduleData>(redisTemplate) {
    override fun String.withTag() = "$SCHEDULE_TAG$this"

    companion object {
        const val SCHEDULE_TAG = "schedule:"
    }
}