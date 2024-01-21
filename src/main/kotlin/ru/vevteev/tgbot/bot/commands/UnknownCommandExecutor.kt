package ru.vevteev.tgbot.bot.commands

import org.springframework.context.MessageSource
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.objects.Update
import ru.vevteev.tgbot.bot.TelegramLongPollingBotExt
import ru.vevteev.tgbot.extension.createSendMessage
import ru.vevteev.tgbot.extension.createSticker
import ru.vevteev.tgbot.extension.get
import ru.vevteev.tgbot.extension.locale
import java.util.*

@Component
class UnknownCommandExecutor(private val messageSource: MessageSource) : CommandExecutor {
    private val stickerList = listOf(
        "CAACAgIAAxkBAAELOThlrPPeEjK_-lrbWnHWNpegrwU8mAACoxIAAqp5IEsNePw5S7rddjQE",
        "CAACAgIAAxkBAAEKDl5k3oUfA5Vf4ZiyG2URcZFlq4m0eAACrx4AAq0g0EoW-WsFcEaYfjAE",
        "CAACAgIAAxkBAAELOTplrPQ0aDB1TwSf5sQSuHA5y37cGAACsxcAArjd8EhO5CInWnSNNjQE",
        "CAACAgIAAxkBAAELOTxlrPRie7f2PLKx-PxxNKkz6kLUogACiiMAAtPbcEtYY9jIok-9ozQE",
        "CAACAgIAAxkBAAELOT5lrPR7jzHxvvI75J6el6hkZu1fUQACHiIAAnJtaUvi0fgDnkPV9TQE",
        "CAACAgIAAxkBAAELOUBlrPTX-AkZMHO1ezXWhEe2R_rG7AACOwAD5e9kEs8hMHUOk-iTNAQ",
        "CAACAgIAAxkBAAELOUJlrPWAmXwho6DKEx185H2kW-vjrQACswADV08VCMo0X3aGlhzDNAQ",
        "CAACAgIAAxkBAAELOURlrPXQoQeEgmEkYmOJzx1yS_g_yQACvBwAAln-8ElmrcMcU0eq_TQE"
    )
    override fun commandName(): String = ""
    override fun commandDescription(locale: Locale): String? = null
    override fun apply(command: String): Boolean = false

    override fun perform(update: Update, bot: TelegramLongPollingBotExt, arguments: List<String>) {
        update.run {
            bot.execute(createSendMessage(messageSource.get("msg.unknown", locale(arguments))))
            bot.execute(createSticker(stickerList.random()))
        }
    }
}