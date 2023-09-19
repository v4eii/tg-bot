package ru.vevteev.tgbot.extension

import org.springframework.context.MessageSource
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.send.SendSticker
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage
import org.telegram.telegrambots.meta.api.objects.InputFile
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup
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


fun Update.chatId(): String = message.chatId.toString()
fun Update.userName(): String = message.from.userName
fun Update.firstName(): String = message.from.firstName
fun Update.lastName(): String = message.from.lastName
fun Update.messageId(): Int = message.messageId
fun Update.replyMessageId(): Int = message.replyToMessage.messageId
fun Update.coordinatePair() = message.location.latitude to message.location.longitude
fun Update.isCommand() = message.isCommand
fun Update.text(): String? = message.text
fun Update.replyMessageText(): String? = message.replyToMessage.text
fun Update.isReply() = message.isReply
fun Update.isReplyCommand() = message.replyToMessage.isCommand
fun Update.languageCode(): String = message.from.languageCode
fun Update.locale(arguments: List<String> = emptyList()) = Locale(arguments.lastOrNull() ?: languageCode()).let {
    if (it in Locale.getAvailableLocales()) it else Locale(languageCode())
}

fun Update.createSticker(stickerId: String) = SendSticker(chatId(), InputFile(stickerId))
fun Update.createSendMessage(text: String, additionalCustomize: SendMessage.() -> Unit = {}) =
    SendMessage(chatId(), text).apply { additionalCustomize.invoke(this) }

fun Message.shortInfo() = "${from.firstName} says $text"

fun Update.createDeleteMessage(messageId: Int) = DeleteMessage(chatId(), messageId)

fun TelegramLongPollingBotExt.buildDefaultKeyboard() = ReplyKeyboardMarkup().apply {
    keyboard = getCommandsExecutor().chunked(3).map { chunk -> KeyboardRow(chunk.map { KeyboardButton("/${it.commandName()}") }) }
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
fun Locale.isNotRu() = this == Locale("ru")


fun String.numCodeToCharCode(exchanges: CbrDailyDTO) = exchanges.valCurs.map { it.numCode to it.charCode }.find { it.first == this }?.second