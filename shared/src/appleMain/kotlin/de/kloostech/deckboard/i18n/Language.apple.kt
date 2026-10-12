package de.kloostech.deckboard.i18n

import platform.Foundation.NSLocale
import platform.Foundation.preferredLanguages

private val SUPPORTED = listOf("de", "en")

/** The first of the user's preferred languages that the app is translated into (`de-DE` → `de`), else English. */
actual fun systemLanguage(): String =
    NSLocale.preferredLanguages
        .mapNotNull { (it as? String)?.substringBefore('-')?.substringBefore('_')?.lowercase() }
        .firstOrNull { it in SUPPORTED } ?: "en"
