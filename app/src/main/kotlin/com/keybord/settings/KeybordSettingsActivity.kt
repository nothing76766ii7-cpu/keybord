package com.keybord.settings

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.keybord.clipboard.ClipboardActivity
import com.keybord.theme.ThemeGalleryActivity
import com.keybord.ui.UiKit

/**
 * Main Settings Dashboard for Keybord v1.1.7.
 */
class KeybordSettingsActivity : AppCompatActivity() {

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
                title = "Keybord Settings",
                subtitle = "On-device, smooth & customizable",
                showBack = false
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
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, (16 * dp).toInt())
        }

        // Hero Privacy & Version Card
        val heroCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = UiKit.roundedDrawable(this@KeybordSettingsActivity, UiKit.SURFACE_CARD, 18f, UiKit.BORDER_SUBTLE, 1f)
            setPadding((16 * dp).toInt(), (16 * dp).toInt(), (16 * dp).toInt(), (16 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (14 * dp).toInt() }
        }
        val heroTop = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val badgeIcon = TextView(this).apply {
            text = "⌨"
            textSize = 22f
            gravity = Gravity.CENTER
            background = UiKit.roundedDrawable(this@KeybordSettingsActivity, UiKit.ACCENT_SOFT, 14f, UiKit.ACCENT_PRIMARY, 1f)
            val sz = (44 * dp).toInt()
            layoutParams = LinearLayout.LayoutParams(sz, sz).apply { marginEnd = (12 * dp).toInt() }
        }
        val heroTextCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        heroTextCol.addView(
            TextView(this).apply {
                text = "Keybord v1.1.7"
                textSize = 18f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(UiKit.TEXT_PRIMARY)
            }
        )
        heroTextCol.addView(
            TextView(this).apply {
                text = "English + Hinglish • Glide • Emoji • Voice Rambler"
                textSize = 12f
                setTextColor(UiKit.TEXT_SECONDARY)
                setPadding(0, (2 * dp).toInt(), 0, 0)
            }
        )
        val privacyPill = TextView(this).apply {
            text = "🔒 100% Offline"
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(UiKit.SUCCESS_FG)
            background = UiKit.roundedDrawable(this@KeybordSettingsActivity, UiKit.SUCCESS_BG, 10f)
            setPadding((9 * dp).toInt(), (4 * dp).toInt(), (9 * dp).toInt(), (4 * dp).toInt())
        }
        heroTop.addView(badgeIcon)
        heroTop.addView(heroTextCol)
        heroTop.addView(privacyPill)
        heroCard.addView(heroTop)
        list.addView(heroCard)

        // Customize Section
        list.addView(UiKit.createSectionLabel(this, "Look & Layout"))
        list.addView(
            buildMenuRow(
                icon = "📐",
                iconBg = "#1F2D4D",
                title = "Height & Buttons",
                subtitle = "Live interactive preview, key size, corners, gaps & number row"
            ) {
                startActivity(Intent(this, KeyboardLayoutCustomizeActivity::class.java))
            }
        )
        list.addView(
            buildMenuRow(
                icon = "🎨",
                iconBg = "#2D1E3E",
                title = "Appearance & Themes",
                subtitle = "Theme gallery, animated key effects, custom colors & glow"
            ) {
                startActivity(Intent(this, ThemeGalleryActivity::class.java))
            }
        )

        // Input & Intelligence Section
        list.addView(UiKit.createSectionLabel(this, "Input & Intelligence"))
        list.addView(
            buildMenuRow(
                icon = "⚡",
                iconBg = "#1A2E2B",
                title = "Typing, Glide & Hinglish",
                subtitle = "Hinglish dictionary, auto-correct, space cursor & learning"
            ) {
                startActivity(Intent(this, TypingSettingsActivity::class.java))
            }
        )
        list.addView(
            buildMenuRow(
                icon = "📖",
                iconBg = "#2C241D",
                title = "Personal Dictionary",
                subtitle = "Custom words, text expansion shortcuts & system sync"
            ) {
                startActivity(Intent(this, PersonalDictionaryActivity::class.java))
            }
        )

        // Clipboard Section
        list.addView(UiKit.createSectionLabel(this, "Clipboard"))
        list.addView(
            buildMenuRow(
                icon = "📋",
                iconBg = "#1B2A38",
                title = "Clipboard History Manager",
                subtitle = "Search, filter, pin & bulk-manage copied items"
            ) {
                startActivity(Intent(this, ClipboardActivity::class.java))
            }
        )
        list.addView(
            buildMenuRow(
                icon = "🗂",
                iconBg = "#222436",
                title = "Clipboard Rules & Storage",
                subtitle = "Auto-expiry retention policy, max capacity & password safety"
            ) {
                startActivity(Intent(this, ClipboardSettingsActivity::class.java))
            }
        )

        // Info Section
        list.addView(UiKit.createSectionLabel(this, "Application"))
        list.addView(
            buildMenuRow(
                icon = "ℹ",
                iconBg = "#1E2230",
                title = "About Keybord",
                subtitle = "Version 1.1.7 • Privacy guarantee & engine overview"
            ) {
                startActivity(Intent(this, AboutActivity::class.java))
            }
        )

        scroll.addView(list)
        root.addView(scroll)
        setContentView(root)
    }

    private fun buildMenuRow(
        icon: String,
        iconBg: String,
        title: String,
        subtitle: String,
        onClick: () -> Unit
    ): LinearLayout {
        val dp = resources.displayMetrics.density
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = UiKit.roundedDrawable(this@KeybordSettingsActivity, UiKit.SURFACE_CARD, 16f, UiKit.BORDER_SUBTLE, 1f)
            setPadding((14 * dp).toInt(), (14 * dp).toInt(), (14 * dp).toInt(), (14 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = (9 * dp).toInt()
            }
            setOnClickListener { onClick() }
        }

        val tvIcon = TextView(this).apply {
            text = icon
            textSize = 19f
            gravity = Gravity.CENTER
            background = UiKit.roundedDrawable(this@KeybordSettingsActivity, Color.parseColor(iconBg), 12f)
            val size = (42 * dp).toInt()
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                marginEnd = (14 * dp).toInt()
            }
        }

        val textContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val tvTitle = TextView(this).apply {
            text = title
            textSize = 15.5f
            setTextColor(UiKit.TEXT_PRIMARY)
            typeface = Typeface.DEFAULT_BOLD
        }

        val tvSub = TextView(this).apply {
            text = subtitle
            textSize = 12.5f
            setTextColor(UiKit.TEXT_SECONDARY)
            setPadding(0, (3 * dp).toInt(), 0, 0)
        }

        textContainer.addView(tvTitle)
        textContainer.addView(tvSub)

        val chevron = TextView(this).apply {
            text = "›"
            textSize = 22f
            setTextColor(UiKit.TEXT_MUTED)
            setPadding((8 * dp).toInt(), 0, (4 * dp).toInt(), 0)
        }

        row.addView(tvIcon)
        row.addView(textContainer)
        row.addView(chevron)
        return row
    }
}
