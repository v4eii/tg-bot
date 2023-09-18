package ru.vevteev.tgbot.client

import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import ru.vevteev.tgbot.extension.isRu
import java.util.*


class CbrClient(
    private val webClient: WebClient,
) {

    fun getCbrExchangeRate(locale: Locale = Locale.getDefault()): String {
        return webClient.get()
            .uri(if (locale.isRu()) "/scripts/XML_daily.asp" else "/scripts/XML_daily_eng.asp")
            .accept(MediaType.APPLICATION_XML)
            .retrieve()
            .bodyToMono<String>()
            .block() ?: throw RuntimeException("oops")
    }

}