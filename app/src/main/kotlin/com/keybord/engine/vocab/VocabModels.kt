package com.keybord.engine.vocab

/**
 * Sources for candidate words.
 */
enum class CandidateSource {
    STATIC_DICTIONARY,
    USER_HISTORY,
    PERSONAL_DICTIONARY,
    OOV_LEARNED
}

/**
 * Learning trigger event types mirroring PRD specs:
 * - NORMAL_TYPING: Promoted after 2 uses
 * - SUGGESTION_ACCEPTED: 3x boost
 * - MANUAL_CORRECTION: 4x boost for replacement, demotion for reverted word
 * - REJECTED_SUGGESTION: Demotion / penalty
 */
enum class LearningEvent {
    NORMAL_TYPING,
    SUGGESTION_ACCEPTED,
    MANUAL_CORRECTION,
    REJECTED_SUGGESTION
}

/**
 * A learned vocabulary record.
 */
data class WordEntry(
    val word: String,
    val count: Int,
    val lastUsedTimestamp: Long,
    val isOov: Boolean = false,
    val shortcut: String? = null
)

/**
 * Candidate prediction for display in the keyboard suggestion strip.
 */
data class PredictionCandidate(
    val word: String,
    val score: Float,
    val source: CandidateSource,
    val contextMatchOrder: Int = 0 // 0=unigram, 1=bigram, 2=trigram, 3=shortcut
) : Comparable<PredictionCandidate> {
    override fun compareTo(other: PredictionCandidate): Int = other.score.compareTo(this.score)
}

/**
 * Configuration parameters for the Adaptive Vocabulary Learning Engine.
 */
data class AdaptiveVocabConfig(
    val maxUnigramEntries: Int = 20_000,
    val maxBigramEntries: Int = 40_000,
    val maxTrigramEntries: Int = 20_000,
    val oovPromotionThreshold: Int = 2, // PRD: typed twice before promoted to suggestions
    val halfLifeDays: Float = 30.0f,    // PRD: decay over 30 days if unused
    val suggestionBoostMultiplier: Int = 3, // PRD: 3x boost
    val manualCorrectionBoostMultiplier: Int = 4, // PRD: 4x boost
    val rejectionPenalty: Int = 2,
    val recencyWeight: Float = 0.35f,
    val frequencyWeight: Float = 0.40f,
    val contextWeight: Float = 0.25f
)
