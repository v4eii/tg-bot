package ru.vevteev.tgbot.bot.commands

import org.springframework.context.MessageSource
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.objects.Update
import ru.vevteev.tgbot.bot.TelegramLongPollingBotExt
import ru.vevteev.tgbot.extension.buildDefaultKeyboard
import ru.vevteev.tgbot.extension.createSendMessage
import ru.vevteev.tgbot.extension.createSticker
import ru.vevteev.tgbot.extension.firstName
import ru.vevteev.tgbot.extension.getMessage
import ru.vevteev.tgbot.extension.locale
import java.util.*

@Component
class StartCommandExecutor(
    private val messageSource: MessageSource,
) : CommandExecutor {
    private val stickerList = listOf(
        "CAACAgIAAxkBAAEKDlJk3oOWYjSczQh3ZEH9VBPdwr1qywACQBUAAmZfqUpz2pY56rD8dzAE",
        "CAACAgIAAxkBAANLZN0oxQ8WGrBKlddr3aoaDuccCUwAAmAAA8GcYAyuBXtmcqTrazAE",
        "CAACAgIAAxkBAAEKDlZk3oPO-ALHV_mVPJN6n4wg-xxdgAAC1xgAAm4m4UsFYy3CmOv8qzAE",
    )

    override fun commandName(): String = "start"

    override fun commandDescription(locale: Locale): String =
        messageSource.getMessage("command.description.start", locale)

    override fun perform(update: Update, bot: TelegramLongPollingBotExt, arguments: List<String>) {
        update.run {
            bot.execute(
                createSendMessage(
                    messageSource.getMessage(
                        "msg.start",
                        arrayOf(firstName()),
                        locale(arguments)
                    )
                ) {
                    replyMarkup = bot.buildDefaultKeyboard()
                }
            )
            bot.execute(createSticker(stickerList.random()))
        }
    }
}