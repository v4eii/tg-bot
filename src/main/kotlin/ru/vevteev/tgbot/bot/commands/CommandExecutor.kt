package ru.vevteev.tgbot.bot.commands

import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.objects.Update
import java.util.*

interface CommandExecutor {
    fun commandName(): String

    fun commandDescription(locale: Locale = Locale.getDefault()): String?

    fun perform(update: Update, bot: TelegramLongPollingBot, arguments: List<String> = emptyList())

    fun apply(command: String): Boolean = command.drop(1) == commandName()

}