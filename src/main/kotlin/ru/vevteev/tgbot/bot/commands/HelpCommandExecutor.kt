package ru.vevteev.tgbot.bot.commands

import org.springframework.context.MessageSource
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.objects.Update
import ru.vevteev.tgbot.bot.TelegramLongPollingBotExt
import ru.vevteev.tgbot.extension.asCommand
import ru.vevteev.tgbot.extension.createSendMessage
import ru.vevteev.tgbot.extension.get
import ru.vevteev.tgbot.extension.locale
import ru.vevteev.tgbot.extension.space
import java.util.*

@Component
class HelpCommandExecutor(
    private val messageSource: MessageSource
) : CommandExecutor {

    override fun commandName(): String = "help"

    override fun commandDescription(locale: Locale): String =
        messageSource.get("command.description.help", locale)

    override fun perform(update: Update, bot: TelegramLongPollingBotExt, arguments: List<String>) {
        update.run {
            val locale = locale(arguments)
            bot.execute(
                createSendMessage(
                    messageSource.getMessage(
                        "msg.help",
                        arrayOf(
                            bot.getCommandsExecutor().joinToString("".space()) {
                                "${it.commandName().asCommand()} - ${it.commandDescription(locale)}"
                            }
                        ),
                        locale
                    )
                )
            )
        }
    }
}