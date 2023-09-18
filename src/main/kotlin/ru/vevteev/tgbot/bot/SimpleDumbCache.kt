package ru.vevteev.tgbot.bot

import ru.vevteev.tgbot.dto.DrinkRemember


object SimpleDumbCache {
    val map = mutableMapOf<String, String>()
    val drinkRememberSet = mutableSetOf<DrinkRemember>()
}