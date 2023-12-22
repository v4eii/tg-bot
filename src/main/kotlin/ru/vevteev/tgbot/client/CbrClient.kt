package ru.vevteev.tgbot.client

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import ru.vevteev.tgbot.dto.CbrDailyDTO
import ru.vevteev.tgbot.extension.isRu
import java.util.*


class CbrClient(private val webClient: WebClient) {
    private val xmlMapper: ObjectMapper = XmlMapper().registerModule(JavaTimeModule())
        .registerKotlinModule()
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .disable(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES)

    fun getCbrExchangeRate(locale: Locale = Locale.getDefault()): CbrDailyDTO {
        val response = webClient.get()
            .uri(if (locale.isRu()) "/scripts/XML_daily.asp" else "/scripts/XML_daily_eng.asp")
            .accept(MediaType.APPLICATION_XML)
            .retrieve()
            .bodyToMono<String>()
            .block() ?: throw RuntimeException("oops")

        return xmlMapper.readValue(response.replace(",", "."), CbrDailyDTO::class.java)
    }

}