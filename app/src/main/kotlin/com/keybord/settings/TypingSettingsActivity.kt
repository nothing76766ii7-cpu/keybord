package com.keybord.settings

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.keybord.theme.KeyboardLayoutPrefs
import com.keybord.ui.UiKit

/**
 * Typing behavior, Bilingual Hinglish engine, & Layout quick toggles.
 */
class TypingSettingsActivity : AppCompatActivity() {

    private val prefs by lazy { getSharedPreferences("keybord_typing_prefs", Context.MODE_PRIVATE) }
    private val layoutPrefs by lazy { KeyboardLayoutPrefs(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val dp = resources.displayMetrics.density
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(UiKit.BG_CANVAS)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        UiKit.applyEdgeToEdgePadding(root, leftDp = 16f, topDp = 14f, rightDp = 16f, bottomDp = 16f)

        root.addView(
            UiKit.createHeaderBar(
                this,
                title = "Typing & Hinglish",
                subtitle = "Glide typing, auto-correct, bilingual vocab & cursor controls",
                showBack = true
            )
        )

        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            isVerticalScrollBarEnabled = false
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, (16 * dp).toInt())
        }

        // Bilingual & Prediction Section
        content.addView(UiKit.createSectionLabel(this, "Languages & Learning"))
        content.addView(
            UiKit.createSwitchCard(
                this,
                title = "Hinglish (English + Hindi Roman)",
                subtitle = "Enable Hinglish suggestions, auto-correct & glide swiping (e.g. namaste, bhai, kya, achha)",
                checked = prefs.getBoolean("hinglish_typing", true)
            ) { checked ->
                prefs.edit().putBoolean("hinglish_typing", checked).apply()
            }
        )

        // Smart Typing Section
        content.addView(UiKit.createSectionLabel(this, "Smart Input & Corrections"))
        content.addView(
            UiKit.createSwitchCard(
                this,
                title = "Glide Typing (Swipe)",
                subtitle = "Slide smoothly across keys to type complete English & Hinglish words",
                checked = prefs.getBoolean("glide_typing", true)
            ) { checked ->
                prefs.edit().putBoolean("glide_typing", checked).apply()
            }
        )
        content.addView(
            UiKit.createSwitchCard(
                this,
                title = "Auto-Correction",
                subtitle = "Fix common typos automatically on space/punctuation (tap ⌫ immediately to revert)",
                checked = prefs.getBoolean("auto_correct", true)
            ) { checked ->
                prefs.edit().putBoolean("auto_correct", checked).apply()
            }
        )
        content.addView(
            UiKit.createSwitchCard(
                this,
                title = "Auto-Capitalisation",
                subtitle = "Capitalise the first letter of sentences and standalone 'I'",
                checked = prefs.getBoolean("auto_capitalise", true)
            ) { checked ->
                prefs.edit().putBoolean("auto_capitalise", checked).apply()
            }
        )
        content.addView(
            UiKit.createSwitchCard(
                this,
                title = "Double-Space Period",
                subtitle = "Double-tapping the Space bar inserts '. ' quickly",
                checked = prefs.getBoolean("double_space_period", true)
            ) { checked ->
                prefs.edit().putBoolean("double_space_period", checked).apply()
            }
        )

        // Keys & Extra Controls Section
        content.addView(UiKit.createSectionLabel(this, "Keys & Extra Controls"))
        content.addView(
            UiKit.createSwitchCard(
                this,
                title = "Dedicated Number Row",
                subtitle = "Show 1–0 row permanently above QWERTY letters",
                checked = layoutPrefs.showNumberRow
            ) { checked ->
                layoutPrefs.showNumberRow = checked
            }
        )
        content.addView(
            UiKit.createSwitchCard(
                this,
                title = "Secondary Symbol Hints",
                subtitle = "Show corner hints & 360ms long-press alternate characters",
                checked = layoutPrefs.showSecondarySymbols
            ) { checked ->
                layoutPrefs.showSecondarySymbols = checked
            }
        )

        // Interactive Gestures Summary + Height Studio Link
        val tipCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = UiKit.roundedDrawable(this@TypingSettingsActivity, UiKit.SURFACE_CARD, 16f, UiKit.BORDER_SUBTLE, 1f)
            setPadding((16 * dp).toInt(), (14 * dp).toInt(), (16 * dp).toInt(), (14 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (6 * dp).toInt()
                bottomMargin = (12 * dp).toInt()
            }
        }
        tipCard.addView(
            TextView(this).apply {
                text = "Built-in Gboard-Style Gestures"
                textSize = 14.5f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(UiKit.TEXT_PRIMARY)
            }
        )
        tipCard.addView(
            TextView(this).apply {
                text = "• Space Cursor Control: Slide horizontally on the Space bar to glide the cursor.\n" +
                    "• Swipe-to-Delete: Slide left from ⌫ to highlight & delete whole words cleanly.\n" +
                    "• 2-Thumb Rollover: Press next key before lifting first thumb — zero dropped letters.\n" +
                    "• Adaptive Learning: Words you type twice promote automatically into Glide & Suggestions."
                textSize = 12.5f
                setTextColor(UiKit.TEXT_SECONDARY)
                setLineSpacing(3f * dp, 1f)
                setPadding(0, (6 * dp).toInt(), 0, 0)
            }
        )
        content.addView(tipCard)

        val studioCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = UiKit.roundedDrawable(this@TypingSettingsActivity, UiKit.ACCENT_SOFT, 16f, UiKit.ACCENT_PRIMARY, 1f)
            setPadding((16 * dp).toInt(), (14 * dp).toInt(), (16 * dp).toInt(), (14 * dp).toInt())
            setOnClickListener {
                startActivity(Intent(this@TypingSettingsActivity, KeyboardLayoutCustomizeActivity::class.java))
            }
        }
        studioCard.addView(
            TextView(this).apply {
                text = "📐 Open Height & Buttons Live Preview Studio →"
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(UiKit.TEXT_PRIMARY)
            }
        )
        content.addView(studioCard)

        scroll.addView(content)
        root.addView(scroll)
        setContentView(root)
    }
}
