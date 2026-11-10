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
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File

/**
 * Gallery displaying all 10 built-in themes and user-created themes.
 * Supports 1-tap activation, theme creation, import and export.
 */
class ThemeGalleryActivity : AppCompatActivity() {

    private lateinit var themeRepo: ThemeRepository
    private lateinit var recyclerView: RecyclerView
    private val PICK_THEME_FILE_REQUEST = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        themeRepo = ThemeRepository(this)

        val dp = resources.displayMetrics.density
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#12121A"))
            setPadding((16 * dp).toInt(), (16 * dp).toInt(), (16 * dp).toInt(), (16 * dp).toInt())
        }

        // Top Toolbar
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, (12 * dp).toInt())
        }

        val title = TextView(this).apply {
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

        topBar.addView(title)
        topBar.addView(importBtn)
        topBar.addView(createBtn)
        root.addView(topBar)

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
    }

    override fun onResume() {
        super.onResume()
        refreshThemes()
    }

    private fun refreshThemes() {
        val themes = themeRepo.getAllThemes()
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
            val moreBtn: TextView
        ) : RecyclerView.ViewHolder(view)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val dp = parent.context.resources.displayMetrics.density
            val card = LinearLayout(parent.context).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Color.parseColor("#1B1B26"))
                layoutParams = MarginLayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (170 * dp).toInt()).apply {
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

            val moreBtn = TextView(parent.context).apply {
                text = "⋮"
                setTextColor(Color.parseColor("#AAAAAA"))
                textSize = 16f
                setPadding((8 * dp).toInt(), 0, (4 * dp).toInt(), 0)
            }

            bottomRow.addView(checkBadge)
            bottomRow.addView(title)
            bottomRow.addView(moreBtn)

            card.addView(preview)
            card.addView(bottomRow)

            return ViewHolder(card, preview, title, checkBadge, moreBtn)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val theme = themes[position]
            val isActive = (theme.id == activeThemeId)

            holder.preview.theme = theme
            holder.title.text = theme.name
            holder.checkBadge.visibility = if (isActive) View.VISIBLE else View.GONE

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
