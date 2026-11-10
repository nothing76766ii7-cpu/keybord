package com.keybord.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import java.util.Locale

/**
 * Manages Android SpeechRecognizer sessions for Voice Rambler dictation.
 * Streams live partial hypotheses and final utterances cleanly on the main looper,
 * preferring offline recognition when supported by the system speech service.
 */
class VoiceRamblerManager(
    private val context: Context,
    private val onReady: () -> Unit,
    private val onPartialSpeech: (String) -> Unit,
    private val onFinalSpeech: (String) -> Unit,
    private val onError: (String, Boolean) -> Unit,
    private val onStateChanged: (Boolean) -> Unit
) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null
    private var lastPartialHypothesis: String = ""

    var isListening: Boolean = false
        private set

    fun hasAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    fun requestAudioPermission() {
        val intent = Intent(context, MicPermissionActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    fun startListening(): Boolean {
        if (isListening) return true

        if (!hasAudioPermission()) {
            requestAudioPermission()
            onError("Microphone permission required", true)
            return false
        }

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError("Speech recognition unavailable on this device", false)
            return false
        }

        lastPartialHypothesis = ""
        isListening = true
        onStateChanged(true)

        mainHandler.post {
            try {
                speechRecognizer?.destroy()
                val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
                recognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        onReady()
                    }

                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}

                    override fun onError(error: Int) {
                        isListening = false
                        onStateChanged(false)
                        if (lastPartialHypothesis.isNotBlank() &&
                            (error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT || error == SpeechRecognizer.ERROR_NO_MATCH)
                        ) {
                            val fallback = lastPartialHypothesis
                            lastPartialHypothesis = ""
                            onFinalSpeech(fallback)
                            return
                        }
                        lastPartialHypothesis = ""
                        val needsPermission = error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS
                        val msg = when (error) {
                            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required"
                            SpeechRecognizer.ERROR_NO_MATCH -> "Didn't catch that — tap mic to retry"
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech heard"
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer busy"
                            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                                "Offline speech pack unavailable"
                            else -> "Speech recognition error ($error)"
                        }
                        onError(msg, needsPermission)
                    }

                    override fun onResults(results: Bundle?) {
                        isListening = false
                        onStateChanged(false)
                        val text = results
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()
                            ?.trim()
                            .orEmpty()
                        val effective = text.ifEmpty { lastPartialHypothesis.trim() }
                        lastPartialHypothesis = ""
                        if (effective.isNotEmpty()) {
                            onFinalSpeech(effective)
                        } else {
                            onError("No speech recognized", false)
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val partial = partialResults
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()
                            ?.trim()
                            .orEmpty()
                        if (partial.isNotEmpty() && partial != lastPartialHypothesis) {
                            lastPartialHypothesis = partial
                            onPartialSpeech(partial)
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.ACTION_RECOGNIZE_SPEECH.let { RecognizerIntent.LANGUAGE_MODEL_FREE_FORM }
                    )
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                    putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                }

                speechRecognizer = recognizer
                recognizer.startListening(intent)
            } catch (t: Throwable) {
                isListening = false
                onStateChanged(false)
                onError("Unable to start voice recognition", false)
            }
        }
        return true
    }

    fun stopListening() {
        if (!isListening) return
        isListening = false
        onStateChanged(false)
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
            } catch (_: Exception) {
                // Ignore if already stopped
            }
        }
    }

    fun destroy() {
        isListening = false
        lastPartialHypothesis = ""
        mainHandler.post {
            try {
                speechRecognizer?.cancel()
                speechRecognizer?.destroy()
            } catch (_: Exception) {
                // Ignore
            } finally {
                speechRecognizer = null
            }
        }
    }
}
