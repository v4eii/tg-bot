package ru.vevteev.tgbot.bot.commands

import org.telegram.telegrambots.meta.api.objects.Update
import ru.vevteev.tgbot.bot.TelegramLongPollingBotExt


interface CommandReplyExecutor : CommandExecutor {
    fun processReply(update: Update, bot: TelegramLongPollingBotExt, arguments: List<String> = emptyList())
}