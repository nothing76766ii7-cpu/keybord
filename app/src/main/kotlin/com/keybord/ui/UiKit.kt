package com.keybord.ui

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable
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
 * Unified Tactile Skeuomorphic Design System for Keybord IME & Settings.
 * Built with lightweight multi-layer bevels, recessed wells, physical switches,
 * and grounded depth shadows with zero blur overhead.
 */
object UiKit {
    val BG_CANVAS = Color.parseColor("#0B0D13")
    val SURFACE_CARD = Color.parseColor("#181C27")
    val SURFACE_ELEVATED = Color.parseColor("#222736")
    val SURFACE_INPUT = Color.parseColor("#0E1018")
    val BORDER_SUBTLE = Color.parseColor("#24FFFFFF")
    val DIVIDER_HAIRLINE = Color.parseColor("#16FFFFFF")
    val ACCENT_PRIMARY = Color.parseColor("#4F8BFF")
    val ACCENT_SOFT = Color.parseColor("#244F8BFF")
    val ACCENT_AI = Color.parseColor("#7C5CFF")
    val ACCENT_AI_SOFT = Color.parseColor("#267C5CFF")
    val SUCCESS_BG = Color.parseColor("#12291F")
    val SUCCESS_FG = Color.parseColor("#34D399")
    val SUCCESS_BORDER = Color.parseColor("#3834D399")
    val WARNING_BG = Color.parseColor("#2B2112")
    val WARNING_FG = Color.parseColor("#FBBF24")
    val DANGER_BG = Color.parseColor("#2D161E")
    val DANGER_FG = Color.parseColor("#FB7185")
    val TEXT_PRIMARY = Color.parseColor("#F4F6FB")
    val TEXT_SECONDARY = Color.parseColor("#969DB5")
    val TEXT_MUTED = Color.parseColor("#67708B")

    fun shadeColor(color: Int, delta: Float): Int {
        val a = Color.alpha(color)
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        val nr = if (delta >= 0f) r + ((255 - r) * delta).toInt() else (r * (1f + delta)).toInt()
        val ng = if (delta >= 0f) g + ((255 - g) * delta).toInt() else (g * (1f + delta)).toInt()
        val nb = if (delta >= 0f) b + ((255 - b) * delta).toInt() else (b * (1f + delta)).toInt()
        return Color.argb(a, nr.coerceIn(0, 255), ng.coerceIn(0, 255), nb.coerceIn(0, 255))
    }

    fun blendColors(c1: Int, c2: Int, ratioC2: Float): Int {
        val t = ratioC2.coerceIn(0f, 1f)
        val inv = 1f - t
        return Color.argb(
            (Color.alpha(c1) * inv + Color.alpha(c2) * t).toInt().coerceIn(0, 255),
            (Color.red(c1) * inv + Color.red(c2) * t).toInt().coerceIn(0, 255),
            (Color.green(c1) * inv + Color.green(c2) * t).toInt().coerceIn(0, 255),
            (Color.blue(c1) * inv + Color.blue(c2) * t).toInt().coerceIn(0, 255)
        )
    }

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

    /**
     * Raised physical card/bezel with bottom ambient drop shadow, top specular edge highlight,
     * and sculpted vertical surface gradient.
     */
    fun skeuoCardDrawable(
        context: Context,
        fillColor: Int = SURFACE_CARD,
        cornerRadiusDp: Float = 18f,
        strokeColor: Int = BORDER_SUBTLE,
        elevationDp: Float = 2.5f
    ): Drawable {
        val dp = context.resources.displayMetrics.density
        val r = cornerRadiusDp * dp
        val elevPx = (elevationDp * dp).toInt().coerceAtLeast(2)
        val highlightPx = (1 * dp).toInt().coerceAtLeast(1)
        val isLight = Color.luminance(fillColor) > 0.55f

        val shadow = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = r
            setColor(if (isLight) Color.argb(46, 15, 23, 42) else Color.argb(135, 0, 0, 0))
        }
        val topBevel = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = r
            setColor(if (isLight) Color.argb(215, 255, 255, 255) else Color.argb(44, 255, 255, 255))
        }
        val face = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(
                shadeColor(fillColor, if (isLight) 0.04f else 0.09f),
                fillColor,
                shadeColor(fillColor, if (isLight) -0.06f else -0.14f)
            )
        ).apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = r
            setStroke((1 * dp).toInt().coerceAtLeast(1), strokeColor)
        }
        return LayerDrawable(arrayOf(shadow, topBevel, face)).apply {
            setLayerInset(0, 0, elevPx, 0, 0)
            setLayerInset(1, 0, 0, 0, elevPx)
            setLayerInset(2, 0, highlightPx, 0, elevPx)
        }
    }

    /**
     * Recessed inset trough/well with top inner shadow and subtle bottom rim bounce light.
     */
    fun skeuoWellDrawable(
        context: Context,
        wellColor: Int = SURFACE_INPUT,
        cornerRadiusDp: Float = 13f,
        strokeColor: Int = Color.argb(120, 0, 0, 0)
    ): Drawable {
        val dp = context.resources.displayMetrics.density
        val r = cornerRadiusDp * dp
        val insetTop = (2 * dp).toInt().coerceAtLeast(2)
        val insetBottom = (1 * dp).toInt().coerceAtLeast(1)
        val isLight = Color.luminance(wellColor) > 0.55f

        val bottomBounce = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = r
            setColor(if (isLight) Color.argb(145, 255, 255, 255) else Color.argb(26, 255, 255, 255))
        }
        val topInnerShadow = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = r
            setColor(if (isLight) Color.argb(48, 15, 23, 42) else Color.argb(145, 0, 0, 0))
        }
        val inner = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(
                shadeColor(wellColor, -0.16f),
                wellColor,
                shadeColor(wellColor, 0.04f)
            )
        ).apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = r
            setStroke((1 * dp).toInt().coerceAtLeast(1), strokeColor)
        }
        return LayerDrawable(arrayOf(bottomBounce, topInnerShadow, inner)).apply {
            setLayerInset(0, 0, insetTop, 0, 0)
            setLayerInset(1, 0, 0, 0, insetBottom)
            setLayerInset(2, 0, insetTop, 0, insetBottom)
        }
    }

    /**
     * Dimensional push-button with distinct raised released cap and depressed tactile state.
     */
    fun skeuoButtonDrawable(
        context: Context,
        baseColor: Int,
        cornerRadiusDp: Float = 12f,
        strokeColor: Int? = null
    ): Drawable {
        val dp = context.resources.displayMetrics.density
        val r = cornerRadiusDp * dp
        val lipPx = (2.5f * dp).toInt().coerceAtLeast(2)
        val isLight = Color.luminance(baseColor) > 0.55f
        val rim = strokeColor ?: if (isLight) Color.argb(55, 15, 23, 42) else Color.argb(60, 255, 255, 255)

        val skirt = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = r
            setColor(shadeColor(baseColor, -0.45f))
        }
        val topEdge = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = r
            setColor(Color.argb(if (isLight) 210 else 80, 255, 255, 255))
        }
        val capUp = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(
                shadeColor(baseColor, 0.16f),
                baseColor,
                shadeColor(baseColor, -0.12f)
            )
        ).apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = r
            setStroke((1 * dp).toInt().coerceAtLeast(1), rim)
        }
        val raised = LayerDrawable(arrayOf(skirt, topEdge, capUp)).apply {
            setLayerInset(0, 0, lipPx, 0, 0)
            setLayerInset(1, 0, 0, 0, lipPx)
            setLayerInset(2, 0, (1 * dp).toInt().coerceAtLeast(1), 0, lipPx)
        }

        val capDown = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(
                shadeColor(baseColor, -0.14f),
                shadeColor(baseColor, -0.06f)
            )
        ).apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = r
            setStroke((1 * dp).toInt().coerceAtLeast(1), rim)
        }
        val depressed = LayerDrawable(arrayOf(capDown)).apply {
            setLayerInset(0, 0, lipPx, 0, 0)
        }

        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), depressed)
            addState(intArrayOf(), raised)
        }
    }

    /**
     * Sculpted icon bezel well used inside Settings rows and panel headers.
     */
    fun skeuoIconWellDrawable(context: Context, tintColor: Int, cornerRadiusDp: Float = 11f): Drawable {
        return skeuoCardDrawable(
            context = context,
            fillColor = tintColor,
            cornerRadiusDp = cornerRadiusDp,
            strokeColor = Color.argb(54, 255, 255, 255),
            elevationDp = 1.5f
        )
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
                background = skeuoButtonDrawable(activity, SURFACE_ELEVATED, 12f, BORDER_SUBTLE)
                val size = (40 * dp).toInt()
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
            background = skeuoWellDrawable(
                context,
                bgColor,
                999f,
                strokeColor ?: BORDER_SUBTLE
            )
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
            danger -> Color.parseColor("#881D3A")
            filled -> ACCENT_PRIMARY
            else -> SURFACE_ELEVATED
        }
        val stroke = when {
            danger -> Color.argb(120, 251, 113, 133)
            filled -> Color.argb(115, 191, 219, 254)
            else -> BORDER_SUBTLE
        }
        val fg = Color.WHITE
        return TextView(context).apply {
            text = label
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(fg)
            gravity = Gravity.CENTER
            background = skeuoButtonDrawable(context, fill, 12f, stroke)
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
            background = skeuoCardDrawable(context, SURFACE_CARD, radiusDp, BORDER_SUBTLE, 2.5f)
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

    private fun styleSkeuoSwitch(context: Context, sw: Switch) {
        val dp = context.resources.displayMetrics.density
        val thumbSize = (22 * dp).toInt()
        val thumbUp = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.parseColor("#FFFFFF"), Color.parseColor("#CBD5E1"))
        ).apply {
            shape = GradientDrawable.OVAL
            setSize(thumbSize, thumbSize)
            setStroke((1 * dp).toInt().coerceAtLeast(1), Color.argb(120, 15, 23, 42))
        }
        val trackOff = skeuoWellDrawable(context, Color.parseColor("#0D1017"), 999f, BORDER_SUBTLE)
        val trackOn = skeuoWellDrawable(context, Color.parseColor("#2A5BD7"), 999f, Color.argb(130, 147, 197, 253))
        val trackState = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_checked), trackOn)
            addState(intArrayOf(), trackOff)
        }
        sw.thumbDrawable = thumbUp
        sw.trackDrawable = trackState
        sw.switchMinWidth = (48 * dp).toInt()
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
                createBadgePill(context, badgeText, SURFACE_INPUT, TEXT_SECONDARY, BORDER_SUBTLE).apply {
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
            styleSkeuoSwitch(context, this)
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
            background = skeuoCardDrawable(context, SURFACE_CARD, 16f, BORDER_SUBTLE, 2.5f)
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
            styleSkeuoSwitch(context, this)
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
            background = skeuoCardDrawable(context, SURFACE_CARD, 16f, BORDER_SUBTLE, 2.5f)
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
            background = skeuoWellDrawable(context, SURFACE_INPUT, 8f, BORDER_SUBTLE)
            setPadding((9 * dp).toInt(), (3 * dp).toInt(), (9 * dp).toInt(), (3 * dp).toInt())
        }
        headerRow.addView(tvLabel)
        headerRow.addView(tvValue)

        // Recessed slider groove + physical metallic circular thumb
        val trackBg = skeuoWellDrawable(context, SURFACE_INPUT, 999f, BORDER_SUBTLE)
        val fillGradient = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(shadeColor(ACCENT_PRIMARY, 0.18f), ACCENT_PRIMARY)
        ).apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 999f * dp
        }
        val trackClip = ClipDrawable(fillGradient, Gravity.START, ClipDrawable.HORIZONTAL)
        val progressLayer = LayerDrawable(arrayOf(trackBg, trackClip)).apply {
            setId(0, android.R.id.background)
            setId(1, android.R.id.progress)
        }
        val knobSize = (20 * dp).toInt()
        val thumbKnob = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.parseColor("#FFFFFF"), Color.parseColor("#94A3B8"))
        ).apply {
            shape = GradientDrawable.OVAL
            setSize(knobSize, knobSize)
            setStroke((1.5f * dp).toInt().coerceAtLeast(1), ACCENT_PRIMARY)
        }

        val seek = SeekBar(context).apply {
            this.max = (max - min).coerceAtLeast(1)
            progress = (current - min).coerceIn(0, this.max)
            progressDrawable = progressLayer
            thumb = thumbKnob
            splitTrack = false
            setPadding((6 * dp).toInt(), (10 * dp).toInt(), (6 * dp).toInt(), (4 * dp).toInt())
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
            background = skeuoWellDrawable(context, SURFACE_INPUT, 13f, BORDER_SUBTLE)
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
                tv.background = if (active) skeuoButtonDrawable(context, ACCENT_PRIMARY, 10f) else null
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
            background = skeuoCardDrawable(context, SUCCESS_BG, 16f, SUCCESS_BORDER, 2f)
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
                    background = skeuoIconWellDrawable(context, Color.parseColor("#1D4D3A"), 10f)
                    val sz = (36 * dp).toInt()
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
