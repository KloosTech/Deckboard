package de.kloostech.deckboard

import de.kloostech.deckboard.i18n.Language
import de.kloostech.deckboard.i18n.tr
import kotlin.test.Test
import kotlin.test.assertEquals

class TranslationsTest {
    private inline fun inLanguage(code: String, block: () -> Unit) {
        val before = Language.current
        Language.current = code
        try { block() } finally { Language.current = before }
    }

    @Test
    fun englishReturnsTheKeyAndFillsPlaceholders() = inLanguage("en") {
        assertEquals("Cancel new label", tr("Cancel new label"))
        assertEquals("Comments (3)", tr("Comments ({count})", "count" to 3))
    }

    @Test
    fun germanTranslatesAndFillsPlaceholders() = inLanguage("de") {
        assertEquals("Karte hinzufügen", tr("Add card"))
        assertEquals("Kommentare (3)", tr("Comments ({count})", "count" to 3))
        assertEquals("Verwende 5 bis 86400 Sekunden.", tr("Use {min} to {max} seconds.", "min" to 5, "max" to 86400))
    }

    @Test
    fun anUntranslatedTextFallsBackToEnglish() = inLanguage("de") {
        assertEquals("Some brand new text", tr("Some brand new text"))
        assertEquals("Hello Jack", tr("Hello {user}", "user" to "Jack"))
    }

    @Test
    fun otherLanguagesUseEnglish() = inLanguage("fr") {
        assertEquals("Add card", tr("Add card"))
    }
}
