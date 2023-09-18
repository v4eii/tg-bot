package ru.vevteev.tgbot.bot.commands

import org.springframework.context.MessageSource
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.objects.Update
import ru.vevteev.tgbot.bot.TelegramLongPollingBotExt
import ru.vevteev.tgbot.extension.createSendMessage
import ru.vevteev.tgbot.extension.getMessage
import ru.vevteev.tgbot.extension.locale
import java.util.*

@Component
class HelpCommandExecutor(
    private val messageSource: MessageSource
) : CommandExecutor {

    override fun commandName(): String = "help"

    override fun commandDescription(locale: Locale): String =
        messageSource.getMessage("command.description.help", locale)

    override fun perform(update: Update, bot: TelegramLongPollingBotExt, arguments: List<String>) {
        update.run {
            val locale = locale(arguments)
            bot.execute(
                createSendMessage(
                    messageSource.getMessage(
                        "msg.help",
                        arrayOf(
                            bot.getCommandsExecutor()
                                .joinToString("\n") {
                                    "/${it.commandName()} - ${it.commandDescription(locale)}"
                                }
                        ),
                        locale
                    )
                )
            )
        }
    }
}