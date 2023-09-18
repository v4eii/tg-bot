package ru.vevteev.tgbot.bot.commands

import org.springframework.context.MessageSource
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.objects.Update
import ru.vevteev.tgbot.bot.TelegramLongPollingBotExt
import ru.vevteev.tgbot.extension.createSendMessage
import ru.vevteev.tgbot.extension.createSticker
import ru.vevteev.tgbot.extension.getMessage
import ru.vevteev.tgbot.extension.locale
import java.util.*

@Component
class UnknownCommandExecutor(private val messageSource: MessageSource) : CommandExecutor {
    override fun commandName(): String = ""
    override fun commandDescription(locale: Locale): String? = null

    override fun apply(command: String): Boolean = false

    override fun perform(update: Update, bot: TelegramLongPollingBotExt, arguments: List<String>) {
        update.run {
            bot.execute(createSendMessage(messageSource.getMessage("msg.unknown", locale(arguments))))
            bot.execute(createSticker("CAACAgIAAxkBAAEKDl5k3oUfA5Vf4ZiyG2URcZFlq4m0eAACrx4AAq0g0EoW-WsFcEaYfjAE"))
        }
    }
}