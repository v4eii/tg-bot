package ru.vevteev.tgbot.repository

import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component
import ru.vevteev.tgbot.dto.DrinkRemember


@Component
class RedisDrinkDao(redisTemplate: RedisTemplate<String, DrinkRemember>) :
    AbstractRedisDao<String, DrinkRemember>(redisTemplate) {

    override fun save(key: String, value: DrinkRemember) {
        super.save(key.withDrinkTag(), value)
    }

    override fun get(key: String) = super.get(key.withDrinkTag())

    override fun exists(key: String) = super.exists(key.withDrinkTag())

    override fun delete(key: String) = super.delete(key.withDrinkTag())

    fun getAllReminder() = getAll("$DRINK_TAG*")

    private fun String.withDrinkTag() = "$DRINK_TAG$this"

    companion object {
        const val DRINK_TAG = "drink:"
    }
}