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
 * Unified Obsidian Tactile & Clean Porcelain Design System tokens and components
 * imported from the Keybord Stitch design suite.
 */
object UiKit {
    val BG_CANVAS = Color.parseColor("#0D0F16")
    val SURFACE_CARD = Color.parseColor("#161924")
    val SURFACE_ELEVATED = Color.parseColor("#1D2130")
    val SURFACE_INPUT = Color.parseColor("#11131C")
    val BORDER_SUBTLE = Color.parseColor("#1AFFFFFF")
    val DIVIDER_HAIRLINE = Color.parseColor("#12FFFFFF")
    val ACCENT_PRIMARY = Color.parseColor("#4F8BFF")
    val ACCENT_SOFT = Color.parseColor("#244F8BFF")
    val ACCENT_AI = Color.parseColor("#7C5CFF")
    val ACCENT_AI_SOFT = Color.parseColor("#267C5CFF")
    val SUCCESS_BG = Color.parseColor("#12291F")
    val SUCCESS_FG = Color.parseColor("#34D399")
    val SUCCESS_BORDER = Color.parseColor("#3334D399")
    val WARNING_BG = Color.parseColor("#2B2112")
    val WARNING_FG = Color.parseColor("#FBBF24")
    val DANGER_BG = Color.parseColor("#2D161E")
    val DANGER_FG = Color.parseColor("#FB7185")
    val TEXT_PRIMARY = Color.parseColor("#F4F6FB")
    val TEXT_SECONDARY = Color.parseColor("#8B91A7")
    val TEXT_MUTED = Color.parseColor("#5F667D")

    fun roundedDrawable(
        context: Context,
        fillColor: Int,
        cornerRadiusDp: Float = 16f,
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

    fun applyEdgeToEdgePadding(
        view: View,
        leftDp: Float = 16f,
        topDp: Float = 14f,
        rightDp: Float = 16f,
        bottomDp: Float = 16f
    ) {
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

    fun createBadgePill(
        context: Context,
        label: String,
        bgColor: Int = ACCENT_SOFT,
        fgColor: Int = ACCENT_PRIMARY,
        strokeColor: Int? = null
    ): TextView {
        val dp = context.resources.displayMetrics.density
        return TextView(context).apply {
            text = label
            textSize = 11.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(fgColor)
            gravity = Gravity.CENTER
            background = roundedDrawable(context, bgColor, 999f, strokeColor, if (strokeColor != null) 1f else 0f)
            setPadding((11 * dp).toInt(), (5 * dp).toInt(), (11 * dp).toInt(), (5 * dp).toInt())
        }
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
            setTextColor(TEXT_SECONDARY)
            setPadding((2 * dp).toInt(), (14 * dp).toInt(), 0, (8 * dp).toInt())
        }
    }

    fun createCardContainer(
        context: Context,
        paddingDp: Float = 16f,
        radiusDp: Float = 18f,
        bottomMarginDp: Float = 12f
    ): LinearLayout {
        val dp = context.resources.displayMetrics.density
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedDrawable(context, SURFACE_CARD, radiusDp, BORDER_SUBTLE, 1f)
            setPadding((paddingDp * dp).toInt(), (paddingDp * dp).toInt(), (paddingDp * dp).toInt(), (paddingDp * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (bottomMarginDp * dp).toInt() }
        }
    }

    fun createHairlineDivider(context: Context): View {
        val dp = context.resources.displayMetrics.density
        return View(context).apply {
            setBackgroundColor(DIVIDER_HAIRLINE)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (1 * dp).toInt().coerceAtLeast(1)
            ).apply {
                topMargin = (2 * dp).toInt()
                bottomMargin = (2 * dp).toInt()
            }
        }
    }

    fun createSwitchRow(
        context: Context,
        title: String,
        subtitle: String,
        checked: Boolean,
        badgeText: String? = null,
        onToggle: (Boolean) -> Unit
    ): LinearLayout {
        val dp = context.resources.displayMetrics.density
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, (10 * dp).toInt(), 0, (10 * dp).toInt())
        }

        val textCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = (10 * dp).toInt()
            }
        }
        val titleRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        titleRow.addView(
            TextView(context).apply {
                text = title
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(TEXT_PRIMARY)
            }
        )
        if (!badgeText.isNullOrBlank()) {
            titleRow.addView(
                createBadgePill(context, badgeText, SURFACE_ELEVATED, TEXT_SECONDARY, BORDER_SUBTLE).apply {
                    textSize = 10.5f
                    setPadding((7 * dp).toInt(), (2 * dp).toInt(), (7 * dp).toInt(), (2 * dp).toInt())
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        marginStart = (8 * dp).toInt()
                    }
                }
            )
        }
        textCol.addView(titleRow)
        textCol.addView(
            TextView(context).apply {
                text = subtitle
                textSize = 12f
                setTextColor(TEXT_SECONDARY)
                setPadding(0, (2 * dp).toInt(), 0, 0)
            }
        )

        val sw = Switch(context).apply {
            isChecked = checked
            setOnCheckedChangeListener { _, isChecked -> onToggle(isChecked) }
        }
        row.setOnClickListener { sw.toggle() }
        row.addView(textCol)
        row.addView(sw)
        return row
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
        textCol.addView(
            TextView(context).apply {
                text = title
                textSize = 15.5f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(TEXT_PRIMARY)
            }
        )
        textCol.addView(
            TextView(context).apply {
                text = subtitle
                textSize = 12.5f
                setTextColor(TEXT_SECONDARY)
                setPadding(0, (3 * dp).toInt(), 0, 0)
            }
        )

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
            background = roundedDrawable(context, SURFACE_CARD, 16f, BORDER_SUBTLE, 1f)
            setPadding((14 * dp).toInt(), (12 * dp).toInt(), (14 * dp).toInt(), (12 * dp).toInt())
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
            textSize = 12.5f
            typeface = Typeface.MONOSPACE
            setTextColor(ACCENT_PRIMARY)
            background = roundedDrawable(context, ACCENT_SOFT, 8f)
            setPadding((9 * dp).toInt(), (3 * dp).toInt(), (9 * dp).toInt(), (3 * dp).toInt())
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

    fun createSegmentedControl(
        context: Context,
        options: List<String>,
        selectedIndex: Int,
        onSelected: (Int) -> Unit
    ): LinearLayout {
        val dp = context.resources.displayMetrics.density
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            background = roundedDrawable(context, SURFACE_INPUT, 12f, BORDER_SUBTLE, 1f)
            setPadding((4 * dp).toInt(), (4 * dp).toInt(), (4 * dp).toInt(), (4 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val itemViews = mutableListOf<TextView>()
        fun updateSelection(activeIdx: Int) {
            itemViews.forEachIndexed { i, tv ->
                val active = (i == activeIdx)
                tv.setTextColor(if (active) Color.WHITE else TEXT_SECONDARY)
                tv.typeface = if (active) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                tv.background = if (active) roundedDrawable(context, ACCENT_PRIMARY, 9f) else null
            }
        }

        options.forEachIndexed { idx, label ->
            val tv = TextView(context).apply {
                text = label
                textSize = 12.5f
                gravity = Gravity.CENTER
                setPadding((8 * dp).toInt(), (8 * dp).toInt(), (8 * dp).toInt(), (8 * dp).toInt())
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener {
                    updateSelection(idx)
                    onSelected(idx)
                }
            }
            itemViews.add(tv)
            container.addView(tv)
        }
        updateSelection(selectedIndex.coerceIn(0, (options.size - 1).coerceAtLeast(0)))
        return container
    }

    fun createPrivacyFooter(
        context: Context,
        title: String,
        subtitle: String
    ): LinearLayout {
        val dp = context.resources.displayMetrics.density
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundedDrawable(context, SUCCESS_BG, 16f, SUCCESS_BORDER, 1f)
            setPadding((14 * dp).toInt(), (12 * dp).toInt(), (14 * dp).toInt(), (12 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (8 * dp).toInt()
                bottomMargin = (6 * dp).toInt()
            }

            addView(
                TextView(context).apply {
                    text = "🛡"
                    textSize = 18f
                    gravity = Gravity.CENTER
                    background = roundedDrawable(context, Color.parseColor("#1D4D3A"), 10f)
                    val sz = (34 * dp).toInt()
                    layoutParams = LinearLayout.LayoutParams(sz, sz).apply { marginEnd = (12 * dp).toInt() }
                }
            )

            val col = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            col.addView(
                TextView(context).apply {
                    text = title
                    textSize = 13f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(SUCCESS_FG)
                }
            )
            col.addView(
                TextView(context).apply {
                    text = subtitle
                    textSize = 11.5f
                    setTextColor(TEXT_SECONDARY)
                    setPadding(0, (2 * dp).toInt(), 0, 0)
                }
            )
            addView(col)
        }
    }
}
