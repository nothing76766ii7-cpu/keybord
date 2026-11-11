package com.keybord.onboarding

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.keybord.settings.KeyboardLayoutCustomizeActivity
import com.keybord.settings.KeybordSettingsActivity
import com.keybord.theme.BuiltInThemes
import com.keybord.theme.KeyboardLayoutPrefs
import com.keybord.theme.ThemePreviewView
import com.keybord.theme.ThemeRepository
import com.keybord.ui.UiKit

/**
 * First Launch Onboarding Flow:
 * - Screen 1: Privacy & IME activation deep link
 * - Screen 2: Theme & layout quick setup
 * - Screen 3: Ready — live test input & Settings shortcut
 */
class OnboardingActivity : AppCompatActivity() {

    private lateinit var contentFrame: FrameLayout
    private lateinit var prevBtn: TextView
    private lateinit var nextBtn: TextView
    private lateinit var stepIndicators: LinearLayout

    private var currentStep = 0
    private val totalSteps = 3
    private lateinit var themeRepo: ThemeRepository
    private lateinit var layoutPrefs: KeyboardLayoutPrefs

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
        UiKit.applyEdgeToEdgePadding(root, leftDp = 18f, topDp = 16f, rightDp = 18f, bottomDp = 18f)

        // Top Brand Header
        val brandHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, (14 * dp).toInt())
        }
        val logoBadge = TextView(this).apply {
            text = "⌨ Keybord"
            textSize = 22f
            setTextColor(UiKit.TEXT_PRIMARY)
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val versionPill = UiKit.createBadgePill(
            this,
            "v1.2.1",
            UiKit.SURFACE_INPUT,
            UiKit.ACCENT_PRIMARY,
            UiKit.ACCENT_PRIMARY
        ).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = (10 * dp).toInt()
            }
        }
        val skipBtn = TextView(this).apply {
            text = "Settings ↗"
            setTextColor(UiKit.TEXT_SECONDARY)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            background = UiKit.skeuoButtonDrawable(this@OnboardingActivity, UiKit.SURFACE_ELEVATED, 10f, UiKit.BORDER_SUBTLE)
            setPadding((10 * dp).toInt(), (6 * dp).toInt(), (10 * dp).toInt(), (6 * dp).toInt())
            setOnClickListener { finishOnboarding() }
        }
        brandHeader.addView(logoBadge)
        brandHeader.addView(versionPill)
        brandHeader.addView(skipBtn)
        root.addView(brandHeader)

        // Content Area
        contentFrame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }
        root.addView(contentFrame)

        // Bottom Navigation Bar
        val bottomBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, (14 * dp).toInt(), 0, 0)
        }

        prevBtn = UiKit.createPillButton(this, "Back", filled = false) {
            if (currentStep > 0) {
                currentStep--
                showStep(currentStep)
            }
        }.apply {
            setPadding((18 * dp).toInt(), (11 * dp).toInt(), (18 * dp).toInt(), (11 * dp).toInt())
        }

        stepIndicators = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        nextBtn = UiKit.createPillButton(this, "Next →", filled = true) {
            if (currentStep < totalSteps - 1) {
                currentStep++
                showStep(currentStep)
            } else {
                finishOnboarding()
            }
        }.apply {
            setPadding((20 * dp).toInt(), (11 * dp).toInt(), (20 * dp).toInt(), (11 * dp).toInt())
        }

        bottomBar.addView(prevBtn)
        bottomBar.addView(stepIndicators)
        bottomBar.addView(nextBtn)
        root.addView(bottomBar)

        setContentView(root)
        showStep(0)
    }

    private fun setupStepIndicators() {
        val dp = resources.displayMetrics.density
        stepIndicators.removeAllViews()
        for (i in 0 until totalSteps) {
            val active = (i == currentStep)
            val dot = View(this).apply {
                val w = if (active) (22 * dp).toInt() else (8 * dp).toInt()
                val h = (8 * dp).toInt()
                layoutParams = LinearLayout.LayoutParams(w, h).apply {
                    setMargins((4 * dp).toInt(), 0, (4 * dp).toInt(), 0)
                }
                background = UiKit.roundedDrawable(
                    this@OnboardingActivity,
                    if (active) UiKit.ACCENT_PRIMARY else UiKit.BORDER_SUBTLE,
                    4f
                )
            }
            stepIndicators.addView(dot)
        }
    }

    private fun showStep(step: Int) {
        setupStepIndicators()
        contentFrame.removeAllViews()
        prevBtn.visibility = if (step == 0) View.INVISIBLE else View.VISIBLE
        nextBtn.text = if (step == totalSteps - 1) "Get Started" else "Next →"

        when (step) {
            0 -> renderStep1ClipboardExplainer()
            1 -> renderStep2ThemePicker()
            2 -> renderStep3Done()
        }
    }

    private fun renderStep1ClipboardExplainer() {
        val dp = resources.displayMetrics.density
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }

        val infoCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = UiKit.roundedDrawable(this@OnboardingActivity, UiKit.SURFACE_CARD, 22f, UiKit.BORDER_SUBTLE, 1f)
            setPadding((22 * dp).toInt(), (26 * dp).toInt(), (22 * dp).toInt(), (24 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = (18 * dp).toInt()
            }
        }

        val icon = TextView(this).apply {
            text = "📋"
            textSize = 40f
            gravity = Gravity.CENTER
            background = UiKit.roundedDrawable(this@OnboardingActivity, UiKit.ACCENT_SOFT, 18f, UiKit.ACCENT_PRIMARY, 1f)
            val sz = (76 * dp).toInt()
            layoutParams = LinearLayout.LayoutParams(sz, sz)
        }
        val title = TextView(this).apply {
            text = "Your Copies Stay Yours"
            textSize = 23f
            setTextColor(UiKit.TEXT_PRIMARY)
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, (14 * dp).toInt(), 0, (10 * dp).toInt())
        }
        val desc = TextView(this).apply {
            text = "Keep unlimited clipboard history with local encryption, type smoothly in English + Hinglish, swipe to type or delete words, and customise every key size & effect — 100% offline."
            textSize = 14f
            setTextColor(UiKit.TEXT_SECONDARY)
            gravity = Gravity.CENTER
            setLineSpacing(4f * dp, 1f)
        }
        infoCard.addView(icon)
        infoCard.addView(title)
        infoCard.addView(desc)

        val enableBtn = UiKit.createPillButton(this, "1. Enable Keybord in System Settings", filled = false) {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }.apply {
            textSize = 14.5f
            setPadding(0, (13 * dp).toInt(), 0, (13 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val selectBtn = UiKit.createPillButton(this, "2. Select Keybord as Active Keyboard", filled = true) {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showInputMethodPicker()
        }.apply {
            textSize = 14.5f
            setPadding(0, (13 * dp).toInt(), 0, (13 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = (10 * dp).toInt()
            }
        }

        layout.addView(infoCard)
        layout.addView(enableBtn)
        layout.addView(selectBtn)
        contentFrame.addView(layout)
    }

    private fun renderStep2ThemePicker() {
        val dp = resources.displayMetrics.density
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val title = TextView(this).apply {
            text = "Pick Your Style"
            textSize = 22f
            setTextColor(UiKit.TEXT_PRIMARY)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, (8 * dp).toInt(), 0, (4 * dp).toInt())
        }
        val sub = TextView(this).apply {
            text = "Choose a theme preset or customise height & buttons."
            textSize = 13.5f
            setTextColor(UiKit.TEXT_SECONDARY)
            setPadding(0, 0, 0, (14 * dp).toInt())
        }

        val previewCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = UiKit.roundedDrawable(this@OnboardingActivity, UiKit.SURFACE_CARD, 18f, UiKit.BORDER_SUBTLE, 1f)
            setPadding((6 * dp).toInt(), (6 * dp).toInt(), (6 * dp).toInt(), (6 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        val preview = ThemePreviewView(this).apply {
            showNumberRow = layoutPrefs.showNumberRow
            theme = themeRepo.getActiveTheme()
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        previewCard.addView(preview)

        val scroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            setPadding(0, (14 * dp).toInt(), 0, (10 * dp).toInt())
        }
        val themeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        for (th in BuiltInThemes.ALL_BUILT_IN) {
            val active = (th.id == themeRepo.getActiveTheme().id)
            val chip = TextView(this).apply {
                text = th.name
                textSize = 13f
                typeface = if (active) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                setTextColor(if (active) Color.WHITE else UiKit.TEXT_SECONDARY)
                background = UiKit.roundedDrawable(
                    this@OnboardingActivity,
                    if (active) UiKit.ACCENT_PRIMARY else UiKit.SURFACE_CARD,
                    12f,
                    if (active) null else UiKit.BORDER_SUBTLE,
                    1f
                )
                setPadding((14 * dp).toInt(), (9 * dp).toInt(), (14 * dp).toInt(), (9 * dp).toInt())
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    marginEnd = (8 * dp).toInt()
                }
                setOnClickListener {
                    themeRepo.setActiveTheme(th.id)
                    preview.theme = th
                    renderStep2ThemePicker()
                }
            }
            themeRow.addView(chip)
        }
        scroll.addView(themeRow)

        val customizeBtn = UiKit.createPillButton(this, "📐 Customise Height & Buttons", filled = false) {
            startActivity(Intent(this, KeyboardLayoutCustomizeActivity::class.java))
        }.apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = (6 * dp).toInt()
            }
        }

        layout.addView(title)
        layout.addView(sub)
        layout.addView(previewCard)
        layout.addView(scroll)
        layout.addView(customizeBtn)
        contentFrame.addView(layout)
    }

    private fun renderStep3Done() {
        val dp = resources.displayMetrics.density
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }

        val icon = TextView(this).apply {
            text = "🎉"
            textSize = 48f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, (8 * dp).toInt())
        }
        val title = TextView(this).apply {
            text = "Keyboard is Live!"
            textSize = 24f
            setTextColor(UiKit.TEXT_PRIMARY)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, (8 * dp).toInt())
        }
        val desc = TextView(this).apply {
            text = "Try glide typing (English or Hinglish), swipe Space to glide the cursor, tap 😊 for emojis, or slide left from ⌫ to delete words:"
            textSize = 13.5f
            setTextColor(UiKit.TEXT_SECONDARY)
            gravity = Gravity.CENTER
            setLineSpacing(3f * dp, 1f)
            setPadding(0, 0, 0, (16 * dp).toInt())
        }

        val testField = EditText(this).apply {
            hint = "Tap here to test typing, Hinglish, Emoji & Space cursor..."
            setHintTextColor(UiKit.TEXT_MUTED)
            setTextColor(UiKit.TEXT_PRIMARY)
            textSize = 15.5f
            background = UiKit.roundedDrawable(this@OnboardingActivity, UiKit.SURFACE_CARD, 14f, UiKit.BORDER_SUBTLE, 1f)
            setPadding((14 * dp).toInt(), (14 * dp).toInt(), (14 * dp).toInt(), (14 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = (14 * dp).toInt()
            }
        }

        val settingsBtn = UiKit.createPillButton(this, "Open Full Dashboard & Customiser", filled = false) {
            startActivity(Intent(this@OnboardingActivity, KeybordSettingsActivity::class.java))
        }.apply {
            textSize = 14f
            setPadding(0, (12 * dp).toInt(), 0, (12 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        layout.addView(icon)
        layout.addView(title)
        layout.addView(desc)
        layout.addView(testField)
        layout.addView(settingsBtn)
        contentFrame.addView(layout)
    }

    private fun finishOnboarding() {
        getSharedPreferences("keybord_prefs", Context.MODE_PRIVATE)
            .edit().putBoolean("onboarding_complete", true).apply()
        startActivity(Intent(this, KeybordSettingsActivity::class.java))
        finish()
    }
}
