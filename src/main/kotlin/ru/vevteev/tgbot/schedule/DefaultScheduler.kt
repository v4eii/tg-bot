package ru.vevteev.tgbot.schedule

import org.springframework.context.MessageSource
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import ru.vevteev.tgbot.bot.DefaultBot
import ru.vevteev.tgbot.bot.SimpleDumbCache
import ru.vevteev.tgbot.extension.getMessage
import java.util.*

@Component
@EnableScheduling
class DefaultScheduler(private val bot: DefaultBot, private val messageSource: MessageSource) {

    @Scheduled(cron = "0 0 9-21/2 * * *")
    fun drinkRemember() {
        //tmp
        SimpleDumbCache.drinkRememberSet.add("1768783702")
        SimpleDumbCache.drinkRememberSet.add("494449240")
        //tmp

        SimpleDumbCache.drinkRememberSet.forEach {
            bot.sendMsg(it, messageSource.getMessage("msg.drink-water-remember",  Locale("ru"))) // todo
        }
    }

}