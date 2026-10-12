package de.kloostech.deckboard.i18n

/** Two-letter lower-case code of the device's preferred language (`"en"`, `"de"`, ...). */
expect fun systemLanguage(): String

/** The language the app speaks: German when the device is set to German, English otherwise. Tests can override [current]. */
object Language {
    var current: String = systemLanguage()

    val isGerman: Boolean get() = current == "de"
}

/**
 * Translates a user-visible text. The English text is the key, so the code stays readable and an untranslated text
 * simply shows in English. `{name}` placeholders are filled from [args]: `tr("Hello {user}", "user" to name)`.
 */
fun tr(english: String, vararg args: Pair<String, Any?>): String {
    val template = if (Language.isGerman) GermanTranslations[english] ?: english else english
    return if (args.isEmpty()) template else args.fold(template) { text, (name, value) -> text.replace("{$name}", value.toString()) }
}
