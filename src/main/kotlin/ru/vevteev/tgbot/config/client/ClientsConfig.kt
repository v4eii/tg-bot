package ru.vevteev.tgbot.config.client

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.codec.xml.Jaxb2XmlDecoder
import org.springframework.http.codec.xml.Jaxb2XmlEncoder
import org.springframework.web.reactive.function.client.ExchangeStrategies
import org.springframework.web.reactive.function.client.WebClient
import ru.vevteev.tgbot.client.CbrClient
import ru.vevteev.tgbot.client.WeatherClient


@Configuration
@EnableConfigurationProperties(CbrProperties::class, WeatherProperties::class)
class ClientsConfig(
    private val cbrProperties: CbrProperties,
    private val weatherProperties: WeatherProperties,
) {

    @Bean
    fun weatherClient() = weatherProperties.run {
        WeatherClient(
            webClient = WebClient.create(baseUrl),
            apiKey = apiKey
        )
    }

    @Bean
    fun cbrClient() = cbrProperties.run {
        CbrClient(
            webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .exchangeStrategies(
                    ExchangeStrategies.builder()
                        .codecs {
                            it.defaultCodecs().apply {
                                jaxb2Decoder(Jaxb2XmlDecoder())
                                jaxb2Encoder(Jaxb2XmlEncoder())
                            }
                        }
                        .build()
                )
                .build()
        )
    }

}