package ru.vevteev.tgbot.repository

import org.springframework.data.redis.core.RedisTemplate


abstract class AbstractRedisDao<K : Any, V : Any>(private val redisTemplate: RedisTemplate<K, V>) {
    open fun save(key: K, value: V) {
        redisTemplate.opsForValue().set(key, value)
    }

    open fun get(key: K) = redisTemplate.opsForValue().get(key)

    open fun exists(key: K) = redisTemplate.hasKey(key)

    open fun getAll(keyPattern: K) = redisTemplate.keys(keyPattern).mapNotNull {
        redisTemplate.opsForValue().get(it)
    }

    open fun delete(key: K) = redisTemplate.delete(key)

    open fun String.withTag() = ""
}