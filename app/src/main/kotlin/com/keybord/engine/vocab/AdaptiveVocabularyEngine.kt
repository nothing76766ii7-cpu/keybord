package com.keybord.engine.vocab

import android.content.Context
import kotlin.math.exp
import kotlin.math.ln

/**
 * Adaptive Vocabulary Learning Engine managing user-specific learning,
 * n-gram prediction, manual correction tracking, and contextual ranking.
 */
class AdaptiveVocabularyEngine(
    context: Context?,
    private val staticWordLookup: (String) -> Boolean = { false },
    private val config: AdaptiveVocabConfig = AdaptiveVocabConfig(),
    inMemory: Boolean = (context == null)
) {

    val storage = UserHistoryStorage(context, config, inMemory)
    val personalDictionary = PersonalDictionaryManager(context)

    fun getBigramScore(prevWord: String, word: String): Float =
        storage.getBigramScore(prevWord, word)

    /**
     * Called whenever a word is committed to the text input field.
     * Records unigram, bigram (W_t-1 -> W_t), and trigram (W_t-2, W_t-1 -> W_t).
     */
    fun onWordCommitted(
        word: String,
        contextWords: List<String> = emptyList(),
        event: LearningEvent = LearningEvent.NORMAL_TYPING
    ) {
        val cleanWord = word.trim().lowercase()
        if (cleanWord.length <= 1 && !cleanWord.matches(Regex("[a-z0-9]"))) return

        val isKnownInStatic = staticWordLookup(cleanWord)
        val isOov = !isKnownInStatic && !personalDictionary.contains(cleanWord)

        val countDelta = when (event) {
            LearningEvent.NORMAL_TYPING -> 1
            LearningEvent.SUGGESTION_ACCEPTED -> config.suggestionBoostMultiplier
            LearningEvent.MANUAL_CORRECTION -> config.manualCorrectionBoostMultiplier
            LearningEvent.REJECTED_SUGGESTION -> 0
        }

        val now = System.currentTimeMillis()

        // 1. Record Unigram
        storage.recordUnigram(cleanWord, countDelta, now, isOov)

        // 2. Record Bigram if preceding word exists
        if (contextWords.isNotEmpty()) {
            val prev1 = contextWords.last().trim().lowercase()
            storage.recordBigram(prev1, cleanWord, countDelta, now)
        }

        // 3. Record Trigram if preceding two words exist
        if (contextWords.size >= 2) {
            val prev2 = contextWords[contextWords.size - 2].trim().lowercase()
            val prev1 = contextWords[contextWords.size - 1].trim().lowercase()
            storage.recordTrigram(prev2, prev1, cleanWord, countDelta, now)
        }
    }

    /**
     * User tapped a suggested word in the keyboard candidate strip.
     * PRD: Boosts word frequency with 3x multiplier.
     */
    fun onSuggestionSelected(selectedWord: String, contextWords: List<String>) {
        onWordCommitted(selectedWord, contextWords, LearningEvent.SUGGESTION_ACCEPTED)
    }

    /**
     * User backspaced an autocorrected word and manually typed what they intended.
     * PRD: Demotes old word, elevates replacement with 4x multiplier.
     */
    fun onManualCorrection(revertedWord: String, correctedWord: String, contextWords: List<String>) {
        storage.recordRejection(revertedWord, config.rejectionPenalty)
        onWordCommitted(correctedWord, contextWords, LearningEvent.MANUAL_CORRECTION)
    }

    /**
     * User scrub-deleted or immediately backspaced an inserted suggestion.
     */
    fun onScrubDelete(deletedWord: String) {
        storage.recordRejection(deletedWord, config.rejectionPenalty)
    }

    /**
     * Deletes a word from personal learned history (PRD Section 4.3).
     */
    fun deleteLearnedWord(word: String) {
        storage.deleteWord(word)
        personalDictionary.deleteWord(word)
    }

    /**
     * Predicts next words based on preceding context (Katz backoff).
     * 1. Trigram context: P(W | prev2, prev1)
     * 2. Bigram context: P(W | prev1)
     * 3. Frequent unigrams
     */
    fun predictNextWords(contextWords: List<String>, maxCandidates: Int = 3): List<PredictionCandidate> {
        val candidates = mutableListOf<PredictionCandidate>()
        val seenWords = HashSet<String>()

        val prev1 = if (contextWords.isNotEmpty()) contextWords.last().lowercase().trim() else null
        val prev2 = if (contextWords.size >= 2) contextWords[contextWords.size - 2].lowercase().trim() else null

        // 1. Trigram predictions
        if (prev2 != null && prev1 != null) {
            val trigrams = storage.getTrigramCompletions(prev2, prev1, limit = maxCandidates)
            for ((w3, count) in trigrams) {
                if (seenWords.add(w3)) {
                    val score = 0.85f + (count * 0.05f).coerceAtMost(0.14f)
                    candidates.add(PredictionCandidate(w3, score, CandidateSource.USER_HISTORY, contextMatchOrder = 2))
                }
            }
        }

        // 2. Bigram predictions
        if (prev1 != null && candidates.size < maxCandidates) {
            val bigrams = storage.getBigramCompletions(prev1, limit = maxCandidates - candidates.size)
            for ((w2, count) in bigrams) {
                if (seenWords.add(w2)) {
                    val score = 0.65f + (count * 0.03f).coerceAtMost(0.18f)
                    candidates.add(PredictionCandidate(w2, score, CandidateSource.USER_HISTORY, contextMatchOrder = 1))
                }
            }
        }

        // 3. Fallback to frequent unigrams
        if (candidates.size < maxCandidates) {
            val topUnigrams = storage.searchPrefix("", limit = maxCandidates - candidates.size)
            for (entry in topUnigrams) {
                if (seenWords.add(entry.word)) {
                    candidates.add(
                        PredictionCandidate(
                            entry.word,
                            0.4f,
                            CandidateSource.USER_HISTORY,
                            contextMatchOrder = 0
                        )
                    )
                }
            }
        }

        candidates.sort()
        return candidates.take(maxCandidates)
    }

    /**
     * Ranks prefix completion candidates blending shortcuts, static lexicon,
     * and user history n-grams with recency half-life decay.
     */
    fun rankPrefixCompletions(
        prefix: String,
        contextWords: List<String> = emptyList(),
        maxCandidates: Int = 5
    ): List<PredictionCandidate> {
        val cleanPrefix = prefix.lowercase().trim()
        if (cleanPrefix.isEmpty()) {
            return predictNextWords(contextWords, maxCandidates)
        }

        val results = mutableListOf<PredictionCandidate>()
        val seen = HashSet<String>()
        val now = System.currentTimeMillis()
        val halfLifeMillis = config.halfLifeDays * 24 * 3600 * 1000f

        // Check shortcut in personal dictionary first (e.g. "omw" -> "On my way!")
        val shortcutExpansion = personalDictionary.getShortcutExpansion(cleanPrefix)
        if (shortcutExpansion != null) {
            results.add(
                PredictionCandidate(
                    shortcutExpansion,
                    1.0f,
                    CandidateSource.PERSONAL_DICTIONARY,
                    contextMatchOrder = 3
                )
            )
            seen.add(shortcutExpansion.lowercase())
        }

        // Query user history completions
        val userEntries = storage.searchPrefix(cleanPrefix, limit = 15)
        val prev1 = contextWords.lastOrNull()?.lowercase()?.trim()

        for (entry in userEntries) {
            if (!seen.add(entry.word)) continue

            val penalty = storage.getRejectionPenalty(entry.word)
            val effectiveCount = (entry.count - penalty).coerceAtLeast(1)

            val freqScore = (ln(effectiveCount.toFloat() + 1f) / ln(100f)).coerceIn(0f, 1f)
            val age = (now - entry.lastUsedTimestamp).coerceAtLeast(0L)
            val recencyScore = exp(-ln(2f) * (age / halfLifeMillis))

            var contextScore = 0f
            if (prev1 != null) {
                val bigrams = storage.getBigramCompletions(prev1, limit = 10)
                if (bigrams.any { it.first == entry.word }) {
                    contextScore = 0.5f
                }
            }

            val totalScore = config.frequencyWeight * freqScore +
                    config.recencyWeight * recencyScore +
                    config.contextWeight * contextScore

            results.add(
                PredictionCandidate(
                    word = entry.word,
                    score = totalScore,
                    source = if (entry.isOov) CandidateSource.OOV_LEARNED else CandidateSource.USER_HISTORY,
                    contextMatchOrder = if (contextScore > 0f) 1 else 0
                )
            )
        }

        results.sort()
        return results.take(maxCandidates)
    }
}
