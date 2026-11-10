package com.keyboard.engine.magicwrite

import android.view.inputmethod.InputConnection
import java.util.Locale

/**
 * ============================================================================
 * MAGIC WRITING COMPONENT: PROOFREAD, TONE REWRITING & CONVERSATIONAL PROMPTER
 * ============================================================================
 * Extracted from Google Gboard (com.google.android.inputmethod.latin).
 *
 * Capabilities:
 * - Proofread Engine ("Fix it"):
 *     Real-time grammar, spelling, capitalization, and punctuation detection
 *     with character-accurate diff replacement spans.
 * - Style / Tone Transformation Engine:
 *     Transforms text into 6 canonical Gboard styles:
 *       1. Formal (FORMALIZE): expands contractions, softens imperatives, professional tone.
 *       2. Casual (CASUALIZE): friendly greetings, contractions, warmth.
 *       3. Concise (CONCISE): cuts wordy padding, direct to the point.
 *       4. Elaborate (ELABORATE): adds courteous background detail and inquiry.
 *       5. Emojify (EMOJIFY): context-aware semantic emoji placement.
 *       6. Poetic (POETIC): lyrical rhythm and expressive prose.
 * - Conversational Prompter:
 *     Constructs XML-delimited few-shot prompts with <CONTEXT>, <CURRENT_DRAFT>,
 *     <RELATED_TEXT>, <INSTRUCTION>, and parses <INSTRUCTION_CLASS>, <DRAFT>,
 *     and <SUGGESTED_INSTRUCTION> tags.
 * - MagicWritingCoordinator:
 *     Unified API for InputConnection batch replacement, preview chips, and undo cache.
 */

// ----------------------------------------------------------------------------
// 1. DATA MODELS & ENUMS
// ----------------------------------------------------------------------------

enum class WritingStyle(val displayName: String, val promptInstruction: String) {
    FORMAL("Formal", "Make it more formal, polite, and professional."),
    CASUAL("Casual", "Make it casual, friendly, and conversational."),
    CONCISE("Concise", "Make it more concise and directly to the point."),
    ELABORATE("Elaborate", "Expand on details and explain more thoroughly."),
    EMOJIFY("Emojify", "Add expressive emojis naturally throughout the text."),
    POETIC("Poetic", "Rewrite it with lyrical rhythm and poetic flair.")
}

enum class CorrectionType {
    SPELLING,
    GRAMMAR,
    PUNCTUATION,
    CAPITALIZATION,
    REDUNDANCY
}

data class ProofreadCorrection(
    val originalSpan: String,
    val replacementSpan: String,
    val startIndex: Int,
    val endIndex: Int,
    val type: CorrectionType,
    val explanation: String
)

data class ProofreadResult(
    val originalText: String,
    val correctedText: String,
    val corrections: List<ProofreadCorrection>,
    val hasChanges: Boolean = corrections.isNotEmpty()
)

data class RewriteDraft(
    val text: String,
    val style: WritingStyle,
    val instructionClass: String = "MODIFICATION",
    val suggestedFollowUps: List<String> = emptyList()
)

data class ConversationContext(
    val recentMessages: List<Pair<String, String>> = emptyList(),
    val userPersona: String = "User",
    val recipientName: String? = null
)

data class MagicWriteConfig(
    val inputWordsLimit: Int = 120,
    val proofreadTriggerDelayMs: Long = 600L,
    val maxDraftSuggestions: Int = 3,
    val preferOnDeviceInference: Boolean = true
)

// ----------------------------------------------------------------------------
// 2. PROOFREAD ENGINE ("FIX IT")
// ----------------------------------------------------------------------------

class ProofreadEngine {

    private val commonSpellingErrors = mapOf(
        "teh" to "the", "recieve" to "receive", "seperate" to "separate",
        "definately" to "definitely", "untill" to "until", "occured" to "occurred",
        "truely" to "truly", "wierd" to "weird", "acheive" to "achieve",
        "alot" to "a lot", "goverment" to "government", "tommorrow" to "tomorrow",
        "dont" to "don't", "cant" to "can't", "wont" to "won't", "isnt" to "isn't",
        "im" to "I'm", "ive" to "I've", "ill" to "I'll", "id" to "I'd"
    )

    private val grammarPatterns = listOf(
        Regex("(?i)\\b(could|should|would)\\s+of\\b") to "$1 have",
        Regex("(?i)\\bthey\\s+is\\b") to "they are",
        Regex("(?i)\\bwe\\s+was\\b") to "we were",
        Regex("(?i)\\byou\\s+was\\b") to "you were",
        Regex("(?i)\\bhe\\s+don't\\b") to "he doesn't",
        Regex("(?i)\\bshe\\s+don't\\b") to "she doesn't"
    )

    private val questionStarters = setOf(
        "who", "what", "where", "when", "why", "how", "can", "could", "would", "is", "are", "do", "does"
    )

    fun proofread(text: String): ProofreadResult {
        if (text.isBlank()) {
            return ProofreadResult(text, text, emptyList())
        }

        try {
            val jsonStr = com.keyboard.engine.bridge.NativeBridge.magicProofread(text)
            val obj = org.json.JSONObject(jsonStr)
            val correctedText = obj.optString("corrected_text", text)
            val correctionsJson = obj.optJSONArray("corrections")
            val list = mutableListOf<ProofreadCorrection>()
            if (correctionsJson != null) {
                for (i in 0 until correctionsJson.length()) {
                    val c = correctionsJson.getJSONObject(i)
                    list.add(
                        ProofreadCorrection(
                            originalSpan = c.getString("original_span"),
                            replacementSpan = c.getString("replacement_span"),
                            startIndex = c.getInt("start_index"),
                            endIndex = c.getInt("end_index"),
                            type = CorrectionType.valueOf(c.optString("correction_type", "SPELLING").uppercase()),
                            explanation = c.optString("explanation", "")
                        )
                    )
                }
            }
            if (list.isNotEmpty() || correctedText != text) {
                return ProofreadResult(
                    originalText = text,
                    correctedText = correctedText,
                    corrections = list,
                    hasChanges = list.isNotEmpty()
                )
            }
        } catch (e: Throwable) {
            // fallback
        }

        val corrections = mutableListOf<ProofreadCorrection>()

        // 1. Check spelling errors
        val wordsRegex = Regex("\\b[a-zA-Z']+\\b")
        for (match in wordsRegex.findAll(text)) {
            val word = match.value
            val lower = word.lowercase(Locale.ROOT)
            val replacement = commonSpellingErrors[lower]
            if (replacement != null && !word.equals(replacement, ignoreCase = false)) {
                val finalReplacement = if (word.first().isUpperCase()) {
                    replacement.replaceFirstChar { it.uppercase() }
                } else replacement

                corrections.add(
                    ProofreadCorrection(
                        originalSpan = word,
                        replacementSpan = finalReplacement,
                        startIndex = match.range.first,
                        endIndex = match.range.last + 1,
                        type = CorrectionType.SPELLING,
                        explanation = "Corrected spelling of '$word'"
                    )
                )
            }
        }

        // 2. Check grammar patterns
        for ((pattern, fix) in grammarPatterns) {
            for (match in pattern.findAll(text)) {
                corrections.add(
                    ProofreadCorrection(
                        originalSpan = match.value,
                        replacementSpan = match.value.replace(pattern, fix),
                        startIndex = match.range.first,
                        endIndex = match.range.last + 1,
                        type = CorrectionType.GRAMMAR,
                        explanation = "Grammatical agreement fix"
                    )
                )
            }
        }

        // 3. Capitalization & Punctuation
        val trimmed = text.trimEnd()
        if (trimmed.isNotEmpty()) {
            if (trimmed.first().isLowerCase()) {
                corrections.add(
                    ProofreadCorrection(
                        originalSpan = trimmed.first().toString(),
                        replacementSpan = trimmed.first().uppercase(),
                        startIndex = 0,
                        endIndex = 1,
                        type = CorrectionType.CAPITALIZATION,
                        explanation = "Capitalize first word of sentence"
                    )
                )
            }

            val standaloneIRegex = Regex("(?:^|\\s)i(?=\\s|['.,?!]|$)")
            for (match in standaloneIRegex.findAll(text)) {
                val offset = if (match.value.startsWith(" ")) match.range.first + 1 else match.range.first
                corrections.add(
                    ProofreadCorrection(
                        originalSpan = "i",
                        replacementSpan = "I",
                        startIndex = offset,
                        endIndex = offset + 1,
                        type = CorrectionType.CAPITALIZATION,
                        explanation = "Capitalize pronoun 'I'"
                    )
                )
            }

            val lastChar = trimmed.last()
            if (lastChar != '.' && lastChar != '?' && lastChar != '!') {
                val firstWord = trimmed.split(" ").firstOrNull()?.lowercase(Locale.ROOT)
                val expectedPunctuation = if (firstWord in questionStarters) "?" else "."
                corrections.add(
                    ProofreadCorrection(
                        originalSpan = "",
                        replacementSpan = expectedPunctuation,
                        startIndex = trimmed.length,
                        endIndex = trimmed.length,
                        type = CorrectionType.PUNCTUATION,
                        explanation = "Add missing sentence terminator"
                    )
                )
            }
        }

        val sortedCorrections = corrections.sortedByDescending { it.startIndex }
        val sb = StringBuilder(text)
        for (c in sortedCorrections) {
            if (c.startIndex <= c.endIndex && c.endIndex <= sb.length) {
                sb.replace(c.startIndex, c.endIndex, c.replacementSpan)
            }
        }

        return ProofreadResult(
            originalText = text,
            correctedText = sb.toString(),
            corrections = corrections.sortedBy { it.startIndex }
        )
    }
}

// ----------------------------------------------------------------------------
// 3. STYLE & TONE REWRITE ENGINE
// ----------------------------------------------------------------------------

class StyleRewriteEngine {

    private val emojiDictionary = mapOf(
        "love" to "❤️", "great" to "🔥", "awesome" to "✨", "cool" to "😎",
        "happy" to "😊", "congrats" to "🎉", "celebrate" to "🥳", "party" to "🎈",
        "work" to "💼", "job" to "💻", "study" to "📚", "school" to "🏫",
        "yes" to "👍", "ok" to "👌", "agree" to "✅", "good" to "🙌",
        "food" to "🍽️", "pizza" to "🍕", "coffee" to "☕", "beer" to "🍻",
        "idea" to "💡", "think" to "🤔", "question" to "❓", "fire" to "🔥",
        "thanks" to "🙏", "thank" to "🙏", "hello" to "👋", "hi" to "👋",
        "fast" to "⚡", "time" to "⏰", "money" to "💰", "car" to "🚗"
    )

    private val wordyPhrases = listOf(
        Regex("(?i)\\bin order to\\b") to "to",
        Regex("(?i)\\bdue to the fact that\\b") to "because",
        Regex("(?i)\\bat this point in time\\b") to "currently",
        Regex("(?i)\\bat the present moment\\b") to "now",
        Regex("(?i)\\bfor the purpose of\\b") to "for",
        Regex("(?i)\\bi am writing to let you know that\\s*") to "",
        Regex("(?i)\\bplease be advised that\\s*") to ""
    )

    private val formalExpansions = listOf(
        Regex("(?i)\\bcan't\\b") to "cannot",
        Regex("(?i)\\bdon't\\b") to "do not",
        Regex("(?i)\\bwon't\\b") to "will not",
        Regex("(?i)\\bwanna\\b") to "would like to",
        Regex("(?i)\\bgonna\\b") to "going to",
        Regex("(?i)\\bgimme\\b") to "please provide",
        Regex("(?i)\\bthanks\\b") to "Thank you",
        Regex("(?i)\\bhey\\b") to "Greetings,",
        Regex("(?i)\\byeah|yep\\b") to "Yes, certainly"
    )

    fun transformStyle(input: String, style: WritingStyle): RewriteDraft {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            return RewriteDraft(input, style)
        }

        try {
            val jsonStr = com.keyboard.engine.bridge.NativeBridge.magicRewrite(input, style.ordinal)
            val obj = org.json.JSONObject(jsonStr)
            val draftText = obj.optString("text")
            if (draftText.isNotEmpty()) {
                val followUpsJson = obj.optJSONArray("suggested_follow_ups")
                val followUps = mutableListOf<String>()
                if (followUpsJson != null) {
                    for (i in 0 until followUpsJson.length()) {
                        followUps.add(followUpsJson.getString(i))
                    }
                }
                return RewriteDraft(
                    text = draftText,
                    style = style,
                    instructionClass = obj.optString("instruction_class", "MODIFICATION"),
                    suggestedFollowUps = followUps
                )
            }
        } catch (e: Throwable) {
            // fallback
        }

        val rewrittenText = when (style) {
            WritingStyle.FORMAL -> formalize(trimmed)
            WritingStyle.CASUAL -> casualize(trimmed)
            WritingStyle.CONCISE -> makeConcise(trimmed)
            WritingStyle.ELABORATE -> elaborate(trimmed)
            WritingStyle.EMOJIFY -> emojify(trimmed)
            WritingStyle.POETIC -> makePoetic(trimmed)
        }

        val followUps = generateFollowUpSuggestions(style)
        return RewriteDraft(
            text = rewrittenText,
            style = style,
            instructionClass = "MODIFICATION",
            suggestedFollowUps = followUps
        )
    }

    private fun formalize(text: String): String {
        var res = text
        for ((pattern, replacement) in formalExpansions) {
            res = pattern.replace(res, replacement)
        }
        res = res.replace(Regex("(?i)^send me\\b"), "Could you please send me")
        res = res.replace(Regex("(?i)^tell me\\b"), "Please let me know")
        if (!res.endsWith(".") && !res.endsWith("?")) res += "."
        return res.replaceFirstChar { it.uppercase() }
    }

    private fun casualize(text: String): String {
        var res = text
        res = res.replace(Regex("(?i)\\bdo not\\b"), "don't")
        res = res.replace(Regex("(?i)\\bcannot\\b"), "can't")
        res = res.replace(Regex("(?i)\\bwill not\\b"), "won't")
        res = res.replace(Regex("(?i)\\bwould like to\\b"), "wanna")
        res = res.replace(Regex("(?i)^greetings,?"), "Hey!")
        if (res.endsWith(".")) res = res.dropLast(1) + "!"
        return res
    }

    private fun makeConcise(text: String): String {
        var res = text
        for ((pattern, replacement) in wordyPhrases) {
            res = pattern.replace(res, replacement)
        }
        return res.replace(Regex("\\s{2,}"), " ").trim().replaceFirstChar { it.uppercase() }
    }

    private fun elaborate(text: String): String {
        val base = text.trimEnd('.', '!', '?')
        return "I wanted to reach out regarding this: $base. Please let me know if you need any additional context or clarification, and I will be glad to follow up."
    }

    private fun emojify(text: String): String {
        val words = text.split(Regex("(?<=\\s)|(?=\\s)"))
        val sb = StringBuilder()
        for (w in words) {
            sb.append(w)
            val clean = w.lowercase(Locale.ROOT).replace(Regex("[^a-z]"), "")
            val emoji = emojiDictionary[clean]
            if (emoji != null) {
                sb.append(" ").append(emoji)
            }
        }
        return sb.toString().replace(Regex("\\s{2,}"), " ").trim()
    }

    private fun makePoetic(text: String): String {
        val base = text.trimEnd('.', '!', '?')
        return "Beneath the sky of quiet thought, $base, as destiny softly sought."
    }

    private fun generateFollowUpSuggestions(style: WritingStyle): List<String> {
        return when (style) {
            WritingStyle.FORMAL -> listOf("Add greeting", "Make more concise", "Include deadline")
            WritingStyle.CASUAL -> listOf("Add emoji", "Make shorter", "Sounds good")
            WritingStyle.CONCISE -> listOf("Make formal", "Add gratitude", "Ask for confirmation")
            WritingStyle.ELABORATE -> listOf("Summarize", "Make polite", "Request review")
            WritingStyle.EMOJIFY -> listOf("More emojis", "Make casual", "Tone down")
            WritingStyle.POETIC -> listOf("Make playful", "Shorter verse", "Rhyme more")
        }
    }
}

// ----------------------------------------------------------------------------
// 4. CONVERSATIONAL PROMPTER
// ----------------------------------------------------------------------------

class ConversationalPrompter {

    val systemRolePrompt = """
You are a context-aware writing assistant helping the user refine text for personal and professional communication.
- Persona & Roles: The participant labeled 'User' is the person you are assisting. All other participants are third parties. Generate content strictly from the User's perspective.
- Human-written features: Use emotional nuance, contractions, natural variable structure, and specific experiential details.
- Avoid AI features: Do not use sterile academic phrasing, excessive neutrality, or jargon like 'encompass', 'originate', 'aligns well'.
- Output Format:
  <INSTRUCTION_CLASS>MODIFICATION</INSTRUCTION_CLASS>
  <DRAFT>[Rewritten text here]</DRAFT>
  <SUGGESTED_INSTRUCTION>[Follow-up chip 1]</SUGGESTED_INSTRUCTION>
  <SUGGESTED_INSTRUCTION>[Follow-up chip 2]</SUGGESTED_INSTRUCTION>
  <SUGGESTED_INSTRUCTION>[Follow-up chip 3]</SUGGESTED_INSTRUCTION>
    """.trimIndent()

    fun buildPrompt(draft: String, style: WritingStyle, context: ConversationContext): String {
        val sb = StringBuilder()
        sb.append("<CONTEXT>\n")
        sb.append("<CURRENT_DRAFT>").append(draft.trim()).append("</CURRENT_DRAFT>\n")

        if (context.recentMessages.isNotEmpty()) {
            sb.append("<RELATED_TEXT>\n")
            for ((sender, message) in context.recentMessages.takeLast(4)) {
                sb.append("$sender: $message\n")
            }
            sb.append("</RELATED_TEXT>\n")
        }
        sb.append("</CONTEXT>\n")

        sb.append("<INSTRUCTION>").append(style.promptInstruction).append("</INSTRUCTION>\n")
        sb.append("Your output:\n")
        return sb.toString()
    }

    fun parseLlmResponse(rawOutput: String, requestedStyle: WritingStyle): RewriteDraft {
        val draftRegex = Regex("<DRAFT>([\\s\\S]*?)</DRAFT>")
        val instructionClassRegex = Regex("<INSTRUCTION_CLASS>([\\s\\S]*?)</INSTRUCTION_CLASS>")
        val suggestionRegex = Regex("<SUGGESTED_INSTRUCTION>([\\s\\S]*?)</SUGGESTED_INSTRUCTION>")

        val draftMatch = draftRegex.find(rawOutput)
        val extractedDraft = draftMatch?.groupValues?.get(1)?.trim() ?: rawOutput.trim()
        val instructionClass = instructionClassRegex.find(rawOutput)?.groupValues?.get(1)?.trim() ?: "MODIFICATION"
        val suggestions = suggestionRegex.findAll(rawOutput).map { it.groupValues[1].trim() }.toList()

        return RewriteDraft(
            text = extractedDraft,
            style = requestedStyle,
            instructionClass = instructionClass,
            suggestedFollowUps = if (suggestions.isNotEmpty()) suggestions else listOf("Make formal", "Make casual", "Add emoji")
        )
    }
}

// ----------------------------------------------------------------------------
// 5. COORDINATOR
// ----------------------------------------------------------------------------

class MagicWritingCoordinator(
    private val config: MagicWriteConfig = MagicWriteConfig()
) {

    private val proofreadEngine = ProofreadEngine()
    private val styleEngine = StyleRewriteEngine()
    val prompter = ConversationalPrompter()

    private var lastOriginalText: String? = null
    private var lastRewrittenText: String? = null

    fun proofread(text: String): ProofreadResult {
        return proofreadEngine.proofread(text)
    }

    fun rewriteInStyle(text: String, style: WritingStyle): RewriteDraft {
        return styleEngine.transformStyle(text, style)
    }

    fun applyProofread(ic: InputConnection?, result: ProofreadResult): Boolean {
        if (ic == null || !result.hasChanges) return false

        lastOriginalText = result.originalText
        lastRewrittenText = result.correctedText

        ic.beginBatchEdit()
        try {
            ic.deleteSurroundingText(result.originalText.length, 0)
            ic.commitText(result.correctedText, 1)
        } finally {
            ic.endBatchEdit()
        }
        return true
    }

    fun applyRewrite(ic: InputConnection?, originalText: String, draft: RewriteDraft): Boolean {
        if (ic == null) return false

        lastOriginalText = originalText
        lastRewrittenText = draft.text

        ic.beginBatchEdit()
        try {
            ic.deleteSurroundingText(originalText.length, 0)
            ic.commitText(draft.text, 1)
        } finally {
            ic.endBatchEdit()
        }
        return true
    }

    fun revertLastChange(ic: InputConnection?): Boolean {
        val original = lastOriginalText ?: return false
        val rewritten = lastRewrittenText ?: return false
        if (ic == null) return false

        ic.beginBatchEdit()
        try {
            ic.deleteSurroundingText(rewritten.length, 0)
            ic.commitText(original, 1)
        } finally {
            ic.endBatchEdit()
        }

        lastOriginalText = null
        lastRewrittenText = null
        return true
    }
}
