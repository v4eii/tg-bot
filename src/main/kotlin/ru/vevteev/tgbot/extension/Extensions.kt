package ru.vevteev.tgbot.extension

import org.springframework.context.MessageSource
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.send.SendSticker
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText
import org.telegram.telegrambots.meta.api.objects.InputFile
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow
import ru.vevteev.tgbot.bot.TelegramLongPollingBotExt
import ru.vevteev.tgbot.dto.CbrDailyDTO
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.*
import kotlin.math.absoluteValue

const val CANCEL_DATA = "cancel"
const val CANCEL_DESCRIPTION = "Отмена"

fun oneButtonInlineKeyboard(text: String, callbackData: String) = InlineKeyboardMarkup(
    listOf(listOf(callbackButton(text, callbackData)))
)

fun Update.cancelHandler(bot: TelegramLongPollingBotExt) {
//    bot.execute(createDeleteMessage(callbackQueryMessageId()))
    bot.execute(createEditMessage(callbackQueryMessageId(), CANCEL_DESCRIPTION))
}

fun Update.messageChatIdSafe(): String =
    if (hasCallbackQuery()) callbackQueryMessageChatId().toString() else messageChatId()

fun Update.messageUserIdSafe(): String =
    if (hasCallbackQuery()) callbackQueryFromUserId().toString() else messageUserId().toString()

fun Update.messageChatId(): String = message.chatId.toString()
fun Update.messageSenderChatId(): String = message.senderChat.id.toString()
fun Update.isGroupMessage(): Boolean = message.isGroupMessage
fun Update.isSuperGroupMessage(): Boolean = message.isSuperGroupMessage
fun Update.messageUserId(): Long = message.from.id
fun Update.messageUserName(): String = message.from.userName
fun Update.messageFirstName(): String = message.from.firstName
fun Update.messageId(): Int = message.messageId
fun Update.replyMessageId(): Int = message.replyToMessage.messageId
fun Update.coordinatePair() = message.location.latitude to message.location.longitude
fun Update.isMessageCommand() = message.isCommand
fun Update.messageText(): String? = message.text
fun Update.replyMessageText(): String? = message.replyToMessage.text
fun Update.callbackQueryMessageText(): String? = callbackQuery.message.text
fun Update.callbackQueryData(): String = callbackQuery.data
fun Update.callbackQueryFromUserId(): Long = callbackQuery.from.id
fun Update.callbackQueryMessageId(): Int = callbackQuery.message.messageId
fun Update.callbackQueryMessageChatId(): Long = callbackQuery.message.chat.id
fun Update.isReply() = message.isReply
fun Update.isReplyMessageCommand() = message.replyToMessage.isCommand
fun Update.messageUserLanguageCode(): String = message.from.languageCode
fun Update.callbackQueryUserLanguageCode(): String = callbackQuery.from.languageCode
fun Update.isReplyMessageWithInlineMarkup(): Boolean = message.replyToMessage?.replyMarkup != null
fun Update.locale(arguments: List<String> = emptyList()): Locale {
    val languageCode = if (hasCallbackQuery()) callbackQueryUserLanguageCode() else messageUserLanguageCode()

    return Locale(arguments.lastOrNull() ?: languageCode).let {
        if (it in Locale.getAvailableLocales()) it else Locale(languageCode)
    }
}

fun Update.createSticker(stickerId: String) = SendSticker(messageChatIdSafe(), InputFile(stickerId))
fun Update.createSendMessage(text: String, additionalCustomize: SendMessage.() -> Unit = {}) =
    SendMessage(messageChatIdSafe(), text).apply { additionalCustomize.invoke(this) }

fun Update.createDeleteMessage(messageId: Int) = DeleteMessage(messageChatIdSafe(), messageId)
fun Update.createEditMessage(
    messageId: Int,
    text: String,
    chatId: String = messageChatIdSafe(),
    additionalCustomize: EditMessageText.() -> Unit = {}
) = EditMessageText(text).apply {
    this.messageId = messageId
    this.chatId = chatId
    additionalCustomize.invoke(this)
}

fun SendMessage.withCommandKeyboard(bot: TelegramLongPollingBotExt) = apply {
    enableMarkdown(true)
    replyMarkup = bot.buildDefaultCommandKeyboard()
}

fun Message?.shortInfo() = "${this?.from?.firstName} says ${this?.text}"
fun InlineKeyboardMarkup.withCancelButton() = apply {
    keyboard = keyboard.toMutableList().apply {
        add(listOf(callbackButton(CANCEL_DESCRIPTION, CANCEL_DATA)))
    }
}

fun TelegramLongPollingBotExt.buildDefaultCommandKeyboard() = ReplyKeyboardMarkup().apply {
    keyboard = getCommandsExecutor().chunked(3)
        .map { chunk -> KeyboardRow(chunk.map { KeyboardButton("/${it.commandName()}") }) }
    resizeKeyboard = true
}

fun MessageSource.getMessage(code: String, locale: Locale = Locale.getDefault()) = getMessage(code, null, locale)


fun Int.toLocalDate(zoneId: ZoneId): LocalDate = LocalDate.ofInstant(Instant.ofEpochSecond(this.toLong()), zoneId)
fun Int.toLocalDateTime(zoneId: ZoneId): LocalDateTime =
    LocalDateTime.ofInstant(Instant.ofEpochSecond(this.toLong()), zoneId)

fun Int.toZoneId(): ZoneId = ZoneId.of(
    if (this > 0) {
        "+" + LocalTime.ofSecondOfDay(this.toLong()).toString()
    } else {
        "-" + LocalTime.ofSecondOfDay(this.absoluteValue.toLong()).toString()
    }
)

fun String.commandMarker(arguments: List<String> = emptyList()) =
    "/$this ${arguments.joinToString(" ")}".trim() + "|"

fun String.bold() = "*$this*"
fun String.space(count: Int = 1) = "$this${"\n".repeat(count)}"
fun String.isDigits() = toIntOrNull() != null
fun String.toCurrencyPair() = substring(0..2) to substring(3..5)
fun <T> T?.valueOrAbsent() = this?.toString() ?: "absent"

fun Pair<String, String>.convertNumericPair(exchanges: CbrDailyDTO): Pair<String, String> {
    val firstVal = if (first.isDigits()) first.numCodeToCharCode(exchanges) ?: "" else first
    val secondVal = if (second.isDigits()) second.numCodeToCharCode(exchanges) ?: "" else second

    return firstVal to secondVal
}

fun Pair<String, String>.isNonEmptyPair() = first != "" && second != ""

fun Locale.isRu() = this == Locale("ru")

fun String.numCodeToCharCode(exchanges: CbrDailyDTO) =
    exchanges.valCurs.map { it.numCode to it.charCode }.find { it.first == this }?.second

fun callbackButton(text: String, callbackData: Enum<*>) =
    InlineKeyboardButton(text).apply { this.callbackData = callbackData.toString() }

fun callbackButton(text: String, callbackData: String) =
    InlineKeyboardButton(text).apply { this.callbackData = callbackData }