package ru.vevteev.tgbot.bot.commands

import org.springframework.context.MessageSource
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.objects.Update
import ru.vevteev.tgbot.bot.DefaultBot
import ru.vevteev.tgbot.bot.TelegramLongPollingBotExt
import ru.vevteev.tgbot.dto.DrinkRemember
import ru.vevteev.tgbot.extension.messageChatId
import ru.vevteev.tgbot.extension.createSendMessage
import ru.vevteev.tgbot.extension.getMessage
import ru.vevteev.tgbot.extension.isGroupMessage
import ru.vevteev.tgbot.extension.locale
import ru.vevteev.tgbot.extension.messageSenderChatId
import ru.vevteev.tgbot.repository.RedisDrinkDao
import java.util.*

@Component
class DrinkRememberSubscribeCommandExecutor(
    private val messageSource: MessageSource,
    private val redisDrinkDao: RedisDrinkDao,
) : CommandExecutor {
    override fun commandName(): String = "drink"

    override fun commandDescription(locale: Locale): String =
        messageSource.getMessage("command.description.drink-remember", locale)

    override fun perform(update: Update, bot: TelegramLongPollingBotExt, arguments: List<String>) {
        update.run {
            val key = if (isGroupMessage()) messageSenderChatId() else messageChatId()
            val locale = locale(arguments)
            if (redisDrinkDao.exists(key)) {
                redisDrinkDao.delete(key)
                bot.execute(createSendMessage(messageSource.getMessage("msg.drink-off", locale)))
            } else {
                redisDrinkDao.save(key, DrinkRemember(locale, messageChatId()))
                bot.execute(createSendMessage(messageSource.getMessage("msg.drink-on", locale)))
            }
        }
    }

    fun sendRemember(bot: DefaultBot) {
        redisDrinkDao.getAllReminder().forEach {
            bot.sendMsg(it.chatId, messageSource.getMessage("msg.drink-water-remember", it.locale))
        }
    }
}