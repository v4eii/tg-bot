package ru.vevteev.tgbot.bot

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.Update
import ru.vevteev.tgbot.bot.commands.CommandExecutor
import ru.vevteev.tgbot.config.BaseProperties
import ru.vevteev.tgbot.extension.isCommand
import ru.vevteev.tgbot.extension.isReply
import ru.vevteev.tgbot.extension.isReplyCommand
import ru.vevteev.tgbot.extension.replyMessageText
import ru.vevteev.tgbot.extension.text


@Component
@EnableConfigurationProperties(BaseProperties::class)
class DefaultBot(
    private val baseProperties: BaseProperties,
    private val commandExecutors: List<CommandExecutor>,
) : TelegramLongPollingBotExt(baseProperties.token) {

    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    override fun getBotUsername(): String = baseProperties.name

    override fun onUpdateReceived(update: Update) {
        update.run {
            logger.info(toString())
            if (update.hasMessage()) {
                if (isCommand()) {
                    val args = text()?.split(" ") ?: listOf("")
                    commandExecutors.find { it.apply(args.first()) }
                        ?.perform(this, this@DefaultBot, args.drop(1))
                        ?: commandExecutors.find { it.commandName() == "" }!!
                            .perform(this, this@DefaultBot, args.drop(1))
                } else if (isReply() and isReplyCommand()) {
                    val args = replyMessageText()?.split("|")?.first()?.split(" ") ?: listOf("")
                    commandExecutors.find { it.apply(args.first()) }
                        ?.perform(this, this@DefaultBot, args.drop(1))
                        ?: commandExecutors.find { it.commandName() == "" }!!
                            .perform(this, this@DefaultBot, args.drop(1))

                }
            }
        }
    }

    override fun sendMsg(chatId: String, msg: String) {
        execute(SendMessage(chatId, msg))
    }

    override fun getCommandsExecutor(): List<CommandExecutor> =
        commandExecutors.sortedBy { it.commandName() }.toMutableList().apply { removeIf { it.commandName() == "" } }
}