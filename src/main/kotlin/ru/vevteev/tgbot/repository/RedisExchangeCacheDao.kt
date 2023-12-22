package ru.vevteev.tgbot.repository

import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component
import ru.vevteev.tgbot.dto.CbrDailyDTO


@Component
class RedisExchangeCacheDao(redisTemplate: RedisTemplate<String, CbrDailyDTO>) : AbstractStringKeyRedisDao<CbrDailyDTO>(redisTemplate)  {
    override fun String.withTag() = "$EXCHANGE_TAG$this"

    companion object {
        const val EXCHANGE_TAG = "exchange:"
    }
}