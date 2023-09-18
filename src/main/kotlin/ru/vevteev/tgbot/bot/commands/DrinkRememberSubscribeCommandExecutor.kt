package ru.vevteev.tgbot.bot.commands

import org.springframework.context.MessageSource
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.objects.Update
import ru.vevteev.tgbot.bot.SimpleDumbCache
import ru.vevteev.tgbot.bot.TelegramLongPollingBotExt
import ru.vevteev.tgbot.dto.DrinkRemember
import ru.vevteev.tgbot.extension.chatId
import ru.vevteev.tgbot.extension.createSendMessage
import ru.vevteev.tgbot.extension.getMessage
import ru.vevteev.tgbot.extension.locale
import java.util.*

@Component
class DrinkRememberSubscribeCommandExecutor(private val messageSource: MessageSource) : CommandExecutor {
    override fun commandName(): String = "drink"

    override fun commandDescription(locale: Locale): String =
        messageSource.getMessage("command.description.drink-remember", locale)

    override fun perform(update: Update, bot: TelegramLongPollingBotExt, arguments: List<String>) {
        update.run {
            val chatLambda: (DrinkRemember) -> Boolean = { it.chatId == chatId() }
            val locale = locale(arguments)
            if (SimpleDumbCache.drinkRememberSet.any(chatLambda).not()) {
                bot.execute(createSendMessage(messageSource.getMessage("msg.drink-on", locale)))
                SimpleDumbCache.drinkRememberSet.add(DrinkRemember(locale(arguments), chatId()))
            } else {
                bot.execute(createSendMessage(messageSource.getMessage("msg.drink-off", locale)))
                SimpleDumbCache.drinkRememberSet.removeIf(chatLambda)
            }
        }
    }
}