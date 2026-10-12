package de.kloostech.deckboard

import de.kloostech.deckboard.i18n.GermanTranslations
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Reads the app's source code and checks that every text passed to `tr(...)` has a German translation, that no
 * translation is left over after a text changed, and that placeholders such as `{name}` match in both languages.
 */
class TranslationCoverageTest {
    private val literal = """"(?:[^"\\]|\\.)*""""
    private val call = Regex("""\btr\(\s*((?:$literal\s*\+?\s*)+)""")

    private fun unescape(lit: String) = lit.drop(1).dropLast(1)
        .replace("\\\"", "\"").replace("\\n", "\n").replace("\\$", "$").replace("\\\\", "\\")

    private fun sourceKeys(): Set<String> {
        val roots = listOf(File("src/commonMain"), File("../desktopApp/src"))
        val files = roots.filter { it.exists() }.flatMap { it.walkTopDown().filter { f -> f.extension == "kt" }.toList() }
            .filterNot { it.name == "Translations.kt" || it.name == "GermanTranslations.kt" }
        assertTrue(files.isNotEmpty(), "found no source files; run from the shared module")
        return files.flatMap { f ->
            call.findAll(f.readText()).map { m -> Regex(literal).findAll(m.groupValues[1]).joinToString("") { unescape(it.value) } }.toList()
        }.toSet()
    }

    private fun placeholders(text: String) = Regex("""\{[a-zA-Z]+}""").findAll(text).map { it.value }.toSortedSet()

    @Test
    fun everyTextHasAGermanTranslation() {
        val keys = sourceKeys()
        assertTrue(keys.size > 150, "expected to find the app's texts, found only ${keys.size}")
        val missing = keys - GermanTranslations.keys
        assertTrue(missing.isEmpty(), "Missing German translations:\n" + missing.sorted().joinToString("\n"))
    }

    @Test
    fun noUnusedTranslations() {
        val unused = GermanTranslations.keys - sourceKeys()
        assertTrue(unused.isEmpty(), "German entries whose English text no longer exists:\n" + unused.sorted().joinToString("\n"))
    }

    @Test
    fun placeholdersMatchInBothLanguages() {
        GermanTranslations.forEach { (english, german) -> assertEquals(placeholders(english), placeholders(german), "placeholders differ for: $english") }
    }
}
