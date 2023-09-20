package ru.vevteev.tgbot.schedule

import org.springframework.context.MessageSource
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import ru.vevteev.tgbot.bot.DefaultBot
import ru.vevteev.tgbot.extension.getMessage
import ru.vevteev.tgbot.repository.RedisDrinkDao

@Component
@EnableScheduling
class DefaultScheduler(
    private val bot: DefaultBot,
    private val messageSource: MessageSource,
    private val redisDrinkDao: RedisDrinkDao,
) {

    @Scheduled(cron = "0 0 9-21/2 * * *")
    fun drinkRemember() {
        redisDrinkDao.getAllReminder()
            .forEach {
                bot.sendMsg(it.chatId, messageSource.getMessage("msg.drink-water-remember", it.locale))
            }
    }

}