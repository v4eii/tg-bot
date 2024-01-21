package ru.vevteev.tgbot.bot.commands

import org.slf4j.LoggerFactory
import org.springframework.context.MessageSource
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import ru.vevteev.tgbot.bot.TelegramLongPollingBotExt
import ru.vevteev.tgbot.bot.commands.ExchangeRateCommandExecutor.CallbackExchangeMode.CURRENCY
import ru.vevteev.tgbot.bot.commands.ExchangeRateCommandExecutor.CallbackExchangeMode.CUSTOM
import ru.vevteev.tgbot.bot.commands.ExchangeRateCommandExecutor.CallbackExchangeMode.EUR_RUB
import ru.vevteev.tgbot.bot.commands.ExchangeRateCommandExecutor.CallbackExchangeMode.USD_RUB
import ru.vevteev.tgbot.client.CbrClient
import ru.vevteev.tgbot.config.CommandProperties
import ru.vevteev.tgbot.dto.CbrDailyDTO
import ru.vevteev.tgbot.dto.CbrDailyValuteDTO
import ru.vevteev.tgbot.extension.CANCEL_DATA
import ru.vevteev.tgbot.extension.callbackButton
import ru.vevteev.tgbot.extension.callbackQueryData
import ru.vevteev.tgbot.extension.callbackQueryMessageId
import ru.vevteev.tgbot.extension.cancelHandler
import ru.vevteev.tgbot.extension.convertNumericPair
import ru.vevteev.tgbot.extension.createDeleteMessage
import ru.vevteev.tgbot.extension.createEditMessage
import ru.vevteev.tgbot.extension.createSendMessage
import ru.vevteev.tgbot.extension.get
import ru.vevteev.tgbot.extension.isNonEmptyPair
import ru.vevteev.tgbot.extension.isRu
import ru.vevteev.tgbot.extension.locale
import ru.vevteev.tgbot.extension.messageText
import ru.vevteev.tgbot.extension.oneButtonInlineKeyboard
import ru.vevteev.tgbot.extension.space
import ru.vevteev.tgbot.extension.toCurrencyPair
import ru.vevteev.tgbot.extension.withCancelButton
import ru.vevteev.tgbot.extension.withCommandMarker
import ru.vevteev.tgbot.repository.RedisExchangeCacheDao
import ru.vevteev.tgbot.schedule.DefaultScheduler
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.*

@Component
class ExchangeRateCommandExecutor(
    private val messageSource: MessageSource,
    private val cbrClient: CbrClient,
    private val cache: RedisExchangeCacheDao,
    private val defaultScheduler: DefaultScheduler,
    private val commandProperties: CommandProperties,
) : CommandCallbackExecutor, CommandReplyExecutor {
    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun commandName(): String = "exchange"

    override fun commandDescription(locale: Locale): String =
        messageSource.get("command.description.exchange", locale)

    override fun init(bot: TelegramLongPollingBotExt) {
        defaultScheduler.registerNewFixedScheduleTask(
            commandProperties.exchange.exchangeCachePeriod,
            "ADMIN",
            commandName()
        ) {
            val ruLocale = Locale("ru")
            val enLocale = Locale("en")
            val ruCache = cbrClient.getCbrExchangeRate(ruLocale).addRubCurrency(ruLocale)
            val enCache = cbrClient.getCbrExchangeRate(enLocale).addRubCurrency(enLocale)
            cache.save("ru", ruCache)
            cache.save("en", enCache)

            logger.info("Update exchange cache")
        }
    }

    override fun perform(update: Update, bot: TelegramLongPollingBotExt, arguments: List<String>) {
        update.run {
            val locale = locale(arguments)
            bot.execute(
                createSendMessage(messageSource.get("command.exchange.button.exchange-text", locale)
                    .withCommandMarker(commandName(), arguments)) {
                    replyMarkup = InlineKeyboardMarkup(
                        listOf(
                            listOf(
                                callbackButton(messageSource.get("command.exchange.button.usd-rub", locale), USD_RUB),
                                callbackButton(messageSource.get("command.exchange.button.eur-rub", locale), EUR_RUB),
                            ),
                            listOf(
                                callbackButton(messageSource.get("command.exchange.button.custom", locale), CUSTOM),
                            ),
                            listOf(
                                callbackButton(messageSource.get("command.exchange.button.currencies", locale), CURRENCY),
                            )
                        )
                    ).withCancelButton(messageSource.get("msg.cancel", locale))
                }
            )
        }
    }

    override fun processReply(update: Update, bot: TelegramLongPollingBotExt, arguments: List<String>) {
        update.run {
            val replyMarkup = message.replyToMessage.replyMarkup
            val locale = locale(arguments)
            if (replyMarkup?.keyboard?.firstOrNull()?.firstOrNull()?.callbackData == "custom_exchange_waiting") {
                sendExchangeResponse(messageText()!!, getExchanges(locale), bot, locale, message.replyToMessage.messageId)
            }
        }
    }

    override fun processCallback(update: Update, bot: TelegramLongPollingBotExt, arguments: List<String>) {
        update.run {
            val data = callbackQueryData()
            val locale = locale(arguments)

            when (data) {
                CANCEL_DATA -> cancelHandler(bot)
                in CallbackExchangeMode.values().map { it.toString() } -> {
                    when (CallbackExchangeMode.valueOf(data)) {
                        USD_RUB, EUR_RUB -> sendExchangeResponse(data, getExchanges(locale), bot, locale)
                        CUSTOM -> bot.execute(
                            createEditMessage(
                                callbackQueryMessageId(),
                                messageSource.get("command.exchange.button.custom-text", locale).withCommandMarker(commandName(), arguments)
                            ) {
                                replyMarkup = oneButtonInlineKeyboard("Я жду", "custom_exchange_waiting")
                            }
                        )
                        CURRENCY -> sendCurrencyResponse(bot, getExchanges(locale))
                    }
                }
                else -> {}
            }
        }
    }

    private fun Update.sendCurrencyResponse(
        bot: TelegramLongPollingBotExt,
        exchanges: CbrDailyDTO
    ) {
        bot.execute(
            createEditMessage(
                callbackQueryMessageId(),
                exchanges.valCurs
                    .sortedBy { it.numCode }
                    .joinToString("".space()) { "${it.charCode}(${it.numCode}) - ${it.name}" }
            )
        )
    }

    private fun Update.sendExchangeResponse(
        currencies: String,
        exchanges: CbrDailyDTO,
        bot: TelegramLongPollingBotExt,
        locale: Locale,
        messageId: Int = callbackQueryMessageId(),
    ) {
        val currencyList = currencies.split(" ")
            .filter { it.length == 6 }
            .map { it.toCurrencyPair().convertNumericPair(exchanges) }
            .filter { it.isNonEmptyPair() }
            .distinct()
            .flatMap { it.toList() }
            .map { it.uppercase() }
            .ifEmpty { listOf("USD", "RUB") }
        val currencyRate = exchanges.valCurs.filter { it.charCode in currencyList.toSet() }
        bot.execute(createDeleteMessage(messageId))
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
                                it.value.joinToString("".space()) { pair -> "${pair.second} ${pair.first.second}" }
                            ),
                            locale
                        )
                    }.joinToString("".space(2))
            ) {
                enableMarkdown(true)
            }
        )
    }

    private fun getExchanges(locale: Locale): CbrDailyDTO {
        val key = if (locale.isRu()) "ru" else "en"

        return cache.get(key) ?: cbrClient.getCbrExchangeRate(locale = locale).addRubCurrency(locale).also { cache.save(key, it) }
    }

    private fun List<CbrDailyValuteDTO>.firstValue(pair: Pair<String, String>) =
        find { pair.first == it.charCode }?.let {
            if (it.nominal!! > 1) it.value!!.divide(it.nominal!!.toBigDecimal()) else it.value
        }

    private fun List<CbrDailyValuteDTO>.secondValue(pair: Pair<String, String>) =
        find { pair.second == it.charCode }?.let {
            if (it.nominal!! > 1) it.value!!.divide(it.nominal!!.toBigDecimal()) else it.value
        }

    private fun List<Pair<String, String>>.filterValidCurrency(currencyCodes: List<String>) =
        filter { it.first in currencyCodes && it.second in currencyCodes }

    private fun CbrDailyDTO.addRubCurrency(locale: Locale) = apply {
        valCurs = valCurs.toMutableList()
            .apply {
                add(
                    CbrDailyValuteDTO(
                        id = "",
                        "643",
                        "RUB",
                        1,
                        messageSource.get("msg.exchange-rub", locale),
                        BigDecimal.ONE
                    )
                )
            }
    }

    private enum class CallbackExchangeMode {
        USD_RUB,
        EUR_RUB,
        CUSTOM,
        CURRENCY
    }
}