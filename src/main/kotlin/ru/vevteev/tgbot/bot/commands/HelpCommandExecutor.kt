package ru.vevteev.tgbot.bot.commands

import jakarta.annotation.PostConstruct
import org.springframework.context.MessageSource
import org.springframework.stereotype.Component
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.objects.Update
import ru.vevteev.tgbot.extension.createSendMessage
import ru.vevteev.tgbot.extension.getMessage
import ru.vevteev.tgbot.extension.locale
import java.util.*

@Component
class HelpCommandExecutor(
    private val commandExecutors: MutableList<CommandExecutor>,
    private val messageSource: MessageSource
) : CommandExecutor {
    @PostConstruct
    fun youForgotMyself() {
        commandExecutors.add(this)
        commandExecutors.removeIf { it.commandName() == "" }
    }

    override fun commandName(): String = "help"

    override fun commandDescription(locale: Locale): String = messageSource.getMessage("command.description.help", locale)

    override fun perform(update: Update, bot: TelegramLongPollingBot, arguments: List<String>) {
        update.run {
            val locale = locale(arguments)
            bot.execute(
                createSendMessage(
                    messageSource.getMessage(
                        "msg.help",
                        arrayOf(commandExecutors.joinToString("\n") { "/${it.commandName()} - ${it.commandDescription(locale)}" }),
                        locale
                    )
                )
            )
        }
    }
}