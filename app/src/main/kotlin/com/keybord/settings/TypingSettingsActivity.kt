package com.keybord.settings

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Typing settings screen conforming to PRD Section 4.2:
 * - Glide Typing (on/off)
 * - Auto-correct (on/off)
 * - Auto-capitalise (on/off)
 * - Double-space period (on/off)
 */
class TypingSettingsActivity : AppCompatActivity() {

    private val prefs by lazy { getSharedPreferences("keybord_typing_prefs", Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val dp = resources.displayMetrics.density
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#12121A"))
            setPadding((16 * dp).toInt(), (16 * dp).toInt(), (16 * dp).toInt(), (16 * dp).toInt())
        }

        val title = TextView(this).apply {
            text = "Typing Settings"
            textSize = 22f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, (16 * dp).toInt())
        }
        root.addView(title)

        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        addSwitchOption(
            content,
            key = "glide_typing",
            default = true,
            title = "Glide Typing",
            subtitle = "Swipe through letters to type words with real-time glowing trajectory trail"
        )

        addSwitchOption(
            content,
            key = "auto_correct",
            default = true,
            title = "Auto-correct",
            subtitle = "Automatically corrects misspelled words upon pressing spacebar"
        )

        addSwitchOption(
            content,
            key = "auto_capitalise",
            default = true,
            title = "Auto-capitalise",
            subtitle = "Capitalise the first letter of each sentence automatically"
        )

        addSwitchOption(
            content,
            key = "double_space_period",
            default = true,
            title = "Double-space Period",
            subtitle = "Double-tapping the spacebar inserts a period followed by a space"
        )

        scroll.addView(content)
        root.addView(scroll)
        setContentView(root)
    }

    private fun addSwitchOption(
        parent: LinearLayout,
        key: String,
        default: Boolean,
        title: String,
        subtitle: String
    ) {
        val dp = resources.displayMetrics.density
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1B1B26"))
            setPadding((16 * dp).toInt(), (14 * dp).toInt(), (16 * dp).toInt(), (14 * dp).toInt())
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = (12 * dp).toInt()
            }
            layoutParams = lp
        }

        val sw = Switch(this).apply {
            text = title
            setTextColor(Color.WHITE)
            textSize = 16f
            isChecked = prefs.getBoolean(key, default)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setOnCheckedChangeListener { _, isChecked ->
                prefs.edit().putBoolean(key, isChecked).apply()
            }
        }

        val sub = TextView(this).apply {
            text = subtitle
            setTextColor(Color.parseColor("#8E92A2"))
            textSize = 13f
            setPadding(0, (4 * dp).toInt(), 0, 0)
        }

        container.addView(sw)
        container.addView(sub)
        parent.addView(container)
    }
}
