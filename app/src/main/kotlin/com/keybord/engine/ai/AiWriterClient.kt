package com.keybord.engine.ai

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.keyboard.engine.magicwrite.MagicWritingCoordinator
import com.keyboard.engine.magicwrite.WritingStyle
import com.keybord.diagnostics.KeybordCrashLogger
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Bring-Your-Own-Key (BYOK) AI Writer client + offline Proofread/Rewrite engine fallback.
 * Supports Gemini API and OpenAI-compatible Chat Completions endpoints with zero hardcoded keys.
 * Free-form prompt generation never fabricates fake AI responses offline.
 */
class AiWriterClient(context: Context) {

    enum class Provider(val displayName: String, val defaultEndpoint: String, val defaultModel: String) {
        GEMINI(
            "Gemini API",
            "https://generativelanguage.googleapis.com/v1beta/models",
            "gemini-2.5-flash"
        ),
        OPENAI_COMPATIBLE(
            "OpenAI-Compatible",
            "https://api.openai.com/v1/chat/completions",
            "gpt-4o-mini"
        ),
        OFFLINE_ONLY(
            "Offline Only",
            "",
            ""
        )
    }

    enum class WritingFormat(val label: String) {
        AUTO("Auto"),
        MESSAGE("Message"),
        REPLY("Reply"),
        PARAGRAPH("Paragraph"),
        CAPTION("Caption"),
        EMAIL("Email")
    }

    enum class WritingAction(val label: String) {
        COMPOSE("Generate"),
        REWRITE("Rewrite"),
        EXPAND("Expand"),
        SHORTEN("Shorten"),
        CORRECT("Correct"),
        IMPROVE("Improve")
    }

    enum class WritingTone(val label: String, val mappedStyle: WritingStyle?) {
        NATURAL("Natural", null),
        FORMAL("Formal", WritingStyle.FORMAL),
        CASUAL("Casual", WritingStyle.CASUAL),
        PROFESSIONAL("Professional", WritingStyle.PROFESSIONAL),
        FRIENDLY("Friendly", WritingStyle.FRIENDLY),
        CONCISE("Concise", WritingStyle.CONCISE)
    }

    data class AiConfig(
        val provider: Provider,
        val endpointUrl: String,
        val model: String,
        val apiKey: String
    ) {
        val isCloudConfigured: Boolean
            get() = provider != Provider.OFFLINE_ONLY &&
                endpointUrl.isNotBlank() &&
                model.isNotBlank() &&
                apiKey.isNotBlank()
    }

    data class AiRequest(
        val instruction: String,
        val sourceText: String,
        val format: WritingFormat,
        val action: WritingAction,
        val tone: WritingTone
    )

    sealed class AiOutcome {
        data class Success(val text: String, val engineLabel: String) : AiOutcome()
        data class Error(val message: String, val needsConfig: Boolean = false) : AiOutcome()
    }

    private val appContext = context.applicationContext ?: context
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val coordinator = MagicWritingCoordinator()
    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "KeybordAiWriter").apply { isDaemon = true }
    }
    private val mainHandler = Handler(Looper.getMainLooper())
    private val requestSeq = AtomicLong(0L)
    private val inFlight = AtomicBoolean(false)

    @Volatile
    private var activeConnection: HttpURLConnection? = null

    fun loadConfig(): AiConfig {
        val provName = prefs.getString(KEY_PROVIDER, Provider.GEMINI.name) ?: Provider.GEMINI.name
        val provider = try {
            Provider.valueOf(provName)
        } catch (_: Exception) {
            Provider.GEMINI
        }
        val endpoint = prefs.getString(KEY_ENDPOINT, provider.defaultEndpoint).orEmpty().ifBlank {
            provider.defaultEndpoint
        }
        val model = prefs.getString(KEY_MODEL, provider.defaultModel).orEmpty().ifBlank {
            provider.defaultModel
        }
        val apiKey = prefs.getString(KEY_API_KEY, "").orEmpty().trim()
        return AiConfig(provider, endpoint.trim(), model.trim(), apiKey)
    }

    fun saveConfig(provider: Provider, endpointUrl: String, model: String, apiKey: String) {
        prefs.edit()
            .putString(KEY_PROVIDER, provider.name)
            .putString(KEY_ENDPOINT, endpointUrl.trim())
            .putString(KEY_MODEL, model.trim())
            .putString(KEY_API_KEY, apiKey.trim())
            .apply()
    }

    fun isRequestInFlight(): Boolean = inFlight.get()

    fun cancelPending() {
        requestSeq.incrementAndGet()
        inFlight.set(false)
        try {
            activeConnection?.disconnect()
        } catch (_: Exception) {
            // Ignore socket close error
        } finally {
            activeConnection = null
        }
    }

    fun shutdown() {
        cancelPending()
        executor.shutdownNow()
    }

    fun generateAsync(
        request: AiRequest,
        onResult: (AiOutcome) -> Unit
    ): Boolean {
        if (!inFlight.compareAndSet(false, true)) {
            return false // Prevent duplicate concurrent requests
        }
        val seq = requestSeq.incrementAndGet()
        val cfg = loadConfig()

        executor.execute {
            val outcome = try {
                executeInternal(cfg, request)
            } catch (t: Throwable) {
                KeybordCrashLogger.recordNonFatal("AiWriterClient", "Unhandled AI execution failure", t)
                AiOutcome.Error("AI Writer failed: ${t.message ?: t.javaClass.simpleName}")
            } finally {
                inFlight.set(false)
                activeConnection = null
            }

            mainHandler.post {
                if (requestSeq.get() == seq) {
                    onResult(outcome)
                }
            }
        }
        return true
    }

    fun testConnectionAsync(configOverride: AiConfig? = null, onResult: (AiOutcome) -> Unit): Boolean {
        if (!inFlight.compareAndSet(false, true)) {
            return false
        }
        val seq = requestSeq.incrementAndGet()
        val cfg = configOverride ?: loadConfig()

        executor.execute {
            val outcome = try {
                if (!cfg.isCloudConfigured) {
                    AiOutcome.Error("Enter endpoint URL, model, and API key first.", needsConfig = true)
                } else {
                    val prompt = "Reply with only the single word: READY"
                    val reply = when (cfg.provider) {
                        Provider.GEMINI -> callGemini(cfg, "Return concise test confirmation.", prompt)
                        Provider.OPENAI_COMPATIBLE -> callOpenAiCompatible(cfg, "Return concise test confirmation.", prompt)
                        Provider.OFFLINE_ONLY -> ""
                    }
                    AiOutcome.Success(reply, "${cfg.provider.displayName} • ${cfg.model}")
                }
            } catch (e: Exception) {
                KeybordCrashLogger.recordNonFatal("AiWriterClient", "API test failed", e)
                AiOutcome.Error(e.message ?: "Connection failed")
            } finally {
                inFlight.set(false)
                activeConnection = null
            }
            mainHandler.post {
                if (requestSeq.get() == seq) {
                    onResult(outcome)
                }
            }
        }
        return true
    }

    private fun executeInternal(cfg: AiConfig, req: AiRequest): AiOutcome {
        val cleanInstr = req.instruction.trim()
        val cleanSource = req.sourceText.trim()

        if (cleanInstr.isEmpty() && cleanSource.isEmpty()) {
            return AiOutcome.Error("Enter an instruction or select/pull text from the app first.")
        }

        // If user has a BYOK cloud config, send real AI request
        if (cfg.isCloudConfigured) {
            val systemPrompt = buildSystemPrompt(req)
            val userPrompt = buildUserPrompt(req)
            val generated = when (cfg.provider) {
                Provider.GEMINI -> callGemini(cfg, systemPrompt, userPrompt)
                Provider.OPENAI_COMPATIBLE -> callOpenAiCompatible(cfg, systemPrompt, userPrompt)
                Provider.OFFLINE_ONLY -> ""
            }
            val cleaned = cleanModelOutput(generated)
            if (cleaned.isBlank()) {
                return AiOutcome.Error("The AI provider returned an empty response. Please try again.")
            }
            return AiOutcome.Success(cleaned, "${cfg.model} (BYOK)")
        }

        // Offline fallback: deterministic proofreading / tone rewriting ONLY on existing text
        if (cleanSource.isNotEmpty() || req.action != WritingAction.COMPOSE) {
            val workingText = cleanSource.ifEmpty { cleanInstr }
            val offlineResult = runOfflineTransform(workingText, req.action, req.tone)
            if (offlineResult != null) {
                return AiOutcome.Success(offlineResult, "On-Device Engine")
            }
        }

        // Free-form prompt generation without a configured API endpoint/key: fail honestly
        return AiOutcome.Error(
            "Free-form generation requires your Gemini or OpenAI-compatible API key in Writing Tools Settings.",
            needsConfig = true
        )
    }

    private fun runOfflineTransform(text: String, action: WritingAction, tone: WritingTone): String? {
        if (text.isBlank()) return null
        val proofreadFirst = coordinator.proofread(text).correctedText
        val transformed = when (action) {
            WritingAction.CORRECT -> proofreadFirst
            WritingAction.SHORTEN -> coordinator.rewriteInStyle(proofreadFirst, WritingStyle.CONCISE).text
            WritingAction.EXPAND -> coordinator.rewriteInStyle(proofreadFirst, WritingStyle.ELABORATE).text
            WritingAction.REWRITE, WritingAction.IMPROVE -> {
                val style = tone.mappedStyle ?: WritingStyle.PROFESSIONAL
                coordinator.rewriteInStyle(proofreadFirst, style).text
            }
            WritingAction.COMPOSE -> {
                tone.mappedStyle?.let { coordinator.rewriteInStyle(proofreadFirst, it).text } ?: return null
            }
        }
        return transformed.trim().takeIf { it.isNotEmpty() }
    }

    private fun buildSystemPrompt(req: AiRequest): String {
        return buildString {
            append("You are an expert mobile keyboard writing assistant. ")
            append("Return ONLY the final ready-to-insert text without markdown fences, preambles, or explanations. ")
            if (req.format != WritingFormat.AUTO) {
                append("Format as a ${req.format.label.lowercase()}. ")
            }
            if (req.tone != WritingTone.NATURAL) {
                append("Tone must be ${req.tone.label.lowercase()}. ")
            }
            append("Preserve Hinglish, English, names, and numbers accurately if present.")
        }
    }

    private fun buildUserPrompt(req: AiRequest): String {
        val sb = StringBuilder()
        sb.append("Task: ").append(req.action.label).append('\n')
        if (req.format != WritingFormat.AUTO) {
            sb.append("Format: ").append(req.format.label).append('\n')
        }
        if (req.tone != WritingTone.NATURAL) {
            sb.append("Tone: ").append(req.tone.label).append('\n')
        }
        if (req.sourceText.isNotBlank()) {
            sb.append("\nExisting text:\n\"\"\"\n").append(req.sourceText.trim()).append("\n\"\"\"\n")
        }
        if (req.instruction.isNotBlank()) {
            sb.append("\nUser instruction:\n").append(req.instruction.trim()).append('\n')
        }
        return sb.toString()
    }

    private fun callGemini(cfg: AiConfig, systemPrompt: String, userPrompt: String): String {
        val base = cfg.endpointUrl.trimEnd('/')
        val urlStr = if (base.endsWith(":generateContent")) {
            base
        } else {
            "$base/${cfg.model}:generateContent"
        }

        val body = JSONObject().apply {
            put(
                "systemInstruction",
                JSONObject().put(
                    "parts",
                    JSONArray().put(JSONObject().put("text", systemPrompt))
                )
            )
            put(
                "contents",
                JSONArray().put(
                    JSONObject()
                        .put("role", "user")
                        .put("parts", JSONArray().put(JSONObject().put("text", userPrompt)))
                )
            )
            put(
                "generationConfig",
                JSONObject()
                    .put("temperature", 0.65)
                    .put("maxOutputTokens", 512)
            )
        }

        val rawResponse = postJson(
            urlStr = urlStr,
            headers = mapOf("x-goog-api-key" to cfg.apiKey),
            payload = body.toString()
        )
        val root = JSONObject(rawResponse)
        val candidates = root.optJSONArray("candidates")
            ?: throw IllegalStateException("No candidates in Gemini response")
        if (candidates.length() == 0) {
            throw IllegalStateException("Gemini returned empty candidates")
        }
        val content = candidates.getJSONObject(0).optJSONObject("content")
            ?: throw IllegalStateException("Missing content in Gemini response")
        val parts = content.optJSONArray("parts")
            ?: throw IllegalStateException("Missing parts in Gemini response")
        val sb = StringBuilder()
        for (i in 0 until parts.length()) {
            val t = parts.optJSONObject(i)?.optString("text", "").orEmpty()
            if (t.isNotEmpty()) sb.append(t)
        }
        return sb.toString().trim()
    }

    private fun callOpenAiCompatible(cfg: AiConfig, systemPrompt: String, userPrompt: String): String {
        val trimmedBase = cfg.endpointUrl.trimEnd('/')
        val urlStr = if (trimmedBase.endsWith("/chat/completions")) {
            trimmedBase
        } else {
            "$trimmedBase/chat/completions"
        }

        val messages = JSONArray().apply {
            put(JSONObject().put("role", "system").put("content", systemPrompt))
            put(JSONObject().put("role", "user").put("content", userPrompt))
        }
        val body = JSONObject().apply {
            put("model", cfg.model)
            put("messages", messages)
            put("temperature", 0.65)
            put("max_tokens", 512)
        }

        val rawResponse = postJson(
            urlStr = urlStr,
            headers = mapOf("Authorization" to "Bearer ${cfg.apiKey}"),
            payload = body.toString()
        )
        val root = JSONObject(rawResponse)
        val choices = root.optJSONArray("choices")
            ?: throw IllegalStateException("No choices in response")
        if (choices.length() == 0) {
            throw IllegalStateException("Empty choices returned by provider")
        }
        val message = choices.getJSONObject(0).optJSONObject("message")
            ?: throw IllegalStateException("Missing message object in choice")
        return message.optString("content", "").trim()
    }

    private fun postJson(
        urlStr: String,
        headers: Map<String, String>,
        payload: String
    ): String {
        val url = URL(urlStr)
        val conn = (url.openConnection() as? HttpURLConnection)
            ?: throw IllegalStateException("Invalid HTTP endpoint URL")
        activeConnection = conn
        return try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 12_000
            conn.readTimeout = 25_000
            conn.doInput = true
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }

            OutputStreamWriter(conn.outputStream, StandardCharsets.UTF_8).use { writer ->
                writer.write(payload)
                writer.flush()
            }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else (conn.errorStream ?: conn.inputStream)
            val responseBody = BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { reader ->
                reader.readText()
            }
            if (code !in 200..299) {
                val parsedMsg = extractApiErrorMessage(responseBody)
                throw IllegalStateException("HTTP $code: $parsedMsg")
            }
            responseBody
        } catch (e: Exception) {
            if (e is IllegalStateException) throw e
            throw IllegalStateException("Network error: ${e.message ?: e.javaClass.simpleName}", e)
        } finally {
            conn.disconnect()
        }
    }

    private fun extractApiErrorMessage(raw: String): String {
        if (raw.isBlank()) return "Request rejected"
        return try {
            val json = JSONObject(raw)
            val errObj = json.optJSONObject("error")
            errObj?.optString("message")?.takeIf { it.isNotBlank() }
                ?: json.optString("message").takeIf { it.isNotBlank() }
                ?: raw.take(140)
        } catch (_: Exception) {
            raw.take(140)
        }
    }

    private fun cleanModelOutput(raw: String): String {
        var text = raw.trim()
        if (text.startsWith("```") && text.endsWith("```")) {
            text = text.removePrefix("```").removeSuffix("```").trim()
            val firstNewline = text.indexOf('\n')
            if (firstNewline in 1..14 && text.substring(0, firstNewline).all { it.isLetter() }) {
                text = text.substring(firstNewline + 1).trim()
            }
        }
        return text
    }

    companion object {
        const val PREFS_NAME = "keybord_writing_prefs"
        private const val KEY_PROVIDER = "ai_byok_provider"
        private const val KEY_ENDPOINT = "ai_byok_endpoint"
        private const val KEY_MODEL = "ai_byok_model"
        private const val KEY_API_KEY = "ai_byok_api_key"
    }
}
