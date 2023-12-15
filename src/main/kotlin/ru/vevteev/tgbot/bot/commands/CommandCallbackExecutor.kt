package ru.vevteev.tgbot.bot.commands

import org.telegram.telegrambots.meta.api.objects.Update
import ru.vevteev.tgbot.bot.TelegramLongPollingBotExt

interface CommandCallbackExecutor : CommandExecutor {
    fun processCallback(update: Update, bot: TelegramLongPollingBotExt, arguments: List<String> = emptyList())
}