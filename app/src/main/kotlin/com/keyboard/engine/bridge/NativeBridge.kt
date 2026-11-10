package com.keyboard.engine.bridge

/**
 * JNI Bridge to Rust native engine (keybord_native).
 * Offloads heavy computation (Dynamic Time Warping beam search,
 * Katz backoff N-gram modeling, text processing, and regex pipelines).
 */
object NativeBridge {

    init {
        try {
            System.loadLibrary("keybord_native")
        } catch (e: UnsatisfiedLinkError) {
            // Log error or fallback if running outside Android runtime
            System.err.println("Warning: Could not load keybord_native: ${e.message}")
        }
    }

    // Glide
    external fun glideDecodeGesture(
        pointsJson: String,
        keyPositionsJson: String,
        lexiconWords: Array<String>,
        lastTapTimestamp: Long
    ): String   // JSON array of GlideCandidate

    // Vocab
    external fun vocabOnWordCommitted(
        dbPath: String,
        word: String,
        contextJson: String,
        eventOrdinal: Int
    )

    external fun vocabPredictNext(
        dbPath: String,
        contextJson: String,
        maxCandidates: Int
    ): String   // JSON array of PredictionCandidate

    external fun vocabSearchPrefix(
        dbPath: String,
        prefix: String,
        limit: Int
    ): String   // JSON array of WordEntry

    // Magic Writing
    external fun magicProofread(text: String): String   // JSON ProofreadResult

    external fun magicRewrite(text: String, styleOrdinal: Int): String  // JSON RewriteDraft

    // Rambler
    external fun ramblerProcessStream(
        rawText: String,
        isFinalPause: Boolean
    ): String   // cleaned text string
}
