package com.keybord.ime

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.inputmethodservice.InputMethodService
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.keyboard.engine.magicwrite.MagicWritingCoordinator
import com.keyboard.engine.magicwrite.WritingStyle
import com.keyboard.engine.rambler.ChunkState
import com.keyboard.engine.rambler.RamblerConfig
import com.keyboard.engine.rambler.RamblerController
import com.keyboard.engine.rambler.RamblerSessionState
import com.keyboard.engine.rambler.RamblerUndoStateMachine
import com.keyboard.engine.rambler.TextChunk
import com.keybord.clipboard.InlineClipboardTrayView
import com.keybord.clipboard.KeybordClipboardManager
import com.keybord.diagnostics.KeybordCrashLogger
import com.keybord.engine.glide.*
import com.keybord.engine.vocab.*
import com.keybord.profile.AppProfileRepository
import com.keybord.settings.KeyboardLayoutCustomizeActivity
import com.keybord.settings.KeybordSettingsActivity
import com.keybord.settings.WritingToolsActivity
import com.keybord.theme.BuiltInThemes
import com.keybord.theme.KeyboardLayoutPrefs
import com.keybord.theme.ThemeGalleryActivity
import com.keybord.theme.ThemeModel
import com.keybord.theme.ThemeRepository
import com.keybord.voice.MicPermissionActivity
import com.keybord.voice.VoiceRamblerManager

/**
 * Master InputMethodService for Keybord.
 * Unifies Unlimited Clipboard, Visual Customization, SHARK2 Glide Typing,
 * Adaptive Vocabulary + Personal Dictionary Sync, Hinglish Bilingual Support,
 * Emoji Panel, Expanded Tools Drawer, Voice Rambler Dictation, and AI Writer.
 */
class KeybordIME : InputMethodService() {

    // Engines
    private lateinit var lexicon: TrieLexicon
    private lateinit var glideDecoder: GlideDecoder
    private lateinit var vocabEngine: AdaptiveVocabularyEngine
    private lateinit var clipboardManager: KeybordClipboardManager
    private lateinit var themeRepo: ThemeRepository
    private lateinit var profileRepo: AppProfileRepository
    private lateinit var layoutPrefs: KeyboardLayoutPrefs
    private val magicCoordinator = MagicWritingCoordinator()
    private lateinit var ramblerController: RamblerController
    private lateinit var voiceRamblerManager: VoiceRamblerManager

    // Background executors (offload lexicon/SQLite predictions off main thread to prevent ANRs)
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    @Volatile private var isServiceDestroyed = false
    private val lexiconExecutor = java.util.concurrent.Executors.newSingleThreadExecutor { r ->
        Thread(r, "KeybordLexiconLoader").apply { isDaemon = true }
    }
    private val predictionExecutor = java.util.concurrent.Executors.newSingleThreadExecutor { r ->
        Thread(r, "KeybordPredictor").apply { isDaemon = true }
    }
    @Volatile private var isInternalInputEdit = false
    @Volatile private var lexiconLoadGeneration = 0
    @Volatile private var predictionGeneration = 0

    // Views
    private lateinit var rootContainer: LinearLayout
    private lateinit var suggestionStripView: SuggestionStripView
    private lateinit var aiWriterPanelView: AiWriterPanelView
    private lateinit var keyboardFrame: FrameLayout
    private lateinit var keyboardView: KeyboardView
    private lateinit var gestureOverlayView: GestureOverlayView
    private lateinit var popPreviewView: KeyPopPreviewView
    private lateinit var emojiPanelView: EmojiPanelView
    private lateinit var expandedToolsPanel: ExpandedToolsPanelView
    private lateinit var inlineClipboardTray: InlineClipboardTrayView
    private lateinit var base32PanelView: Base32PanelView

    // State & Prefs
    private var baseTheme: ThemeModel = BuiltInThemes.SKEUO_DARK_GRAPHITE
    private var activeTheme: ThemeModel = BuiltInThemes.SKEUO_DARK_GRAPHITE
    private val touchPoints = mutableListOf<TouchPoint>()
    private val recentContextWords = mutableListOf<String>()
    private var currentWordPrefix = StringBuilder()
    private var lastSpaceTapTime = 0L
    private var isGlideTypingEnabled = true
    private var isAutoCorrectEnabled = true
    private var isAutoCapitaliseEnabled = true
    private var isDoubleSpacePeriodEnabled = true
    private var isHinglishEnabled = true
    private var currentEditorInfo: EditorInfo? = null

    // Track for manual correction, auto-correct undo, smart punctuation spacing, and glide alternatives
    private var lastCommittedWord: String? = null
    private var wasLastCommittedFromSuggestion = false
    private var lastGlideCommittedWord: String? = null
    private var autoInsertedSpaceBeforePunctuation = false
    private var lastAutoCorrectedOriginal: String? = null
    private var lastAutoCorrectedReplacement: String? = null
    private var suppressedAutoCorrectWord: String? = null
    private var isSwipeSession = false
    private var pendingMicAfterPermission = false

    private val layoutPrefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        applyTheme(baseTheme)
    }

    // Swipe-to-delete state
    private var swipeUseSelectionHighlight = false
    private var swipeInitialCursorEnd = 0
    private var swipeWordBoundaries = IntArray(0)
    private var lastSwipeWordCount = 0
    private val swipeDeletedChunks = mutableListOf<String>()

    private val micPermissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val granted = intent?.getBooleanExtra(MicPermissionActivity.EXTRA_GRANTED, false) == true
            val shouldStart = pendingMicAfterPermission
            pendingMicAfterPermission = false
            if (granted && shouldStart) {
                startVoiceRamblerDictation()
            } else if (!granted) {
                if (::suggestionStripView.isInitialized) {
                    suggestionStripView.showRamblerMessage("Microphone permission denied")
                }
            }
        }
    }
    private var isMicReceiverRegistered = false

    override fun onCreate() {
        super.onCreate()
        KeybordCrashLogger.install(this)
        KeybordCrashLogger.recordInfo("KeybordIME", "IME service created")
        configureImeWindow()
        layoutPrefs = KeyboardLayoutPrefs(this)
        getSharedPreferences(KeyboardLayoutPrefs.PREFS_NAME, MODE_PRIVATE)
            .registerOnSharedPreferenceChangeListener(layoutPrefsListener)
        loadTypingPreferences()

        // 1. Initialize Lexicon (assets/wordlist.txt + Hinglish) & SHARK2 Glide Engine
        lexicon = TrieLexicon().apply {
            loadFromAssets(this@KeybordIME, includeHinglish = isHinglishEnabled)
        }
        glideDecoder = GlideDecoder(emptyMap(), lexicon)

        // 2. Initialize Adaptive Vocabulary Engine & sync personal words asynchronously
        vocabEngine = AdaptiveVocabularyEngine(
            context = this,
            staticWordLookup = { word -> lexicon.contains(word) }
        ).apply {
            staticLexicon = lexicon
        }
        syncUserVocabularyAsync(lexicon)

        // 3. Initialize Voice Rambler Controller & SpeechRecognizer Manager
        val writingPrefs = getSharedPreferences(WritingToolsActivity.PREFS_NAME, MODE_PRIVATE)
        ramblerController = RamblerController(
            config = RamblerConfig(
                autoPunctuationEnabled = writingPrefs.getBoolean(WritingToolsActivity.KEY_RAMBLER_SMART_PUNCT, true),
                disfluencyFilterEnabled = writingPrefs.getBoolean(WritingToolsActivity.KEY_RAMBLER_FILLERS, true),
                preserveRewrittenOnRevert = writingPrefs.getBoolean(WritingToolsActivity.KEY_RAMBLER_DUAL_UNDO, true)
            )
        )
        ramblerController.undoStateMachine.listener = object : RamblerUndoStateMachine.UndoStateListener {
            override fun onChunkStateChanged(chunk: TextChunk, newState: ChunkState) {
                if (::suggestionStripView.isInitialized) {
                    suggestionStripView.showRamblerRevertState(newState == ChunkState.RAW_TRANSCRIPT)
                }
            }

            override fun onSessionReset(reason: String) {
                if (::suggestionStripView.isInitialized && !voiceRamblerManager.isListening) {
                    suggestionStripView.hideRamblerBar()
                }
            }
        }
        voiceRamblerManager = VoiceRamblerManager(
            context = this,
            onReady = {
                if (::suggestionStripView.isInitialized) {
                    suggestionStripView.showRamblerListeningState()
                }
            },
            onPartialSpeech = { partial ->
                ramblerController.onCumulativeHypothesisReceived(partial, isFinal = false)
                if (::suggestionStripView.isInitialized) {
                    suggestionStripView.showRamblerStreamingPreview(partial)
                }
            },
            onFinalSpeech = { finalText ->
                val chunk = ramblerController.onCumulativeHypothesisReceived(finalText, isFinal = true)
                if (chunk != null) {
                    vocabEngine.onWordCommittedAsync(
                        chunk.cleanedText.split(Regex("\\s+")).lastOrNull().orEmpty(),
                        recentContextWords.toList(),
                        LearningEvent.NORMAL_TYPING
                    )
                    if (::suggestionStripView.isInitialized) {
                        suggestionStripView.showRamblerRevertState(isShowingRaw = false)
                    }
                } else if (::suggestionStripView.isInitialized) {
                    suggestionStripView.hideRamblerBar()
                }
                updateAutoCapShiftState()
            },
            onError = { message, needsPermission ->
                ramblerController.cancelStreaming()
                if (needsPermission) {
                    pendingMicAfterPermission = true
                }
                if (::suggestionStripView.isInitialized) {
                    suggestionStripView.showRamblerMessage(message)
                }
            },
            onStateChanged = { listening ->
                if (!listening && ::suggestionStripView.isInitialized &&
                    !ramblerController.hasActiveToggleChunk()
                ) {
                    // Leave status error or keep revert bar if chunk active
                }
            }
        )

        ContextCompat.registerReceiver(
            this,
            micPermissionReceiver,
            IntentFilter(MicPermissionActivity.ACTION_MIC_PERMISSION_RESULT),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        isMicReceiverRegistered = true

        // 4. Initialize Clipboard Manager
        clipboardManager = KeybordClipboardManager(this).apply {
            startListening()
            addChangeListener {
                if (isServiceDestroyed) return@addChangeListener
                mainHandler.post {
                    if (!isServiceDestroyed && ::suggestionStripView.isInitialized) {
                        updateRecentClipsInStrip()
                    }
                }
            }
        }

        // 5. Initialize Theme & Profile Repositories
        themeRepo = ThemeRepository(this).apply {
            addOnThemeChangeListener { newTheme ->
                if (!isServiceDestroyed) {
                    applyTheme(newTheme)
                }
            }
        }
        baseTheme = themeRepo.getActiveTheme()
        activeTheme = layoutPrefs.applyTo(baseTheme)
        profileRepo = AppProfileRepository(this)
    }

    private fun loadTypingPreferences() {
        val prevHinglish = isHinglishEnabled
        try {
            val prefs = getSharedPreferences("keybord_typing_prefs", MODE_PRIVATE)
            isGlideTypingEnabled = prefs.getBoolean("glide_typing", true)
            isAutoCorrectEnabled = prefs.getBoolean("auto_correct", true)
            isAutoCapitaliseEnabled = prefs.getBoolean("auto_capitalise", true)
            isDoubleSpacePeriodEnabled = prefs.getBoolean("double_space_period", true)
            isHinglishEnabled = prefs.getBoolean("hinglish_typing", true)
        } catch (e: Exception) {
            KeybordCrashLogger.recordNonFatal("KeybordIME", "Failed reading typing prefs", e)
            isGlideTypingEnabled = true
            isAutoCorrectEnabled = true
            isAutoCapitaliseEnabled = true
            isDoubleSpacePeriodEnabled = true
            isHinglishEnabled = true
        }
        if (::keyboardView.isInitialized) {
            keyboardView.spaceBarLabel = if (isHinglishEnabled) "English + Hinglish" else "English • QWERTY"
        }
        if (::suggestionStripView.isInitialized) {
            suggestionStripView.setHinglishEnabled(isHinglishEnabled)
        }
        if (::expandedToolsPanel.isInitialized) {
            expandedToolsPanel.isHinglishEnabled = isHinglishEnabled
        }
        if (::lexicon.isInitialized && prevHinglish != isHinglishEnabled) {
            reloadLexiconAsync(isHinglishEnabled)
        }
    }

    private fun toggleHinglishLanguage() {
        val next = !isHinglishEnabled
        try {
            getSharedPreferences("keybord_typing_prefs", MODE_PRIVATE)
                .edit()
                .putBoolean("hinglish_typing", next)
                .apply()
        } catch (e: Exception) {
            KeybordCrashLogger.recordNonFatal("KeybordIME", "Failed toggling language", e)
        }
        loadTypingPreferences()
        refreshPredictions()
    }

    private fun executeSafely(
        executor: java.util.concurrent.ExecutorService,
        taskName: String,
        block: () -> Unit
    ) {
        if (isServiceDestroyed || executor.isShutdown || executor.isTerminated) return
        try {
            executor.execute {
                if (isServiceDestroyed) return@execute
                try {
                    block()
                } catch (e: Exception) {
                    KeybordCrashLogger.recordNonFatal("KeybordIME", "$taskName failed", e)
                }
            }
        } catch (_: java.util.concurrent.RejectedExecutionException) {
            // Service shutting down cleanly
        }
    }

    private fun reloadLexiconAsync(includeHinglish: Boolean) {
        val generation = ++lexiconLoadGeneration
        executeSafely(lexiconExecutor, "Async lexicon reload") {
            val freshLexicon = TrieLexicon().apply {
                loadFromAssets(this@KeybordIME, includeHinglish = includeHinglish)
            }
            if (isServiceDestroyed) return@executeSafely
            syncUserVocabularyInto(freshLexicon)
            mainHandler.post {
                if (isServiceDestroyed || lexiconLoadGeneration != generation) return@post
                val positions = if (::keyboardView.isInitialized) keyboardView.getKeyPositions() else emptyMap()
                lexicon = freshLexicon
                if (::vocabEngine.isInitialized) {
                    vocabEngine.staticLexicon = freshLexicon
                }
                glideDecoder = GlideDecoder(positions, freshLexicon)
                if (::suggestionStripView.isInitialized) {
                    refreshPredictions()
                }
            }
        }
    }

    private fun syncUserVocabularyAsync(targetLexicon: TrieLexicon) {
        executeSafely(lexiconExecutor, "Vocabulary sync") {
            syncUserVocabularyInto(targetLexicon)
        }
    }

    private fun syncUserVocabularyInto(targetLexicon: TrieLexicon) {
        if (isServiceDestroyed) return
        try {
            vocabEngine.personalDictionary.syncFromSystem(contentResolver)
            for (entry in vocabEngine.personalDictionary.getAllPersonalWords()) {
                if (isServiceDestroyed) return
                targetLexicon.insert(entry.word, maxOf(entry.count.toFloat(), 900f))
            }
            if (isServiceDestroyed) return
            for (entry in vocabEngine.storage.getKnownWords(350)) {
                if (isServiceDestroyed) return
                targetLexicon.insert(entry.word, maxOf(650f, entry.count * 25f))
            }
        } catch (e: Exception) {
            if (!isServiceDestroyed) {
                KeybordCrashLogger.recordNonFatal("KeybordIME", "Vocabulary sync error", e)
            }
        }
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

    override fun onDestroy() {
        isServiceDestroyed = true
        super.onDestroy()
        try {
            getSharedPreferences(KeyboardLayoutPrefs.PREFS_NAME, MODE_PRIVATE)
                .unregisterOnSharedPreferenceChangeListener(layoutPrefsListener)
        } catch (_: Exception) {
            // Safe ignore
        }
        if (isMicReceiverRegistered) {
            try {
                unregisterReceiver(micPermissionReceiver)
            } catch (_: Exception) {
                // Already unregistered
            }
            isMicReceiverRegistered = false
        }
        try {
            mainHandler.removeCallbacksAndMessages(null)
            lexiconExecutor.shutdown()
            predictionExecutor.shutdown()
            try {
                lexiconExecutor.awaitTermination(150, java.util.concurrent.TimeUnit.MILLISECONDS)
                predictionExecutor.awaitTermination(150, java.util.concurrent.TimeUnit.MILLISECONDS)
            } catch (_: InterruptedException) {
                // Continue teardown
            }
            lexiconExecutor.shutdownNow()
            predictionExecutor.shutdownNow()
            if (::aiWriterPanelView.isInitialized) {
                aiWriterPanelView.shutdown()
            }
            if (::base32PanelView.isInitialized) {
                base32PanelView.pauseTicker()
            }
            if (::keyboardView.isInitialized) {
                keyboardView.cancelActiveTouch()
            }
            ramblerController.finishStreaming()
            voiceRamblerManager.destroy()
            clipboardManager.stopListening()
            vocabEngine.shutdown()
            profileRepo.close()
            clipboardManager.storage.close()
        } catch (e: Exception) {
            KeybordCrashLogger.recordNonFatal("KeybordIME", "Service teardown error", e)
        }
    }

    override fun onCreateInputView(): View {
        rootContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.BOTTOM
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            )
        }

        // 1. Enhanced Suggestion Strip (Clipboard, Emoji 😊, EN+HI, AI ✨, Candidates, Mic 🎙, Settings)
        suggestionStripView = SuggestionStripView(this).apply {
            theme = activeTheme
            setHinglishEnabled(this@KeybordIME.isHinglishEnabled)
            onCandidateSelected = { word ->
                commitCandidate(word)
            }
            onCandidateDeleteRequested = { word ->
                vocabEngine.deleteLearnedWord(word)
                refreshPredictions()
            }
            onToolsGridClicked = {
                toggleToolsPanel()
            }
            onClipboardIconClicked = {
                toggleInlineClipboardTray()
            }
            onEmojiIconClicked = {
                toggleEmojiPanel()
            }
            onBase32Clicked = {
                toggleBase32Panel()
            }
            onLanguageToggleClicked = {
                toggleHinglishLanguage()
            }
            onAiWriterClicked = {
                toggleAiWriterPanel()
            }
            onSettingsIconClicked = {
                launchSettings()
            }
            onRecentClipSelected = { text ->
                commitExternalText(text)
            }
            onRecentClipDismissed = { id ->
                clipboardManager.dismissRecentItem(id)
                updateRecentClipsInStrip()
            }
            onMagicProofreadClicked = {
                performMagicProofread()
            }
            onMagicStyleClicked = { style ->
                performMagicRewrite(style)
            }
            onMagicUndoClicked = {
                performMagicUndo()
            }
            onMicClicked = {
                onMicButtonPressed()
            }
            onRamblerStopClicked = {
                voiceRamblerManager.stopListening()
            }
            onRamblerToggleRawClicked = {
                if (ramblerController.toggleTranscriptRevert()) {
                    showRamblerRevertState(ramblerController.isShowingRawTranscript())
                }
            }
        }
        rootContainer.addView(suggestionStripView)

        // 1b. Docked AI Writer Composer (rendered above KeyboardView so physical keys type prompts)
        aiWriterPanelView = AiWriterPanelView(this).apply {
            theme = activeTheme
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            onInsertText = { text ->
                applyAiWriterOutput(text, AiApplyMode.INSERT, false)
            }
            onReplaceSelectedOrText = { text, hadSelection ->
                applyAiWriterOutput(text, AiApplyMode.REPLACE, hadSelection)
            }
            onAppendText = { text ->
                applyAiWriterOutput(text, AiApplyMode.APPEND, false)
            }
            onCopyText = { text ->
                clipboardManager.copyToClipboard(text, "AI Writer")
                Toast.makeText(this@KeybordIME, "Copied to clipboard", Toast.LENGTH_SHORT).show()
            }
            onRequestAppSelectionOrText = {
                readAppSelectionOrDraftForAi()
            }
            onRequestClipboardText = {
                readLatestClipboardText()
            }
            onOpenApiSettingsClicked = {
                launchActivity(WritingToolsActivity::class.java)
            }
            onCloseClicked = {
                showKeyboardMain()
            }
        }
        rootContainer.addView(aiWriterPanelView)

        // 2. Keyboard Layer Frame (holds KeyboardView, Overlay, and PopPreview)
        keyboardFrame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        keyboardView = KeyboardView(this).apply {
            theme = activeTheme
            showNumberRow = layoutPrefs.showNumberRow
            showSecondarySymbols = layoutPrefs.showSecondarySymbols
            vibrationDurationMs = layoutPrefs.vibrationDurationMs
            spaceBarLabel = if (isHinglishEnabled) "English + Hinglish" else "English • QWERTY"
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            )
            onKeyTap = { key ->
                handleKeyTap(key)
            }
            onDeleteRepeatChar = {
                if (isAiPromptCaptureActive()) {
                    aiWriterPanelView.handlePromptBackspace()
                } else {
                    deleteSingleCharOrSelection()
                }
            }
            onDeleteWord = {
                if (isAiPromptCaptureActive()) {
                    aiWriterPanelView.handlePromptWordDelete()
                } else {
                    deleteOneWordBeforeCursor()
                }
            }
            onDeleteSwipeStart = {
                beginSwipeDeleteSession()
            }
            onDeleteSwipeStep = { wordCount ->
                updateSwipeDeleteSelection(wordCount)
            }
            onDeleteSwipeFinish = { commitDelete ->
                finishSwipeDeleteSession(commitDelete)
            }
            onSpaceSwipeCursorStep = { deltaSteps ->
                moveCursorBySteps(deltaSteps)
            }
            onSpaceSwipeStateChanged = { active ->
                handleSpaceSwipeStateChanged(active)
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
        rootContainer.addView(keyboardFrame)

        // 3. Emoji Section Panel (initially hidden)
        emojiPanelView = EmojiPanelView(this).apply {
            theme = activeTheme
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            onEmojiSelected = { emoji ->
                commitExternalText(emoji)
                lastAutoCorrectedOriginal = null
                lastAutoCorrectedReplacement = null
                lastGlideCommittedWord = null
            }
            onBackspaceClicked = {
                deleteSingleCharOrSelection()
            }
            onBackToKeyboardClicked = {
                showKeyboardMain()
            }
        }
        rootContainer.addView(emojiPanelView)

        // 4. Skeuomorphic Expanded Tools & Quick Rambler Drawer (initially hidden)
        expandedToolsPanel = ExpandedToolsPanelView(this).apply {
            theme = activeTheme
            isHinglishEnabled = this@KeybordIME.isHinglishEnabled
            isOneHandedEnabled = layoutPrefs.oneHandedEnabled
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            onCollapseClicked = { showKeyboardMain() }
            onOpenClipboardClicked = { toggleInlineClipboardTray() }
            onOpenEmojiClicked = { toggleEmojiPanel() }
            onOpenBase32Clicked = { toggleBase32Panel() }
            onOpenWritingToolsClicked = { toggleAiWriterPanel() }
            onOpenLayoutStudioClicked = { launchActivity(KeyboardLayoutCustomizeActivity::class.java) }
            onToggleOneHandedClicked = {
                layoutPrefs.oneHandedEnabled = !layoutPrefs.oneHandedEnabled
                isOneHandedEnabled = layoutPrefs.oneHandedEnabled
                applyTheme(baseTheme)
            }
            onOpenThemesClicked = { launchActivity(ThemeGalleryActivity::class.java) }
            onOpenSettingsClicked = { launchSettings() }
            onToggleHinglishClicked = {
                toggleHinglishLanguage()
            }
            onRamblerToggleRawClicked = {
                if (ramblerController.toggleTranscriptRevert()) {
                    val raw = ramblerController.isShowingRawTranscript()
                    setRawState(raw)
                    suggestionStripView.showRamblerRevertState(raw)
                }
            }
            onMagicFixItClicked = {
                showKeyboardMain()
                performMagicProofread()
            }
            onMagicStyleClicked = { style ->
                showKeyboardMain()
                performMagicRewrite(style)
            }
            onMicClicked = {
                showKeyboardMain()
                onMicButtonPressed()
            }
        }
        rootContainer.addView(expandedToolsPanel)

        // 5. Inline Clipboard Tray (initially hidden)
        inlineClipboardTray = InlineClipboardTrayView(this).apply {
            theme = activeTheme
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        rootContainer.addView(inlineClipboardTray)

        // 6. On-Keyboard Base32 Studio (initially hidden)
        base32PanelView = Base32PanelView(this).apply {
            theme = activeTheme
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            onInsertText = { text ->
                commitBase32Text(text)
            }
            onReplaceSelectionOrInsert = { text ->
                commitBase32Text(text)
            }
            onCopyText = { text ->
                clipboardManager.copyToClipboard(text, "Base32 Studio")
                Toast.makeText(this@KeybordIME, "Copied to clipboard", Toast.LENGTH_SHORT).show()
            }
            onRequestClipboardText = {
                readLatestClipboardText()
            }
            onRequestAppText = {
                readActiveAppText()
            }
            onBackToKeyboardClicked = {
                showKeyboardMain()
            }
        }
        rootContainer.addView(base32PanelView)

        // Intercept Touch for Glide Typing & Multi-Touch Rollover
        keyboardFrame.setOnTouchListener { _, event ->
            handleKeyboardTouch(event)
        }

        applyTheme(baseTheme)
        configureImeWindow()
        return rootContainer
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        try {
            currentEditorInfo = info
            loadTypingPreferences()
            syncUserVocabularyAsync(lexicon)

            (rootContainer.layoutParams as? FrameLayout.LayoutParams)?.let { lp ->
                if (lp.gravity != Gravity.BOTTOM) {
                    lp.gravity = Gravity.BOTTOM
                    lp.width = ViewGroup.LayoutParams.MATCH_PARENT
                    lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                    rootContainer.layoutParams = lp
                }
            }
            configureImeWindow()
            showKeyboardMain()
            keyboardView.cancelActiveTouch()
            swipeUseSelectionHighlight = false
            swipeWordBoundaries = IntArray(0)
            swipeDeletedChunks.clear()
            lastSwipeWordCount = 0
            currentWordPrefix.clear()
            recentContextWords.clear()
            lastGlideCommittedWord = null
            autoInsertedSpaceBeforePunctuation = false
            lastAutoCorrectedOriginal = null
            lastAutoCorrectedReplacement = null
            suppressedAutoCorrectWord = null
            isSwipeSession = false
            touchPoints.clear()

            ramblerController.attachInputConnection(currentInputConnection)
            if (::suggestionStripView.isInitialized) {
                suggestionStripView.setMagicUndoVisible(magicCoordinator.hasUndo)
                if (!voiceRamblerManager.isListening && !ramblerController.hasActiveToggleChunk()) {
                    suggestionStripView.hideRamblerBar()
                }
            }

            // 1. Check Per-App Profiles (info or info.packageName can be null in some webviews/overlays)
            val pkgName = info?.packageName?.takeIf { it.isNotBlank() }
            val profile = profileRepo.getProfileForPackage(pkgName)
            val targetTheme = if (profile?.themeIdOverride != null) {
                themeRepo.getThemeById(profile.themeIdOverride) ?: themeRepo.getActiveTheme()
            } else {
                themeRepo.getActiveTheme()
            }
            applyTheme(targetTheme)

            // 2. Sensitive content detection (password fields)
            if (info != null) {
                clipboardManager.updateCurrentEditorInfo(info)
            }

            // 3. Check cursor context & auto-capitalisation
            val textBefore = try {
                currentInputConnection?.getTextBeforeCursor(40, 0)?.toString() ?: ""
            } catch (_: Exception) {
                ""
            }
            val tokens = textBefore.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
            if (tokens.isNotEmpty()) {
                recentContextWords.addAll(tokens.takeLast(3))
            }
            currentInputConnection?.let { syncWordPrefixFromCursor(it) }
            updateAutoCapShiftState()

            refreshPredictions()
            updateRecentClipsInStrip()
            if (::base32PanelView.isInitialized && base32PanelView.visibility == View.VISIBLE) {
                base32PanelView.onPanelOpened()
            }
        } catch (e: Exception) {
            KeybordCrashLogger.recordNonFatal(
                "KeybordIME",
                "onStartInputView failed",
                e,
                mapOf("hasInfo" to (info != null).toString(), "restarting" to restarting.toString())
            )
        }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        if (::keyboardView.isInitialized) keyboardView.cancelActiveTouch()
        if (::gestureOverlayView.isInitialized) gestureOverlayView.clearTrail()
        if (::base32PanelView.isInitialized) {
            base32PanelView.pauseTicker()
        }
        if (::aiWriterPanelView.isInitialized) {
            aiWriterPanelView.setPromptFocus(false)
        }
        if (voiceRamblerManager.isListening) {
            voiceRamblerManager.stopListening()
        }
        ramblerController.finishStreaming()
        ramblerController.attachInputConnection(null)
    }

    override fun onWindowHidden() {
        super.onWindowHidden()
        if (::keyboardView.isInitialized) keyboardView.cancelActiveTouch()
        if (::gestureOverlayView.isInitialized) gestureOverlayView.clearTrail()
        if (::base32PanelView.isInitialized) base32PanelView.pauseTicker()
        if (::aiWriterPanelView.isInitialized) aiWriterPanelView.setPromptFocus(false)
    }

    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        try {
            ramblerController.undoStateMachine.handleTextMutation(newSelEnd)
            if (::suggestionStripView.isInitialized &&
                !voiceRamblerManager.isListening &&
                !ramblerController.hasActiveToggleChunk() &&
                ramblerController.undoStateMachine.sessionState == RamblerSessionState.IDLE
            ) {
                suggestionStripView.hideRamblerBar()
            }
            if (isInternalInputEdit) return
            val ic = currentInputConnection ?: return
            if (newSelStart >= 0 && newSelStart == newSelEnd && (newSelStart != oldSelStart || newSelEnd != oldSelEnd)) {
                lastAutoCorrectedOriginal = null
                lastAutoCorrectedReplacement = null
                lastGlideCommittedWord = null
                syncWordPrefixFromCursor(ic)
                updateAutoCapShiftState()
                refreshPredictions()
            }
        } catch (e: Exception) {
            KeybordCrashLogger.recordNonFatal("KeybordIME", "onUpdateSelection failed", e)
        }
    }

    private inline fun withBatchEdit(ic: InputConnection, block: () -> Unit) {
        isInternalInputEdit = true
        var begun = false
        try {
            begun = try { ic.beginBatchEdit() } catch (_: Exception) { false }
            block()
        } catch (e: Exception) {
            KeybordCrashLogger.recordNonFatal("KeybordIME", "InputConnection edit error", e)
        } finally {
            if (begun) {
                try { ic.endBatchEdit() } catch (_: Exception) { }
            }
            isInternalInputEdit = false
        }
    }

    private fun commitExternalText(text: String) {
        val ic = currentInputConnection ?: return
        withBatchEdit(ic) {
            ic.commitText(text, 1)
        }
    }

    private fun isAiPromptCaptureActive(): Boolean =
        ::aiWriterPanelView.isInitialized &&
            aiWriterPanelView.visibility == View.VISIBLE &&
            aiWriterPanelView.isPromptCaptureActive

    private fun handleKeyboardTouch(event: MotionEvent): Boolean {
        return try {
            if (!isGlideTypingEnabled || keyboardView.layer != KeyboardView.KeyboardLayer.ALPHA) {
                return keyboardView.handleTouchEvent(event)
            }

            val x = event.x
            val y = event.y
            val now = System.currentTimeMillis()

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    touchPoints.clear()
                    isSwipeSession = keyboardView.isInAlphabeticGlideZone(x, y)
                    if (isSwipeSession) {
                        touchPoints.add(TouchPoint(x, y, now, event.pressure))
                        gestureOverlayView.addPoint(x, y)
                    }
                    keyboardView.handleTouchEvent(event)
                    return true
                }

                MotionEvent.ACTION_POINTER_DOWN -> {
                    // Multi-touch 2-thumb rapid typing immediately yields from glide tracking to rollover tap
                    touchPoints.clear()
                    isSwipeSession = false
                    gestureOverlayView.clearTrail()
                    return keyboardView.handleTouchEvent(event)
                }

                MotionEvent.ACTION_MOVE -> {
                    if (isSwipeSession) {
                        touchPoints.add(TouchPoint(x, y, now, event.pressure))
                        gestureOverlayView.addPoint(x, y)
                    }
                    keyboardView.handleTouchEvent(event)
                    return true
                }

                MotionEvent.ACTION_POINTER_UP -> {
                    return keyboardView.handleTouchEvent(event)
                }

                MotionEvent.ACTION_UP -> {
                    gestureOverlayView.clearTrail()

                    if (isSwipeSession) {
                        touchPoints.add(TouchPoint(x, y, now, event.pressure))
                        val pointsSnapshot = touchPoints.toList()
                        touchPoints.clear()
                        isSwipeSession = false

                        val currentPositions = keyboardView.getKeyPositions()
                        if (currentPositions.isNotEmpty()) {
                            glideDecoder.updateKeyPositions(currentPositions)
                        }

                        val rawCandidates = if (pointsSnapshot.size >= 2) {
                            glideDecoder.decodeGesture(pointsSnapshot)
                        } else {
                            emptyList()
                        }
                        val candidates = rerankWithContext(rawCandidates, resolveRecentContextWords())

                        if (candidates.isNotEmpty()) {
                            keyboardView.cancelActiveTouch()
                            onGlideCandidates(candidates)
                            glideDecoder.notifyTap(now)
                            return true
                        }
                    } else {
                        touchPoints.clear()
                    }

                    // Fallback to discrete tap
                    glideDecoder.notifyTap(now)
                    return keyboardView.handleTouchEvent(event)
                }

                MotionEvent.ACTION_CANCEL -> {
                    touchPoints.clear()
                    isSwipeSession = false
                    gestureOverlayView.clearTrail()
                    return keyboardView.handleTouchEvent(event)
                }
            }
            false
        } catch (e: Exception) {
            touchPoints.clear()
            isSwipeSession = false
            if (::gestureOverlayView.isInitialized) gestureOverlayView.clearTrail()
            if (::keyboardView.isInitialized) keyboardView.cancelActiveTouch()
            KeybordCrashLogger.recordNonFatal("KeybordIME", "handleKeyboardTouch failed", e)
            false
        }
    }

    private fun resolveRecentContextWords(): List<String> {
        val before = try {
            currentInputConnection?.getTextBeforeCursor(48, 0)?.toString()
        } catch (_: Exception) {
            null
        }
        if (!before.isNullOrBlank()) {
            val tokens = before.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
            if (tokens.isNotEmpty()) {
                return tokens.takeLast(3)
            }
        }
        return recentContextWords.toList()
    }

    fun rerankWithContext(
        candidates: List<GlideCandidate>,
        recentWords: List<String>
    ): List<GlideCandidate> {
        if (candidates.size <= 1 || isServiceDestroyed) return candidates
        val rawPrev = recentWords.lastOrNull()?.trim()?.lowercase() ?: return candidates
        val prevWord = rawPrev.trimEnd('.', ',', '!', '?', ';', ':', '"', '\'')
        if (prevWord.isEmpty()) return candidates

        return try {
            candidates.map { candidate ->
                val bigramScore = vocabEngine.storage.getBigramScore(prevWord, candidate.word)
                val contextBoost = 1.0f + (bigramScore * 0.4f)
                candidate.copy(totalScore = candidate.totalScore * contextBoost)
            }.sortedByDescending { it.totalScore }
        } catch (e: Exception) {
            KeybordCrashLogger.recordNonFatal("KeybordIME", "rerankWithContext failed", e)
            candidates
        }
    }

    private fun onGlideCandidates(candidates: List<GlideCandidate>) {
        if (candidates.isEmpty()) return
        val topWord = candidates.first().word
        val committedWord = when (keyboardView.shiftState) {
            KeyboardView.ShiftState.CAPS_LOCK -> topWord.uppercase()
            KeyboardView.ShiftState.SHIFTED -> {
                keyboardView.shiftState = KeyboardView.ShiftState.OFF
                topWord.replaceFirstChar { it.uppercase() }
            }
            KeyboardView.ShiftState.OFF -> topWord
        }

        if (isAiPromptCaptureActive()) {
            aiWriterPanelView.appendPromptText("$committedWord ")
            return
        }

        val ic = currentInputConnection ?: return
        val before = try { ic.getTextBeforeCursor(4, 0)?.toString() ?: "" } catch (_: Exception) { "" }
        val leadingSpace = if (
            before.isNotEmpty() &&
            !before.last().isWhitespace() &&
            before.last() !in charArrayOf('(', '[', '{', '"', '\'', '\n', '/')
        ) {
            " "
        } else {
            ""
        }

        withBatchEdit(ic) {
            ic.commitText("$leadingSpace$committedWord ", 1)
        }

        learnAndSyncWord(committedWord, LearningEvent.NORMAL_TYPING)
        addContextWord(committedWord)
        currentWordPrefix.clear()

        lastCommittedWord = committedWord
        lastGlideCommittedWord = committedWord
        autoInsertedSpaceBeforePunctuation = true
        wasLastCommittedFromSuggestion = false
        lastAutoCorrectedOriginal = null
        lastAutoCorrectedReplacement = null
        suppressedAutoCorrectWord = null
        lastSpaceTapTime = 0L

        val alternatives = if (candidates.size > 1) {
            candidates.drop(1).map { it.word }
        } else {
            listOf(committedWord)
        }
        suggestionStripView.setCandidates(alternatives)
        updateAutoCapShiftState()
    }

    private fun resolveAutoCorrection(rawPrefix: String): String? {
        if (!isAutoCorrectEnabled || rawPrefix.isEmpty()) return null
        if (rawPrefix == "i") return "I"
        if (rawPrefix.length < 2) return null
        val lower = rawPrefix.lowercase()
        if (lower == suppressedAutoCorrectWord) return null
        if (lexicon.contains(lower)) return null
        if (isHinglishEnabled && lexicon.isLikelyIntentionalHinglish(rawPrefix)) return null
        if (vocabEngine.personalDictionary.contains(lower)) return null
        val learnedExact = try {
            vocabEngine.storage.searchPrefix(lower, 3).any { it.word == lower }
        } catch (_: Exception) {
            false
        }
        if (learnedExact) return null
        return lexicon.findBestCorrection(rawPrefix, hinglishEnabled = isHinglishEnabled)
    }

    private fun handleKeyTap(key: String) {
        try {
            handleKeyTapInternal(key)
        } catch (e: Exception) {
            KeybordCrashLogger.recordNonFatal("KeybordIME", "handleKeyTap failed", e)
        }
    }

    private fun handleKeyTapInternal(key: String) {
        if (isAiPromptCaptureActive()) {
            when (key) {
                "⌫" -> aiWriterPanelView.handlePromptBackspace()
                "⇧", "⬆", "⇪" -> {
                    keyboardView.shiftState = when (keyboardView.shiftState) {
                        KeyboardView.ShiftState.OFF -> KeyboardView.ShiftState.SHIFTED
                        KeyboardView.ShiftState.SHIFTED -> KeyboardView.ShiftState.CAPS_LOCK
                        KeyboardView.ShiftState.CAPS_LOCK -> KeyboardView.ShiftState.OFF
                    }
                }
                "?123" -> keyboardView.layer = KeyboardView.KeyboardLayer.SYMBOLS
                "ABC" -> keyboardView.layer = KeyboardView.KeyboardLayer.ALPHA
                "=\\<" -> keyboardView.layer = KeyboardView.KeyboardLayer.EXTENDED_SYMBOLS
                " " -> aiWriterPanelView.appendPromptText(" ")
                "Enter" -> aiWriterPanelView.triggerGeneration()
                else -> {
                    val ch = if (keyboardView.shiftState != KeyboardView.ShiftState.OFF && key.length == 1) {
                        key.uppercase()
                    } else {
                        key
                    }
                    aiWriterPanelView.appendPromptText(ch)
                    if (keyboardView.shiftState == KeyboardView.ShiftState.SHIFTED) {
                        keyboardView.shiftState = KeyboardView.ShiftState.OFF
                    }
                }
            }
            return
        }

        val ic = currentInputConnection ?: return
        val prevGlideWord = lastGlideCommittedWord
        val hadAutoSpace = autoInsertedSpaceBeforePunctuation
        lastGlideCommittedWord = null
        autoInsertedSpaceBeforePunctuation = false
        if (key != " ") {
            lastSpaceTapTime = 0L
        }
        if (key != "⌫") {
            lastAutoCorrectedOriginal = null
            lastAutoCorrectedReplacement = null
        }

        when (key) {
            "⌫" -> deleteSingleCharOrSelection()

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
                val rawPrefix = currentWordPrefix.toString()

                if (rawPrefix.isNotEmpty()) {
                    val expandedShortcut = vocabEngine.personalDictionary.getShortcutExpansion(rawPrefix)
                    if (expandedShortcut != null && expandedShortcut != rawPrefix) {
                        withBatchEdit(ic) {
                            ic.deleteSurroundingText(rawPrefix.length, 0)
                            ic.commitText("$expandedShortcut ", 1)
                        }
                        learnAndSyncWord(expandedShortcut, LearningEvent.NORMAL_TYPING)
                        addContextWord(expandedShortcut)
                        currentWordPrefix.clear()
                        suppressedAutoCorrectWord = null
                        lastSpaceTapTime = now
                        updateAutoCapShiftState()
                        refreshPredictions()
                        return
                    }

                    val correction = resolveAutoCorrection(rawPrefix)
                    if (correction != null && correction != rawPrefix) {
                        val replacement = "$correction "
                        withBatchEdit(ic) {
                            ic.deleteSurroundingText(rawPrefix.length, 0)
                            ic.commitText(replacement, 1)
                        }
                        lastAutoCorrectedOriginal = rawPrefix
                        lastAutoCorrectedReplacement = replacement
                        suppressedAutoCorrectWord = null
                        learnAndSyncWord(correction, LearningEvent.NORMAL_TYPING)
                        addContextWord(correction)
                        currentWordPrefix.clear()
                        lastSpaceTapTime = now
                        updateAutoCapShiftState()
                        refreshPredictionsWithFallbackPrefix(rawPrefix)
                        return
                    }
                }

                val before = try { ic.getTextBeforeCursor(4, 0)?.toString() ?: "" } catch (_: Exception) { "" }
                val canInsertPeriod = isDoubleSpacePeriodEnabled &&
                    now - lastSpaceTapTime < 400L &&
                    before.endsWith(" ") &&
                    before.length >= 2 &&
                    !before[before.length - 2].isWhitespace()

                if (canInsertPeriod) {
                    withBatchEdit(ic) {
                        ic.deleteSurroundingText(1, 0)
                        ic.commitText(". ", 1)
                    }
                    lastSpaceTapTime = 0L
                } else {
                    withBatchEdit(ic) {
                        ic.commitText(" ", 1)
                    }
                    lastSpaceTapTime = now
                }

                suppressedAutoCorrectWord = null
                if (currentWordPrefix.isNotEmpty()) {
                    val committed = currentWordPrefix.toString()
                    learnAndSyncWord(committed, LearningEvent.NORMAL_TYPING)
                    addContextWord(committed)
                    currentWordPrefix.clear()
                }

                updateAutoCapShiftState()
                refreshPredictions()
            }

            "Enter" -> {
                val rawPrefix = currentWordPrefix.toString()
                val correction = resolveAutoCorrection(rawPrefix)
                if (correction != null && correction != rawPrefix) {
                    withBatchEdit(ic) {
                        ic.deleteSurroundingText(rawPrefix.length, 0)
                        ic.commitText(correction, 1)
                    }
                    learnAndSyncWord(correction, LearningEvent.NORMAL_TYPING)
                } else if (rawPrefix.isNotEmpty()) {
                    learnAndSyncWord(rawPrefix, LearningEvent.NORMAL_TYPING)
                }
                isInternalInputEdit = true
                try {
                    ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                    ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
                } catch (e: Exception) {
                    KeybordCrashLogger.recordNonFatal("KeybordIME", "Enter sendKeyEvent failed", e)
                } finally {
                    isInternalInputEdit = false
                }
                currentWordPrefix.clear()
                recentContextWords.clear()
                suppressedAutoCorrectWord = null
                updateAutoCapShiftState()
                refreshPredictions()
            }

            ".", ",", "!", "?", ";", ":" -> {
                val before = try { ic.getTextBeforeCursor(4, 0)?.toString() ?: "" } catch (_: Exception) { "" }
                val rawPrefix = currentWordPrefix.toString()
                val correction = resolveAutoCorrection(rawPrefix)
                withBatchEdit(ic) {
                    if (correction != null && correction != rawPrefix) {
                        val replacement = "$correction$key "
                        ic.deleteSurroundingText(rawPrefix.length, 0)
                        ic.commitText(replacement, 1)
                        lastAutoCorrectedOriginal = rawPrefix
                        lastAutoCorrectedReplacement = replacement
                        learnAndSyncWord(correction, LearningEvent.NORMAL_TYPING)
                        addContextWord(correction)
                        currentWordPrefix.clear()
                    } else if ((hadAutoSpace || prevGlideWord != null) && before.endsWith(" ")) {
                        ic.deleteSurroundingText(1, 0)
                        ic.commitText("$key ", 1)
                    } else {
                        ic.commitText(key, 1)
                        if (currentWordPrefix.isNotEmpty()) {
                            val committed = currentWordPrefix.toString()
                            learnAndSyncWord(committed, LearningEvent.NORMAL_TYPING)
                            addContextWord(committed)
                            currentWordPrefix.clear()
                        }
                    }
                }
                suppressedAutoCorrectWord = null
                if (keyboardView.shiftState == KeyboardView.ShiftState.SHIFTED) {
                    keyboardView.shiftState = KeyboardView.ShiftState.OFF
                }
                updateAutoCapShiftState()
                refreshPredictions()
            }

            else -> {
                val charToCommit = if (keyboardView.shiftState != KeyboardView.ShiftState.OFF && key.length == 1) {
                    key.uppercase()
                } else {
                    key
                }

                suppressedAutoCorrectWord = null
                withBatchEdit(ic) {
                    ic.commitText(charToCommit, 1)
                }

                if (charToCommit.length == 1 && (charToCommit[0].isLetter() || charToCommit[0] == '\'')) {
                    currentWordPrefix.append(charToCommit)
                } else {
                    if (currentWordPrefix.isNotEmpty()) {
                        val committed = currentWordPrefix.toString()
                        learnAndSyncWord(committed, LearningEvent.NORMAL_TYPING)
                        addContextWord(committed)
                        currentWordPrefix.clear()
                    }
                }

                if (keyboardView.shiftState == KeyboardView.ShiftState.SHIFTED) {
                    keyboardView.shiftState = KeyboardView.ShiftState.OFF
                }

                refreshPredictions()
            }
        }
    }

    private fun updateAutoCapShiftState() {
        if (!isAutoCapitaliseEnabled || !::keyboardView.isInitialized) return
        if (keyboardView.shiftState == KeyboardView.ShiftState.CAPS_LOCK) return

        val info = currentEditorInfo ?: return
        val inputClass = info.inputType and InputType.TYPE_MASK_CLASS
        if (inputClass != InputType.TYPE_CLASS_TEXT) return

        val variation = info.inputType and InputType.TYPE_MASK_VARIATION
        if (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS ||
            variation == InputType.TYPE_TEXT_VARIATION_URI
        ) {
            return
        }

        if ((info.inputType and InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS) != 0) {
            keyboardView.shiftState = KeyboardView.ShiftState.CAPS_LOCK
            return
        }

        val ic = currentInputConnection ?: return
        val capsMode = try { ic.getCursorCapsMode(info.inputType) } catch (_: Exception) { 0 }
        val before = try { ic.getTextBeforeCursor(48, 0)?.toString() ?: "" } catch (_: Exception) { "" }

        val sentenceStart = capsMode != 0 ||
            before.isEmpty() ||
            before.endsWith("\n") ||
            Regex(".*[.!?][\"')\\]]*\\s+$").matches(before)

        keyboardView.shiftState = if (sentenceStart && currentWordPrefix.isEmpty()) {
            KeyboardView.ShiftState.SHIFTED
        } else if (!sentenceStart && currentWordPrefix.isNotEmpty() && keyboardView.shiftState == KeyboardView.ShiftState.SHIFTED) {
            KeyboardView.ShiftState.OFF
        } else {
            keyboardView.shiftState
        }
    }

    private fun handleSpaceSwipeStateChanged(active: Boolean) {
        if (active) {
            isSwipeSession = false
            touchPoints.clear()
            gestureOverlayView.clearTrail()
            lastAutoCorrectedOriginal = null
            lastAutoCorrectedReplacement = null
            lastGlideCommittedWord = null
        } else {
            currentInputConnection?.let { ic ->
                syncWordPrefixFromCursor(ic)
                updateAutoCapShiftState()
                refreshPredictions()
            }
        }
    }

    private fun moveCursorBySteps(deltaSteps: Int) {
        val ic = currentInputConnection ?: return
        if (deltaSteps == 0) return
        val keyCode = if (deltaSteps < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
        val count = kotlin.math.abs(deltaSteps).coerceAtMost(12)
        try {
            for (i in 0 until count) {
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
            }
        } catch (e: Exception) {
            KeybordCrashLogger.recordNonFatal("KeybordIME", "moveCursorBySteps failed", e)
        }
    }

    private fun onMicButtonPressed() {
        if (voiceRamblerManager.isListening) {
            voiceRamblerManager.stopListening()
            return
        }
        if (!voiceRamblerManager.hasAudioPermission()) {
            pendingMicAfterPermission = true
            voiceRamblerManager.requestAudioPermission()
            return
        }
        startVoiceRamblerDictation()
    }

    private fun startVoiceRamblerDictation() {
        val ic = currentInputConnection
        ramblerController.attachInputConnection(ic)
        val initialOffset = getCurrentCursorOffset(ic)
        ramblerController.startSession(initialOffset)
        suggestionStripView.showRamblerListeningState()
        voiceRamblerManager.startListening()
    }

    private fun getCurrentCursorOffset(ic: InputConnection?): Int {
        if (ic == null) return 0
        val ext = try { ic.getExtractedText(ExtractedTextRequest(), 0) } catch (_: Exception) { null }
        if (ext != null && ext.selectionEnd >= 0) {
            return ext.selectionEnd
        }
        return try { ic.getTextBeforeCursor(1024, 0)?.length ?: 0 } catch (_: Exception) { 0 }
    }

    private fun resolveMagicTarget(ic: InputConnection): Pair<String, Boolean>? {
        val selected = try { ic.getSelectedText(0)?.toString() } catch (_: Exception) { null }
        if (!selected.isNullOrBlank()) {
            return selected to true
        }
        val before = try { ic.getTextBeforeCursor(400, 0)?.toString() } catch (_: Exception) { null }
        val draft = before?.substringAfterLast('\n')?.trimStart().orEmpty()
        if (draft.isBlank()) {
            Toast.makeText(this, "Type or select text to transform", Toast.LENGTH_SHORT).show()
            return null
        }
        return draft to false
    }

    private fun performMagicProofread() {
        val ic = currentInputConnection ?: return
        val (target, hadSelection) = resolveMagicTarget(ic) ?: return
        val result = magicCoordinator.proofread(target)
        if (!result.hasChanges || result.correctedText == target) {
            Toast.makeText(this, "Looks clean — no issues found", Toast.LENGTH_SHORT).show()
            return
        }
        if (magicCoordinator.applyReplacement(ic, target, result.correctedText, hadSelection)) {
            currentWordPrefix.clear()
            autoInsertedSpaceBeforePunctuation = false
            suggestionStripView.setMagicUndoVisible(magicCoordinator.hasUndo)
        }
    }

    private fun performMagicRewrite(style: WritingStyle) {
        val ic = currentInputConnection ?: return
        val (target, hadSelection) = resolveMagicTarget(ic) ?: return
        val draft = magicCoordinator.rewriteInStyle(target, style)
        if (draft.text.isNotBlank() && draft.text != target) {
            if (magicCoordinator.applyReplacement(ic, target, draft.text, hadSelection)) {
                currentWordPrefix.clear()
                autoInsertedSpaceBeforePunctuation = false
                suggestionStripView.setMagicUndoVisible(magicCoordinator.hasUndo)
            }
        }
    }

    private fun performMagicUndo() {
        val ic = currentInputConnection ?: return
        if (magicCoordinator.revertLastChange(ic)) {
            suggestionStripView.setMagicUndoVisible(magicCoordinator.hasUndo)
            syncWordPrefixFromCursor(ic)
            refreshPredictions()
        }
    }

    private fun deleteSingleCharOrSelection() {
        val ic = currentInputConnection ?: return
        lastGlideCommittedWord = null
        autoInsertedSpaceBeforePunctuation = false

        val selected = try { ic.getSelectedText(0) } catch (_: Exception) { null }
        if (!selected.isNullOrEmpty()) {
            lastAutoCorrectedOriginal = null
            lastAutoCorrectedReplacement = null
            withBatchEdit(ic) {
                ic.commitText("", 1)
            }
            syncWordPrefixFromCursor(ic)
            updateAutoCapShiftState()
            refreshPredictions()
            return
        }

        val pendingOrig = lastAutoCorrectedOriginal
        val pendingRepl = lastAutoCorrectedReplacement
        lastAutoCorrectedOriginal = null
        lastAutoCorrectedReplacement = null
        if (pendingOrig != null && pendingRepl != null) {
            val checkBefore = try { ic.getTextBeforeCursor(pendingRepl.length + 2, 0)?.toString() } catch (_: Exception) { null }
            if (checkBefore != null && checkBefore.endsWith(pendingRepl)) {
                withBatchEdit(ic) {
                    ic.deleteSurroundingText(pendingRepl.length, 0)
                    ic.commitText(pendingOrig, 1)
                }
                val correctedWord = pendingRepl.trimEnd('.', ',', '!', '?', ';', ':', ' ')
                vocabEngine.onManualCorrectionAsync(correctedWord, pendingOrig, recentContextWords.toList())
                lexicon.insert(pendingOrig.lowercase(), 800f)
                suppressedAutoCorrectWord = pendingOrig.lowercase()
                syncWordPrefixFromCursor(ic)
                updateAutoCapShiftState()
                refreshPredictions()
                return
            }
        }

        if (lastCommittedWord != null && wasLastCommittedFromSuggestion) {
            vocabEngine.onScrubDeleteAsync(lastCommittedWord!!)
            lastCommittedWord = null
            wasLastCommittedFromSuggestion = false
        }

        val before = try { ic.getTextBeforeCursor(4, 0) } catch (_: Exception) { null }
        val delCount = DeleteWordHelper.computeSingleCharDeleteCount(before)
        if (delCount > 0) {
            withBatchEdit(ic) {
                ic.deleteSurroundingText(delCount, 0)
            }
        }
        syncWordPrefixFromCursor(ic)
        updateAutoCapShiftState()
        refreshPredictions()
    }

    private fun deleteOneWordBeforeCursor() {
        val ic = currentInputConnection ?: return
        lastGlideCommittedWord = null
        autoInsertedSpaceBeforePunctuation = false

        val selected = try { ic.getSelectedText(0) } catch (_: Exception) { null }
        if (!selected.isNullOrEmpty()) {
            withBatchEdit(ic) {
                ic.commitText("", 1)
            }
            syncWordPrefixFromCursor(ic)
            updateAutoCapShiftState()
            refreshPredictions()
            return
        }

        val before = try { ic.getTextBeforeCursor(128, 0) } catch (_: Exception) { null } ?: ""
        val delLen = DeleteWordHelper.computeWordDeleteLength(before)
        if (delLen > 0) {
            withBatchEdit(ic) {
                ic.deleteSurroundingText(delLen, 0)
            }
        }
        syncWordPrefixFromCursor(ic)
        updateAutoCapShiftState()
        refreshPredictions()
    }

    private fun beginSwipeDeleteSession() {
        val ic = currentInputConnection ?: return
        lastGlideCommittedWord = null
        autoInsertedSpaceBeforePunctuation = false
        swipeDeletedChunks.clear()
        swipeWordBoundaries = IntArray(0)
        swipeUseSelectionHighlight = false
        lastSwipeWordCount = 0

        val ext = try { ic.getExtractedText(ExtractedTextRequest(), 0) } catch (_: Exception) { null }
        val extText = ext?.text
        val probeBefore = try { ic.getTextBeforeCursor(8, 0) } catch (_: Exception) { null }

        // Only use absolute ExtractedText indices when startOffset == 0 to avoid multi-page offset mismatch
        if (ext != null &&
            extText != null &&
            ext.startOffset == 0 &&
            ext.selectionStart >= 0 &&
            ext.selectionStart == ext.selectionEnd
        ) {
            val end = ext.selectionEnd.coerceIn(0, extText.length)
            if (end > 0 || probeBefore.isNullOrEmpty()) {
                swipeInitialCursorEnd = end
                swipeWordBoundaries = DeleteWordHelper.computeWordBoundariesBeforeCursor(
                    extText.subSequence(0, end),
                    maxWords = 40
                )
                swipeUseSelectionHighlight = true
                return
            }
        }
    }

    private fun updateSwipeDeleteSelection(requestedWords: Int): Int {
        val ic = currentInputConnection ?: return 0
        val count = if (swipeUseSelectionHighlight) {
            if (swipeWordBoundaries.size <= 1) return 0
            val target = requestedWords.coerceIn(0, swipeWordBoundaries.lastIndex)
            isInternalInputEdit = true
            try {
                ic.setSelection(swipeWordBoundaries[target], swipeInitialCursorEnd)
            } catch (_: Exception) {
                // Ignore stale connection
            } finally {
                isInternalInputEdit = false
            }
            target
        } else {
            val clampedReq = requestedWords.coerceAtLeast(0)
            while (swipeDeletedChunks.size < clampedReq) {
                val before = try { ic.getTextBeforeCursor(128, 0) } catch (_: Exception) { null } ?: ""
                val delLen = DeleteWordHelper.computeWordDeleteLength(before)
                if (delLen <= 0) break
                val chunk = before.subSequence(before.length - delLen, before.length).toString()
                swipeDeletedChunks.add(chunk)
                withBatchEdit(ic) {
                    ic.deleteSurroundingText(delLen, 0)
                }
            }
            while (swipeDeletedChunks.size > clampedReq) {
                val restored = swipeDeletedChunks.removeAt(swipeDeletedChunks.lastIndex)
                withBatchEdit(ic) {
                    ic.commitText(restored, 1)
                }
            }
            swipeDeletedChunks.size
        }
        lastSwipeWordCount = count
        return count
    }

    private fun finishSwipeDeleteSession(commitDelete: Boolean) {
        val ic = currentInputConnection
        if (ic != null) {
            if (swipeUseSelectionHighlight) {
                if (commitDelete && lastSwipeWordCount > 0) {
                    withBatchEdit(ic) {
                        ic.commitText("", 1)
                    }
                } else if (swipeWordBoundaries.isNotEmpty()) {
                    isInternalInputEdit = true
                    try {
                        ic.setSelection(swipeInitialCursorEnd, swipeInitialCursorEnd)
                    } catch (_: Exception) {
                        // Ignore stale connection
                    } finally {
                        isInternalInputEdit = false
                    }
                }
            } else if (!commitDelete && swipeDeletedChunks.isNotEmpty()) {
                withBatchEdit(ic) {
                    while (swipeDeletedChunks.isNotEmpty()) {
                        val restored = swipeDeletedChunks.removeAt(swipeDeletedChunks.lastIndex)
                        ic.commitText(restored, 1)
                    }
                }
            }
            syncWordPrefixFromCursor(ic)
            updateAutoCapShiftState()
            refreshPredictions()
        }
        swipeUseSelectionHighlight = false
        swipeWordBoundaries = IntArray(0)
        swipeDeletedChunks.clear()
        lastSwipeWordCount = 0
    }

    private fun syncWordPrefixFromCursor(ic: InputConnection) {
        currentWordPrefix.clear()
        val before = try { ic.getTextBeforeCursor(32, 0) } catch (_: Exception) { null } ?: return
        var i = before.length
        while (i > 0 && (before[i - 1].isLetter() || before[i - 1] == '\'')) {
            i--
        }
        if (i < before.length) {
            currentWordPrefix.append(before.subSequence(i, before.length))
        }
    }

    private fun learnAndSyncWord(word: String, event: LearningEvent) {
        val clean = word.trim()
        if (clean.isEmpty()) return
        vocabEngine.onWordCommittedAsync(clean, recentContextWords.toList(), event)
        val lower = clean.lowercase()
        if (!lexicon.contains(lower) && (
                event == LearningEvent.SUGGESTION_ACCEPTED ||
                event == LearningEvent.MANUAL_CORRECTION ||
                vocabEngine.personalDictionary.contains(lower)
            )
        ) {
            lexicon.insert(lower, 850f)
        }
    }

    private fun commitWordWithSpace(word: String, event: LearningEvent) {
        val ic = currentInputConnection ?: return
        withBatchEdit(ic) {
            ic.commitText("$word ", 1)
        }

        learnAndSyncWord(word, event)
        addContextWord(word)
        currentWordPrefix.clear()

        lastCommittedWord = word
        autoInsertedSpaceBeforePunctuation = true
        wasLastCommittedFromSuggestion = (event == LearningEvent.SUGGESTION_ACCEPTED)
    }

    private fun commitCandidate(selectedWord: String) {
        val ic = currentInputConnection ?: return

        val pendingOrig = lastAutoCorrectedOriginal
        val pendingRepl = lastAutoCorrectedReplacement
        if (pendingOrig != null && pendingRepl != null && currentWordPrefix.isEmpty()) {
            val checkBefore = try { ic.getTextBeforeCursor(pendingRepl.length + 2, 0)?.toString() } catch (_: Exception) { null }
            if (checkBefore != null && checkBefore.endsWith(pendingRepl)) {
                val suffix = if (pendingRepl.endsWith(" ")) " " else ""
                withBatchEdit(ic) {
                    ic.deleteSurroundingText(pendingRepl.length, 0)
                    ic.commitText("$selectedWord$suffix", 1)
                }
                lastAutoCorrectedOriginal = null
                lastAutoCorrectedReplacement = null
                val correctedWord = pendingRepl.trimEnd('.', ',', '!', '?', ';', ':', ' ')
                if (selectedWord == pendingOrig) {
                    vocabEngine.onManualCorrectionAsync(correctedWord, selectedWord, recentContextWords.toList())
                    suppressedAutoCorrectWord = selectedWord.lowercase()
                    lexicon.insert(selectedWord.lowercase(), 850f)
                } else {
                    learnAndSyncWord(selectedWord, LearningEvent.SUGGESTION_ACCEPTED)
                }
                if (recentContextWords.isNotEmpty() && recentContextWords.last() == correctedWord) {
                    recentContextWords[recentContextWords.lastIndex] = selectedWord
                } else {
                    addContextWord(selectedWord)
                }
                lastCommittedWord = selectedWord
                wasLastCommittedFromSuggestion = true
                updateAutoCapShiftState()
                refreshPredictions()
                return
            }
        }

        val prevGlide = lastGlideCommittedWord
        if (prevGlide != null && currentWordPrefix.isEmpty()) {
            withBatchEdit(ic) {
                ic.deleteSurroundingText(prevGlide.length + 1, 0)
                ic.commitText("$selectedWord ", 1)
            }
            lastGlideCommittedWord = selectedWord
            autoInsertedSpaceBeforePunctuation = true
            vocabEngine.onManualCorrectionAsync(prevGlide, selectedWord, recentContextWords.toList())
            lexicon.insert(selectedWord.lowercase(), 850f)
            if (recentContextWords.isNotEmpty() && recentContextWords.last() == prevGlide) {
                recentContextWords[recentContextWords.lastIndex] = selectedWord
            } else {
                addContextWord(selectedWord)
            }
            lastCommittedWord = selectedWord
            wasLastCommittedFromSuggestion = true
            updateAutoCapShiftState()
            refreshPredictions()
            return
        }

        // Delete currently typed prefix if present
        if (currentWordPrefix.isNotEmpty()) {
            withBatchEdit(ic) {
                ic.deleteSurroundingText(currentWordPrefix.length, 0)
            }
            currentWordPrefix.clear()
        }

        lastAutoCorrectedOriginal = null
        lastAutoCorrectedReplacement = null
        commitWordWithSpace(selectedWord, LearningEvent.SUGGESTION_ACCEPTED)
        updateAutoCapShiftState()
        refreshPredictions()
    }

    private fun addContextWord(word: String) {
        recentContextWords.add(word)
        if (recentContextWords.size > 3) {
            recentContextWords.removeAt(0)
        }
    }

    private fun refreshPredictions() {
        if (isServiceDestroyed || !::suggestionStripView.isInitialized) return
        val prefix = currentWordPrefix.toString()
        val contextSnapshot = recentContextWords.toList()
        val generation = ++predictionGeneration
        executeSafely(predictionExecutor, "Prediction generation") {
            val words = if (prefix.isNotEmpty()) {
                vocabEngine.rankPrefixCompletions(prefix, contextSnapshot, maxCandidates = 3)
            } else {
                vocabEngine.predictNextWords(contextSnapshot, maxCandidates = 3)
            }.map { it.word }
            mainHandler.post {
                if (!isServiceDestroyed && predictionGeneration == generation && ::suggestionStripView.isInitialized) {
                    suggestionStripView.setCandidates(words)
                }
            }
        }
    }

    private fun refreshPredictionsWithFallbackPrefix(rawPrefix: String) {
        if (isServiceDestroyed || !::suggestionStripView.isInitialized) return
        val contextSnapshot = recentContextWords.toList()
        val generation = ++predictionGeneration
        executeSafely(predictionExecutor, "Post-autocorrect prediction") {
            val nextPreds = vocabEngine.predictNextWords(contextSnapshot, maxCandidates = 2)
                .map { it.word }
            val combined = (listOf(rawPrefix) + nextPreds).distinct().take(3)
            mainHandler.post {
                if (!isServiceDestroyed && predictionGeneration == generation && ::suggestionStripView.isInitialized) {
                    suggestionStripView.setCandidates(combined)
                }
            }
        }
    }

    private fun updateRecentClipsInStrip() {
        if (!::suggestionStripView.isInitialized) return
        val recents = clipboardManager.getRecentItemsForStrip()
        suggestionStripView.setRecentClipboardItems(recents)
    }

    private fun showKeyboardMain() {
        if (::aiWriterPanelView.isInitialized) {
            aiWriterPanelView.visibility = View.GONE
            aiWriterPanelView.onPanelHidden()
        }
        if (::emojiPanelView.isInitialized) emojiPanelView.visibility = View.GONE
        if (::expandedToolsPanel.isInitialized) expandedToolsPanel.visibility = View.GONE
        if (::inlineClipboardTray.isInitialized) inlineClipboardTray.visibility = View.GONE
        if (::base32PanelView.isInitialized) {
            base32PanelView.visibility = View.GONE
            base32PanelView.pauseTicker()
        }
        if (::keyboardFrame.isInitialized) keyboardFrame.visibility = View.VISIBLE
        if (::suggestionStripView.isInitialized) {
            suggestionStripView.setEmojiPanelActive(false)
            suggestionStripView.setToolsPanelActive(false)
            suggestionStripView.setBase32PanelActive(false)
            suggestionStripView.setAiWriterActive(false)
        }
    }

    private fun toggleAiWriterPanel() {
        if (!::aiWriterPanelView.isInitialized) return
        if (aiWriterPanelView.visibility == View.VISIBLE) {
            showKeyboardMain()
        } else {
            emojiPanelView.visibility = View.GONE
            expandedToolsPanel.visibility = View.GONE
            inlineClipboardTray.visibility = View.GONE
            base32PanelView.visibility = View.GONE
            base32PanelView.pauseTicker()
            // Keep physical keyboardFrame visible so user can type instructions straight into AI Writer
            keyboardFrame.visibility = View.VISIBLE
            aiWriterPanelView.visibility = View.VISIBLE
            aiWriterPanelView.onPanelOpened()
            suggestionStripView.setAiWriterActive(true)
        }
    }

    private fun toggleToolsPanel() {
        if (expandedToolsPanel.visibility == View.VISIBLE) {
            showKeyboardMain()
        } else {
            val totalClips = try { clipboardManager.storage.getAllItems().size } catch (_: Exception) { 0 }
            expandedToolsPanel.updateClipboardCount(totalClips)
            expandedToolsPanel.isHinglishEnabled = isHinglishEnabled
            expandedToolsPanel.isOneHandedEnabled = layoutPrefs.oneHandedEnabled
            expandedToolsPanel.setRawState(ramblerController.isShowingRawTranscript())
            if (::aiWriterPanelView.isInitialized) {
                aiWriterPanelView.visibility = View.GONE
                aiWriterPanelView.onPanelHidden()
            }
            emojiPanelView.visibility = View.GONE
            inlineClipboardTray.visibility = View.GONE
            base32PanelView.visibility = View.GONE
            base32PanelView.pauseTicker()
            keyboardFrame.visibility = View.GONE
            expandedToolsPanel.visibility = View.VISIBLE
            suggestionStripView.setToolsPanelActive(true)
        }
    }

    private fun toggleEmojiPanel() {
        if (emojiPanelView.visibility == View.VISIBLE) {
            showKeyboardMain()
        } else {
            if (::aiWriterPanelView.isInitialized) {
                aiWriterPanelView.visibility = View.GONE
                aiWriterPanelView.onPanelHidden()
            }
            expandedToolsPanel.visibility = View.GONE
            inlineClipboardTray.visibility = View.GONE
            base32PanelView.visibility = View.GONE
            base32PanelView.pauseTicker()
            keyboardFrame.visibility = View.GONE
            emojiPanelView.visibility = View.VISIBLE
            suggestionStripView.setEmojiPanelActive(true)
        }
    }

    private fun toggleBase32Panel() {
        if (base32PanelView.visibility == View.VISIBLE) {
            showKeyboardMain()
        } else {
            if (::aiWriterPanelView.isInitialized) {
                aiWriterPanelView.visibility = View.GONE
                aiWriterPanelView.onPanelHidden()
            }
            emojiPanelView.visibility = View.GONE
            expandedToolsPanel.visibility = View.GONE
            inlineClipboardTray.visibility = View.GONE
            keyboardFrame.visibility = View.GONE
            base32PanelView.visibility = View.VISIBLE
            base32PanelView.onPanelOpened()
            suggestionStripView.setBase32PanelActive(true)
        }
    }

    private fun toggleInlineClipboardTray() {
        if (inlineClipboardTray.visibility == View.VISIBLE) {
            showKeyboardMain()
        } else {
            if (::aiWriterPanelView.isInitialized) {
                aiWriterPanelView.visibility = View.GONE
                aiWriterPanelView.onPanelHidden()
            }
            emojiPanelView.visibility = View.GONE
            expandedToolsPanel.visibility = View.GONE
            base32PanelView.visibility = View.GONE
            base32PanelView.pauseTicker()
            suggestionStripView.setEmojiPanelActive(false)
            suggestionStripView.setToolsPanelActive(false)
            suggestionStripView.setBase32PanelActive(false)
            suggestionStripView.setAiWriterActive(false)
            inlineClipboardTray.attachClipboard(
                manager = clipboardManager,
                onSelect = { text ->
                    commitExternalText(text)
                    showKeyboardMain()
                },
                onClose = {
                    showKeyboardMain()
                }
            )
            keyboardFrame.visibility = View.GONE
            inlineClipboardTray.visibility = View.VISIBLE
        }
    }

    private enum class AiApplyMode { INSERT, REPLACE, APPEND }

    private var lastAiPulledDraft: String = ""

    private fun readAppSelectionOrDraftForAi(): Pair<String, Boolean>? {
        val ic = currentInputConnection ?: return null
        val selected = try { ic.getSelectedText(0)?.toString() } catch (_: Exception) { null }
        if (!selected.isNullOrBlank()) {
            lastAiPulledDraft = selected
            return selected to true
        }
        val before = try { ic.getTextBeforeCursor(600, 0)?.toString() } catch (_: Exception) { null }
        val line = before?.substringAfterLast('\n')?.trim().orEmpty()
        val chosen = if (line.isNotBlank()) line else before?.trim().orEmpty()
        if (chosen.isBlank()) return null
        lastAiPulledDraft = chosen
        return chosen to false
    }

    private fun applyAiWriterOutput(text: String, mode: AiApplyMode, hadSelection: Boolean) {
        val ic = currentInputConnection
        if (ic == null) {
            Toast.makeText(this, "No active text field — copy result instead", Toast.LENGTH_SHORT).show()
            return
        }
        withBatchEdit(ic) {
            when (mode) {
                AiApplyMode.INSERT -> {
                    ic.commitText(text, 1)
                }
                AiApplyMode.REPLACE -> {
                    val selectedNow = try { ic.getSelectedText(0)?.toString() } catch (_: Exception) { null }
                    if (!selectedNow.isNullOrEmpty()) {
                        ic.commitText(text, 1)
                    } else {
                        val before = try { ic.getTextBeforeCursor(lastAiPulledDraft.length + 12, 0)?.toString() } catch (_: Exception) { null }
                        if (!hadSelection && lastAiPulledDraft.isNotBlank() && before != null && before.endsWith(lastAiPulledDraft)) {
                            ic.deleteSurroundingText(lastAiPulledDraft.length, 0)
                            ic.commitText(text, 1)
                        } else {
                            ic.commitText(text, 1)
                        }
                    }
                }
                AiApplyMode.APPEND -> {
                    val selectedNow = try { ic.getSelectedText(0)?.toString() } catch (_: Exception) { null }
                    if (!selectedNow.isNullOrEmpty()) {
                        ic.commitText("$selectedNow $text", 1)
                    } else {
                        val before = try { ic.getTextBeforeCursor(4, 0)?.toString().orEmpty() } catch (_: Exception) { "" }
                        val sep = if (before.isNotEmpty() && !before.last().isWhitespace()) " " else ""
                        ic.commitText("$sep$text", 1)
                    }
                }
            }
        }
        currentWordPrefix.clear()
        autoInsertedSpaceBeforePunctuation = false
        lastGlideCommittedWord = null
        lastAutoCorrectedOriginal = null
        lastAutoCorrectedReplacement = null
        syncWordPrefixFromCursor(ic)
        updateAutoCapShiftState()
        refreshPredictions()
        showKeyboardMain()
    }

    private fun commitBase32Text(text: String) {
        val ic = currentInputConnection ?: return
        withBatchEdit(ic) {
            val selected = try { ic.getSelectedText(0)?.toString() } catch (_: Exception) { null }
            if (selected.isNullOrEmpty()) {
                ic.finishComposingText()
            }
            ic.commitText(text, 1)
        }
        currentWordPrefix.clear()
        autoInsertedSpaceBeforePunctuation = false
        lastGlideCommittedWord = null
        lastAutoCorrectedOriginal = null
        lastAutoCorrectedReplacement = null
        syncWordPrefixFromCursor(ic)
        updateAutoCapShiftState()
        refreshPredictions()
    }

    private fun readLatestClipboardText(): String? {
        val sys = getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
        val primary = try {
            sys?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
        } catch (_: Exception) {
            null
        }
        if (!primary.isNullOrBlank()) return primary
        return try { clipboardManager.storage.getAllItems().firstOrNull()?.text } catch (_: Exception) { null }
    }

    private fun readActiveAppText(): String? {
        val ic = currentInputConnection ?: return null
        val selected = try { ic.getSelectedText(0)?.toString() } catch (_: Exception) { null }
        if (!selected.isNullOrBlank()) return selected
        val before = try { ic.getTextBeforeCursor(400, 0)?.toString() } catch (_: Exception) { null }
        val line = before?.substringAfterLast('\n')?.trim().orEmpty()
        if (line.isNotBlank()) return line
        return before?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun launchActivity(activityClass: Class<*>) {
        val intent = Intent(this, activityClass).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
    }

    private fun launchSettings() {
        launchActivity(KeybordSettingsActivity::class.java)
    }

    private fun applyTheme(theme: ThemeModel) {
        this.baseTheme = theme
        val effective = if (::layoutPrefs.isInitialized) layoutPrefs.applyTo(theme) else theme
        this.activeTheme = effective
        if (::suggestionStripView.isInitialized) suggestionStripView.theme = effective
        if (::aiWriterPanelView.isInitialized) aiWriterPanelView.theme = effective
        if (::keyboardView.isInitialized) {
            keyboardView.theme = effective
            if (::layoutPrefs.isInitialized) {
                keyboardView.showNumberRow = layoutPrefs.showNumberRow
                keyboardView.showSecondarySymbols = layoutPrefs.showSecondarySymbols
                keyboardView.vibrationDurationMs = layoutPrefs.vibrationDurationMs
            }
        }
        if (::gestureOverlayView.isInitialized) gestureOverlayView.theme = effective
        if (::popPreviewView.isInitialized) popPreviewView.theme = effective
        if (::emojiPanelView.isInitialized) emojiPanelView.theme = effective
        if (::expandedToolsPanel.isInitialized) {
            expandedToolsPanel.theme = effective
            if (::layoutPrefs.isInitialized) {
                expandedToolsPanel.isOneHandedEnabled = layoutPrefs.oneHandedEnabled
            }
        }
        if (::inlineClipboardTray.isInitialized) inlineClipboardTray.theme = effective
        if (::base32PanelView.isInitialized) base32PanelView.theme = effective
    }
}
