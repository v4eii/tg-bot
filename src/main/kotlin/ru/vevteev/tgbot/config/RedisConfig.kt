package ru.vevteev.tgbot.config

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.springframework.boot.autoconfigure.data.redis.RedisProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.connection.RedisPassword
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.StringRedisSerializer
import ru.vevteev.tgbot.dto.CbrDailyDTO
import ru.vevteev.tgbot.dto.DrinkRemember
import ru.vevteev.tgbot.dto.ScheduleData


@Configuration
@EnableConfigurationProperties(RedisProperties::class)
class RedisConfig {

    @Bean
    fun lettuceConnectionFactory(props: RedisProperties): LettuceConnectionFactory = LettuceConnectionFactory(
        RedisStandaloneConfiguration(props.host, props.port).apply {
            password = RedisPassword.of(props.password)
        }
    )

    @Bean
    fun redisTemplate(redisConnectionFactory: RedisConnectionFactory): RedisTemplate<String, String> =
        RedisTemplate<String, String>().apply {
            connectionFactory = redisConnectionFactory
            keySerializer = StringRedisSerializer()
            valueSerializer = StringRedisSerializer()
            isEnableDefaultSerializer = false
        }

    @Bean
    fun drinkRedisTemplate(redisConnectionFactory: RedisConnectionFactory): RedisTemplate<String, DrinkRemember> =
        RedisTemplate<String, DrinkRemember>().apply {
            connectionFactory = redisConnectionFactory
            keySerializer = StringRedisSerializer()
            valueSerializer = Jackson2JsonRedisSerializer(jacksonObjectMapper().registerKotlinModule().registerModule(JavaTimeModule()), DrinkRemember::class.java)
            isEnableDefaultSerializer = false
        }

    @Bean
    fun scheduleRedisTemplate(redisConnectionFactory: RedisConnectionFactory): RedisTemplate<String, ScheduleData> =
        RedisTemplate<String, ScheduleData>().apply {
            connectionFactory = redisConnectionFactory
            keySerializer = StringRedisSerializer()
            valueSerializer = Jackson2JsonRedisSerializer(jacksonObjectMapper().registerKotlinModule().registerModule(JavaTimeModule()), ScheduleData::class.java)
            isEnableDefaultSerializer = false
        }

    @Bean
    fun exchangeCacheRedisTemplate(redisConnectionFactory: RedisConnectionFactory): RedisTemplate<String, CbrDailyDTO> =
        RedisTemplate<String, CbrDailyDTO>().apply {
            connectionFactory = redisConnectionFactory
            keySerializer = StringRedisSerializer()
            valueSerializer = Jackson2JsonRedisSerializer(jacksonObjectMapper().registerKotlinModule().registerModule(JavaTimeModule()), CbrDailyDTO::class.java)
            isEnableDefaultSerializer = false
        }

}