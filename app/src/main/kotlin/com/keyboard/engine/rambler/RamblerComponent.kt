package com.keyboard.engine.rambler

import android.os.Handler
import android.os.Looper
import android.view.inputmethod.InputConnection
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

/**
 * ============================================================================
 * RAMBLER COMPONENT: AGENTIC CONTINUOUS DICTATION & CONTEXTUAL STREAM MANAGER
 * ============================================================================
 * Extracted from Google Gboard (com.google.android.inputmethod.latin).
 *
 * Capabilities:
 * - Continuous stream buffering and pause detection.
 * - Spoken disfluency cleaning ("um", "uh", "err", stuttered repetitions).
 * - True-casing (proper nouns, sentence starts, pronoun "I").
 * - Smart punctuation injection (prosody pauses, interrogative detection).
 * - Spoken number and symbol normalization ("five dollars" -> "$5", "ten percent" -> "10%").
 * - Dual-state undo state machine:
 *     State A: Cleaned, punctuated text.
 *     State B: Raw verbatim transcript.
 *   Enables instant one-tap toggle without losing original speech words.
 * - External text mutation detection: automatically commits & resets if user edits text outside chunk bounds.
 * - Idling timer: auto-commits text after inactivity.
 */

// ----------------------------------------------------------------------------
// 1. DATA MODELS & ENUMS
// ----------------------------------------------------------------------------

enum class ChunkState {
    RAW_TRANSCRIPT,     // State B: Verbatim continuous raw transcript
    CLEANED_REWRITTEN   // State A: Formatted, punctuated, disfluency-free text
}

enum class RamblerSessionState {
    IDLE,               // Inactive, listening for activation
    STREAMING,          // Active continuous text streaming
    STATE_A_REWRITTEN,  // Cleaned formatted text displayed in target editor
    STATE_B_TRANSCRIPT, // User reverted to raw verbatim transcript
    MUTATED_OUTSIDE     // User manually modified text; session committed & reset
}

data class TextChunk(
    val chunkId: Long,
    val rawText: String,
    val cleanedText: String,
    val startOffset: Int,
    val endOffset: Int,
    val timestamp: Long = System.currentTimeMillis(),
    var currentState: ChunkState = ChunkState.CLEANED_REWRITTEN
)

data class RamblerConfig(
    val idleTimeoutMs: Long = 3000L,
    val minChunkLength: Int = 12,
    val autoPunctuationEnabled: Boolean = true,
    val disfluencyFilterEnabled: Boolean = true,
    val numberNormalizationEnabled: Boolean = true,
    val preserveRewrittenOnRevert: Boolean = true
)

// ----------------------------------------------------------------------------
// 2. TEXT PROCESSING PIPELINE
// ----------------------------------------------------------------------------

class RamblerTextProcessor(private val config: RamblerConfig = RamblerConfig()) {

    private val disfluencyRegex = Regex(
        "\\b(um|uh|uhh|umm|ah|ahh|er|err|like,\\s*you\\s*know)\\b[,\\s]*",
        RegexOption.IGNORE_CASE
    )

    private val stutterRegex = Regex("\\b([a-zA-Z]+)\\s+\\1\\b", RegexOption.IGNORE_CASE)

    private val questionStarters = setOf(
        "who", "what", "where", "when", "why", "how",
        "is", "are", "do", "does", "did", "can", "could", "would", "should", "will"
    )

    private val numberMap = mapOf(
        "zero" to "0", "one" to "1", "two" to "2", "three" to "3", "four" to "4",
        "five" to "5", "six" to "6", "seven" to "7", "eight" to "8", "nine" to "9",
        "ten" to "10", "twenty" to "20", "fifty" to "50", "hundred" to "100"
    )

    fun processRawStream(rawText: String, isFinalPause: Boolean = false): String {
        if (rawText.isBlank()) return rawText

        try {
            val nativeResult = com.keyboard.engine.bridge.NativeBridge.ramblerProcessStream(rawText, isFinalPause)
            if (nativeResult.isNotEmpty()) {
                return nativeResult
            }
        } catch (e: Throwable) {
            // fallback
        }

        var text = rawText.trim()

        // 1. Remove spoken disfluencies & stuttered tokens
        if (config.disfluencyFilterEnabled) {
            text = disfluencyRegex.replace(text, "")
            text = stutterRegex.replace(text, "$1")
            text = text.replace(Regex("\\s{2,}"), " ")
        }

        // 2. Normalize spoken numbers and symbols
        if (config.numberNormalizationEnabled) {
            text = normalizeNumbersAndSymbols(text)
        }

        // 3. True-casing (Capitalize 'I', sentence starts)
        text = applyTrueCasing(text)

        // 4. Punctuation injection
        if (config.autoPunctuationEnabled && isFinalPause) {
            text = applySmartPunctuation(text)
        }

        return text.trim()
    }

    private fun normalizeNumbersAndSymbols(input: String): String {
        var text = input
        text = text.replace(Regex("(?i)\\b(\\w+)\\s+dollars?\\b")) { m ->
            val numStr = m.groupValues[1].lowercase(Locale.ROOT)
            val num = numberMap[numStr] ?: numStr
            "$$num"
        }
        text = text.replace(Regex("(?i)\\b(\\w+)\\s+percent\\b")) { m ->
            val numStr = m.groupValues[1].lowercase(Locale.ROOT)
            val num = numberMap[numStr] ?: numStr
            "$num%"
        }
        return text
    }

    private fun applyTrueCasing(input: String): String {
        if (input.isEmpty()) return input

        var text = input.replace(Regex("(?:^|\\s)i(?=\\s|['.,?!]|$)")) { m ->
            if (m.value.startsWith(" ")) " I" else "I"
        }

        val sb = StringBuilder()
        var capitalizeNext = true
        for (i in text.indices) {
            val c = text[i]
            if (capitalizeNext && c.isLetter()) {
                sb.append(c.uppercaseChar())
                capitalizeNext = false
            } else {
                sb.append(c)
                if (c == '.' || c == '?' || c == '!') {
                    capitalizeNext = true
                }
            }
        }
        return sb.toString()
    }

    private fun applySmartPunctuation(input: String): String {
        var text = input.trimEnd()
        if (text.isEmpty()) return text

        val lastChar = text.last()
        if (lastChar == '.' || lastChar == '?' || lastChar == '!') {
            return text
        }

        val firstWord = text.split(" ").firstOrNull()?.lowercase(Locale.ROOT)?.trim()
        val isQuestion = firstWord != null && questionStarters.contains(firstWord)

        return if (isQuestion) "$text?" else "$text."
    }
}

// ----------------------------------------------------------------------------
// 3. UNDO STATE MACHINE
// ----------------------------------------------------------------------------

class RamblerUndoStateMachine(private val config: RamblerConfig = RamblerConfig()) {

    interface UndoStateListener {
        fun onChunkStateChanged(chunk: TextChunk, newState: ChunkState)
        fun onSessionReset(reason: String)
    }

    var listener: UndoStateListener? = null

    private val chunkIdGenerator = AtomicLong(1000L)
    private val chunkHistory = mutableListOf<TextChunk>()

    var sessionState: RamblerSessionState = RamblerSessionState.IDLE
        private set

    val activeChunk: TextChunk?
        get() = chunkHistory.lastOrNull()

    fun startSession() {
        sessionState = RamblerSessionState.STREAMING
    }

    fun registerChunk(rawText: String, cleanedText: String, startOffset: Int): TextChunk {
        val chunkId = chunkIdGenerator.incrementAndGet()
        val chunk = TextChunk(
            chunkId = chunkId,
            rawText = rawText,
            cleanedText = cleanedText,
            startOffset = startOffset,
            endOffset = startOffset + cleanedText.length,
            currentState = ChunkState.CLEANED_REWRITTEN
        )
        chunkHistory.add(chunk)
        sessionState = RamblerSessionState.STATE_A_REWRITTEN
        listener?.onChunkStateChanged(chunk, ChunkState.CLEANED_REWRITTEN)
        return chunk
    }

    fun revertToRawTranscript(): Pair<TextChunk, String>? {
        val chunk = activeChunk ?: return null
        if (chunk.currentState == ChunkState.RAW_TRANSCRIPT) {
            return null
        }

        chunk.currentState = ChunkState.RAW_TRANSCRIPT
        sessionState = RamblerSessionState.STATE_B_TRANSCRIPT
        listener?.onChunkStateChanged(chunk, ChunkState.RAW_TRANSCRIPT)
        return chunk to chunk.rawText
    }

    fun restoreCleanedText(): Pair<TextChunk, String>? {
        val chunk = activeChunk ?: return null
        if (!config.preserveRewrittenOnRevert || chunk.currentState == ChunkState.CLEANED_REWRITTEN) {
            return null
        }

        chunk.currentState = ChunkState.CLEANED_REWRITTEN
        sessionState = RamblerSessionState.STATE_A_REWRITTEN
        listener?.onChunkStateChanged(chunk, ChunkState.CLEANED_REWRITTEN)
        return chunk to chunk.cleanedText
    }

    fun handleTextMutation(cursorPosition: Int) {
        val chunk = activeChunk ?: return
        if (cursorPosition < chunk.startOffset || cursorPosition > chunk.endOffset + 2) {
            sessionState = RamblerSessionState.MUTATED_OUTSIDE
            listener?.onSessionReset("User edited text outside active chunk bounds (cursor=$cursorPosition)")
            endSession()
        }
    }

    fun endSession() {
        sessionState = RamblerSessionState.IDLE
        chunkHistory.clear()
    }
}

// ----------------------------------------------------------------------------
// 4. RAMBLER CONTROLLER
// ----------------------------------------------------------------------------

class RamblerController(
    private val config: RamblerConfig = RamblerConfig(),
    private val mainLooper: Looper = Looper.getMainLooper()
) {

    private val textProcessor = RamblerTextProcessor(config)
    val undoStateMachine = RamblerUndoStateMachine(config)

    private val handler = Handler(mainLooper)
    private var inputConnection: InputConnection? = null

    private val rawBuffer = StringBuilder()
    private var currentCommittedOffset = 0

    private val idleTimeoutRunnable = Runnable {
        finishStreaming(isIdling = true)
    }

    fun attachInputConnection(connection: InputConnection?) {
        this.inputConnection = connection
    }

    fun startSession(initialOffset: Int = 0) {
        rawBuffer.clear()
        currentCommittedOffset = initialOffset
        undoStateMachine.startSession()
        resetIdleTimer()
    }

    fun onStreamTokenReceived(token: String, isFinalPause: Boolean = false) {
        resetIdleTimer()
        rawBuffer.append(token)

        val rawCurrent = rawBuffer.toString()
        val cleaned = textProcessor.processRawStream(rawCurrent, isFinalPause)

        val ic = inputConnection
        if (ic != null) {
            if (isFinalPause) {
                ic.setComposingText("", 1)
                ic.commitText(cleaned, 1)
                undoStateMachine.registerChunk(rawCurrent, cleaned, currentCommittedOffset)
                currentCommittedOffset += cleaned.length
                rawBuffer.clear()
            } else {
                ic.setComposingText(cleaned, 1)
            }
        }
    }

    fun toggleTranscriptRevert(): Boolean {
        val ic = inputConnection ?: return false

        if (undoStateMachine.sessionState == RamblerSessionState.STATE_A_REWRITTEN) {
            val revertResult = undoStateMachine.revertToRawTranscript() ?: return false
            val (chunk, rawText) = revertResult
            replaceChunkText(ic, chunk, rawText)
            return true
        } else if (undoStateMachine.sessionState == RamblerSessionState.STATE_B_TRANSCRIPT) {
            val restoreResult = undoStateMachine.restoreCleanedText() ?: return false
            val (chunk, cleanedText) = restoreResult
            replaceChunkText(ic, chunk, cleanedText)
            return true
        }
        return false
    }

    private fun replaceChunkText(ic: InputConnection, chunk: TextChunk, replacement: String) {
        ic.beginBatchEdit()
        try {
            val prevLen = if (chunk.currentState == ChunkState.RAW_TRANSCRIPT) chunk.cleanedText.length else chunk.rawText.length
            ic.deleteSurroundingText(prevLen, 0)
            ic.commitText(replacement, 1)
        } finally {
            ic.endBatchEdit()
        }
    }

    fun finishStreaming(isIdling: Boolean = false) {
        handler.removeCallbacks(idleTimeoutRunnable)
        if (rawBuffer.isNotEmpty()) {
            val finalCleaned = textProcessor.processRawStream(rawBuffer.toString(), isFinalPause = true)
            inputConnection?.setComposingText("", 1)
            inputConnection?.commitText(finalCleaned, 1)
            undoStateMachine.registerChunk(rawBuffer.toString(), finalCleaned, currentCommittedOffset)
            rawBuffer.clear()
        }
        if (isIdling) {
            undoStateMachine.endSession()
        }
    }

    private fun resetIdleTimer() {
        handler.removeCallbacks(idleTimeoutRunnable)
        handler.postDelayed(idleTimeoutRunnable, config.idleTimeoutMs)
    }
}
