package com.keybord.engine

import com.keybord.engine.vocab.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AdaptiveVocabTest {

    private lateinit var vocabEngine: AdaptiveVocabularyEngine

    @Before
    fun setUp() {
        vocabEngine = AdaptiveVocabularyEngine(
            context = null,
            staticWordLookup = { word -> setOf("the", "quick", "brown", "fox", "hello").contains(word) },
            inMemory = true
        )
    }

    @Test
    fun testNormalTypingAndTrigramPrediction() {
        vocabEngine.onWordCommitted("see", emptyList())
        vocabEngine.onWordCommitted("you", listOf("see"))
        vocabEngine.onWordCommitted("soon", listOf("see", "you"))

        val predictions = vocabEngine.predictNextWords(listOf("see", "you"))
        assertFalse("Predictions should not be empty", predictions.isEmpty())
        assertEquals("Top trigram candidate should be 'soon'", "soon", predictions.first().word)
    }

    @Test
    fun testManualCorrectionReinforcement() {
        // User typed "teh", autocorrected to "the". Corrected back to "teh".
        vocabEngine.onManualCorrection(revertedWord = "the", correctedWord = "teh", contextWords = emptyList())

        val completions = vocabEngine.rankPrefixCompletions("te", emptyList())
        val words = completions.map { it.word }
        assertTrue("Corrected word 'teh' should be promoted in completions", words.contains("teh"))
    }

    @Test
    fun testOovPromotionThreshold() {
        val slang = "yeet"
        vocabEngine.onWordCommitted(slang, emptyList(), LearningEvent.NORMAL_TYPING)
        var completions = vocabEngine.rankPrefixCompletions("yee", emptyList())
        assertTrue("First occurrence shouldn't promote unconfirmed slang", completions.none { it.word == slang && it.source != CandidateSource.OOV_LEARNED })

        vocabEngine.onWordCommitted(slang, emptyList(), LearningEvent.NORMAL_TYPING)
        completions = vocabEngine.rankPrefixCompletions("yee", emptyList())
        assertTrue("Second occurrence promotes slang to general candidates", completions.any { it.word == slang })
    }

    @Test
    fun testRejectionPenalty() {
        val badWord = "accidental"
        vocabEngine.onWordCommitted(badWord, emptyList())
        vocabEngine.onScrubDelete(badWord)

        val completions = vocabEngine.rankPrefixCompletions("accid", emptyList())
        val candidate = completions.find { it.word == badWord }
        if (candidate != null) {
            assertTrue("Score must reflect rejection penalty", candidate.score < 0.6f)
        }
    }

    @Test
    fun testCandidateDeletionOnLongPress() {
        val word = "unwantedword"
        vocabEngine.onWordCommitted(word, emptyList())
        vocabEngine.onWordCommitted(word, emptyList())

        var completions = vocabEngine.rankPrefixCompletions("unwanted", emptyList())
        assertTrue(completions.any { it.word == word })

        // User triggers long-press deletion
        vocabEngine.deleteLearnedWord(word)

        completions = vocabEngine.rankPrefixCompletions("unwanted", emptyList())
        assertFalse("Deleted word must no longer appear in predictions", completions.any { it.word == word })
    }
}
