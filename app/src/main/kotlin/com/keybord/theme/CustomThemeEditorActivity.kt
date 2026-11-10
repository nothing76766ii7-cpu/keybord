package com.keybord.theme

import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.util.UUID

/**
 * Visual Customization Theme Editor exposing all 20+ visual parameters with live preview.
 */
class CustomThemeEditorActivity : AppCompatActivity() {

    private lateinit var themeRepo: ThemeRepository
    private lateinit var previewView: ThemePreviewView
    private var editingTheme: ThemeModel = BuiltInThemes.MATERIAL_MIDNIGHT

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        themeRepo = ThemeRepository(this)

        val themeId = intent.getStringExtra("theme_id")
        editingTheme = if (themeId != null) {
            themeRepo.getThemeById(themeId) ?: themeRepo.getActiveTheme()
        } else {
            themeRepo.getActiveTheme()
        }.copy(id = "custom_" + UUID.randomUUID().toString().take(8), isBuiltIn = false)

        val dp = resources.displayMetrics.density

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#12121A"))
            setPadding((12 * dp).toInt(), (12 * dp).toInt(), (12 * dp).toInt(), (12 * dp).toInt())
        }

        // Action Bar (Top)
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, (8 * dp).toInt())
        }
        val title = TextView(this).apply {
            text = "Custom Theme Editor"
            setTextColor(Color.WHITE)
            textSize = 18f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val saveBtn = Button(this).apply {
            text = "Save Theme"
            textSize = 12f
            setOnClickListener { promptSaveTheme() }
        }
        topBar.addView(title)
        topBar.addView(saveBtn)
        root.addView(topBar)

        // Live Preview (pinned at top)
        previewView = ThemePreviewView(this).apply {
            theme = editingTheme
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (200 * dp).toInt()
            )
        }
        root.addView(previewView)

        // Controls Scroll
        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, (12 * dp).toInt(), 0, (20 * dp).toInt())
        }

        // Section: Layout Geometry
        addSectionHeader(controls, "📐 Layout Geometry")
        addSliderControl(controls, "Key Height", editingTheme.keyHeightDp.toInt(), 40, 80) { value ->
            editingTheme = editingTheme.copy(keyHeightDp = value.toFloat())
            updatePreview()
        }
        addSliderControl(controls, "Corner Radius", editingTheme.keyCornerRadiusDp.toInt(), 0, 24) { value ->
            editingTheme = editingTheme.copy(keyCornerRadiusDp = value.toFloat())
            updatePreview()
        }
        addSliderControl(controls, "Horizontal Key Gap", editingTheme.keyGapHorizontalDp.toInt(), 0, 8) { value ->
            editingTheme = editingTheme.copy(keyGapHorizontalDp = value.toFloat())
            updatePreview()
        }
        addSliderControl(controls, "Vertical Key Gap", editingTheme.keyGapVerticalDp.toInt(), 0, 8) { value ->
            editingTheme = editingTheme.copy(keyGapVerticalDp = value.toFloat())
            updatePreview()
        }
        addSliderControl(controls, "Bottom Padding", editingTheme.bottomPaddingDp.toInt(), 0, 48) { value ->
            editingTheme = editingTheme.copy(bottomPaddingDp = value.toFloat())
            updatePreview()
        }

        // Section: Font & Labels
        addSectionHeader(controls, "🔤 Font & Typography")
        addFontPicker(controls)
        addSliderControl(controls, "Label Size (sp)", editingTheme.keyLabelSizeSp.toInt(), 10, 18) { value ->
            editingTheme = editingTheme.copy(keyLabelSizeSp = value.toFloat())
            updatePreview()
        }

        // Section: Surface & Colors
        addSectionHeader(controls, "🎨 Colors & Surfaces")
        addColorPalette(controls, "Key Face Color", listOf(
            "#252736", "#101010", "#FFFFFF", "#3B4252", "#1A0933", "#263B2E", "#E8E0F2"
        )) { hex ->
            editingTheme = editingTheme.copy(keyFaceColor = ColorSpec.solid(Color.parseColor(hex)))
            updatePreview()
        }
        addColorPalette(controls, "Keyboard Background", listOf(
            "#12121A", "#000000", "#ECEFF1", "#2E3440", "#05000A", "#142119", "#F4EEFB"
        )) { hex ->
            editingTheme = editingTheme.copy(keyboardBackground = BackgroundSpec.solid(Color.parseColor(hex)))
            updatePreview()
        }
        addColorPalette(controls, "Border Color", listOf(
            "#1AFFFFFF", "#FF007F", "#00FF66", "#88C0D0", "#BD93F9", "#444285F4", "#3D5A46"
        )) { hex ->
            editingTheme = editingTheme.copy(keyBorderColor = Color.parseColor(hex))
            updatePreview()
        }

        // Section: Gesture Trail
        addSectionHeader(controls, "✨ Gesture Trail")
        addColorPalette(controls, "Trail Color", listOf(
            "#4285F4", "#00FFFF", "#FF007F", "#00FF66", "#BD93F9", "#FFA07A", "#66BB6A"
        )) { hex ->
            editingTheme = editingTheme.copy(trailColor = Color.parseColor(hex))
            updatePreview()
        }
        addSliderControl(controls, "Trail Width (dp)", editingTheme.trailWidthDp.toInt(), 4, 24) { value ->
            editingTheme = editingTheme.copy(trailWidthDp = value.toFloat())
            updatePreview()
        }
        addSliderControl(controls, "Trail Fade Duration (ms)", editingTheme.trailFadeDurationMs.toInt(), 100, 800) { value ->
            editingTheme = editingTheme.copy(trailFadeDurationMs = value.toLong())
            updatePreview()
        }

        // Section: Animations & Previews
        addSectionHeader(controls, "⚡ Animations")
        addSwitchControl(controls, "Key Press Ripple", editingTheme.keyPressRippleEnabled) { isChecked ->
            editingTheme = editingTheme.copy(keyPressRippleEnabled = isChecked)
        }
        addSwitchControl(controls, "Key Pop Preview", editingTheme.keyPopPreviewEnabled) { isChecked ->
            editingTheme = editingTheme.copy(keyPopPreviewEnabled = isChecked)
        }

        scroll.addView(controls)
        root.addView(scroll)
        setContentView(root)
    }

    private fun updatePreview() {
        previewView.theme = editingTheme
    }

    private fun addSectionHeader(parent: LinearLayout, title: String) {
        val dp = resources.displayMetrics.density
        val tv = TextView(this).apply {
            text = title
            setTextColor(Color.parseColor("#4285F4"))
            textSize = 15f
            setPadding(0, (14 * dp).toInt(), 0, (6 * dp).toInt())
        }
        parent.addView(tv)
    }

    private fun addSliderControl(
        parent: LinearLayout,
        label: String,
        current: Int,
        min: Int,
        max: Int,
        onChange: (Int) -> Unit
    ) {
        val dp = resources.displayMetrics.density
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, (4 * dp).toInt(), 0, (4 * dp).toInt())
        }

        val tvLabel = TextView(this).apply {
            text = "$label: $current"
            setTextColor(Color.WHITE)
            textSize = 13f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val seek = SeekBar(this).apply {
            this.max = max - min
            progress = current - min
            layoutParams = LinearLayout.LayoutParams((180 * dp).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val value = min + progress
                    tvLabel.text = "$label: $value"
                    onChange(value)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }

        row.addView(tvLabel)
        row.addView(seek)
        parent.addView(row)
    }

    private fun addFontPicker(parent: LinearLayout) {
        val dp = resources.displayMetrics.density
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, (6 * dp).toInt(), 0, (6 * dp).toInt())
        }
        val fonts = FontChoice.values()
        for (f in fonts) {
            val btn = TextView(this).apply {
                text = f.displayName.take(8)
                textSize = 11f
                setPadding((8 * dp).toInt(), (4 * dp).toInt(), (8 * dp).toInt(), (4 * dp).toInt())
                setTextColor(if (editingTheme.keyLabelFont == f) Color.WHITE else Color.parseColor("#8888AA"))
                setBackgroundColor(if (editingTheme.keyLabelFont == f) Color.parseColor("#4285F4") else Color.parseColor("#1E1E2C"))
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    marginEnd = (4 * dp).toInt()
                }
                layoutParams = lp
                setOnClickListener {
                    editingTheme = editingTheme.copy(keyLabelFont = f)
                    updatePreview()
                }
            }
            row.addView(btn)
        }
        parent.addView(row)
    }

    private fun addColorPalette(parent: LinearLayout, label: String, hexes: List<String>, onPick: (String) -> Unit) {
        val dp = resources.displayMetrics.density
        val tv = TextView(this).apply {
            text = label
            setTextColor(Color.parseColor("#CCCCCC"))
            textSize = 13f
            setPadding(0, (6 * dp).toInt(), 0, (4 * dp).toInt())
        }
        parent.addView(tv)

        val paletteRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, (6 * dp).toInt())
        }

        for (hex in hexes) {
            val swatch = FrameLayout(this).apply {
                setBackgroundColor(Color.parseColor(hex))
                layoutParams = LinearLayout.LayoutParams((32 * dp).toInt(), (32 * dp).toInt()).apply {
                    marginEnd = (8 * dp).toInt()
                }
                setOnClickListener { onPick(hex) }
            }
            paletteRow.addView(swatch)
        }
        parent.addView(paletteRow)
    }

    private fun addSwitchControl(parent: LinearLayout, label: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
        val dp = resources.displayMetrics.density
        val sw = Switch(this).apply {
            text = label
            setTextColor(Color.WHITE)
            textSize = 14f
            isChecked = checked
            setPadding(0, (4 * dp).toInt(), 0, (4 * dp).toInt())
            setOnCheckedChangeListener { _, isChecked -> onToggle(isChecked) }
        }
        parent.addView(sw)
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
