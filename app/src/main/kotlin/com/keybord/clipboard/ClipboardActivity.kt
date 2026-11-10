package com.keybord.clipboard

import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * Full-screen Clipboard Manager accessible from Keybord Settings.
 * Supports prefix search, category filtering, multi-select bulk operations, and pinning.
 */
class ClipboardActivity : AppCompatActivity() {

    private lateinit var storage: EncryptedClipboardStorage
    private lateinit var searchInput: EditText
    private lateinit var categoryContainer: LinearLayout
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var bulkActionBar: LinearLayout
    private lateinit var selectedCountText: TextView

    private var activeCategory: String = "All"
    private var searchQuery: String = ""
    private val selectedIds = HashSet<Long>()
    private var isSelectionMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        storage = EncryptedClipboardStorage(this)

        val dp = resources.displayMetrics.density
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#12121A"))
            setPadding((16 * dp).toInt(), (16 * dp).toInt(), (16 * dp).toInt(), (16 * dp).toInt())
        }

        // Top Toolbar
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, (12 * dp).toInt())
        }

        val title = TextView(this).apply {
            text = "Clipboard History"
            textSize = 20f
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val clearAllBtn = TextView(this).apply {
            text = "Clear All"
            setTextColor(Color.parseColor("#FF5555"))
            textSize = 14f
            setPadding((8 * dp).toInt(), (4 * dp).toInt(), (8 * dp).toInt(), (4 * dp).toInt())
            setOnClickListener { showClearAllDialog() }
        }

        val addCategoryBtn = TextView(this).apply {
            text = "+ Category"
            setTextColor(Color.parseColor("#4285F4"))
            textSize = 14f
            setPadding((8 * dp).toInt(), (4 * dp).toInt(), (8 * dp).toInt(), (4 * dp).toInt())
            setOnClickListener { showAddCategoryDialog() }
        }

        toolbar.addView(title)
        toolbar.addView(addCategoryBtn)
        toolbar.addView(clearAllBtn)
        root.addView(toolbar)

        // Bulk Action Bar (Visible when items selected)
        bulkActionBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#252538"))
            setPadding((12 * dp).toInt(), (8 * dp).toInt(), (12 * dp).toInt(), (8 * dp).toInt())
            visibility = View.GONE
        }
        selectedCountText = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val bulkCategoryBtn = Button(this).apply {
            text = "Set Category"
            textSize = 12f
            setOnClickListener { showBulkCategoryDialog() }
        }
        val bulkDeleteBtn = Button(this).apply {
            text = "Delete"
            textSize = 12f
            setTextColor(Color.parseColor("#FF5555"))
            setOnClickListener {
                storage.deleteItems(selectedIds.toList())
                exitSelectionMode()
                refreshData()
            }
        }
        val cancelSelectBtn = Button(this).apply {
            text = "Cancel"
            textSize = 12f
            setOnClickListener { exitSelectionMode() }
        }
        bulkActionBar.addView(selectedCountText)
        bulkActionBar.addView(bulkCategoryBtn)
        bulkActionBar.addView(bulkDeleteBtn)
        bulkActionBar.addView(cancelSelectBtn)
        root.addView(bulkActionBar)

        // Search Bar
        searchInput = EditText(this).apply {
            hint = "Search clipboard history..."
            setHintTextColor(Color.parseColor("#666688"))
            setTextColor(Color.WHITE)
            textSize = 14f
            setBackgroundColor(Color.parseColor("#1E1E2C"))
            setPadding((12 * dp).toInt(), (10 * dp).toInt(), (12 * dp).toInt(), (10 * dp).toInt())
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    searchQuery = s?.toString() ?: ""
                    refreshData()
                }
                override fun afterTextChanged(s: Editable?) {}
            })
        }
        root.addView(searchInput)

        // Category Horizontal Scroll
        val categoryScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            setPadding(0, (10 * dp).toInt(), 0, (10 * dp).toInt())
        }
        categoryContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        categoryScroll.addView(categoryContainer)
        root.addView(categoryScroll)

        // Empty view
        emptyView = TextView(this).apply {
            text = "No clipboard entries found."
            setTextColor(Color.parseColor("#666688"))
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, (40 * dp).toInt(), 0, 0)
            visibility = View.GONE
        }
        root.addView(emptyView)

        // RecyclerView
        recyclerView = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@ClipboardActivity)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }
        root.addView(recyclerView)

        setContentView(root)
        setupCategories()
        refreshData()
    }

    private fun setupCategories() {
        categoryContainer.removeAllViews()
        val dp = resources.displayMetrics.density

        val categories = mutableListOf("All")
        categories.addAll(storage.getCategories().map { it.name })

        for (cat in categories) {
            val chip = TextView(this).apply {
                text = cat
                textSize = 13f
                val isSelected = (cat == activeCategory)
                setTextColor(if (isSelected) Color.WHITE else Color.parseColor("#AAACB0"))
                setBackgroundColor(if (isSelected) Color.parseColor("#4285F4") else Color.parseColor("#1E1E2C"))
                setPadding((14 * dp).toInt(), (6 * dp).toInt(), (14 * dp).toInt(), (6 * dp).toInt())
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    marginEnd = (8 * dp).toInt()
                }
                layoutParams = lp

                setOnClickListener {
                    activeCategory = cat
                    setupCategories()
                    refreshData()
                }
            }
            categoryContainer.addView(chip)
        }
    }

    private fun refreshData() {
        val items = if (searchQuery.isNotBlank()) {
            storage.searchItems(searchQuery, if (activeCategory == "All") null else activeCategory)
        } else {
            storage.getAllItems(if (activeCategory == "All") null else activeCategory)
        }

        if (items.isEmpty()) {
            emptyView.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            emptyView.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
            recyclerView.adapter = FullClipboardAdapter(
                items = items,
                selectedIds = selectedIds,
                isSelectionMode = isSelectionMode,
                onItemClick = { item ->
                    if (isSelectionMode) {
                        toggleSelection(item.id)
                    } else {
                        // Copy to clipboard or show details
                        val mgr = KeybordClipboardManager(this, storage)
                        mgr.copyToClipboard(item.text)
                        Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                    }
                },
                onItemLongClick = { item ->
                    if (!isSelectionMode) {
                        isSelectionMode = true
                        selectedIds.add(item.id)
                        updateBulkBar()
                        refreshData()
                    }
                },
                onPinClick = { item ->
                    storage.setPinned(item.id, !item.isPinned)
                    refreshData()
                },
                onDeleteClick = { item ->
                    storage.deleteItem(item.id)
                    refreshData()
                }
            )
        }
    }

    private fun toggleSelection(id: Long) {
        if (selectedIds.contains(id)) {
            selectedIds.remove(id)
        } else {
            selectedIds.add(id)
        }
        if (selectedIds.isEmpty()) {
            exitSelectionMode()
        } else {
            updateBulkBar()
            refreshData()
        }
    }

    private fun updateBulkBar() {
        bulkActionBar.visibility = if (isSelectionMode) View.VISIBLE else View.GONE
        selectedCountText.text = "${selectedIds.size} selected"
    }

    private fun exitSelectionMode() {
        isSelectionMode = false
        selectedIds.clear()
        updateBulkBar()
        refreshData()
    }

    private fun showBulkCategoryDialog() {
        val categories = storage.getCategories().map { it.name }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Assign Category")
            .setItems(categories) { _, which ->
                storage.assignCategory(selectedIds.toList(), categories[which])
                exitSelectionMode()
                refreshData()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAddCategoryDialog() {
        val input = EditText(this).apply {
            hint = "Category Name"
        }
        AlertDialog.Builder(this)
            .setTitle("New Category")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    storage.addCategory(ClipboardCategory(name))
                    setupCategories()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showClearAllDialog() {
        AlertDialog.Builder(this)
            .setTitle("Clear Clipboard")
            .setMessage("Do you want to clear all unpinned items, or delete everything including pinned items?")
            .setPositiveButton("Clear Unpinned Only") { _, _ ->
                storage.clearAll(includePinned = false)
                refreshData()
            }
            .setNeutralButton("Delete All (Inc. Pinned)") { _, _ ->
                storage.clearAll(includePinned = true)
                refreshData()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private class FullClipboardAdapter(
        private val items: List<ClipboardItem>,
        private val selectedIds: Set<Long>,
        private val isSelectionMode: Boolean,
        private val onItemClick: (ClipboardItem) -> Unit,
        private val onItemLongClick: (ClipboardItem) -> Unit,
        private val onPinClick: (ClipboardItem) -> Unit,
        private val onDeleteClick: (ClipboardItem) -> Unit
    ) : RecyclerView.Adapter<FullClipboardAdapter.ViewHolder>() {

        class ViewHolder(
            val view: View,
            val checkBox: CheckBox,
            val tvText: TextView,
            val tvCategory: TextView,
            val tvPin: TextView,
            val tvDelete: TextView
        ) : RecyclerView.ViewHolder(view)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val dp = parent.context.resources.displayMetrics.density
            val row = LinearLayout(parent.context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundColor(Color.parseColor("#1B1B26"))
                layoutParams = MarginLayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(0, 0, 0, (8 * dp).toInt())
                }
                setPadding((12 * dp).toInt(), (10 * dp).toInt(), (12 * dp).toInt(), (10 * dp).toInt())
            }

            val cb = CheckBox(parent.context).apply {
                visibility = View.GONE
            }

            val contentCol = LinearLayout(parent.context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }

            val tvText = TextView(parent.context).apply {
                setTextColor(Color.WHITE)
                textSize = 14f
                maxLines = 3
            }

            val tvCat = TextView(parent.context).apply {
                setTextColor(Color.parseColor("#4285F4"))
                textSize = 11f
                setPadding(0, (4 * dp).toInt(), 0, 0)
            }

            contentCol.addView(tvText)
            contentCol.addView(tvCat)

            val tvPin = TextView(parent.context).apply {
                textSize = 16f
                setPadding((8 * dp).toInt(), 0, (8 * dp).toInt(), 0)
            }

            val tvDel = TextView(parent.context).apply {
                text = "🗑"
                textSize = 16f
                setPadding((8 * dp).toInt(), 0, 0, 0)
            }

            row.addView(cb)
            row.addView(contentCol)
            row.addView(tvPin)
            row.addView(tvDel)

            return ViewHolder(row, cb, tvText, tvCat, tvPin, tvDel)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            val isSelected = selectedIds.contains(item.id)

            holder.checkBox.visibility = if (isSelectionMode) View.VISIBLE else View.GONE
            holder.checkBox.isChecked = isSelected
            holder.tvText.text = item.text
            holder.tvCategory.text = "[${item.category}]"
            holder.tvPin.text = if (item.isPinned) "📌" else "⚪"

            holder.view.setBackgroundColor(
                if (isSelected) Color.parseColor("#2A3A55")
                else if (item.isPinned) Color.parseColor("#222336")
                else Color.parseColor("#1B1B26")
            )

            holder.view.setOnClickListener { onItemClick(item) }
            holder.view.setOnLongClickListener {
                onItemLongClick(item)
                true
            }
            holder.tvPin.setOnClickListener { onPinClick(item) }
            holder.tvDelete.setOnClickListener { onDeleteClick(item) }
        }

        override fun getItemCount(): Int = items.size
    }
}
