package com.keybord.theme

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File

fun View.applySystemBarPadding(leftPx: Int, topPx: Int, rightPx: Int, bottomPx: Int) {
    ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        v.setPadding(leftPx + bars.left, topPx + bars.top, rightPx + bars.right, bottomPx + bars.bottom)
        insets
    }
    ViewCompat.requestApplyInsets(this)
}

/**
 * Gallery displaying all built-in, extracted, and user-created themes with category filtering.
 * Supports 1-tap activation, theme creation, import and export.
 */
class ThemeGalleryActivity : AppCompatActivity() {

    private lateinit var themeRepo: ThemeRepository
    private lateinit var recyclerView: RecyclerView
    private lateinit var categoryBar: LinearLayout
    private lateinit var titleView: TextView
    private var selectedCategory: ThemeCategory = ThemeCategory.ALL
    private val PICK_THEME_FILE_REQUEST = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        themeRepo = ThemeRepository(this)

        val dp = resources.displayMetrics.density
        val pad = (16 * dp).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#12121A"))
            setPadding(pad, pad, pad, pad)
        }
        root.applySystemBarPadding(pad, pad, pad, pad)

        // Top Toolbar
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, (12 * dp).toInt())
        }

        titleView = TextView(this).apply {
            text = "Theme Gallery"
            textSize = 20f
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val importBtn = Button(this).apply {
            text = "Import"
            textSize = 12f
            setOnClickListener { showImportDialog() }
        }

        val createBtn = Button(this).apply {
            text = "+ New"
            textSize = 12f
            setOnClickListener {
                startActivity(Intent(this@ThemeGalleryActivity, CustomThemeEditorActivity::class.java))
            }
        }

        topBar.addView(titleView)
        topBar.addView(importBtn)
        topBar.addView(createBtn)
        root.addView(topBar)

        // Category Filter Chips
        categoryBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, (10 * dp).toInt())
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
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        root.addView(recyclerView)

        setContentView(root)
        handleIncomingThemeIntent(intent)
    }

    private fun handleIncomingThemeIntent(incoming: Intent?) {
        val uri = incoming?.data ?: return
        if (incoming.action != Intent.ACTION_VIEW) return
        try {
            val json = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: return
            val imported = themeRepo.importThemeFromJson(json)
            themeRepo.setActiveTheme(imported.id)
            Toast.makeText(this, "Imported & applied '${imported.name}'!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Invalid theme file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
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
                setTextColor(if (isSelected) Color.WHITE else Color.parseColor("#B0B4C3"))
                setBackgroundColor(if (isSelected) Color.parseColor("#4285F4") else Color.parseColor("#1D1F2D"))
                setPadding((12 * dp).toInt(), (6 * dp).toInt(), (12 * dp).toInt(), (6 * dp).toInt())
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
        titleView.text = "Themes (${themes.size})"
        val activeTheme = themeRepo.getActiveTheme()

        recyclerView.adapter = ThemeGridAdapter(
            themes = themes,
            activeThemeId = activeTheme.id,
            onThemeSelected = { theme ->
                themeRepo.setActiveTheme(theme.id)
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
                setBackgroundColor(Color.parseColor("#1B1B26"))
                layoutParams = MarginLayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (176 * dp).toInt()).apply {
                    setMargins((6 * dp).toInt(), (6 * dp).toInt(), (6 * dp).toInt(), (6 * dp).toInt())
                }
                setPadding((6 * dp).toInt(), (6 * dp).toInt(), (6 * dp).toInt(), (6 * dp).toInt())
            }

            val preview = ThemePreviewView(parent.context).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (110 * dp).toInt())
            }

            val bottomRow = LinearLayout(parent.context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding((4 * dp).toInt(), (6 * dp).toInt(), (4 * dp).toInt(), (2 * dp).toInt())
            }

            val checkBadge = TextView(parent.context).apply {
                text = "✓ "
                setTextColor(Color.parseColor("#4285F4"))
                textSize = 14f
                visibility = View.GONE
            }

            val title = TextView(parent.context).apply {
                setTextColor(Color.WHITE)
                textSize = 13f
                maxLines = 1
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }

            val effectBadge = TextView(parent.context).apply {
                setTextColor(Color.parseColor("#93C5FD"))
                setBackgroundColor(Color.parseColor("#243B82F6"))
                textSize = 10f
                setPadding((6 * dp).toInt(), (2 * dp).toInt(), (6 * dp).toInt(), (2 * dp).toInt())
                visibility = View.GONE
            }

            val moreBtn = TextView(parent.context).apply {
                text = "⋮"
                setTextColor(Color.parseColor("#AAAAAA"))
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

            holder.preview.theme = theme
            holder.title.text = theme.name
            holder.checkBadge.visibility = if (isActive) View.VISIBLE else View.GONE
            if (theme.effectStyle != EffectStyle.NONE && theme.effectStyle.badge.isNotEmpty()) {
                holder.effectBadge.text = theme.effectStyle.badge
                holder.effectBadge.visibility = View.VISIBLE
            } else {
                holder.effectBadge.visibility = View.GONE
            }

            holder.view.setBackgroundColor(
                if (isActive) Color.parseColor("#253046") else Color.parseColor("#1B1B26")
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
