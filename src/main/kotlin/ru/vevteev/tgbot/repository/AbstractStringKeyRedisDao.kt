package ru.vevteev.tgbot.repository

import org.springframework.data.redis.core.RedisTemplate

abstract class AbstractStringKeyRedisDao<V: Any>(redisTemplate: RedisTemplate<String, V>) : AbstractRedisDao<String, V>(redisTemplate) {
    override fun save(key: String, value: V) {
        super.save(key.withTag(), value)
    }

    override fun get(key: String): V? {
        return super.get(key.withTag())
    }

    override fun exists(key: String): Boolean {
        return super.exists(key.withTag())
    }

    override fun getAll(keyPattern: String): List<V> {
        return super.getAll(keyPattern.withTag())
    }

    override fun delete(key: String): Boolean {
        return super.delete(key.withTag())
    }
}