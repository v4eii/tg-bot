package ru.vevteev.tgbot.bot

import jakarta.annotation.PostConstruct
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.MessageSource
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.Update
import ru.vevteev.tgbot.bot.commands.CommandCallbackExecutor
import ru.vevteev.tgbot.bot.commands.CommandExecutor
import ru.vevteev.tgbot.bot.commands.CommandReplyExecutor
import ru.vevteev.tgbot.config.BaseProperties
import ru.vevteev.tgbot.config.CommandProperties
import ru.vevteev.tgbot.extension.callbackQueryMessageChatId
import ru.vevteev.tgbot.extension.callbackQueryMessageText
import ru.vevteev.tgbot.extension.createSendMessage
import ru.vevteev.tgbot.extension.createSticker
import ru.vevteev.tgbot.extension.get
import ru.vevteev.tgbot.extension.isMessageCommand
import ru.vevteev.tgbot.extension.isReply
import ru.vevteev.tgbot.extension.isReplyMessageCommand
import ru.vevteev.tgbot.extension.isSuperGroupMessage
import ru.vevteev.tgbot.extension.locale
import ru.vevteev.tgbot.extension.messageText
import ru.vevteev.tgbot.extension.replyMessageText
import ru.vevteev.tgbot.extension.shortInfo


@Component
@EnableConfigurationProperties(BaseProperties::class, CommandProperties::class)
class DefaultBot(
    private val baseProperties: BaseProperties,
    private val commandExecutors: List<CommandExecutor>,
    private val commandReplyExecutors: List<CommandReplyExecutor>,
    private val commandCallbackExecutors: List<CommandCallbackExecutor>,
    private val messageSource: MessageSource,
) : TelegramLongPollingBotExt(baseProperties.token) {

    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    @PostConstruct
    fun init() {
        commandExecutors.forEach { it.init(this) }
    }

    override fun getBotUsername(): String = baseProperties.name

    override fun onUpdateReceived(update: Update) {
        try {
            update.run {
                logger.info("${message.shortInfo()}; ${toString()}")
                if (hasMessage()) {
                    if (isMessageCommand()) {
                        val args = messageText()?.split(" ") ?: listOf("")
                        commandExecutors.performCommand(this, args)
                    } else if (isReply() && isReplyMessageCommand()) {
                        commandReplyExecutors.performReply(this, replyMessageText().parseCommandArgument())
                    } else if (!isSuperGroupMessage()) {
                        execute(createSendMessage(messageSource.get("msg.some-text-staff", locale())))
                        execute(createSticker("CAACAgIAAxkBAAEKVo1lCfZuLRg2HGJA9fC5ENrczyfufAAClBsAApngyEp67FOO_tH2zTAE"))
                    }
                } else if (hasCallbackQuery()) {
                    commandCallbackExecutors.performCallback(this, callbackQueryMessageText().parseCommandArgument())
                }
                return
            }
        } catch (e: Exception) {
            processException(e, update)
        }
    }

    override fun sendMsg(chatId: String, msg: String) {
        execute(SendMessage(chatId, msg))
    }

    override fun getCommandsExecutor(): List<CommandExecutor> =
        commandExecutors.sortedBy { it.commandName() }.toMutableList().apply { removeIf { it.commandName() == "" } }

    fun List<CommandExecutor>.performUnknownCommand(update: Update, args: List<String>) =
        find { it.commandName() == "" }!!.perform(update, this@DefaultBot, args.drop(1))

    fun List<CommandExecutor>.performCommandIfExists(update: Update, args: List<String>) =
        find { it.apply(args.first()) }?.perform(update, this@DefaultBot, args.drop(1))

    fun List<CommandExecutor>.performCommand(update: Update, args: List<String>) =
        performCommandIfExists(update, args) ?: performUnknownCommand(update, args)

    fun List<CommandCallbackExecutor>.performCallback(update: Update, args: List<String>) =
        find { it.apply(args.first()) }?.processCallback(update, this@DefaultBot, args.drop(1))

    fun List<CommandReplyExecutor>.performReply(update: Update, args: List<String>) =
        find { it.apply(args.first()) }?.processReply(update, this@DefaultBot, args.drop(1))

    fun String?.parseCommandArgument() = this?.split("|")?.first()?.split(" ") ?: listOf("")

    private fun processException(
        e: Exception,
        update: Update
    ) {
        logger.error(e.message, e)
        update.run {
            if (hasCallbackQuery()) {
                sendMsg(callbackQueryMessageChatId().toString(), messageSource.get("msg.i-broke", locale()))
            } else {
                execute(createSendMessage(messageSource.get("msg.i-broke", locale())))
                execute(createSticker("CAACAgIAAxkBAAEKVp5lCgdMJuY6c6cywnQ1oNBbxOLXlQACRR4AAl_V0Ep-aKASQp4NCDAE"))
            }
        }
    }
}