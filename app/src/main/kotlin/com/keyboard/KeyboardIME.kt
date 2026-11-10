package com.keyboard

import android.inputmethodservice.InputMethodService
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.keyboard.engine.glide.GestureOverlayView
import com.keyboard.engine.magicwrite.*
import com.keyboard.engine.rambler.*
import com.keyboard.engine.vocab.*

/**
 * Main InputMethodService for Keybord adhering strictly to INTEGRATION_PRD.txt
 * wiring steps:
 * - Step 2.2: GlideTypingComponent
 * - Step 2.3: AdaptiveVocabularyComponent
 * - Step 2.4: MagicWritingComponent
 * - Step 2.5: RamblerComponent
 */
class KeyboardIME : InputMethodService() {

    // Engines
    private lateinit var vocabEngine: AdaptiveVocabularyEngine
    private lateinit var magicCoordinator: MagicWritingCoordinator
    private lateinit var ramblerController: RamblerController
    private lateinit var clipboardManager: ClipboardManager
    private lateinit var themeEngine: ThemeEngine
    private lateinit var appProfileManager: AppProfileManager

    // UI
    private lateinit var rootLayout: LinearLayout
    private lateinit var suggestionStrip: SuggestionStrip
    private lateinit var keyboardFrame: FrameLayout
    private lateinit var keyboardView: KeyboardView
    private lateinit var gestureOverlay: GestureOverlayView

    // State tracking
    private val recentWords = mutableListOf<String>()
    private val staticDictionary = setOf("the", "be", "to", "of", "and", "a", "in", "that", "have", "i", "it", "for", "not", "hello", "keyboard")
    private var currentPrefix = StringBuilder()
    private var lastAutoCorrectWord: String? = null

    override fun onCreate() {
        super.onCreate()
        configureImeWindow()

        // Step 2.3a: Instantiate AdaptiveVocabularyEngine once in onCreate
        vocabEngine = AdaptiveVocabularyEngine(
            context = this,
            staticWordLookup = { word -> staticDictionary.contains(word) }
        )

        // Step 2.4a: Instantiate MagicWritingCoordinator once in onCreate
        magicCoordinator = MagicWritingCoordinator()

        // Step 2.5a: Instantiate RamblerController once in onCreate
        ramblerController = RamblerController()

        clipboardManager = ClipboardManager(this)
        themeEngine = ThemeEngine(this)
        appProfileManager = AppProfileManager(this)
    }

    private fun applyBottomWindowConfig(win: Window) {
        win.setGravity(Gravity.BOTTOM)
        win.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        @Suppress("DEPRECATION")
        win.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        win.attributes = win.attributes?.also { params ->
            params.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            params.width = ViewGroup.LayoutParams.MATCH_PARENT
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT
        }
    }

    private fun configureImeWindow() {
        window?.window?.let { applyBottomWindowConfig(it) }
    }

    override fun onConfigureWindow(
        win: Window,
        isFullscreen: Boolean,
        isCandidatesOnly: Boolean
    ) {
        super.onConfigureWindow(win, isFullscreen, isCandidatesOnly)
        if (!isFullscreen) {
            applyBottomWindowConfig(win)
        }
    }

    override fun onCreateInputView(): View {
        val dp = resources.displayMetrics.density
        rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.BOTTOM
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            )
        }

        // Suggestion strip
        suggestionStrip = SuggestionStrip(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (44f * dp).toInt()
            )
            onCandidateSelected = { candidate ->
                handleCandidateSelected(candidate)
            }
            onMagicWriteTapped = {
                triggerMagicWrite()
            }
            onVoiceToggleTapped = {
                ramblerController.toggleTranscriptRevert()
            }
        }
        rootLayout.addView(suggestionStrip)

        // Keyboard Frame with transparent GestureOverlayView above key rows (PRD 2.2d)
        keyboardFrame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        keyboardView = KeyboardView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            )
            this.suggestionStrip = this@KeyboardIME.suggestionStrip
            onKeyTapListener = { ch ->
                handleKeyTap(ch)
            }
            onWordCommittedListener = { word ->
                commitWord(word)
            }
        }

        gestureOverlay = GestureOverlayView(this).apply {
            id = android.R.id.custom
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            isClickable = false
            isFocusable = false
        }

        keyboardView.overlayView = gestureOverlay
        keyboardFrame.addView(keyboardView)
        keyboardFrame.addView(gestureOverlay)

        rootLayout.addView(keyboardFrame)
        configureImeWindow()
        return rootLayout
    }

    // Step 2.5c: Attach InputConnection on each input session start
    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        (rootLayout.layoutParams as? FrameLayout.LayoutParams)?.let { lp ->
            if (lp.gravity != Gravity.BOTTOM) {
                lp.gravity = Gravity.BOTTOM
                lp.width = ViewGroup.LayoutParams.MATCH_PARENT
                lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                rootLayout.layoutParams = lp
            }
        }
        configureImeWindow()
        val currentCursorOffset = currentInputConnection?.getTextBeforeCursor(1000, 0)?.length ?: 0
        ramblerController.attachInputConnection(currentInputConnection)
        ramblerController.startSession(currentCursorOffset)

        // Section F: Per-App Keyboard Profile Hook
        val packageName = currentInputEditorInfo?.packageName ?: info.packageName ?: ""
        val profile = appProfileManager.getProfileForPackage(packageName)
        if (profile != null) {
            themeEngine.applyTheme(profile.toTheme())
        } else {
            themeEngine.restoreGlobalTheme()
        }

        currentPrefix.clear()
        updateNextWordPredictions()
    }

    private fun handleKeyTap(ch: Char) {
        val ic = currentInputConnection ?: return
        if (ch == '⌫') {
            handleBackspace()
            return
        }

        ic.commitText(ch.toString(), 1)
        if (ch == ' ' || ch == '.' || ch == '?' || ch == '!') {
            if (currentPrefix.isNotEmpty()) {
                val committed = currentPrefix.toString()
                commitWord(committed)
                currentPrefix.clear()
            }
            updateNextWordPredictions()
        } else {
            currentPrefix.append(ch)
            updatePrefixCompletions()
        }
    }

    private fun handleBackspace() {
        val ic = currentInputConnection ?: return
        if (currentPrefix.isNotEmpty()) {
            currentPrefix.deleteCharAt(currentPrefix.length - 1)
            ic.deleteSurroundingText(1, 0)
            updatePrefixCompletions()
        } else {
            // Check manual backspace correction (PRD 2.3d)
            val autoCorrectWord = lastAutoCorrectWord
            ic.deleteSurroundingText(1, 0)
            if (autoCorrectWord != null) {
                // If user replaced it, onManualCorrection will be called
            }
            updateNextWordPredictions()
        }
    }

    // Step 2.3b: commitText flow
    fun commitWord(word: String) {
        val ic = currentInputConnection ?: return
        ic.commitText("$word ", 1)

        // Check if this was a manual correction
        val prevAuto = lastAutoCorrectWord
        if (prevAuto != null && prevAuto != word) {
            // PRD 2.3d
            vocabEngine.onManualCorrection(prevAuto, word, recentWords)
            lastAutoCorrectWord = null
        } else {
            vocabEngine.onWordCommitted(word, recentWords)
        }

        recentWords.add(word)
        if (recentWords.size > 8) {
            recentWords.removeAt(0)
        }
        currentPrefix.clear()
        updateNextWordPredictions()
    }

    // Step 2.3c: When user taps a suggestion strip item
    private fun handleCandidateSelected(selectedWord: String) {
        val ic = currentInputConnection ?: return
        if (currentPrefix.isNotEmpty()) {
            ic.deleteSurroundingText(currentPrefix.length, 0)
            currentPrefix.clear()
        }
        ic.commitText("$selectedWord ", 1)
        vocabEngine.onSuggestionSelected(selectedWord, recentWords)
        recentWords.add(selectedWord)
        updateNextWordPredictions()
    }

    // Step 2.3e: Next-word prediction
    private fun updateNextWordPredictions() {
        val predictions = vocabEngine.predictNextWords(recentWords)
        val words = predictions.map { it.word }
        suggestionStrip.setCandidates(if (words.isNotEmpty()) words else listOf("the", "and", "to"))
    }

    // Step 2.3f: Prefix completion
    private fun updatePrefixCompletions() {
        val prefix = currentPrefix.toString()
        if (prefix.isEmpty()) {
            updateNextWordPredictions()
            return
        }
        val completions = vocabEngine.storage.searchPrefix(prefix)
        suggestionStrip.setCandidates(completions.map { it.word })
    }

    // Step 2.4: Magic Writing bottom sheet / actions
    private fun triggerMagicWrite() {
        val ic = currentInputConnection ?: return
        // PRD 2.4c: Getting selected text
        val selected = ic.getSelectedText(0)?.toString()
            ?: ic.getTextBeforeCursor(200, 0)?.toString()
            ?: return

        if (selected.isBlank()) return

        // Step 2.4b: Proofread ("Fix it")
        val proofreadResult = magicCoordinator.proofread(selected)
        if (proofreadResult.hasChanges) {
            // PRD 2.4d: Apply proofread
            magicCoordinator.applyProofread(ic, proofreadResult)
            suggestionStrip.setCandidates(listOf("↩ Undo Fix", "Make Formal", "Make Casual", "Emojify"))
        } else {
            // Style rewrite (Formal default)
            val draft = magicCoordinator.rewriteInStyle(selected, WritingStyle.FORMAL)
            magicCoordinator.applyRewrite(ic, selected, draft)

            // PRD 2.4f: Follow-up chips
            val followUps = mutableListOf("↩ Undo")
            followUps.addAll(draft.suggestedFollowUps)
            suggestionStrip.setCandidates(followUps)
        }
    }

    // Step 2.5b: Connect speech-to-text token stream
    fun onSpeechTokenReceived(token: String, isFinalPause: Boolean = false) {
        ramblerController.onStreamTokenReceived(token, isFinalPause)
    }

    // Step 2.5e: Monitor cursor movement
    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int,
        newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        ramblerController.undoStateMachine.handleTextMutation(newSelStart)
    }
}
