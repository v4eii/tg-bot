package ru.vevteev.tgbot.bot.commands

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.springframework.context.MessageSource
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.objects.Update
import ru.vevteev.tgbot.bot.TelegramLongPollingBotExt
import ru.vevteev.tgbot.client.CbrClient
import ru.vevteev.tgbot.dto.CbrDailyDTO
import ru.vevteev.tgbot.dto.CbrDailyValuteDTO
import ru.vevteev.tgbot.extension.convertNumericPair
import ru.vevteev.tgbot.extension.createSendMessage
import ru.vevteev.tgbot.extension.getMessage
import ru.vevteev.tgbot.extension.isNonEmptyPair
import ru.vevteev.tgbot.extension.locale
import ru.vevteev.tgbot.extension.space
import ru.vevteev.tgbot.extension.toCurrencyPair
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.*

@Component
class ExchangeRateCommandExecutor(
    private val messageSource: MessageSource,
    private val cbrClient: CbrClient,
) : CommandExecutor {

    private val xmlMapper: ObjectMapper = XmlMapper().registerModule(JavaTimeModule()).registerKotlinModule()

    override fun commandName(): String = "exchange"

    override fun commandDescription(locale: Locale): String =
        messageSource.getMessage("command.description.exchange", locale)

    override fun perform(update: Update, bot: TelegramLongPollingBotExt, arguments: List<String>) {
        update.run {
            val locale = locale(arguments)
            val firstArg = arguments.firstOrNull()
            val exchanges = xmlMapper.readValue(
                cbrClient.getCbrExchangeRate(locale = locale).replace(",", "."),
                CbrDailyDTO::class.java
            ).apply {
                valCurs = valCurs.toMutableList()
                    .apply {
                        add(
                            CbrDailyValuteDTO(
                                id = "",
                                "643",
                                "RUB",
                                1,
                                messageSource.getMessage("msg.exchange-rub", locale),
                                BigDecimal.ONE
                            )
                        )
                    }
            }
            when (firstArg) {
                "c" -> {
                    bot.execute(
                        createSendMessage(
                            exchanges.valCurs
                                .sortedBy { it.numCode }
                                .joinToString("\n") { "${it.charCode}(${it.numCode}) - ${it.name}" }
                        )
                    )
                }
                else -> {
                    val currencies = firstArg ?: "USDRUB"
                    val currencyList = currencies.split(",")
                        .filter { it.length == 6 }
                        .map { it.toCurrencyPair().convertNumericPair(exchanges) }
                        .filter { it.isNonEmptyPair() }
                        .distinct()
                        .flatMap { it.toList() }
                        .ifEmpty { listOf("USD", "RUB") }
                    val currencyRate = exchanges.valCurs.filter { it.charCode in currencyList.toSet() }
                    bot.execute(
                        createSendMessage(
                            currencyList.chunked(2)
                                .flatMap { it.zipWithNext() }
                                .filterValidCurrency(currencyRate.map { dto -> dto.charCode!! })
                                .map {
                                    it to currencyRate.firstValue(it)
                                        ?.divide(currencyRate.secondValue(it), 4, RoundingMode.HALF_UP)
                                }
                                .groupBy { it.first.first }
                                .map {
                                    messageSource.getMessage(
                                        "msg.exchange",
                                        arrayOf(
                                            it.key,
                                            it.value.joinToString("\n") { pair -> "${pair.second} ${pair.first.second}" }
                                        ),
                                        locale
                                    )
                                }.joinToString("".space(2))
                        ) {
                            enableMarkdown(true)
                        }
                    )
                }
            }
        }
    }

    fun List<CbrDailyValuteDTO>.firstValue(pair: Pair<String, String>) = find { pair.first == it.charCode }?.let {
        if (it.nominal!! > 1) it.value!!.divide(it.nominal!!.toBigDecimal()) else it.value
    }

    fun List<CbrDailyValuteDTO>.secondValue(pair: Pair<String, String>) = find { pair.second == it.charCode }?.let {
        if (it.nominal!! > 1) it.value!!.divide(it.nominal!!.toBigDecimal()) else it.value
    }

    fun List<Pair<String, String>>.filterValidCurrency(currencyCodes: List<String>) =
        filter { it.first in currencyCodes && it.second in currencyCodes }
}