package com.keybord.settings

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.keybord.theme.CustomThemeEditorActivity
import com.keybord.theme.ThemeGalleryActivity
import com.keybord.theme.applySystemBarPadding

/**
 * Root Settings Activity strictly conforming to PRD Section 4.2:
 * - Appearance
 * - Clipboard
 * - Typing
 * - Personal Dictionary
 * - About
 */
class KeybordSettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val dp = resources.displayMetrics.density
        val padH = (16 * dp).toInt()
        val padT = (20 * dp).toInt()
        val padB = (16 * dp).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#12121A"))
            setPadding(padH, padT, padH, padB)
        }
        root.applySystemBarPadding(padH, padT, padH, padB)

        // Title
        val title = TextView(this).apply {
            text = "Keybord Settings"
            textSize = 24f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, (20 * dp).toInt())
        }
        root.addView(title)

        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        val menuList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        // 1. Appearance
        addMenuItem(
            menuList,
            icon = "🎨",
            title = "Appearance",
            subtitle = "Theme gallery, 20+ visual controls, import & export",
            onClick = {
                startActivity(Intent(this, ThemeGalleryActivity::class.java))
            }
        )

        // 2. Clipboard
        addMenuItem(
            menuList,
            icon = "📋",
            title = "Clipboard",
            subtitle = "Retention policies, categories, sensitive content rules",
            onClick = {
                startActivity(Intent(this, ClipboardSettingsActivity::class.java))
            }
        )

        // 3. Typing
        addMenuItem(
            menuList,
            icon = "⚡",
            title = "Typing",
            subtitle = "Glide typing, auto-correct, auto-capitalise, period shortcut",
            onClick = {
                startActivity(Intent(this, TypingSettingsActivity::class.java))
            }
        )

        // 4. Personal Dictionary
        addMenuItem(
            menuList,
            icon = "📖",
            title = "Personal Dictionary",
            subtitle = "Learned vocabulary, custom shortcuts, system dictionary sync",
            onClick = {
                startActivity(Intent(this, PersonalDictionaryActivity::class.java))
            }
        )

        // 5. About
        addMenuItem(
            menuList,
            icon = "ℹ️",
            title = "About Keybord",
            subtitle = "Version 1.0 • Zero internet permissions • Local AES encryption",
            onClick = {
                startActivity(Intent(this, AboutActivity::class.java))
            }
        )

        scroll.addView(menuList)
        root.addView(scroll)
        setContentView(root)
    }

    private fun addMenuItem(
        parent: LinearLayout,
        icon: String,
        title: String,
        subtitle: String,
        onClick: () -> Unit
    ) {
        val dp = resources.displayMetrics.density
        val item = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#1B1B26"))
            setPadding((16 * dp).toInt(), (16 * dp).toInt(), (16 * dp).toInt(), (16 * dp).toInt())
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = (12 * dp).toInt()
            }
            layoutParams = lp
            setOnClickListener { onClick() }
        }

        val tvIcon = TextView(this).apply {
            text = icon
            textSize = 24f
            setPadding(0, 0, (14 * dp).toInt(), 0)
        }

        val textCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val tvTitle = TextView(this).apply {
            text = title
            textSize = 16f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }

        val tvSub = TextView(this).apply {
            text = subtitle
            textSize = 13f
            setTextColor(Color.parseColor("#8E92A2"))
            setPadding(0, (2 * dp).toInt(), 0, 0)
        }

        textCol.addView(tvTitle)
        textCol.addView(tvSub)

        val arrow = TextView(this).apply {
            text = "›"
            textSize = 20f
            setTextColor(Color.parseColor("#666688"))
        }

        item.addView(tvIcon)
        item.addView(textCol)
        item.addView(arrow)
        parent.addView(item)
    }
}
