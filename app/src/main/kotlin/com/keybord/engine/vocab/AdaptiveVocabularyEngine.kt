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
    var staticLexicon: com.keybord.engine.glide.TrieLexicon? = null

    private val writeExecutor = java.util.concurrent.Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "KeybordVocabWriter").apply { isDaemon = true }
    }
    private val penaltyCache = java.util.concurrent.ConcurrentHashMap<String, Int>()
    @Volatile private var isShutdown = false

    fun getBigramScore(prevWord: String, word: String): Float =
        if (isShutdown) 0f else storage.getBigramScore(prevWord, word)

    /**
     * Offloads DB unigram/bigram/trigram persistence off the main UI thread during continuous typing.
     */
    fun onWordCommittedAsync(
        word: String,
        contextWords: List<String> = emptyList(),
        event: LearningEvent = LearningEvent.NORMAL_TYPING
    ) {
        if (isShutdown || writeExecutor.isShutdown) return
        val contextSnapshot = contextWords.toList()
        try {
            writeExecutor.execute {
                if (isShutdown) return@execute
                try {
                    onWordCommitted(word, contextSnapshot, event)
                } catch (e: Exception) {
                    com.keybord.diagnostics.KeybordCrashLogger.recordNonFatal(
                        "AdaptiveVocabEngine",
                        "Async word commit failed",
                        e
                    )
                }
            }
        } catch (_: java.util.concurrent.RejectedExecutionException) {
            // Engine already shutting down
        }
    }

    fun shutdown() {
        isShutdown = true
        writeExecutor.shutdown()
        try {
            if (!writeExecutor.awaitTermination(250, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                writeExecutor.shutdownNow()
            }
        } catch (_: InterruptedException) {
            writeExecutor.shutdownNow()
        }
        try {
            storage.close()
        } catch (_: Exception) {
            // Ignore close during shutdown
        }
    }

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

        val isKnownInStatic = staticWordLookup(cleanWord) || (staticLexicon?.contains(cleanWord) == true)
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
     */
    fun onSuggestionSelected(selectedWord: String, contextWords: List<String>) {
        onWordCommitted(selectedWord, contextWords, LearningEvent.SUGGESTION_ACCEPTED)
    }

    /**
     * User backspaced an autocorrected word and manually typed what they intended.
     */
    fun onManualCorrection(revertedWord: String, correctedWord: String, contextWords: List<String>) {
        storage.recordRejection(revertedWord, config.rejectionPenalty)
        penaltyCache.remove(revertedWord.lowercase().trim())
        onWordCommitted(correctedWord, contextWords, LearningEvent.MANUAL_CORRECTION)
    }

    fun onManualCorrectionAsync(revertedWord: String, correctedWord: String, contextWords: List<String>) {
        if (isShutdown || writeExecutor.isShutdown) return
        penaltyCache.remove(revertedWord.lowercase().trim())
        val ctx = contextWords.toList()
        try {
            writeExecutor.execute {
                if (isShutdown) return@execute
                try {
                    onManualCorrection(revertedWord, correctedWord, ctx)
                } catch (e: Exception) {
                    com.keybord.diagnostics.KeybordCrashLogger.recordNonFatal("AdaptiveVocabEngine", "Async manual correction failed", e)
                }
            }
        } catch (_: java.util.concurrent.RejectedExecutionException) {
            // Engine already shutting down
        }
    }

    /**
     * User scrub-deleted or immediately backspaced an inserted suggestion.
     */
    fun onScrubDelete(deletedWord: String) {
        storage.recordRejection(deletedWord, config.rejectionPenalty)
        penaltyCache.remove(deletedWord.lowercase().trim())
    }

    fun onScrubDeleteAsync(deletedWord: String) {
        if (isShutdown || writeExecutor.isShutdown) return
        penaltyCache.remove(deletedWord.lowercase().trim())
        try {
            writeExecutor.execute {
                if (isShutdown) return@execute
                try {
                    onScrubDelete(deletedWord)
                } catch (e: Exception) {
                    com.keybord.diagnostics.KeybordCrashLogger.recordNonFatal("AdaptiveVocabEngine", "Async scrub delete failed", e)
                }
            }
        } catch (_: java.util.concurrent.RejectedExecutionException) {
            // Engine already shutting down
        }
    }

    /**
     * Deletes a word from personal learned history (PRD Section 4.3).
     */
    fun deleteLearnedWord(word: String) {
        storage.deleteWord(word)
        penaltyCache.remove(word.lowercase().trim())
        personalDictionary.deleteWord(word)
    }

    /**
     * Predicts next words based on preceding context (Katz backoff + English/Hinglish hints).
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

        // 3. Static English + Hinglish contextual bigram fallback when history is sparse
        if (prev1 != null && candidates.size < maxCandidates) {
            val hints = com.keybord.engine.glide.TrieLexicon.getContextualFallbackPredictions(prev1)
            for ((idx, hint) in hints.withIndex()) {
                if (seenWords.add(hint)) {
                    candidates.add(
                        PredictionCandidate(
                            hint,
                            0.55f - (idx * 0.02f),
                            CandidateSource.STATIC_DICTIONARY,
                            contextMatchOrder = 1
                        )
                    )
                }
            }
        }

        // 4. Fallback to frequent user unigrams
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
     * Ranks prefix completion candidates blending shortcuts, user history n-grams,
     * and static English + Hinglish lexicon completions without N+1 SQLite queries.
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

        // Query user history completions and fetch bigram context ONCE outside loop
        val userEntries = storage.searchPrefix(cleanPrefix, limit = 15)
        val prev1 = contextWords.lastOrNull()?.lowercase()?.trim()
        val prevBigrams = if (prev1 != null && userEntries.isNotEmpty()) {
            storage.getBigramCompletions(prev1, limit = 12).map { it.first }.toHashSet()
        } else {
            emptySet()
        }

        for (entry in userEntries) {
            val lowerWord = entry.word.lowercase()
            if (!seen.add(lowerWord)) continue

            val penalty = penaltyCache.getOrPut(lowerWord) {
                storage.getRejectionPenalty(lowerWord)
            }
            if (penaltyCache.size > 512) penaltyCache.clear()
            val effectiveCount = (entry.count - penalty).coerceAtLeast(1)

            val freqScore = (ln(effectiveCount.toFloat() + 1f) / ln(100f)).coerceIn(0f, 1f)
            val age = (now - entry.lastUsedTimestamp).coerceAtLeast(0L)
            val recencyScore = exp(-ln(2f) * (age / halfLifeMillis))

            val contextScore = if (prevBigrams.contains(entry.word)) 0.5f else 0f

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

        // Blend static English + Hinglish completions when user history alone doesn't fill strip
        val lex = staticLexicon
        if (lex != null && results.size < maxCandidates) {
            val staticMatches = lex.searchPrefix(cleanPrefix, limit = maxCandidates * 2)
            val maxFreqLn = ln(lex.maxFrequency + 1f).coerceAtLeast(1f)
            for ((word, freq) in staticMatches) {
                if (!seen.add(word.lowercase())) continue
                val normFreq = (ln(freq + 1f) / maxFreqLn).coerceIn(0f, 1f)
                val exactMatchBoost = if (word.equals(cleanPrefix, ignoreCase = true)) 0.14f else 0f
                results.add(
                    PredictionCandidate(
                        word = word,
                        score = (0.30f + 0.34f * normFreq + exactMatchBoost).coerceIn(0f, 0.78f),
                        source = CandidateSource.STATIC_DICTIONARY,
                        contextMatchOrder = 0
                    )
                )
            }
        }

        results.sort()
        return results.take(maxCandidates)
    }
}
