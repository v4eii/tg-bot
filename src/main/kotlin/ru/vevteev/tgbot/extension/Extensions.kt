package ru.vevteev.tgbot.extension

import org.springframework.context.MessageSource
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.send.SendSticker
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage
import org.telegram.telegrambots.meta.api.objects.InputFile
import org.telegram.telegrambots.meta.api.objects.Update
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*


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
fun Update.locale(arguments: List<String> = emptyList()) = Locale(arguments.lastOrNull() ?: languageCode())

fun Update.createSticker(stickerId: String) = SendSticker(chatId(), InputFile(stickerId))
fun Update.createSendMessage(text: String, additionalCustomize: SendMessage.() -> Unit = {}) = SendMessage(chatId(), text).apply { additionalCustomize.invoke(this) }
fun Update.createDeleteMessage(messageId: Int) = DeleteMessage(chatId(), messageId)


fun MessageSource.getMessage(code: String, locale: Locale = Locale.getDefault()) = getMessage(code, null, locale)


fun Int.toLocalDate(zoneId: ZoneId): LocalDate = LocalDate.ofInstant(Instant.ofEpochSecond(this.toLong()), zoneId)
fun Int.toLocalDateTime(zoneId: ZoneId): LocalDateTime =
    LocalDateTime.ofInstant(Instant.ofEpochSecond(this.toLong()), zoneId)

fun String.bold() = "*$this*"
fun String.space(count: Int = 1) = "$this${"\n".repeat(count)}"
fun <T> T?.valueOrAbsent() = this?.toString() ?: "absent"