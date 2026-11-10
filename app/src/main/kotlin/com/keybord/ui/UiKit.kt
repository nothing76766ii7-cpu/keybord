package com.keybord.ui

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Unified dark design tokens and reusable components for Keybord activities and trays.
 */
object UiKit {
    val BG_CANVAS = Color.parseColor("#0D0F16")
    val SURFACE_CARD = Color.parseColor("#171B26")
    val SURFACE_ELEVATED = Color.parseColor("#202636")
    val SURFACE_INPUT = Color.parseColor("#141822")
    val BORDER_SUBTLE = Color.parseColor("#2B3245")
    val ACCENT_PRIMARY = Color.parseColor("#4F8CFF")
    val ACCENT_SOFT = Color.parseColor("#264F8CFF")
    val SUCCESS_BG = Color.parseColor("#162B22")
    val SUCCESS_FG = Color.parseColor("#6EE7B7")
    val DANGER_BG = Color.parseColor("#2D161E")
    val DANGER_FG = Color.parseColor("#FB7185")
    val TEXT_PRIMARY = Color.parseColor("#F3F6FC")
    val TEXT_SECONDARY = Color.parseColor("#949EB8")
    val TEXT_MUTED = Color.parseColor("#67728E")

    fun roundedDrawable(
        context: Context,
        fillColor: Int,
        cornerRadiusDp: Float = 14f,
        strokeColor: Int? = null,
        strokeWidthDp: Float = 1f
    ): GradientDrawable {
        val dp = context.resources.displayMetrics.density
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = cornerRadiusDp * dp
            setColor(fillColor)
            if (strokeColor != null && strokeWidthDp > 0f) {
                setStroke((strokeWidthDp * dp).toInt().coerceAtLeast(1), strokeColor)
            }
        }
    }

    fun applyEdgeToEdgePadding(view: View, leftDp: Float = 16f, topDp: Float = 14f, rightDp: Float = 16f, bottomDp: Float = 16f) {
        val dp = view.resources.displayMetrics.density
        val l = (leftDp * dp).toInt()
        val t = (topDp * dp).toInt()
        val r = (rightDp * dp).toInt()
        val b = (bottomDp * dp).toInt()
        view.setPadding(l, t, r, b)
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(l + bars.left, t + bars.top, r + bars.right, b + bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(view)
    }

    fun createHeaderBar(
        activity: Activity,
        title: String,
        subtitle: String? = null,
        showBack: Boolean = true,
        trailingView: View? = null
    ): LinearLayout {
        val dp = activity.resources.displayMetrics.density
        val bar = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, (14 * dp).toInt())
        }

        if (showBack) {
            val backBtn = TextView(activity).apply {
                text = "←"
                textSize = 18f
                setTextColor(TEXT_PRIMARY)
                gravity = Gravity.CENTER
                background = roundedDrawable(activity, SURFACE_CARD, 12f, BORDER_SUBTLE, 1f)
                val size = (38 * dp).toInt()
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    marginEnd = (12 * dp).toInt()
                }
                setOnClickListener { activity.finish() }
            }
            bar.addView(backBtn)
        }

        val titleCol = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val tvTitle = TextView(activity).apply {
            text = title
            textSize = 21f
            setTextColor(TEXT_PRIMARY)
            typeface = Typeface.DEFAULT_BOLD
        }
        titleCol.addView(tvTitle)
        if (!subtitle.isNullOrBlank()) {
            val tvSub = TextView(activity).apply {
                text = subtitle
                textSize = 12.5f
                setTextColor(TEXT_SECONDARY)
                setPadding(0, (2 * dp).toInt(), 0, 0)
            }
            titleCol.addView(tvSub)
        }
        bar.addView(titleCol)

        if (trailingView != null) {
            bar.addView(trailingView)
        }
        return bar
    }

    fun createPillButton(
        context: Context,
        label: String,
        filled: Boolean = true,
        danger: Boolean = false,
        onClick: () -> Unit
    ): TextView {
        val dp = context.resources.displayMetrics.density
        val fill = when {
            danger -> DANGER_BG
            filled -> ACCENT_PRIMARY
            else -> SURFACE_ELEVATED
        }
        val stroke = when {
            danger -> Color.argb(90, Color.red(DANGER_FG), Color.green(DANGER_FG), Color.blue(DANGER_FG))
            filled -> null
            else -> BORDER_SUBTLE
        }
        val fg = when {
            danger -> DANGER_FG
            else -> Color.WHITE
        }
        return TextView(context).apply {
            text = label
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(fg)
            gravity = Gravity.CENTER
            background = roundedDrawable(context, fill, 12f, stroke, 1f)
            setPadding((14 * dp).toInt(), (9 * dp).toInt(), (14 * dp).toInt(), (9 * dp).toInt())
            setOnClickListener { onClick() }
        }
    }

    fun createSectionLabel(context: Context, title: String): TextView {
        val dp = context.resources.displayMetrics.density
        return TextView(context).apply {
            text = title.uppercase()
            textSize = 11.5f
            letterSpacing = 0.08f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(ACCENT_PRIMARY)
            setPadding((2 * dp).toInt(), (14 * dp).toInt(), 0, (8 * dp).toInt())
        }
    }

    fun createSwitchCard(
        context: Context,
        title: String,
        subtitle: String,
        checked: Boolean,
        onToggle: (Boolean) -> Unit
    ): LinearLayout {
        val dp = context.resources.displayMetrics.density
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundedDrawable(context, SURFACE_CARD, 16f, BORDER_SUBTLE, 1f)
            setPadding((16 * dp).toInt(), (14 * dp).toInt(), (14 * dp).toInt(), (14 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (10 * dp).toInt() }
        }

        val textCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = (12 * dp).toInt()
            }
        }
        val tvTitle = TextView(context).apply {
            text = title
            textSize = 15.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(TEXT_PRIMARY)
        }
        val tvSub = TextView(context).apply {
            text = subtitle
            textSize = 12.5f
            setTextColor(TEXT_SECONDARY)
            setPadding(0, (3 * dp).toInt(), 0, 0)
        }
        textCol.addView(tvTitle)
        textCol.addView(tvSub)

        val sw = Switch(context).apply {
            isChecked = checked
            setOnCheckedChangeListener { _, isChecked -> onToggle(isChecked) }
        }

        card.setOnClickListener { sw.toggle() }
        card.addView(textCol)
        card.addView(sw)
        return card
    }

    fun createSliderCard(
        context: Context,
        title: String,
        unitSuffix: String,
        current: Int,
        min: Int,
        max: Int,
        onChanged: (Int) -> Unit
    ): LinearLayout {
        val dp = context.resources.displayMetrics.density
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedDrawable(context, SURFACE_CARD, 14f, BORDER_SUBTLE, 1f)
            setPadding((14 * dp).toInt(), (11 * dp).toInt(), (14 * dp).toInt(), (11 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (8 * dp).toInt() }
        }

        val headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val tvLabel = TextView(context).apply {
            text = title
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(TEXT_PRIMARY)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val tvValue = TextView(context).apply {
            text = "$current$unitSuffix"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(ACCENT_PRIMARY)
            background = roundedDrawable(context, ACCENT_SOFT, 8f)
            setPadding((8 * dp).toInt(), (2 * dp).toInt(), (8 * dp).toInt(), (2 * dp).toInt())
        }
        headerRow.addView(tvLabel)
        headerRow.addView(tvValue)

        val seek = SeekBar(context).apply {
            this.max = (max - min).coerceAtLeast(1)
            progress = (current - min).coerceIn(0, this.max)
            setPadding(0, (8 * dp).toInt(), 0, (2 * dp).toInt())
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val value = min + progress
                    tvValue.text = "$value$unitSuffix"
                    onChanged(value)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }

        card.addView(headerRow)
        card.addView(seek)
        return card
    }
}
