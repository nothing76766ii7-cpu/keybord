package com.keybord.settings

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.keybord.clipboard.ClipboardActivity
import com.keybord.theme.KeyboardLayoutPrefs
import com.keybord.theme.ThemeGalleryActivity
import com.keybord.theme.ThemePreviewView
import com.keybord.theme.ThemeRepository
import com.keybord.ui.UiKit

/**
 * Stitch Screen 04: Keybord Settings Hub
 * Features Active Status pill, live Mini Keyboard Preview + Test/Studio CTAs,
 * grouped 8-category engine list with rounded icon badges, and Zero Telemetry footer.
 */
class KeybordSettingsActivity : AppCompatActivity() {

    private lateinit var themeRepo: ThemeRepository
    private lateinit var layoutPrefs: KeyboardLayoutPrefs
    private lateinit var miniPreview: ThemePreviewView
    private lateinit var activeThemeNameTv: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        themeRepo = ThemeRepository(this)
        layoutPrefs = KeyboardLayoutPrefs(this)

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

        val activeBadge = UiKit.createBadgePill(
            this,
            "● Active • Offline",
            UiKit.SUCCESS_BG,
            UiKit.SUCCESS_FG,
            UiKit.SUCCESS_BORDER
        )
        root.addView(
            UiKit.createHeaderBar(
                this,
                title = "Keybord Settings",
                subtitle = null,
                showBack = false,
                trailingView = activeBadge
            )
        )

        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            isVerticalScrollBarEnabled = false
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, (14 * dp).toInt())
        }

        // 1. Hero Live Preview Card (Stitch 04 top card)
        val previewCard = UiKit.createCardContainer(this, paddingDp = 14f, radiusDp = 20f, bottomMarginDp = 14f)
        val previewSurface = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = UiKit.roundedDrawable(this@KeybordSettingsActivity, UiKit.SURFACE_INPUT, 14f, UiKit.BORDER_SUBTLE, 1f)
            setPadding((6 * dp).toInt(), (6 * dp).toInt(), (6 * dp).toInt(), (6 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = (12 * dp).toInt()
            }
        }
        miniPreview = ThemePreviewView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (148 * dp).toInt())
        }
        previewSurface.addView(miniPreview)
        previewCard.addView(previewSurface)

        val infoRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((2 * dp).toInt(), 0, (2 * dp).toInt(), (12 * dp).toInt())
        }
        activeThemeNameTv = TextView(this).apply {
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(UiKit.TEXT_PRIMARY)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val verTag = TextView(this).apply {
            text = "Obsidian v1.1.9"
            textSize = 11.5f
            typeface = Typeface.MONOSPACE
            setTextColor(UiKit.TEXT_SECONDARY)
        }
        infoRow.addView(activeThemeNameTv)
        infoRow.addView(verTag)
        previewCard.addView(infoRow)

        val ctaRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val testBtn = UiKit.createPillButton(this, "⌨  Test Keyboard", filled = false) {
            showTestKeyboardDialog()
        }.apply {
            setPadding(0, (11 * dp).toInt(), 0, (11 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = (8 * dp).toInt()
            }
        }
        val studioBtn = UiKit.createPillButton(this, "🎨  Theme Studio", filled = true) {
            startActivity(Intent(this, ThemeGalleryActivity::class.java))
        }.apply {
            setPadding(0, (11 * dp).toInt(), 0, (11 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        ctaRow.addView(testBtn)
        ctaRow.addView(studioBtn)
        previewCard.addView(ctaRow)
        content.addView(previewCard)

        // 2. Grouped "Preferences & Engine" Card (Stitch 04 list)
        content.addView(
            TextView(this).apply {
                text = "Preferences & Engine"
                textSize = 15.5f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(UiKit.TEXT_PRIMARY)
                setPadding((2 * dp).toInt(), (2 * dp).toInt(), 0, (10 * dp).toInt())
            }
        )

        val menuCard = UiKit.createCardContainer(this, paddingDp = 12f, radiusDp = 18f, bottomMarginDp = 10f)
        menuCard.addView(
            buildMenuRow("🎨", "#1F2D4D", "Appearance", "Theme gallery, accents & animated effects") {
                startActivity(Intent(this, ThemeGalleryActivity::class.java))
            }
        )
        menuCard.addView(UiKit.createHairlineDivider(this))
        menuCard.addView(
            buildMenuRow("〰", "#252046", "Typing & Hinglish", "Glide typing, auto-correct, bilingual & feedback") {
                startActivity(Intent(this, TypingSettingsActivity::class.java))
            }
        )
        menuCard.addView(UiKit.createHairlineDivider(this))
        menuCard.addView(
            buildMenuRow("📖", "#152B23", "Personal Dictionary", "Learned words, text shortcuts & system sync") {
                startActivity(Intent(this, PersonalDictionaryActivity::class.java))
            }
        )
        menuCard.addView(UiKit.createHairlineDivider(this))
        menuCard.addView(
            buildMenuRow("📋", "#2E2614", "Clipboard Vault", "Encrypted clips, pinned cards & search") {
                startActivity(Intent(this, ClipboardActivity::class.java))
            }
        )
        menuCard.addView(UiKit.createHairlineDivider(this))
        menuCard.addView(
            buildMenuRow("✨", "#281D46", "Writing Tools", "Rambler voice cleanup & Magic Writing tones", pill = "⚡ Local AI") {
                startActivity(Intent(this, WritingToolsActivity::class.java))
            }
        )
        menuCard.addView(UiKit.createHairlineDivider(this))
        menuCard.addView(
            buildMenuRow("📐", "#142B34", "Keyboard Layout", "Live height slider, button radius, gaps & number row") {
                startActivity(Intent(this, KeyboardLayoutCustomizeActivity::class.java))
            }
        )
        menuCard.addView(UiKit.createHairlineDivider(this))
        menuCard.addView(
            buildMenuRow("🛡", "#1D2434", "Clipboard Rules & Security", "Retention policy, AES-256 vault & password safety") {
                startActivity(Intent(this, ClipboardSettingsActivity::class.java))
            }
        )
        menuCard.addView(UiKit.createHairlineDivider(this))
        menuCard.addView(
            buildMenuRow("ℹ", "#1E2230", "About Keybord", "Version 1.1.9 • Zero-internet privacy & engine info") {
                startActivity(Intent(this, AboutActivity::class.java))
            }
        )
        content.addView(menuCard)

        // 3. Zero Telemetry Footer
        content.addView(
            UiKit.createPrivacyFooter(
                this,
                "Zero Telemetry Active",
                "Sandbox Isolation 100% • No internet permission declared"
            )
        )

        scroll.addView(content)
        root.addView(scroll)
        setContentView(root)
        refreshPreviewCard()
    }

    override fun onResume() {
        super.onResume()
        if (::miniPreview.isInitialized) {
            refreshPreviewCard()
        }
    }

    private fun refreshPreviewCard() {
        val raw = themeRepo.getActiveTheme()
        val effective = layoutPrefs.applyTo(raw)
        miniPreview.showNumberRow = layoutPrefs.showNumberRow
        miniPreview.theme = effective
        activeThemeNameTv.text = "Active Theme: ${raw.name}"
    }

    private fun showTestKeyboardDialog() {
        val dp = resources.displayMetrics.density
        val input = EditText(this).apply {
            hint = "Tap here to test Keybord (English, Hinglish, Emoji, Space cursor)..."
            setPadding((14 * dp).toInt(), (14 * dp).toInt(), (14 * dp).toInt(), (14 * dp).toInt())
        }
        AlertDialog.Builder(this)
            .setTitle("Test Keybord Live")
            .setView(input)
            .setPositiveButton("Done", null)
            .show()
        input.requestFocus()
    }

    private fun buildMenuRow(
        icon: String,
        iconBg: String,
        title: String,
        subtitle: String,
        pill: String? = null,
        onClick: () -> Unit
    ): LinearLayout {
        val dp = resources.displayMetrics.density
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((4 * dp).toInt(), (10 * dp).toInt(), (4 * dp).toInt(), (10 * dp).toInt())
            setOnClickListener { onClick() }
        }

        row.addView(
            TextView(this).apply {
                text = icon
                textSize = 18f
                gravity = Gravity.CENTER
                background = UiKit.roundedDrawable(this@KeybordSettingsActivity, Color.parseColor(iconBg), 12f)
                val sz = (40 * dp).toInt()
                layoutParams = LinearLayout.LayoutParams(sz, sz).apply { marginEnd = (12 * dp).toInt() }
            }
        )

        val textContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val titleLine = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        titleLine.addView(
            TextView(this).apply {
                text = title
                textSize = 15.5f
                setTextColor(UiKit.TEXT_PRIMARY)
                typeface = Typeface.DEFAULT_BOLD
            }
        )
        if (!pill.isNullOrBlank()) {
            titleLine.addView(
                UiKit.createBadgePill(
                    this,
                    pill,
                    UiKit.ACCENT_AI_SOFT,
                    Color.parseColor("#C4B5FD"),
                    Color.argb(90, 124, 92, 255)
                ).apply {
                    textSize = 10.5f
                    setPadding((8 * dp).toInt(), (2 * dp).toInt(), (8 * dp).toInt(), (2 * dp).toInt())
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        marginStart = (8 * dp).toInt()
                    }
                }
            )
        }
        textContainer.addView(titleLine)
        textContainer.addView(
            TextView(this).apply {
                text = subtitle
                textSize = 12f
                setTextColor(UiKit.TEXT_SECONDARY)
                setSingleLine(true)
                setPadding(0, (2 * dp).toInt(), 0, 0)
            }
        )

        row.addView(textContainer)
        row.addView(
            TextView(this).apply {
                text = "›"
                textSize = 21f
                setTextColor(UiKit.TEXT_MUTED)
                setPadding((8 * dp).toInt(), 0, (4 * dp).toInt(), 0)
            }
        )
        return row
    }
}
