package com.keyboard

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*

/**
 * Custom Theme Editor with live preview updating < 200ms per Section B.3.
 */
class CustomThemeEditorActivity : Activity() {

    private lateinit var themeEngine: ThemeEngine
    private val workingParams = mutableMapOf<String, String>()

    // Live preview view
    private lateinit var previewContainer: LinearLayout
    private val previewKeys = mutableListOf<TextView>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        themeEngine = ThemeEngine(this)
        workingParams.putAll(themeEngine.currentTheme.params)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#181825"))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // Toolbar: Title + Save + Export + Import
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#1E1E2E"))
            setPadding(24, 20, 24, 20)
        }

        val title = TextView(this).apply {
            text = "Theme Editor"
            setTextColor(Color.WHITE)
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        toolbar.addView(title)

        val importBtn = createActionBtn("Import") { showImportDialog() }
        val exportBtn = createActionBtn("Export") { exportActiveTheme() }
        val saveBtn = createActionBtn("Save") { showSaveDialog() }

        toolbar.addView(importBtn)
        toolbar.addView(exportBtn)
        toolbar.addView(saveBtn)
        root.addView(toolbar)

        // Live Preview Keyboard Frame (Section B.3: Updates < 200ms)
        previewContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(16, 24, 16, 24)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        root.addView(previewContainer)
        updatePreview()

        // Scrollable Controls
        val controlsScroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1.0f
            )
        }

        val controlsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 16, 24, 32)
        }
        controlsScroll.addView(controlsContainer)
        root.addView(controlsScroll)

        // Section "Layout"
        controlsContainer.addView(createSectionHeader("Layout Geometry"))
        controlsContainer.addView(createSliderControl("Key Height (dp)", 40, 80, "key_height_dp", 56))
        controlsContainer.addView(createSliderControl("Corner Radius (dp)", 0, 24, "key_corner_radius_dp", 8))
        controlsContainer.addView(createSliderControl("Horizontal Gap (dp)", 0, 8, "key_gap_horizontal_dp", 3))
        controlsContainer.addView(createSliderControl("Vertical Gap (dp)", 0, 8, "key_gap_vertical_dp", 3))
        controlsContainer.addView(createSliderControl("Bottom Padding (dp)", 0, 32, "bottom_padding_dp", 8))

        // Section "Colours"
        controlsContainer.addView(createSectionHeader("Colours & Surfaces"))
        controlsContainer.addView(createColorPickerControl("Key Face Colour", "key_face_color", "#313244"))
        controlsContainer.addView(createColorPickerControl("Keyboard Background", "keyboard_background", "#181825"))
        controlsContainer.addView(createColorPickerControl("Key Border Colour", "key_border_color", "#45475A"))
        controlsContainer.addView(createColorPickerControl("Key Label Colour", "key_label_color", "#CDD6F4"))
        controlsContainer.addView(createColorPickerControl("Suggestion Strip Bg", "strip_background_color", "#1E1E2E"))

        // Section "Font"
        controlsContainer.addView(createSectionHeader("Font & Typography"))
        controlsContainer.addView(createFontPickerControl())
        controlsContainer.addView(createSliderControl("Label Size (sp)", 10, 18, "key_label_size_sp", 14))

        // Section "Trail"
        controlsContainer.addView(createSectionHeader("Gesture Trail"))
        controlsContainer.addView(createColorPickerControl("Trail Colour", "trail_color", "#4285F4"))
        controlsContainer.addView(createSliderControl("Trail Width (dp)", 4, 24, "trail_width_dp", 14))
        controlsContainer.addView(createSliderControl("Trail Fade (ms)", 100, 600, "trail_fade_ms", 350))

        // Section "Effects"
        controlsContainer.addView(createSectionHeader("Effects & Animations"))
        controlsContainer.addView(createToggleControl("Key Press Ripple", "key_press_ripple", true))
        controlsContainer.addView(createToggleControl("Key Pop Preview", "key_pop_preview", true))

        setContentView(root)
    }

    private fun createActionBtn(text: String, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            this.text = text
            setTextColor(Color.parseColor("#89B4FA"))
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(16, 8, 16, 8)
            setOnClickListener { onClick() }
        }
    }

    private fun createSectionHeader(title: String): TextView {
        return TextView(this).apply {
            text = title
            setTextColor(Color.parseColor("#A6ADC8"))
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 24, 0, 8)
        }
    }

    private fun createSliderControl(label: String, min: Int, max: Int, paramKey: String, default: Int): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 8, 0, 8)
        }

        val currentVal = workingParams[paramKey]?.toIntOrNull() ?: default
        val labelTv = TextView(this).apply {
            text = "$label: $currentVal"
            setTextColor(Color.WHITE)
            textSize = 13f
        }
        row.addView(labelTv)

        val seekBar = SeekBar(this).apply {
            this.max = max - min
            progress = (currentVal - min).coerceAtLeast(0)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    val newVal = min + p
                    labelTv.text = "$label: $newVal"
                    workingParams[paramKey] = newVal.toString()
                    updatePreview()
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        row.addView(seekBar)
        return row
    }

    private fun createColorPickerControl(label: String, paramKey: String, default: String): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 12, 0, 12)
        }

        val labelTv = TextView(this).apply {
            text = label
            setTextColor(Color.WHITE)
            textSize = 13f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        row.addView(labelTv)

        val colorPreview = View(this).apply {
            val hex = workingParams[paramKey] ?: default
            try { setBackgroundColor(Color.parseColor(hex)) } catch (e: Exception) {}
            layoutParams = LinearLayout.LayoutParams(48, 48).apply { setMargins(8, 0, 8, 0) }
        }
        row.addView(colorPreview)

        val editBtn = Button(this).apply {
            text = "Pick"
            textSize = 11f
            setOnClickListener {
                showColorInputDialog(label, paramKey) { newColor ->
                    workingParams[paramKey] = newColor
                    try { colorPreview.setBackgroundColor(Color.parseColor(newColor)) } catch (e: Exception) {}
                    updatePreview()
                }
            }
        }
        row.addView(editBtn)
        return row
    }

    private fun createToggleControl(label: String, paramKey: String, default: Boolean): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 8, 0, 8)
        }

        val labelTv = TextView(this).apply {
            text = label
            setTextColor(Color.WHITE)
            textSize = 13f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        row.addView(labelTv)

        val switchView = Switch(this).apply {
            isChecked = workingParams[paramKey]?.toBooleanStrictOrNull() ?: default
            setOnCheckedChangeListener { _, isChecked ->
                workingParams[paramKey] = isChecked.toString()
                updatePreview()
            }
        }
        row.addView(switchView)
        return row
    }

    private fun createFontPickerControl(): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 8, 0, 8)
        }

        val labelTv = TextView(this).apply {
            text = "Font Family"
            setTextColor(Color.WHITE)
            textSize = 13f
        }
        row.addView(labelTv)

        val fonts = listOf("Inter", "Roboto", "JetBrainsMono", "Nunito", "Raleway", "Ubuntu")
        val spinner = Spinner(this)
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, fonts)
        spinner.adapter = adapter
        val currentFont = workingParams["key_label_font"] ?: "Roboto"
        val idx = fonts.indexOf(currentFont).coerceAtLeast(0)
        spinner.setSelection(idx)
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                workingParams["key_label_font"] = fonts[pos]
                updatePreview()
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        row.addView(spinner)
        return row
    }

    private fun showColorInputDialog(title: String, paramKey: String, onColorSelected: (String) -> Unit) {
        val input = EditText(this).apply {
            hint = "#RRGGBB"
            setText(workingParams[paramKey] ?: "#4285F4")
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(input)
            .setPositiveButton("OK") { _, _ ->
                val hex = input.text.toString().trim()
                if (hex.startsWith("#") && (hex.length == 7 || hex.length == 9)) {
                    onColorSelected(hex)
                } else {
                    Toast.makeText(this, "Invalid hex colour format", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // Live preview updates < 200ms
    private fun updatePreview() {
        previewContainer.removeAllViews()
        previewKeys.clear()

        val bgHex = workingParams["keyboard_background"] ?: "#181825"
        val faceHex = workingParams["key_face_color"] ?: "#313244"
        val textHex = workingParams["key_label_color"] ?: "#CDD6F4"
        val cornerRadius = workingParams["key_corner_radius_dp"]?.toIntOrNull() ?: 8
        val keyHeight = workingParams["key_height_dp"]?.toIntOrNull() ?: 56

        try { previewContainer.setBackgroundColor(Color.parseColor(bgHex)) } catch (e: Exception) {}

        val previewRows = listOf("qwerty", "asdfgh", "zxcvbn")
        for (rowChars in previewRows) {
            val rowLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                setPadding(0, 4, 0, 4)
            }
            for (ch in rowChars) {
                val key = TextView(this).apply {
                    text = ch.uppercase()
                    try { setTextColor(Color.parseColor(textHex)) } catch (e: Exception) {}
                    try { setBackgroundColor(Color.parseColor(faceHex)) } catch (e: Exception) {}
                    textSize = 14f
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(54, (keyHeight * 0.75).toInt()).apply {
                        setMargins(3, 0, 3, 0)
                    }
                }
                previewKeys.add(key)
                rowLayout.addView(key)
            }
            previewContainer.addView(rowLayout)
        }
    }

    private fun showSaveDialog() {
        val input = EditText(this).apply {
            hint = "Theme Name"
            setText("My Custom Theme")
        }
        AlertDialog.Builder(this)
            .setTitle("Save Theme")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    val theme = ThemeEngine.KeybordTheme(name, 1, workingParams)
                    themeEngine.saveCustomTheme(theme)
                    themeEngine.applyTheme(theme)
                    Toast.makeText(this, "Theme '$name' saved and applied!", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun exportActiveTheme() {
        val theme = ThemeEngine.KeybordTheme("ExportedTheme", 1, workingParams)
        val json = themeEngine.exportTheme(theme)

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_TEXT, json)
            putExtra(Intent.EXTRA_SUBJECT, "Theme.keybordtheme")
        }
        startActivity(Intent.createChooser(intent, "Share Theme (.keybordtheme)"))
    }

    private fun showImportDialog() {
        val input = EditText(this).apply {
            hint = "Paste .keybordtheme JSON here"
            minLines = 4
        }
        AlertDialog.Builder(this)
            .setTitle("Import Theme")
            .setView(input)
            .setPositiveButton("Import") { _, _ ->
                val json = input.text.toString().trim()
                themeEngine.importTheme(json).onSuccess { theme ->
                    workingParams.clear()
                    workingParams.putAll(theme.params)
                    themeEngine.applyTheme(theme)
                    updatePreview()
                    Toast.makeText(this, "Imported '${theme.name}' successfully!", Toast.LENGTH_SHORT).show()
                }.onFailure { err ->
                    Toast.makeText(this, "Import failed: ${err.message}", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
