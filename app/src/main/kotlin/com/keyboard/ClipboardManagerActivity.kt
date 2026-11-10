package com.keyboard

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.keyboard.data.ClipboardCategory
import com.keyboard.data.ClipboardItem

/**
 * Full Clipboard Manager Activity strictly matching Section A.4.
 */
class ClipboardManagerActivity : Activity() {

    private lateinit var clipboardManager: ClipboardManager
    private lateinit var searchEditText: EditText
    private lateinit var categoryChipsContainer: LinearLayout
    private lateinit var itemsListContainer: LinearLayout
    private var selectedCategoryId: Long? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        clipboardManager = ClipboardManager(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#181825"))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // Toolbar: Title + Clear all button
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#1E1E2E"))
            setPadding(32, 24, 32, 24)
        }

        val title = TextView(this).apply {
            text = "Clipboard Manager"
            setTextColor(Color.WHITE)
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        toolbar.addView(title)

        val clearAllBtn = TextView(this).apply {
            text = "Clear All"
            setTextColor(Color.parseColor("#F38BA8"))
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(16, 8, 16, 8)
            setOnClickListener {
                showClearAllConfirmation()
            }
        }
        toolbar.addView(clearAllBtn)
        root.addView(toolbar)

        // Search Bar (Real-time filtering)
        searchEditText = EditText(this).apply {
            hint = "Search clipboard..."
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#313244"))
            setPadding(32, 20, 32, 20)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(24, 16, 24, 8)
            }
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    refreshList()
                }
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            })
        }
        root.addView(searchEditText)

        // Category Horizontal Chips + Add Category (+)
        val categoryScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(24, 8, 24, 8)
            }
        }
        categoryChipsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        categoryScroll.addView(categoryChipsContainer)
        root.addView(categoryScroll)

        // Scrollable Items Container
        val itemsScroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1.0f
            )
        }
        itemsListContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 8, 24, 24)
        }
        itemsScroll.addView(itemsListContainer)
        root.addView(itemsScroll)

        // FAB: "New Category"
        val fab = Button(this).apply {
            text = "+ New Category"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#89B4FA"))
            setOnClickListener {
                showNewCategoryDialog()
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                setMargins(0, 8, 0, 16)
            }
        }
        root.addView(fab)

        setContentView(root)
        renderCategories()
        refreshList()
    }

    private fun renderCategories() {
        categoryChipsContainer.removeAllViews()

        // "All" chip
        val allChip = createCategoryChip("All", selectedCategoryId == null) {
            selectedCategoryId = null
            renderCategories()
            refreshList()
        }
        categoryChipsContainer.addView(allChip)

        val categories = clipboardManager.getCategories()
        for (cat in categories) {
            val isSelected = (selectedCategoryId == cat.id)
            val chip = createCategoryChip(cat.label, isSelected) {
                selectedCategoryId = cat.id
                renderCategories()
                refreshList()
            }
            categoryChipsContainer.addView(chip)
        }
    }

    private fun createCategoryChip(label: String, selected: Boolean, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            text = label
            setTextColor(if (selected) Color.WHITE else Color.parseColor("#CDD6F4"))
            setBackgroundColor(if (selected) Color.parseColor("#45475A") else Color.parseColor("#313244"))
            textSize = 13f
            typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            setPadding(28, 14, 28, 14)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 16, 0)
            }
            setOnClickListener { onClick() }
        }
    }

    private fun refreshList() {
        itemsListContainer.removeAllViews()

        val query = searchEditText.text.toString().trim()
        val items = if (query.isNotEmpty()) {
            clipboardManager.searchItems(query)
        } else {
            clipboardManager.getAllItems(selectedCategoryId)
        }

        if (items.isEmpty()) {
            val emptyTv = TextView(this).apply {
                text = "No clipboard items found."
                setTextColor(Color.GRAY)
                textSize = 15f
                gravity = Gravity.CENTER
                setPadding(0, 64, 0, 64)
            }
            itemsListContainer.addView(emptyTv)
            return
        }

        for (item in items) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Color.parseColor("#313244"))
                setPadding(24, 20, 24, 20)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 8, 0, 8)
                }
            }

            // Top row with Pin badge and Delete
            val headerRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val pinBtn = TextView(this).apply {
                text = if (item.pinned) "📌 Pinned" else "☆ Pin"
                setTextColor(if (item.pinned) Color.parseColor("#F9E2AF") else Color.GRAY)
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener {
                    if (item.pinned) clipboardManager.unpinItem(item.id) else clipboardManager.pinItem(item.id)
                    refreshList()
                }
            }
            headerRow.addView(pinBtn)

            val delBtn = TextView(this).apply {
                text = "✕ Delete"
                setTextColor(Color.parseColor("#F38BA8"))
                textSize = 12f
                setOnClickListener {
                    clipboardManager.deleteItem(item.id)
                    refreshList()
                }
            }
            headerRow.addView(delBtn)
            card.addView(headerRow)

            // Content
            val contentTv = TextView(this).apply {
                text = item.content
                setTextColor(Color.WHITE)
                textSize = 14f
                setPadding(0, 12, 0, 8)
            }
            card.addView(contentTv)

            // Tap to copy to system clipboard
            card.setOnClickListener {
                val clipService = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipService.setPrimaryClip(ClipData.newPlainText("Copied", item.content))
                Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show()
            }

            itemsListContainer.addView(card)
        }
    }

    private fun showClearAllConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Clear All Clipboard")
            .setMessage("All unpinned clipboard items will be deleted permanently. Pinned items will be kept.")
            .setPositiveButton("Clear") { _, _ ->
                clipboardManager.deleteAll()
                refreshList()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showNewCategoryDialog() {
        val input = EditText(this).apply {
            hint = "Category Name"
        }
        AlertDialog.Builder(this)
            .setTitle("New Category")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    clipboardManager.createCategory(name)
                    renderCategories()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
