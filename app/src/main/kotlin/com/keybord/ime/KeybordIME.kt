package com.keybord.ime

import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.keybord.clipboard.InlineClipboardTrayView
import com.keybord.clipboard.KeybordClipboardManager
import com.keybord.engine.glide.*
import com.keybord.engine.vocab.*
import com.keybord.profile.AppProfileRepository
import com.keybord.settings.KeybordSettingsActivity
import com.keybord.theme.BuiltInThemes
import com.keybord.theme.ThemeModel
import com.keybord.theme.ThemeRepository

/**
 * Master InputMethodService for Keybord.
 * Unifies Unlimited Clipboard, Visual Customization, Glide Typing,
 * and Adaptive Vocabulary Learning with <30ms latency.
 */
class KeybordIME : InputMethodService() {

    // Engines
    private lateinit var lexicon: TrieLexicon
    private lateinit var glideDecoder: GlideDecoder
    private lateinit var vocabEngine: AdaptiveVocabularyEngine
    private lateinit var clipboardManager: KeybordClipboardManager
    private lateinit var themeRepo: ThemeRepository
    private lateinit var profileRepo: AppProfileRepository

    // Views
    private lateinit var rootContainer: LinearLayout
    private lateinit var suggestionStripView: SuggestionStripView
    private lateinit var keyboardFrame: FrameLayout
    private lateinit var keyboardView: KeyboardView
    private lateinit var gestureOverlayView: GestureOverlayView
    private lateinit var popPreviewView: KeyPopPreviewView
    private lateinit var inlineClipboardTray: InlineClipboardTrayView

    // State
    private var activeTheme: ThemeModel = BuiltInThemes.MATERIAL_MIDNIGHT
    private val touchPoints = mutableListOf<TouchPoint>()
    private val recentContextWords = mutableListOf<String>()
    private var currentWordPrefix = StringBuilder()
    private var lastSpaceTapTime = 0L
    private var isGlideTypingEnabled = true
    private var isAutoCapitaliseEnabled = true
    private var isDoubleSpacePeriodEnabled = true

    // Track for manual correction detection
    private var lastCommittedWord: String? = null
    private var wasLastCommittedFromSuggestion = false

    override fun onCreate() {
        super.onCreate()

        // 1. Initialize Lexicon & Glide Engine
        lexicon = TrieLexicon().apply {
            loadDefaultVocabulary()
        }
        glideDecoder = GlideDecoder(emptyMap(), lexicon)

        // 2. Initialize Adaptive Vocabulary Engine
        vocabEngine = AdaptiveVocabularyEngine(
            context = this,
            staticWordLookup = { word -> lexicon.contains(word) }
        )

        // 3. Initialize Clipboard Manager
        clipboardManager = KeybordClipboardManager(this).apply {
            startListening()
            addChangeListener {
                updateRecentClipsInStrip()
            }
        }

        // 4. Initialize Theme & Profile Repositories
        themeRepo = ThemeRepository(this).apply {
            addOnThemeChangeListener { newTheme ->
                applyTheme(newTheme)
            }
        }
        activeTheme = themeRepo.getActiveTheme()
        profileRepo = AppProfileRepository(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        clipboardManager.stopListening()
    }

    override fun onCreateInputView(): View {
        val dp = resources.displayMetrics.density

        rootContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        // 1. Suggestion Strip
        suggestionStripView = SuggestionStripView(this).apply {
            theme = activeTheme
            onCandidateSelected = { word ->
                commitCandidate(word)
            }
            onCandidateDeleteRequested = { word ->
                vocabEngine.deleteLearnedWord(word)
                refreshPredictions()
            }
            onClipboardIconClicked = {
                toggleInlineClipboardTray()
            }
            onSettingsIconClicked = {
                launchSettings()
            }
            onRecentClipSelected = { text ->
                currentInputConnection?.commitText(text, 1)
            }
            onRecentClipDismissed = { id ->
                clipboardManager.dismissRecentItem(id)
                updateRecentClipsInStrip()
            }
        }
        rootContainer.addView(suggestionStripView)

        // 2. Keyboard Layer Frame (holds KeyboardView, Overlay, and PopPreview)
        keyboardFrame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        keyboardView = KeyboardView(this).apply {
            theme = activeTheme
            onKeyTap = { key ->
                handleKeyTap(key)
            }
            onKeyPositionsChanged = { positions ->
                glideDecoder.updateKeyPositions(positions)
            }
        }
        keyboardFrame.addView(keyboardView)

        gestureOverlayView = GestureOverlayView(this).apply {
            theme = activeTheme
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        keyboardFrame.addView(gestureOverlayView)

        popPreviewView = KeyPopPreviewView(this).apply {
            theme = activeTheme
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        keyboardView.popPreviewView = popPreviewView
        keyboardFrame.addView(popPreviewView)

        // 3. Inline Clipboard Tray (initially hidden)
        inlineClipboardTray = InlineClipboardTrayView(this).apply {
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        rootContainer.addView(keyboardFrame)
        rootContainer.addView(inlineClipboardTray)

        // Intercept Touch for Glide Typing
        keyboardFrame.setOnTouchListener { _, event ->
            handleKeyboardTouch(event)
        }

        applyTheme(activeTheme)
        return rootContainer
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        currentWordPrefix.clear()
        recentContextWords.clear()

        // 1. Check Per-App Profiles (Feature 5)
        val profile = profileRepo.getProfileForPackage(info.packageName)
        val targetTheme = if (profile?.themeIdOverride != null) {
            themeRepo.getThemeById(profile.themeIdOverride) ?: themeRepo.getActiveTheme()
        } else {
            themeRepo.getActiveTheme()
        }
        applyTheme(targetTheme)

        // 2. Sensitive content detection (password fields)
        clipboardManager.updateCurrentEditorInfo(info)

        // 3. Check cursor context
        val textBefore = currentInputConnection?.getTextBeforeCursor(40, 0)?.toString() ?: ""
        val tokens = textBefore.trim().split(Regex("\\s+"))
        if (tokens.isNotEmpty() && tokens.last().isNotBlank()) {
            recentContextWords.addAll(tokens.takeLast(3))
        }

        refreshPredictions()
        updateRecentClipsInStrip()
    }

    private fun handleKeyboardTouch(event: MotionEvent): Boolean {
        if (!isGlideTypingEnabled || keyboardView.layer != KeyboardView.KeyboardLayer.ALPHA) {
            return keyboardView.handleTouchEvent(event)
        }

        val x = event.x
        val y = event.y
        val time = event.eventTime

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchPoints.clear()
                touchPoints.add(TouchPoint(x, y, time, event.pressure))
                gestureOverlayView.addPoint(x, y)
                keyboardView.handleTouchEvent(event)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                touchPoints.add(TouchPoint(x, y, time, event.pressure))
                gestureOverlayView.addPoint(x, y)
                keyboardView.handleTouchEvent(event)
                return true
            }

            MotionEvent.ACTION_UP -> {
                gestureOverlayView.clearTrail()

                // Check if valid swipe
                if (touchPoints.size > 2) {
                    val candidates = glideDecoder.decodeGesture(touchPoints)
                    if (candidates.isNotEmpty()) {
                        keyboardView.handleTouchEvent(event) // Clears pressed state
                        val bestWord = candidates.first().word
                        commitWordWithSpace(bestWord, LearningEvent.NORMAL_TYPING)
                        suggestionStripView.setCandidates(candidates.map { it.word })
                        return true
                    }
                }

                // Fallback to discrete tap
                glideDecoder.notifyTap(time)
                return keyboardView.handleTouchEvent(event)
            }

            MotionEvent.ACTION_CANCEL -> {
                touchPoints.clear()
                gestureOverlayView.clearTrail()
                return keyboardView.handleTouchEvent(event)
            }
        }
        return false
    }

    private fun handleKeyTap(key: String) {
        val ic = currentInputConnection ?: return

        when (key) {
            "⌫" -> {
                // Backspace logic
                if (currentWordPrefix.isNotEmpty()) {
                    currentWordPrefix.deleteCharAt(currentWordPrefix.length - 1)
                }

                // Check for manual correction / scrub delete
                if (lastCommittedWord != null && wasLastCommittedFromSuggestion) {
                    vocabEngine.onScrubDelete(lastCommittedWord!!)
                    lastCommittedWord = null
                    wasLastCommittedFromSuggestion = false
                }

                ic.deleteSurroundingText(1, 0)
                refreshPredictions()
            }

            "⇧", "⬆", "⇪" -> {
                keyboardView.shiftState = when (keyboardView.shiftState) {
                    KeyboardView.ShiftState.OFF -> KeyboardView.ShiftState.SHIFTED
                    KeyboardView.ShiftState.SHIFTED -> KeyboardView.ShiftState.CAPS_LOCK
                    KeyboardView.ShiftState.CAPS_LOCK -> KeyboardView.ShiftState.OFF
                }
            }

            "?123" -> {
                keyboardView.layer = KeyboardView.KeyboardLayer.SYMBOLS
            }

            "ABC" -> {
                keyboardView.layer = KeyboardView.KeyboardLayer.ALPHA
            }

            "=\\<" -> {
                keyboardView.layer = KeyboardView.KeyboardLayer.EXTENDED_SYMBOLS
            }

            " " -> {
                val now = System.currentTimeMillis()
                if (isDoubleSpacePeriodEnabled && now - lastSpaceTapTime < 400L) {
                    // Double space period
                    ic.deleteSurroundingText(1, 0)
                    ic.commitText(". ", 1)
                    lastSpaceTapTime = 0L
                } else {
                    ic.commitText(" ", 1)
                    lastSpaceTapTime = now
                }

                if (currentWordPrefix.isNotEmpty()) {
                    val committed = currentWordPrefix.toString()
                    vocabEngine.onWordCommitted(committed, recentContextWords, LearningEvent.NORMAL_TYPING)
                    addContextWord(committed)
                    currentWordPrefix.clear()
                }

                refreshPredictions()
            }

            "Enter" -> {
                ic.sendKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_ENTER))
                ic.sendKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_ENTER))
                currentWordPrefix.clear()
                recentContextWords.clear()
                refreshPredictions()
            }

            else -> {
                // Regular character
                val charToCommit = if (keyboardView.shiftState != KeyboardView.ShiftState.OFF && key.length == 1) {
                    key.uppercase()
                } else {
                    key
                }

                ic.commitText(charToCommit, 1)

                if (charToCommit.length == 1 && charToCommit[0].isLetter()) {
                    currentWordPrefix.append(charToCommit)
                } else {
                    if (currentWordPrefix.isNotEmpty()) {
                        addContextWord(currentWordPrefix.toString())
                        currentWordPrefix.clear()
                    }
                }

                // Auto reset shift if not caps lock
                if (keyboardView.shiftState == KeyboardView.ShiftState.SHIFTED) {
                    keyboardView.shiftState = KeyboardView.ShiftState.OFF
                }

                refreshPredictions()
            }
        }
    }

    private fun commitWordWithSpace(word: String, event: LearningEvent) {
        val ic = currentInputConnection ?: return
        ic.commitText("$word ", 1)

        vocabEngine.onWordCommitted(word, recentContextWords, event)
        addContextWord(word)
        currentWordPrefix.clear()

        lastCommittedWord = word
        wasLastCommittedFromSuggestion = (event == LearningEvent.SUGGESTION_ACCEPTED)
    }

    private fun commitCandidate(selectedWord: String) {
        val ic = currentInputConnection ?: return

        // Delete currently typed prefix if present
        if (currentWordPrefix.isNotEmpty()) {
            ic.deleteSurroundingText(currentWordPrefix.length, 0)
            currentWordPrefix.clear()
        }

        commitWordWithSpace(selectedWord, LearningEvent.SUGGESTION_ACCEPTED)
        refreshPredictions()
    }

    private fun addContextWord(word: String) {
        recentContextWords.add(word)
        if (recentContextWords.size > 3) {
            recentContextWords.removeAt(0)
        }
    }

    private fun refreshPredictions() {
        val prefix = currentWordPrefix.toString()
        val candidates = if (prefix.isNotEmpty()) {
            vocabEngine.rankPrefixCompletions(prefix, recentContextWords, maxCandidates = 3)
        } else {
            vocabEngine.predictNextWords(recentContextWords, maxCandidates = 3)
        }
        suggestionStripView.setCandidates(candidates.map { it.word })
    }

    private fun updateRecentClipsInStrip() {
        val recents = clipboardManager.getRecentItemsForStrip()
        suggestionStripView.setRecentClipboardItems(recents)
    }

    private fun toggleInlineClipboardTray() {
        if (inlineClipboardTray.visibility == View.VISIBLE) {
            inlineClipboardTray.visibility = View.GONE
            keyboardFrame.visibility = View.VISIBLE
        } else {
            inlineClipboardTray.attachClipboard(
                manager = clipboardManager,
                onSelect = { text ->
                    currentInputConnection?.commitText(text, 1)
                    inlineClipboardTray.visibility = View.GONE
                    keyboardFrame.visibility = View.VISIBLE
                },
                onClose = {
                    inlineClipboardTray.visibility = View.GONE
                    keyboardFrame.visibility = View.VISIBLE
                }
            )
            keyboardFrame.visibility = View.GONE
            inlineClipboardTray.visibility = View.VISIBLE
        }
    }

    private fun launchSettings() {
        val intent = Intent(this, KeybordSettingsActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
    }

    private fun applyTheme(theme: ThemeModel) {
        this.activeTheme = theme
        if (::suggestionStripView.isInitialized) suggestionStripView.theme = theme
        if (::keyboardView.isInitialized) keyboardView.theme = theme
        if (::gestureOverlayView.isInitialized) gestureOverlayView.theme = theme
        if (::popPreviewView.isInitialized) popPreviewView.theme = theme
    }
}
