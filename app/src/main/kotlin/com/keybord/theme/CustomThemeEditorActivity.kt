package com.keybord.theme

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.keybord.settings.KeyboardLayoutCustomizeActivity
import com.keybord.ui.UiKit
import java.util.UUID

/**
 * Visual Customization Theme Editor exposing all 20+ visual parameters with live preview
 * styled with the unified Keybord dark UI kit.
 */
class CustomThemeEditorActivity : AppCompatActivity() {

    private lateinit var themeRepo: ThemeRepository
    private lateinit var layoutPrefs: KeyboardLayoutPrefs
    private lateinit var previewView: ThemePreviewView
    private var editingTheme: ThemeModel = BuiltInThemes.MATERIAL_MIDNIGHT

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        themeRepo = ThemeRepository(this)
        layoutPrefs = KeyboardLayoutPrefs(this)

        val themeId = intent.getStringExtra("theme_id")
        editingTheme = if (themeId != null) {
            themeRepo.getThemeById(themeId) ?: themeRepo.getActiveTheme()
        } else {
            themeRepo.getActiveTheme()
        }.copy(id = "custom_" + UUID.randomUUID().toString().take(8), isBuiltIn = false)

        val dp = resources.displayMetrics.density

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(UiKit.BG_CANVAS)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        UiKit.applyEdgeToEdgePadding(root, leftDp = 14f, topDp = 14f, rightDp = 14f, bottomDp = 14f)

        val saveBtn = UiKit.createPillButton(this, "Save Theme", filled = true) {
            promptSaveTheme()
        }
        root.addView(
            UiKit.createHeaderBar(
                this,
                title = "Theme Studio",
                subtitle = "Colors, typography & animated effects",
                showBack = true,
                trailingView = saveBtn
            )
        )

        // Pinned Rounded Preview Card (dynamically grows/shrinks with height!)
        val previewCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = UiKit.roundedDrawable(this@CustomThemeEditorActivity, UiKit.SURFACE_CARD, 16f, UiKit.BORDER_SUBTLE, 1f)
            setPadding((6 * dp).toInt(), (6 * dp).toInt(), (6 * dp).toInt(), (6 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (10 * dp).toInt() }
        }
        previewView = ThemePreviewView(this).apply {
            showNumberRow = layoutPrefs.showNumberRow
            theme = editingTheme
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        previewCard.addView(previewView)
        root.addView(previewCard)

        // Quick jump to Height & Buttons Studio
        val geometryBanner = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = UiKit.roundedDrawable(this@CustomThemeEditorActivity, UiKit.ACCENT_SOFT, 14f, UiKit.ACCENT_PRIMARY, 1f)
            setPadding((14 * dp).toInt(), (10 * dp).toInt(), (14 * dp).toInt(), (10 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (8 * dp).toInt() }
            setOnClickListener {
                startActivity(Intent(this@CustomThemeEditorActivity, KeyboardLayoutCustomizeActivity::class.java))
            }
        }
        geometryBanner.addView(
            TextView(this).apply {
                text = "📐 Need Height, Gaps & Interactive Key Studio?  Tap to open →"
                textSize = 12.5f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(UiKit.TEXT_PRIMARY)
            }
        )
        root.addView(geometryBanner)

        // Controls Scroll
        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            isVerticalScrollBarEnabled = false
        }
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, (4 * dp).toInt(), 0, (24 * dp).toInt())
        }

        // Section: Layout Geometry
        controls.addView(UiKit.createSectionLabel(this, "Layout Geometry"))
        controls.addView(
            UiKit.createSliderCard(this, "Key Height", " dp", editingTheme.keyHeightDp.toInt(), 40, 80) { value ->
                editingTheme = editingTheme.copy(keyHeightDp = value.toFloat())
                updatePreview()
            }
        )
        controls.addView(
            UiKit.createSliderCard(this, "Corner Radius", " dp", editingTheme.keyCornerRadiusDp.toInt(), 0, 24) { value ->
                editingTheme = editingTheme.copy(keyCornerRadiusDp = value.toFloat())
                updatePreview()
            }
        )
        controls.addView(
            UiKit.createSliderCard(this, "Horizontal Key Gap", " dp", editingTheme.keyGapHorizontalDp.toInt(), 0, 8) { value ->
                editingTheme = editingTheme.copy(keyGapHorizontalDp = value.toFloat())
                updatePreview()
            }
        )
        controls.addView(
            UiKit.createSliderCard(this, "Vertical Key Gap", " dp", editingTheme.keyGapVerticalDp.toInt(), 0, 8) { value ->
                editingTheme = editingTheme.copy(keyGapVerticalDp = value.toFloat())
                updatePreview()
            }
        )
        controls.addView(
            UiKit.createSliderCard(this, "Bottom Padding", " dp", editingTheme.bottomPaddingDp.toInt(), 0, 48) { value ->
                editingTheme = editingTheme.copy(bottomPaddingDp = value.toFloat())
                updatePreview()
            }
        )

        // Section: Font & Labels
        controls.addView(UiKit.createSectionLabel(this, "Font & Typography"))
        addFontPicker(controls)
        controls.addView(
            UiKit.createSliderCard(this, "Label Size", " sp", editingTheme.keyLabelSizeSp.toInt(), 10, 20) { value ->
                editingTheme = editingTheme.copy(keyLabelSizeSp = value.toFloat())
                updatePreview()
            }
        )

        // Section: Surface & Colors
        controls.addView(UiKit.createSectionLabel(this, "Colors & Surfaces"))
        addColorPalette(controls, "Key Face Color", listOf(
            "#252736", "#101010", "#FFFFFF", "#3B4252", "#1A0933", "#263B2E", "#E8E0F2", "#1E293B"
        )) { hex ->
            editingTheme = editingTheme.copy(keyFaceColor = ColorSpec.solid(Color.parseColor(hex)))
            updatePreview()
        }
        addColorPalette(controls, "Keyboard Background", listOf(
            "#12121A", "#000000", "#ECEFF1", "#2E3440", "#05000A", "#142119", "#F4EEFB", "#0F172A"
        )) { hex ->
            editingTheme = editingTheme.copy(keyboardBackground = BackgroundSpec.solid(Color.parseColor(hex)))
            updatePreview()
        }
        addColorPalette(controls, "Border Color", listOf(
            "#1AFFFFFF", "#FF007F", "#00FF66", "#88C0D0", "#BD93F9", "#4F8CFF", "#3D5A46", "#334155"
        )) { hex ->
            editingTheme = editingTheme.copy(keyBorderColor = Color.parseColor(hex))
            updatePreview()
        }

        // Section: Gesture Trail
        controls.addView(UiKit.createSectionLabel(this, "Gesture Trail"))
        addColorPalette(controls, "Trail Color", listOf(
            "#4F8CFF", "#00FFFF", "#FF007F", "#00FF66", "#BD93F9", "#FFA07A", "#66BB6A", "#FACC15"
        )) { hex ->
            editingTheme = editingTheme.copy(trailColor = Color.parseColor(hex))
            updatePreview()
        }
        controls.addView(
            UiKit.createSliderCard(this, "Trail Width", " dp", editingTheme.trailWidthDp.toInt(), 4, 24) { value ->
                editingTheme = editingTheme.copy(trailWidthDp = value.toFloat())
                updatePreview()
            }
        )
        controls.addView(
            UiKit.createSliderCard(this, "Trail Fade Duration", " ms", editingTheme.trailFadeDurationMs.toInt(), 100, 800) { value ->
                editingTheme = editingTheme.copy(trailFadeDurationMs = value.toLong())
                updatePreview()
            }
        )

        // Section: Animations & Visual Effects
        controls.addView(UiKit.createSectionLabel(this, "Animations & Visual Effects"))
        addEffectPicker(controls)
        controls.addView(
            UiKit.createSwitchCard(
                this,
                title = "Key Press Effects",
                subtitle = "Render glow, ripple, sparkle or matrix animation on tap",
                checked = editingTheme.keyPressRippleEnabled
            ) { isChecked ->
                editingTheme = editingTheme.copy(keyPressRippleEnabled = isChecked)
                updatePreview()
            }
        )
        controls.addView(
            UiKit.createSwitchCard(
                this,
                title = "Key Pop Preview",
                subtitle = "Show magnified character bubble above pressed key",
                checked = editingTheme.keyPopPreviewEnabled
            ) { isChecked ->
                editingTheme = editingTheme.copy(keyPopPreviewEnabled = isChecked)
                updatePreview()
            }
        )

        scroll.addView(controls)
        root.addView(scroll)
        setContentView(root)
    }

    private fun updatePreview() {
        previewView.theme = editingTheme
    }

    private fun addFontPicker(parent: LinearLayout) {
        addOptionChips(
            parent = parent,
            options = FontChoice.values().toList(),
            labelOf = { it.displayName },
            isSelected = { editingTheme.keyLabelFont == it },
            onSelect = { editingTheme = editingTheme.copy(keyLabelFont = it) }
        )
    }

    private fun addEffectPicker(parent: LinearLayout) {
        addOptionChips(
            parent = parent,
            options = EffectStyle.values().toList(),
            labelOf = { it.displayName },
            isSelected = { editingTheme.effectStyle == it },
            onSelect = { editingTheme = editingTheme.copy(effectStyle = it, keyPressRippleEnabled = true) }
        )
    }

    private fun <T> addOptionChips(
        parent: LinearLayout,
        options: List<T>,
        labelOf: (T) -> String,
        isSelected: (T) -> Boolean,
        onSelect: (T) -> Unit
    ) {
        val dp = resources.displayMetrics.density
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, (2 * dp).toInt(), 0, (10 * dp).toInt())
        }
        val views = mutableListOf<Pair<T, TextView>>()
        fun refreshChipStyles() {
            for ((item, tv) in views) {
                val active = isSelected(item)
                tv.setTextColor(if (active) Color.WHITE else UiKit.TEXT_SECONDARY)
                tv.background = UiKit.roundedDrawable(
                    this,
                    if (active) UiKit.ACCENT_PRIMARY else UiKit.SURFACE_CARD,
                    12f,
                    if (active) null else UiKit.BORDER_SUBTLE,
                    1f
                )
            }
        }
        for (option in options) {
            val btn = TextView(this).apply {
                text = labelOf(option)
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                setPadding((12 * dp).toInt(), (7 * dp).toInt(), (12 * dp).toInt(), (7 * dp).toInt())
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = (6 * dp).toInt() }
                setOnClickListener {
                    onSelect(option)
                    refreshChipStyles()
                    updatePreview()
                }
            }
            views.add(option to btn)
            row.addView(btn)
        }
        refreshChipStyles()
        val scroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(row)
        }
        parent.addView(scroll)
    }

    private fun addColorPalette(parent: LinearLayout, label: String, hexes: List<String>, onPick: (String) -> Unit) {
        val dp = resources.displayMetrics.density
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = UiKit.roundedDrawable(this@CustomThemeEditorActivity, UiKit.SURFACE_CARD, 14f, UiKit.BORDER_SUBTLE, 1f)
            setPadding((14 * dp).toInt(), (10 * dp).toInt(), (14 * dp).toInt(), (12 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (8 * dp).toInt() }
        }
        val tv = TextView(this).apply {
            text = label
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(UiKit.TEXT_PRIMARY)
            textSize = 13.5f
            setPadding(0, 0, 0, (8 * dp).toInt())
        }
        card.addView(tv)

        val paletteRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val swatches = mutableListOf<Pair<String, FrameLayout>>()
        var selectedHex: String? = null
        fun updateBorders() {
            for ((h, sw) in swatches) {
                val isSel = (h == selectedHex)
                sw.background = UiKit.roundedDrawable(
                    this,
                    Color.parseColor(h),
                    10f,
                    if (isSel) UiKit.ACCENT_PRIMARY else UiKit.BORDER_SUBTLE,
                    if (isSel) 2.5f else 1f
                )
            }
        }
        for (hex in hexes) {
            val swatch = FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams((32 * dp).toInt(), (32 * dp).toInt()).apply {
                    marginEnd = (8 * dp).toInt()
                }
                setOnClickListener {
                    selectedHex = hex
                    updateBorders()
                    onPick(hex)
                }
            }
            swatches.add(hex to swatch)
            paletteRow.addView(swatch)
        }
        updateBorders()
        val scroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(paletteRow)
        }
        card.addView(scroll)
        parent.addView(card)
    }

    private fun promptSaveTheme() {
        val input = EditText(this).apply {
            hint = "Theme Name"
            setText(editingTheme.name)
        }
        AlertDialog.Builder(this)
            .setTitle("Save Custom Theme")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    val finalTheme = editingTheme.copy(name = name)
                    themeRepo.saveCustomTheme(finalTheme)
                    themeRepo.setActiveTheme(finalTheme.id)
                    Toast.makeText(this, "Theme '${finalTheme.name}' saved and applied!", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
