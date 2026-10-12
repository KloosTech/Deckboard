package de.kloostech.deckboard.i18n

import java.util.Locale

actual fun systemLanguage(): String = Locale.getDefault().language.lowercase()
