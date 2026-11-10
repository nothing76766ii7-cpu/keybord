package com.keybord.theme

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.keybord.settings.KeyboardLayoutCustomizeActivity
import com.keybord.ui.UiKit
import java.io.File

/**
 * Stitch Screen 05: Appearance & Theme Studio
 * Live top keyboard preview card, quick circular Accent Color override swatches,
 * Height & Geometry studio banner, category filter pills, and Preset Themes gallery.
 */
class ThemeGalleryActivity : AppCompatActivity() {

    private lateinit var themeRepo: ThemeRepository
    private lateinit var layoutPrefs: KeyboardLayoutPrefs
    private lateinit var livePreview: ThemePreviewView
    private lateinit var liveThemeCaption: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var categoryBar: LinearLayout
    private var selectedCategory: ThemeCategory = ThemeCategory.ALL
    private val PICK_THEME_FILE_REQUEST = 1001

    private val accentSwatches = listOf(
        Color.parseColor("#4F8BFF"),
        Color.parseColor("#7C5CFF"),
        Color.parseColor("#10B981"),
        Color.parseColor("#F59E0B"),
        Color.parseColor("#EC4899"),
        0 // 0 resets to theme default
    )

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
        UiKit.applyEdgeToEdgePadding(root, leftDp = 14f, topDp = 14f, rightDp = 14f, bottomDp = 14f)

        val actionGroup = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val importBtn = UiKit.createPillButton(this, "Import", filled = false) {
            showImportDialog()
        }.apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = (6 * dp).toInt()
            }
        }
        val createBtn = UiKit.createPillButton(this, "+ Custom", filled = true) {
            startActivity(Intent(this@ThemeGalleryActivity, CustomThemeEditorActivity::class.java))
        }
        actionGroup.addView(importBtn)
        actionGroup.addView(createBtn)

        root.addView(
            UiKit.createHeaderBar(
                this,
                title = "Appearance",
                subtitle = "Live preview, accent colors & themes",
                showBack = true,
                trailingView = actionGroup
            )
        )

        // Stitch 05 Live Preview + Accent Color Studio Header Card
        val previewCard = UiKit.createCardContainer(this, paddingDp = 10f, radiusDp = 18f, bottomMarginDp = 10f)
        val previewHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((4 * dp).toInt(), 0, (4 * dp).toInt(), (6 * dp).toInt())
        }
        liveThemeCaption = TextView(this).apply {
            text = "LIVE PREVIEW"
            textSize = 11f
            letterSpacing = 0.08f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(UiKit.TEXT_SECONDARY)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val layoutStudioPill = UiKit.createPillButton(this, "📐 Height & Keys", filled = false) {
            startActivity(Intent(this, KeyboardLayoutCustomizeActivity::class.java))
        }.apply {
            textSize = 11.5f
            setPadding((10 * dp).toInt(), (5 * dp).toInt(), (10 * dp).toInt(), (5 * dp).toInt())
        }
        previewHeader.addView(liveThemeCaption)
        previewHeader.addView(layoutStudioPill)
        previewCard.addView(previewHeader)

        val previewFrame = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = UiKit.roundedDrawable(this@ThemeGalleryActivity, UiKit.SURFACE_INPUT, 12f, UiKit.BORDER_SUBTLE, 1f)
            setPadding((4 * dp).toInt(), (4 * dp).toInt(), (4 * dp).toInt(), (4 * dp).toInt())
        }
        livePreview = ThemePreviewView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (124 * dp).toInt())
        }
        previewFrame.addView(livePreview)
        previewCard.addView(previewFrame)

        // Circular Accent Color Swatch Bar (Stitch 05)
        previewCard.addView(buildAccentSwatchRow())
        root.addView(previewCard)

        // Category Filter Chips
        categoryBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, (8 * dp).toInt())
        }
        val categoryScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            addView(categoryBar)
        }
        root.addView(categoryScroll)
        renderCategoryChips()

        // Grid of Themes
        recyclerView = RecyclerView(this).apply {
            layoutManager = GridLayoutManager(this@ThemeGalleryActivity, 2)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }
        root.addView(recyclerView)

        setContentView(root)
        refreshLiveHeader()
        handleIncomingThemeIntent(intent)
    }

    private fun buildAccentSwatchRow(): LinearLayout {
        val dp = resources.displayMetrics.density
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((4 * dp).toInt(), (10 * dp).toInt(), (4 * dp).toInt(), (2 * dp).toInt())
        }
        wrap.addView(
            TextView(this).apply {
                text = "Accent"
                textSize = 12.5f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(UiKit.TEXT_SECONDARY)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    marginEnd = (10 * dp).toInt()
                }
            }
        )

        val dotViews = mutableListOf<Pair<Int, TextView>>()
        fun syncDots() {
            val currentOverride = layoutPrefs.accentColorOverride
            for ((colorInt, tv) in dotViews) {
                val active = (colorInt == currentOverride)
                if (colorInt == 0) {
                    tv.text = "↺"
                    tv.setTextColor(if (active) Color.WHITE else UiKit.TEXT_SECONDARY)
                    tv.background = UiKit.roundedDrawable(
                        this,
                        if (active) UiKit.ACCENT_PRIMARY else UiKit.SURFACE_ELEVATED,
                        999f,
                        UiKit.BORDER_SUBTLE,
                        1f
                    )
                } else {
                    tv.text = if (active) "✓" else ""
                    tv.setTextColor(if (Color.luminance(colorInt) > 0.55f) Color.BLACK else Color.WHITE)
                    tv.background = UiKit.roundedDrawable(
                        this,
                        colorInt,
                        999f,
                        if (active) Color.WHITE else null,
                        if (active) 2.5f else 0f
                    )
                }
            }
        }

        for (colorInt in accentSwatches) {
            val dot = TextView(this).apply {
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                val sz = (28 * dp).toInt()
                layoutParams = LinearLayout.LayoutParams(sz, sz).apply { marginEnd = (8 * dp).toInt() }
                setOnClickListener {
                    layoutPrefs.accentColorOverride = colorInt
                    syncDots()
                    refreshLiveHeader()
                    refreshThemes()
                }
            }
            dotViews.add(colorInt to dot)
            wrap.addView(dot)
        }
        syncDots()
        return wrap
    }

    private fun refreshLiveHeader() {
        val raw = themeRepo.getActiveTheme()
        val effective = layoutPrefs.applyTo(raw)
        livePreview.showNumberRow = layoutPrefs.showNumberRow
        livePreview.theme = effective
        liveThemeCaption.text = "ACTIVE • ${raw.name.uppercase()}"
    }

    private fun handleIncomingThemeIntent(incoming: Intent?) {
        val uri = incoming?.data ?: return
        if (incoming.action != Intent.ACTION_VIEW) return
        try {
            val json = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: return
            val imported = themeRepo.importThemeFromJson(json)
            themeRepo.setActiveTheme(imported.id)
            Toast.makeText(this, "Imported & applied '${imported.name}'!", Toast.LENGTH_SHORT).show()
            refreshLiveHeader()
        } catch (e: Exception) {
            Toast.makeText(this, "Invalid theme file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshLiveHeader()
        refreshThemes()
    }

    private fun renderCategoryChips() {
        val dp = resources.displayMetrics.density
        categoryBar.removeAllViews()
        for (cat in ThemeCategory.values()) {
            val isSelected = (cat == selectedCategory)
            val chip = TextView(this).apply {
                text = cat.label
                textSize = 12f
                typeface = if (isSelected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                setTextColor(if (isSelected) Color.WHITE else UiKit.TEXT_SECONDARY)
                background = UiKit.roundedDrawable(
                    this@ThemeGalleryActivity,
                    if (isSelected) UiKit.ACCENT_PRIMARY else UiKit.SURFACE_CARD,
                    12f,
                    if (isSelected) null else UiKit.BORDER_SUBTLE,
                    1f
                )
                setPadding((12 * dp).toInt(), (7 * dp).toInt(), (12 * dp).toInt(), (7 * dp).toInt())
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = (8 * dp).toInt() }
                setOnClickListener {
                    selectedCategory = cat
                    renderCategoryChips()
                    refreshThemes()
                }
            }
            categoryBar.addView(chip)
        }
    }

    private fun refreshThemes() {
        val allThemes = themeRepo.getAllThemes()
        val themes = if (selectedCategory == ThemeCategory.ALL) {
            allThemes
        } else {
            allThemes.filter { it.category == selectedCategory || (selectedCategory == ThemeCategory.EFFECTS && it.effectStyle != EffectStyle.NONE) }
        }
        val activeTheme = themeRepo.getActiveTheme()

        recyclerView.adapter = ThemeGridAdapter(
            themes = themes,
            activeThemeId = activeTheme.id,
            showNumberRow = layoutPrefs.showNumberRow,
            onThemeSelected = { theme ->
                themeRepo.setActiveTheme(theme.id)
                refreshLiveHeader()
                Toast.makeText(this, "Theme '${theme.name}' applied!", Toast.LENGTH_SHORT).show()
                refreshThemes()
            },
            onThemeEdit = { theme ->
                val intent = Intent(this, CustomThemeEditorActivity::class.java).apply {
                    putExtra("theme_id", theme.id)
                }
                startActivity(intent)
            },
            onThemeExport = { theme ->
                exportTheme(theme)
            },
            onThemeDelete = { theme ->
                if (!theme.isBuiltIn) {
                    AlertDialog.Builder(this)
                        .setTitle("Delete Theme")
                        .setMessage("Delete '${theme.name}'?")
                        .setPositiveButton("Delete") { _, _ ->
                            themeRepo.deleteCustomTheme(theme.id)
                            refreshLiveHeader()
                            refreshThemes()
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            }
        )
    }

    private fun showImportDialog() {
        val options = arrayOf("Paste .keybordtheme JSON", "Pick .keybordtheme File")
        AlertDialog.Builder(this)
            .setTitle("Import Theme")
            .setItems(options) { _, which ->
                if (which == 0) {
                    val input = EditText(this).apply {
                        hint = "Paste JSON here..."
                        maxLines = 10
                    }
                    AlertDialog.Builder(this)
                        .setTitle("Paste Theme JSON")
                        .setView(input)
                        .setPositiveButton("Import") { _, _ ->
                            try {
                                val theme = themeRepo.importThemeFromJson(input.text.toString().trim())
                                Toast.makeText(this, "Successfully imported '${theme.name}'!", Toast.LENGTH_SHORT).show()
                                refreshThemes()
                            } catch (e: ThemeValidationException) {
                                AlertDialog.Builder(this)
                                    .setTitle("Theme Import Error")
                                    .setMessage(e.message)
                                    .setPositiveButton("OK", null)
                                    .show()
                            }
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                } else {
                    val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                        type = "*/*"
                    }
                    startActivityForResult(intent, PICK_THEME_FILE_REQUEST)
                }
            }
            .show()
    }

    private fun exportTheme(theme: ThemeModel) {
        val json = ThemeSerializer.toJson(theme)
        val exportFile = File(getExternalFilesDir(null) ?: filesDir, "${theme.name.replace(" ", "_")}.keybordtheme")
        exportFile.writeText(json)

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_SUBJECT, "Keybord Theme: ${theme.name}")
            putExtra(Intent.EXTRA_TEXT, json)
        }
        startActivity(Intent.createChooser(shareIntent, "Export Theme '${theme.name}'"))
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_THEME_FILE_REQUEST && resultCode == Activity.RESULT_OK) {
            val uri = data?.data ?: return
            try {
                val json = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: return
                val theme = themeRepo.importThemeFromJson(json)
                Toast.makeText(this, "Imported '${theme.name}'!", Toast.LENGTH_SHORT).show()
                refreshThemes()
            } catch (e: ThemeValidationException) {
                AlertDialog.Builder(this)
                    .setTitle("Theme Import Error")
                    .setMessage(e.message)
                    .setPositiveButton("OK", null)
                    .show()
            } catch (e: Exception) {
                Toast.makeText(this, "Failed to read file: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private class ThemeGridAdapter(
        private val themes: List<ThemeModel>,
        private val activeThemeId: String,
        private val showNumberRow: Boolean,
        private val onThemeSelected: (ThemeModel) -> Unit,
        private val onThemeEdit: (ThemeModel) -> Unit,
        private val onThemeExport: (ThemeModel) -> Unit,
        private val onThemeDelete: (ThemeModel) -> Unit
    ) : RecyclerView.Adapter<ThemeGridAdapter.ViewHolder>() {

        class ViewHolder(
            val view: View,
            val preview: ThemePreviewView,
            val title: TextView,
            val checkBadge: TextView,
            val effectBadge: TextView,
            val moreBtn: TextView
        ) : RecyclerView.ViewHolder(view)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val dp = parent.context.resources.displayMetrics.density
            val card = LinearLayout(parent.context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = MarginLayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (170 * dp).toInt()).apply {
                    setMargins((5 * dp).toInt(), (5 * dp).toInt(), (5 * dp).toInt(), (5 * dp).toInt())
                }
                setPadding((7 * dp).toInt(), (7 * dp).toInt(), (7 * dp).toInt(), (7 * dp).toInt())
            }

            val preview = ThemePreviewView(parent.context).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (108 * dp).toInt())
            }

            val bottomRow = LinearLayout(parent.context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding((4 * dp).toInt(), (7 * dp).toInt(), (2 * dp).toInt(), (2 * dp).toInt())
            }

            val checkBadge = TextView(parent.context).apply {
                text = "✓ "
                setTextColor(UiKit.ACCENT_PRIMARY)
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                visibility = View.GONE
            }

            val title = TextView(parent.context).apply {
                setTextColor(UiKit.TEXT_PRIMARY)
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                maxLines = 1
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }

            val effectBadge = TextView(parent.context).apply {
                setTextColor(UiKit.ACCENT_PRIMARY)
                background = UiKit.roundedDrawable(parent.context, UiKit.ACCENT_SOFT, 6f)
                textSize = 10f
                typeface = Typeface.DEFAULT_BOLD
                setPadding((6 * dp).toInt(), (2 * dp).toInt(), (6 * dp).toInt(), (2 * dp).toInt())
                visibility = View.GONE
            }

            val moreBtn = TextView(parent.context).apply {
                text = "⋮"
                setTextColor(UiKit.TEXT_SECONDARY)
                textSize = 16f
                setPadding((8 * dp).toInt(), 0, (4 * dp).toInt(), 0)
            }

            bottomRow.addView(checkBadge)
            bottomRow.addView(title)
            bottomRow.addView(effectBadge)
            bottomRow.addView(moreBtn)

            card.addView(preview)
            card.addView(bottomRow)

            return ViewHolder(card, preview, title, checkBadge, effectBadge, moreBtn)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val theme = themes[position]
            val isActive = (theme.id == activeThemeId)

            holder.preview.showNumberRow = showNumberRow
            holder.preview.theme = theme
            holder.title.text = theme.name
            holder.checkBadge.visibility = if (isActive) View.VISIBLE else View.GONE
            if (theme.effectStyle != EffectStyle.NONE && theme.effectStyle.badge.isNotEmpty()) {
                holder.effectBadge.text = theme.effectStyle.badge
                holder.effectBadge.visibility = View.VISIBLE
            } else {
                holder.effectBadge.visibility = View.GONE
            }

            holder.view.background = UiKit.roundedDrawable(
                holder.view.context,
                if (isActive) Color.parseColor("#1D273B") else UiKit.SURFACE_CARD,
                16f,
                if (isActive) UiKit.ACCENT_PRIMARY else UiKit.BORDER_SUBTLE,
                if (isActive) 2f else 1f
            )

            holder.view.setOnClickListener { onThemeSelected(theme) }

            holder.moreBtn.setOnClickListener { v ->
                val popup = PopupMenu(v.context, v)
                popup.menu.add("Apply")
                popup.menu.add("Export (.keybordtheme)")
                popup.menu.add("Duplicate / Edit")
                if (!theme.isBuiltIn) {
                    popup.menu.add("Delete")
                }
                popup.setOnMenuItemClickListener { item ->
                    when (item.title) {
                        "Apply" -> onThemeSelected(theme)
                        "Export (.keybordtheme)" -> onThemeExport(theme)
                        "Duplicate / Edit" -> onThemeEdit(theme)
                        "Delete" -> onThemeDelete(theme)
                    }
                    true
                }
                popup.show()
            }
        }

        override fun getItemCount(): Int = themes.size
    }
}
