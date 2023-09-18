package ru.vevteev.tgbot.bot

import org.telegram.telegrambots.bots.TelegramLongPollingBot
import ru.vevteev.tgbot.bot.commands.CommandExecutor


abstract class TelegramLongPollingBotExt(token: String) : TelegramLongPollingBot(token) {

    abstract fun sendMsg(chatId: String, msg: String)

    abstract fun getCommandsExecutor(): List<CommandExecutor>

}