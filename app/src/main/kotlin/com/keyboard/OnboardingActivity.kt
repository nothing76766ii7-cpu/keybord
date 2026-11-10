package com.keyboard

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * OnboardingActivity implementing Section D specifications.
 * 3 screens: Clipboard, Make it yours, Done.
 * No account, no email. SharedPreferences key "onboarding_complete" = true.
 */
class OnboardingActivity : Activity() {

    private var currentScreenIndex = 0
    private lateinit var contentCard: LinearLayout
    private lateinit var dotIndicators: LinearLayout
    private lateinit var nextBtn: Button
    private lateinit var skipBtn: TextView

    private val prefs by lazy { getSharedPreferences("keybord_prefs", Context.MODE_PRIVATE) }

    data class OnboardingSlide(
        val icon: String,
        val headline: String,
        val body: String
    )

    private val slides = listOf(
        OnboardingSlide(
            icon = "📋 ∞",
            headline = "Your copies stay yours",
            body = "No more losing what you copied an hour ago. Keybord keeps everything until you delete it."
        ),
        OnboardingSlide(
            icon = "🎨 ⚙️",
            headline = "Built for how you look at your phone",
            body = "Change fonts, colours, key size — everything. Your keyboard, your rules."
        ),
        OnboardingSlide(
            icon = "🚀",
            headline = "One step left",
            body = "Set Keybord as your default keyboard to get started."
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check if already completed
        if (prefs.getBoolean("onboarding_complete", false)) {
            launchMainApp()
            return
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Color.parseColor("#181825"))
            setPadding(48, 64, 48, 48)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // Top skip button
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        skipBtn = TextView(this).apply {
            text = "Skip"
            setTextColor(Color.parseColor("#A6ADC8"))
            textSize = 14f
            setPadding(16, 8, 16, 8)
            setOnClickListener {
                // Skip goes straight to Screen 3 (Section D)
                currentScreenIndex = 2
                renderSlide()
            }
        }
        topBar.addView(skipBtn)
        root.addView(topBar)

        // Slide Content Area
        contentCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1.0f
            )
        }
        root.addView(contentCard)

        // Dot indicators
        dotIndicators = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 16, 0, 32)
        }
        root.addView(dotIndicators)

        // Next / Set Default Button
        nextBtn = Button(this).apply {
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#89B4FA"))
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                140
            )
            setOnClickListener {
                if (currentScreenIndex < 2) {
                    currentScreenIndex++
                    renderSlide()
                } else {
                    completeOnboarding()
                }
            }
        }
        root.addView(nextBtn)

        setContentView(root)
        renderSlide()
    }

    private fun renderSlide() {
        contentCard.removeAllViews()
        val slide = slides[currentScreenIndex]

        val iconTv = TextView(this).apply {
            text = slide.icon
            textSize = 64f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 48)
        }
        contentCard.addView(iconTv)

        val headlineTv = TextView(this).apply {
            text = slide.headline
            setTextColor(Color.WHITE)
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 16)
        }
        contentCard.addView(headlineTv)

        val bodyTv = TextView(this).apply {
            text = slide.body
            setTextColor(Color.parseColor("#BAC2DE"))
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(24, 0, 24, 0)
        }
        contentCard.addView(bodyTv)

        // Update dots
        dotIndicators.removeAllViews()
        for (i in 0..2) {
            val dot = View(this).apply {
                val active = (i == currentScreenIndex)
                setBackgroundColor(if (active) Color.parseColor("#89B4FA") else Color.parseColor("#45475A"))
                layoutParams = LinearLayout.LayoutParams(if (active) 36 else 16, 16).apply {
                    setMargins(8, 0, 8, 0)
                }
            }
            dotIndicators.addView(dot)
        }

        // Button text & skip visibility
        if (currentScreenIndex == 2) {
            nextBtn.text = "Set as Default"
            skipBtn.visibility = View.INVISIBLE
        } else {
            nextBtn.text = "Next"
            skipBtn.visibility = View.VISIBLE
        }
    }

    private fun completeOnboarding() {
        prefs.edit().putBoolean("onboarding_complete", true).apply()
        // Section D: Deep link to Settings > System > Language & Input
        val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            launchMainApp()
        }
        finish()
    }

    private fun launchMainApp() {
        val intent = Intent(this, SettingsActivity::class.java)
        startActivity(intent)
        finish()
    }
}
