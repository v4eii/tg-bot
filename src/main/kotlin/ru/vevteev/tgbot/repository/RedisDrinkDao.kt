package ru.vevteev.tgbot.repository

import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component
import ru.vevteev.tgbot.dto.DrinkRemember


@Component
class RedisDrinkDao(redisTemplate: RedisTemplate<String, DrinkRemember>) :
    AbstractStringKeyRedisDao<DrinkRemember>(redisTemplate) {

    fun getAllReminder() = getAll("*")

    override fun String.withTag() = "$DRINK_TAG$this"

    companion object {
        const val DRINK_TAG = "drink:"
    }
}