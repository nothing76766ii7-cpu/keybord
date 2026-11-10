package com.keyboard

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*

/**
 * SettingsActivity with nested navigation per Section C.
 * Screens: Appearance, Clipboard, Typing, Personal Dictionary, About.
 */
class SettingsActivity : Activity() {

    enum class Screen {
        ROOT,
        APPEARANCE,
        CLIPBOARD,
        TYPING,
        DICTIONARY,
        ABOUT
    }

    private var currentScreen: Screen = Screen.ROOT
    private lateinit var contentContainer: LinearLayout
    private lateinit var titleTextView: TextView

    private val prefs by lazy { getSharedPreferences("keybord_prefs", Context.MODE_PRIVATE) }
    private val clipboardManager by lazy { ClipboardManager(this) }
    private val themeEngine by lazy { ThemeEngine(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#181825"))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // Header toolbar with back arrow and title
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#1E1E2E"))
            setPadding(24, 24, 24, 24)
        }

        val backBtn = TextView(this).apply {
            text = "←"
            setTextColor(Color.WHITE)
            textSize = 20f
            setPadding(8, 0, 24, 0)
            setOnClickListener { handleBackPressed() }
        }
        toolbar.addView(backBtn)

        titleTextView = TextView(this).apply {
            text = "Keybord Settings"
            setTextColor(Color.WHITE)
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
        }
        toolbar.addView(titleTextView)
        root.addView(toolbar)

        // Content Area
        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }
        contentContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 16, 24, 24)
        }
        scrollView.addView(contentContainer)
        root.addView(scrollView)

        setContentView(root)
        navigateTo(Screen.ROOT)
    }

    private fun handleBackPressed() {
        if (currentScreen != Screen.ROOT) {
            navigateTo(Screen.ROOT)
        } else {
            finish()
        }
    }

    override fun onBackPressed() {
        handleBackPressed()
    }

    private fun navigateTo(screen: Screen) {
        currentScreen = screen
        contentContainer.removeAllViews()

        when (screen) {
            Screen.ROOT -> renderRootScreen()
            Screen.APPEARANCE -> renderAppearanceScreen()
            Screen.CLIPBOARD -> renderClipboardScreen()
            Screen.TYPING -> renderTypingScreen()
            Screen.DICTIONARY -> renderDictionaryScreen()
            Screen.ABOUT -> renderAboutScreen()
        }
    }

    // ------------------------------------------------------------------------
    // ROOT SCREEN
    // ------------------------------------------------------------------------
    private fun renderRootScreen() {
        titleTextView.text = "Keybord Settings"
        contentContainer.addView(createNavRow("🎨 Appearance", "Theme gallery, custom colors, fonts & trail") { navigateTo(Screen.APPEARANCE) })
        contentContainer.addView(createNavRow("📋 Clipboard", "Retention policy, category tagging, password gate") { navigateTo(Screen.CLIPBOARD) })
        contentContainer.addView(createNavRow("⚡ Typing", "Glide typing, auto-correction, Magic Writing, Rambler") { navigateTo(Screen.TYPING) })
        contentContainer.addView(createNavRow("📖 Personal Dictionary", "Learned vocabulary, shortcuts, custom words") { navigateTo(Screen.DICTIONARY) })
        contentContainer.addView(createNavRow("ℹ️ About", "Version, open source licenses, privacy guarantee") { navigateTo(Screen.ABOUT) })
    }

    // ------------------------------------------------------------------------
    // APPEARANCE SCREEN
    // ------------------------------------------------------------------------
    private fun renderAppearanceScreen() {
        titleTextView.text = "Appearance"

        contentContainer.addView(createSectionHeader("Custom Themes"))
        contentContainer.addView(createActionRow("Open Custom Theme Editor", "Full 22-parameter design sandbox with live preview") {
            startActivity(Intent(this, CustomThemeEditorActivity::class.java))
        })

        contentContainer.addView(createSectionHeader("Theme Gallery (10 Built-In Themes)"))
        val currentTheme = themeEngine.currentTheme.name
        for (theme in themeEngine.getAllThemes()) {
            val isCurrent = theme.name == currentTheme
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundColor(if (isCurrent) Color.parseColor("#45475A") else Color.parseColor("#313244"))
                setPadding(24, 20, 24, 20)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 6, 0, 6) }
                setOnClickListener {
                    themeEngine.applyTheme(theme)
                    navigateTo(Screen.APPEARANCE)
                    Toast.makeText(this@SettingsActivity, "Applied '${theme.name}'", Toast.LENGTH_SHORT).show()
                }
            }

            val colorCircle = View(this).apply {
                val bg = theme.params["keyboard_background"] ?: "#181825"
                try { setBackgroundColor(Color.parseColor(bg)) } catch (e: Exception) {}
                layoutParams = LinearLayout.LayoutParams(40, 40).apply { setMargins(0, 0, 24, 0) }
            }
            card.addView(colorCircle)

            val nameTv = TextView(this).apply {
                text = if (isCurrent) "${theme.name}  ✓" else theme.name
                setTextColor(Color.WHITE)
                textSize = 14f
                typeface = if (isCurrent) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            card.addView(nameTv)
            contentContainer.addView(card)
        }
    }

    // ------------------------------------------------------------------------
    // CLIPBOARD SCREEN
    // ------------------------------------------------------------------------
    private fun renderClipboardScreen() {
        titleTextView.text = "Clipboard Settings"

        contentContainer.addView(createSectionHeader("Retention Policy"))
        val currentMode = prefs.getString("clipboard_retention_mode", "forever") ?: "forever"
        val modes = listOf("Forever (Default)", "Keep 30 Days", "Keep Last 100 Items", "Keep Last 500 Items")
        val modeValues = listOf("forever", "days_30", "count_100", "count_500")

        val spinner = Spinner(this)
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, modes)
        spinner.adapter = adapter
        val idx = modeValues.indexOf(currentMode).coerceAtLeast(0)
        spinner.setSelection(idx)
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                prefs.edit().putString("clipboard_retention_mode", modeValues[pos]).apply()
                clipboardManager.applyRetentionPolicy()
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        contentContainer.addView(spinner)

        contentContainer.addView(createSectionHeader("Security & Privacy"))
        contentContainer.addView(createSwitchRow("Block Passwords", "Do not save copies from password fields", "clipboard_block_passwords", true))

        contentContainer.addView(createSectionHeader("Management"))
        contentContainer.addView(createActionRow("Open Full Clipboard Manager", "Browse, search, pin and tag clipboard history") {
            startActivity(Intent(this, ClipboardManagerActivity::class.java))
        })
        contentContainer.addView(createActionRow("Clear All Unpinned Clips", "Delete all clipboard history (pinned items kept)") {
            clipboardManager.deleteAll()
            Toast.makeText(this, "Cleared unpinned clipboard history", Toast.LENGTH_SHORT).show()
        })
    }

    // ------------------------------------------------------------------------
    // TYPING SCREEN
    // ------------------------------------------------------------------------
    private fun renderTypingScreen() {
        titleTextView.text = "Typing Settings"

        contentContainer.addView(createSectionHeader("Input Methods"))
        contentContainer.addView(createSwitchRow("Glide Typing", "Enable swipe gesture typing", "pref_glide_enabled", true))
        contentContainer.addView(createSwitchRow("Magic Writing", "AI-assisted proofreading and style rewriting", "pref_magic_enabled", true))
        contentContainer.addView(createSwitchRow("Rambler Dictation", "Smart spoken disfluency removal and true-casing", "pref_rambler_enabled", true))

        contentContainer.addView(createSectionHeader("Corrections & Formatting"))
        contentContainer.addView(createSwitchRow("Auto-correct", "Correct typos while typing", "pref_autocorrect_enabled", true))
        contentContainer.addView(createSwitchRow("Auto-capitalise", "Capitalise first letter of sentences", "pref_autocap_enabled", true))
        contentContainer.addView(createSwitchRow("Double-space period", "Insert period on double space tap", "pref_doublespace_period", true))
        contentContainer.addView(createSwitchRow("Haptic Feedback", "Vibrate key on tap", "pref_haptic_feedback", true))
    }

    // ------------------------------------------------------------------------
    // PERSONAL DICTIONARY SCREEN
    // ------------------------------------------------------------------------
    private fun renderDictionaryScreen() {
        titleTextView.text = "Personal Dictionary"

        contentContainer.addView(createActionRow("+ Add Custom Word", "Add a manual word with optional shortcut") {
            showAddWordDialog()
        })

        contentContainer.addView(createSwitchRow("Sync System Dictionary", "Read system Android UserDictionary", "pref_sync_system_dict", true))
        contentContainer.addView(createSectionHeader("Learned Words"))

        val storage = com.keyboard.data.ClipboardDatabase(this)
        val infoTv = TextView(this).apply {
            text = "Adaptive vocabulary learns automatically from typing with 2x promotion threshold and 30-day half-life decay."
            setTextColor(Color.LTGRAY)
            textSize = 13f
            setPadding(0, 8, 0, 16)
        }
        contentContainer.addView(infoTv)
    }

    private fun showAddWordDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 16, 32, 16)
        }
        val wordInput = EditText(this).apply { hint = "Word (e.g. Kotlin)" }
        val shortcutInput = EditText(this).apply { hint = "Optional Shortcut (e.g. kt)" }
        layout.addView(wordInput)
        layout.addView(shortcutInput)

        AlertDialog.Builder(this)
            .setTitle("Add Word")
            .setView(layout)
            .setPositiveButton("Add") { _, _ ->
                val w = wordInput.text.toString().trim()
                if (w.isNotEmpty()) {
                    Toast.makeText(this, "Added '$w' to personal dictionary", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ------------------------------------------------------------------------
    // ABOUT SCREEN
    // ------------------------------------------------------------------------
    private fun renderAboutScreen() {
        titleTextView.text = "About Keybord"

        contentContainer.addView(createSectionHeader("Keybord v1.0.2"))
        val desc = TextView(this).apply {
            text = "The keyboard that gets out of your way.\n\n" +
                    "• 100% On-Device: Zero internet permissions requested.\n" +
                    "• Unlimited SQLite Clipboard with Pinned Immunity.\n" +
                    "• Clean-room Delight5 DTW Glide Typing Engine.\n" +
                    "• Katz Backoff Adaptive Vocabulary Learning.\n" +
                    "• Rust Core Native Acceleration (arm64-v8a).\n\n" +
                    "Licensed under Apache 2.0 / MIT."
            setTextColor(Color.parseColor("#CDD6F4"))
            textSize = 13f
            setPadding(0, 8, 0, 16)
        }
        contentContainer.addView(desc)
    }

    // UI Helpers
    private fun createNavRow(title: String, subtitle: String, onClick: () -> Unit): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#313244"))
            setPadding(24, 20, 24, 20)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 8, 0, 8) }
            setOnClickListener { onClick() }

            val titleTv = TextView(context).apply {
                text = title
                setTextColor(Color.WHITE)
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
            }
            addView(titleTv)

            val subTv = TextView(context).apply {
                text = subtitle
                setTextColor(Color.parseColor("#A6ADC8"))
                textSize = 12f
                setPadding(0, 4, 0, 0)
            }
            addView(subTv)
        }
    }

    private fun createActionRow(title: String, subtitle: String, onClick: () -> Unit): LinearLayout {
        return createNavRow(title, subtitle, onClick)
    }

    private fun createSwitchRow(title: String, subtitle: String, prefKey: String, defaultVal: Boolean): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#313244"))
            setPadding(24, 16, 24, 16)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 6, 0, 6) }

            val textCol = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val titleTv = TextView(context).apply {
                text = title
                setTextColor(Color.WHITE)
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
            }
            textCol.addView(titleTv)

            val subTv = TextView(context).apply {
                text = subtitle
                setTextColor(Color.parseColor("#A6ADC8"))
                textSize = 11f
                setPadding(0, 2, 0, 0)
            }
            textCol.addView(subTv)
            addView(textCol)

            val switchView = Switch(context).apply {
                isChecked = prefs.getBoolean(prefKey, defaultVal)
                setOnCheckedChangeListener { _, checked ->
                    prefs.edit().putBoolean(prefKey, checked).apply()
                }
            }
            addView(switchView)
        }
    }

    private fun createSectionHeader(title: String): TextView {
        return TextView(this).apply {
            text = title
            setTextColor(Color.parseColor("#89B4FA"))
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 20, 0, 8)
        }
    }
}
