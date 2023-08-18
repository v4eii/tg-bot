package ru.vevteev.tgbot.bot.commands

import org.springframework.context.MessageSource
import org.springframework.stereotype.Component
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.objects.Update
import ru.vevteev.tgbot.bot.SimpleDumbCache
import ru.vevteev.tgbot.extension.createSendMessage
import ru.vevteev.tgbot.extension.getMessage
import java.util.*

@Component
class DrinkRememberSubscribeCommandExecutor(private val messageSource: MessageSource) : CommandExecutor {
    override fun commandName(): String = "drink"

    override fun commandDescription(locale: Locale): String = messageSource.getMessage("command.description.drink-remember", locale)

    override fun perform(update: Update, bot: TelegramLongPollingBot, arguments: List<String>) {
        update.run {
            if (SimpleDumbCache.drinkRememberSet.any { it == message.chatId.toString() }) {
                bot.execute(createSendMessage("Теперь ты отписан(а) от напоминалок"))
                SimpleDumbCache.drinkRememberSet.remove(message.chatId.toString())
            } else {
                bot.execute(createSendMessage("Теперь ты подписан(а) на напоминалки"))
                SimpleDumbCache.drinkRememberSet.add(message.chatId.toString())
            }

        }
    }
}