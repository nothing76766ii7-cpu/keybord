package com.keybord.onboarding

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.keybord.settings.KeybordSettingsActivity
import com.keybord.theme.BuiltInThemes
import com.keybord.theme.ThemePreviewView
import com.keybord.theme.ThemeRepository
import com.keybord.theme.applySystemBarPadding

/**
 * First Launch Onboarding Flow conforming to PRD Section 4.1:
 * - Screen 1: Clipboard explainer — "your copies stay yours" & IME activation deep link
 * - Screen 2: One theme picker — sets initial look
 * - Screen 3: Done — keyboard is live with interactive test field
 *
 * No account required. No email. No sign-in wall.
 */
class OnboardingActivity : AppCompatActivity() {

    private lateinit var contentFrame: FrameLayout
    private lateinit var prevBtn: Button
    private lateinit var nextBtn: Button
    private lateinit var stepIndicators: LinearLayout

    private var currentStep = 0
    private val totalSteps = 3
    private lateinit var themeRepo: ThemeRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        themeRepo = ThemeRepository(this)

        val dp = resources.displayMetrics.density
        val padH = (20 * dp).toInt()
        val padT = (24 * dp).toInt()
        val padB = (20 * dp).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#12121A"))
            setPadding(padH, padT, padH, padB)
        }
        root.applySystemBarPadding(padH, padT, padH, padB)

        // Top Brand Header
        val brandHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, (16 * dp).toInt())
        }
        val logoText = TextView(this).apply {
            text = "⌨ Keybord"
            textSize = 22f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val skipBtn = TextView(this).apply {
            text = "Skip"
            setTextColor(Color.parseColor("#8888AA"))
            textSize = 14f
            setPadding((8 * dp).toInt(), (4 * dp).toInt(), (8 * dp).toInt(), (4 * dp).toInt())
            setOnClickListener { finishOnboarding() }
        }
        brandHeader.addView(logoText)
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
            setPadding(0, (16 * dp).toInt(), 0, 0)
        }

        prevBtn = Button(this).apply {
            text = "Back"
            textSize = 13f
            setOnClickListener {
                if (currentStep > 0) {
                    currentStep--
                    showStep(currentStep)
                }
            }
        }

        stepIndicators = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        nextBtn = Button(this).apply {
            text = "Next"
            textSize = 13f
            setBackgroundColor(Color.parseColor("#4285F4"))
            setTextColor(Color.WHITE)
            setOnClickListener {
                if (currentStep < totalSteps - 1) {
                    currentStep++
                    showStep(currentStep)
                } else {
                    finishOnboarding()
                }
            }
        }

        bottomBar.addView(prevBtn)
        bottomBar.addView(stepIndicators)
        bottomBar.addView(nextBtn)
        root.addView(bottomBar)

        setContentView(root)
        setupStepIndicators()
        showStep(0)
    }

    private fun setupStepIndicators() {
        stepIndicators.removeAllViews()
        val dp = resources.displayMetrics.density
        for (i in 0 until totalSteps) {
            val dot = View(this).apply {
                val size = if (i == currentStep) (10 * dp).toInt() else (6 * dp).toInt()
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    setMargins((4 * dp).toInt(), 0, (4 * dp).toInt(), 0)
                }
                setBackgroundColor(if (i == currentStep) Color.parseColor("#4285F4") else Color.parseColor("#444466"))
            }
            stepIndicators.addView(dot)
        }
    }

    private fun showStep(step: Int) {
        setupStepIndicators()
        contentFrame.removeAllViews()
        prevBtn.visibility = if (step == 0) View.INVISIBLE else View.VISIBLE
        nextBtn.text = if (step == totalSteps - 1) "Get Started" else "Next"

        when (step) {
            0 -> renderStep1ClipboardExplainer()
            1 -> renderStep2ThemePicker()
            2 -> renderStep3Done()
        }
    }

    // Step 1: Clipboard Explainer & IME Enable
    private fun renderStep1ClipboardExplainer() {
        val dp = resources.displayMetrics.density
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding((12 * dp).toInt(), 0, (12 * dp).toInt(), 0)
        }

        val icon = TextView(this).apply {
            text = "📋"
            textSize = 48f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, (12 * dp).toInt())
        }

        val title = TextView(this).apply {
            text = "Your Copies Stay Yours"
            textSize = 24f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, (10 * dp).toInt())
        }

        val desc = TextView(this).apply {
            text = "Gboard erases your copied text after one hour. Keybord keeps your history forever unless you set a custom rule.\n\nAll data is stored locally with AES encryption at rest. Zero cloud sync. Zero telemetry."
            textSize = 15f
            setTextColor(Color.parseColor("#CCCCCC"))
            gravity = Gravity.CENTER
            setLineSpacing(4f * dp, 1f)
            setPadding(0, 0, 0, (24 * dp).toInt())
        }

        val enableBtn = Button(this).apply {
            text = "1. Enable Keybord in Settings"
            setBackgroundColor(Color.parseColor("#2A3A55"))
            setTextColor(Color.WHITE)
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
            }
        }

        val selectBtn = Button(this).apply {
            text = "2. Select Keybord as Input Method"
            setBackgroundColor(Color.parseColor("#4285F4"))
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = (10 * dp).toInt()
            }
            setOnClickListener {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.showInputMethodPicker()
            }
        }

        layout.addView(icon)
        layout.addView(title)
        layout.addView(desc)
        layout.addView(enableBtn)
        layout.addView(selectBtn)
        contentFrame.addView(layout)
    }

    // Step 2: Theme Picker
    private fun renderStep2ThemePicker() {
        val dp = resources.displayMetrics.density
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val title = TextView(this).apply {
            text = "Pick Your Look"
            textSize = 22f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, (4 * dp).toInt())
        }

        val sub = TextView(this).apply {
            text = "Choose your starting theme. You can fine-tune all 20+ visual parameters later."
            textSize = 14f
            setTextColor(Color.parseColor("#9999AA"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, (12 * dp).toInt())
        }

        val preview = ThemePreviewView(this).apply {
            theme = themeRepo.getActiveTheme()
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (140 * dp).toInt())
        }

        val scroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            setPadding(0, (10 * dp).toInt(), 0, 0)
        }

        val themeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val themes = BuiltInThemes.ALL_BUILT_IN
        for (th in themes) {
            val chip = TextView(this).apply {
                text = th.name
                textSize = 13f
                setTextColor(Color.WHITE)
                setBackgroundColor(if (th.id == themeRepo.getActiveTheme().id) Color.parseColor("#4285F4") else Color.parseColor("#262938"))
                setPadding((14 * dp).toInt(), (8 * dp).toInt(), (14 * dp).toInt(), (8 * dp).toInt())
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    marginEnd = (8 * dp).toInt()
                }
                layoutParams = lp

                setOnClickListener {
                    themeRepo.setActiveTheme(th.id)
                    preview.theme = th
                    renderStep2ThemePicker() // Refresh selected highlight
                }
            }
            themeRow.addView(chip)
        }

        scroll.addView(themeRow)
        layout.addView(title)
        layout.addView(sub)
        layout.addView(preview)
        layout.addView(scroll)
        contentFrame.addView(layout)
    }

    // Step 3: Done with Test Text Field
    private fun renderStep3Done() {
        val dp = resources.displayMetrics.density
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding((12 * dp).toInt(), 0, (12 * dp).toInt(), 0)
        }

        val icon = TextView(this).apply {
            text = "🎉"
            textSize = 48f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, (10 * dp).toInt())
        }

        val title = TextView(this).apply {
            text = "Keyboard is Live!"
            textSize = 24f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, (8 * dp).toInt())
        }

        val desc = TextView(this).apply {
            text = "Try typing or swiping below to test out your new setup:"
            textSize = 14f
            setTextColor(Color.parseColor("#AAAAAA"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, (16 * dp).toInt())
        }

        val testField = EditText(this).apply {
            hint = "Tap here and start typing..."
            setHintTextColor(Color.parseColor("#666688"))
            setTextColor(Color.WHITE)
            textSize = 16f
            setBackgroundColor(Color.parseColor("#1E1E2C"))
            setPadding((14 * dp).toInt(), (14 * dp).toInt(), (14 * dp).toInt(), (14 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = (20 * dp).toInt()
            }
        }

        val settingsBtn = Button(this).apply {
            text = "Open Keybord Settings"
            setBackgroundColor(Color.parseColor("#2A3A55"))
            setTextColor(Color.WHITE)
            setOnClickListener {
                startActivity(Intent(this@OnboardingActivity, KeybordSettingsActivity::class.java))
            }
        }

        layout.addView(icon)
        layout.addView(title)
        layout.addView(desc)
        layout.addView(testField)
        layout.addView(settingsBtn)
        contentFrame.addView(layout)
    }

    private fun finishOnboarding() {
        val prefs = getSharedPreferences("keybord_app", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("onboarding_completed", true).apply()
        startActivity(Intent(this, KeybordSettingsActivity::class.java))
        finish()
    }
}
