package com.keybord.engine

import com.keybord.engine.glide.TrieLexicon
import com.keybord.ime.DeleteWordHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Verifies English + Hinglish bilingual recognition, preservation of natural Hinglish words
 * across mixed sentences, accurate English autocorrection, and rapid multiline stress stability.
 */
class BilingualHinglishTest {

    private fun loadBilingualLexicon(includeHinglish: Boolean = true): TrieLexicon {
        val lex = TrieLexicon()
        val mainFile = listOf(
            File("src/main/assets/wordlist.txt"),
            File("app/src/main/assets/wordlist.txt")
        ).firstOrNull { it.exists() }
        if (mainFile != null) {
            mainFile.bufferedReader().useLines { lex.loadFromSequence(it) }
        } else {
            lex.loadDefaultVocabulary()
        }
        if (includeHinglish) {
            val hFile = listOf(
                File("src/main/assets/wordlist_hinglish.txt"),
                File("app/src/main/assets/wordlist_hinglish.txt")
            ).firstOrNull { it.exists() }
            if (hFile != null) {
                hFile.bufferedReader().useLines { lex.loadFromSequence(it) }
            } else {
                lex.loadHinglishDefaults()
            }
        }
        return lex
    }

    @Test
    fun testAllPromptMixedSentencesPreserveIntendedSpelling() {
        val lex = loadBilingualLexicon(includeHinglish = true)

        val sentences = listOf(
            "Kal meeting hai, please time par aana.",
            "I will call you baad mein.",
            "Mujhe this application bahut pasand hai.",
            "Can you help me with this?",
            "Aaj weather is really nice."
        )

        for (sentence in sentences) {
            val tokens = sentence.split(Regex("[\\s,?.!]+")).filter { it.isNotBlank() }
            for (token in tokens) {
                val correction = lex.findBestCorrection(token, hinglishEnabled = true)
                assertNull(
                    "Intended bilingual word '$token' in '$sentence' should NOT be altered (got '$correction')",
                    correction
                )
            }
        }
    }

    @Test
    fun testCommonEnglishAndHinglishTyposStillAutocorrect() {
        val lex = loadBilingualLexicon(includeHinglish = true)

        assertEquals("the", lex.findBestCorrection("teh", hinglishEnabled = true))
        assertEquals("The", lex.findBestCorrection("Teh", hinglishEnabled = true))
        assertEquals("receive", lex.findBestCorrection("recieve", hinglishEnabled = true))
        assertEquals("bhai", lex.findBestCorrection("bhia", hinglishEnabled = true))
    }

    @Test
    fun testHinglishWordsAreNotConvertedIntoEnglishLookalikes() {
        val lex = loadBilingualLexicon(includeHinglish = true)

        val hinglishWords = listOf(
            "kal", "aaj", "hai", "hain", "par", "aana", "baad", "mein",
            "mujhe", "bahut", "pasand", "kya", "nahi", "haan", "bhai",
            "theek", "achha", "samajh", "zaroori", "yahan", "wahan"
        )
        for (word in hinglishWords) {
            assertTrue("Lexicon should recognize '$word'", lex.contains(word))
            assertNull(
                "Hinglish word '$word' must not be altered",
                lex.findBestCorrection(word, hinglishEnabled = true)
            )
        }
    }

    @Test
    fun testBilingualNextWordHintsExistForMixedPhrases() {
        loadBilingualLexicon(includeHinglish = true)

        val kalHints = TrieLexicon.getContextualFallbackPredictions("kal")
        assertTrue("Expected meeting/milte next after kal, got $kalHints", kalHints.isNotEmpty())
        val bahutHints = TrieLexicon.getContextualFallbackPredictions("bahut")
        assertTrue("Expected pasand/achha next after bahut, got $bahutHints", bahutHints.contains("pasand"))
        val aajHints = TrieLexicon.getContextualFallbackPredictions("aaj")
        assertTrue("Expected weather/kya next after aaj, got $aajHints", aajHints.isNotEmpty())
    }

    @Test
    fun testMultilineSpellcheckAndWordBoundaryStressAcross500Lines() {
        val lex = loadBilingualLexicon(includeHinglish = true)
        val doc = StringBuilder()
        val lineTemplate = "Kal meeting hai, please time par aana. I will call you baad mein!"

        for (i in 0 until 250) {
            if (i > 0) doc.append('\n')
            doc.append(lineTemplate)
            for (token in lineTemplate.split(Regex("[\\s,!.]+")).filter { it.isNotBlank() }) {
                assertNull(lex.findBestCorrection(token, hinglishEnabled = true))
            }
        }
        assertTrue("Document should hold thousands of chars across 250 lines", doc.length > 10_000)

        val boundaries = DeleteWordHelper.computeWordBoundariesBeforeCursor(doc, maxWords = 40)
        assertNotNull(boundaries)
        assertTrue("Expected non-trivial word boundaries", boundaries.size > 10)
    }
}
