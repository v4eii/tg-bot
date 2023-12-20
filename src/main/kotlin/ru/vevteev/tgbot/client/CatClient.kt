package ru.vevteev.tgbot.client

import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import ru.vevteev.tgbot.dto.RandomCatDTO


class CatClient(
    private val webClient: WebClient,
) {

    fun getRandomCat() = webClient.get()
        .uri("/v1/images/search?limit=1")
        .retrieve()
        .bodyToMono<List<RandomCatDTO>>()
        .block()
        ?.first()!!

}