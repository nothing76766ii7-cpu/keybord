package com.keybord.clipboard

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import android.widget.CheckBox
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.keybord.ui.UiKit

/**
 * Full-screen Clipboard Manager accessible from Keybord Settings and Inline Tray.
 * Features rounded Material dark cards, search pill, category chips, pinning, timestamps, and bulk operations.
 */
class ClipboardActivity : AppCompatActivity() {

    private lateinit var storage: EncryptedClipboardStorage
    private lateinit var searchInput: EditText
    private lateinit var clearSearchBtn: TextView
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
            setBackgroundColor(UiKit.BG_CANVAS)
        }
        UiKit.applyEdgeToEdgePadding(root)

        // Top Bar
        val actionsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val addCatBtn = UiKit.createPillButton(this, "+ Category", filled = false) {
            showAddCategoryDialog()
        }.apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = (8 * dp).toInt()
            }
        }
        val clearAllBtn = UiKit.createPillButton(this, "Clear", filled = false, danger = true) {
            showClearAllDialog()
        }
        actionsRow.addView(addCatBtn)
        actionsRow.addView(clearAllBtn)

        root.addView(
            UiKit.createHeaderBar(
                activity = this,
                title = "Clipboard History",
                subtitle = "AES-encrypted • Pinned items never expire",
                showBack = true,
                trailingView = actionsRow
            )
        )

        // Bulk Action Bar
        bulkActionBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = UiKit.roundedDrawable(this@ClipboardActivity, UiKit.SURFACE_ELEVATED, 14f, UiKit.ACCENT_PRIMARY, 1f)
            setPadding((12 * dp).toInt(), (8 * dp).toInt(), (10 * dp).toInt(), (8 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = (10 * dp).toInt()
            }
            visibility = View.GONE
        }
        selectedCountText = TextView(this).apply {
            setTextColor(UiKit.TEXT_PRIMARY)
            typeface = Typeface.DEFAULT_BOLD
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val bulkCategoryBtn = UiKit.createPillButton(this, "Tag", filled = false) {
            showBulkCategoryDialog()
        }.apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = (6 * dp).toInt()
            }
        }
        val bulkDeleteBtn = UiKit.createPillButton(this, "Delete", filled = false, danger = true) {
            storage.deleteItems(selectedIds.toList())
            exitSelectionMode()
            refreshData()
        }.apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = (6 * dp).toInt()
            }
        }
        val cancelSelectBtn = UiKit.createPillButton(this, "Done", filled = true) {
            exitSelectionMode()
        }
        bulkActionBar.addView(selectedCountText)
        bulkActionBar.addView(bulkCategoryBtn)
        bulkActionBar.addView(bulkDeleteBtn)
        bulkActionBar.addView(cancelSelectBtn)
        root.addView(bulkActionBar)

        // Search Pill
        val searchBox = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = UiKit.skeuoWellDrawable(this@ClipboardActivity, UiKit.SURFACE_CARD, 14f, UiKit.BORDER_SUBTLE)
            setPadding((14 * dp).toInt(), (4 * dp).toInt(), (10 * dp).toInt(), (4 * dp).toInt())
        }
        val searchIcon = TextView(this).apply {
            text = "🔍"
            textSize = 14f
            setPadding(0, 0, (8 * dp).toInt(), 0)
        }
        searchInput = EditText(this).apply {
            hint = "Search copied text..."
            setHintTextColor(UiKit.TEXT_MUTED)
            setTextColor(UiKit.TEXT_PRIMARY)
            textSize = 14.5f
            background = null
            setSingleLine(true)
            setPadding(0, (8 * dp).toInt(), 0, (8 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    searchQuery = s?.toString() ?: ""
                    clearSearchBtn.visibility = if (searchQuery.isNotEmpty()) View.VISIBLE else View.GONE
                    refreshData()
                }
                override fun afterTextChanged(s: Editable?) {}
            })
        }
        clearSearchBtn = TextView(this).apply {
            text = "✕"
            textSize = 14f
            setTextColor(UiKit.TEXT_SECONDARY)
            visibility = View.GONE
            setPadding((8 * dp).toInt(), (4 * dp).toInt(), (4 * dp).toInt(), (4 * dp).toInt())
            setOnClickListener { searchInput.setText("") }
        }
        searchBox.addView(searchIcon)
        searchBox.addView(searchInput)
        searchBox.addView(clearSearchBtn)
        root.addView(searchBox)

        // Category Horizontal Scroll
        val categoryScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            setPadding(0, (12 * dp).toInt(), 0, (12 * dp).toInt())
        }
        categoryContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        categoryScroll.addView(categoryContainer)
        root.addView(categoryScroll)

        // Empty view
        emptyView = TextView(this).apply {
            text = "No clipboard items match this view.\nLong-press items to bulk select & organize."
            setTextColor(UiKit.TEXT_MUTED)
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, (48 * dp).toInt(), 0, 0)
            visibility = View.GONE
        }
        root.addView(emptyView)

        // RecyclerView
        recyclerView = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@ClipboardActivity)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
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
            val isSelected = (cat == activeCategory)
            val chip = TextView(this).apply {
                text = cat
                textSize = 13f
                typeface = if (isSelected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                setTextColor(if (isSelected) Color.WHITE else UiKit.TEXT_SECONDARY)
                background = UiKit.roundedDrawable(
                    this@ClipboardActivity,
                    if (isSelected) UiKit.ACCENT_PRIMARY else UiKit.SURFACE_CARD,
                    12f,
                    if (isSelected) null else UiKit.BORDER_SUBTLE,
                    1f
                )
                setPadding((14 * dp).toInt(), (7 * dp).toInt(), (14 * dp).toInt(), (7 * dp).toInt())
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    marginEnd = (8 * dp).toInt()
                }
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
        if (selectedIds.contains(id)) selectedIds.remove(id) else selectedIds.add(id)
        if (selectedIds.isEmpty()) exitSelectionMode() else {
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
        val input = EditText(this).apply { hint = "Category Name" }
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
            .setMessage("Clear unpinned items only, or delete everything including pinned items?")
            .setPositiveButton("Clear Unpinned") { _, _ ->
                storage.clearAll(includePinned = false)
                refreshData()
            }
            .setNeutralButton("Delete All") { _, _ ->
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
            val tvTime: TextView,
            val tvPin: TextView,
            val tvDelete: TextView
        ) : RecyclerView.ViewHolder(view)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val dp = parent.context.resources.displayMetrics.density
            val row = LinearLayout(parent.context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = MarginLayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(0, 0, 0, (10 * dp).toInt())
                }
                setPadding((14 * dp).toInt(), (12 * dp).toInt(), (12 * dp).toInt(), (12 * dp).toInt())
            }

            val cb = CheckBox(parent.context).apply { visibility = View.GONE }

            val contentCol = LinearLayout(parent.context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }

            val tvText = TextView(parent.context).apply {
                setTextColor(UiKit.TEXT_PRIMARY)
                textSize = 14.5f
                maxLines = 4
                setLineSpacing(2f * dp, 1f)
            }

            val metaRow = LinearLayout(parent.context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, (7 * dp).toInt(), 0, 0)
            }
            val tvCat = TextView(parent.context).apply {
                setTextColor(UiKit.ACCENT_PRIMARY)
                textSize = 11f
                typeface = Typeface.DEFAULT_BOLD
                background = UiKit.roundedDrawable(parent.context, UiKit.ACCENT_SOFT, 8f)
                setPadding((8 * dp).toInt(), (2 * dp).toInt(), (8 * dp).toInt(), (2 * dp).toInt())
            }
            val tvTime = TextView(parent.context).apply {
                setTextColor(UiKit.TEXT_MUTED)
                textSize = 11.5f
                setPadding((8 * dp).toInt(), 0, 0, 0)
            }
            metaRow.addView(tvCat)
            metaRow.addView(tvTime)

            contentCol.addView(tvText)
            contentCol.addView(metaRow)

            val tvPin = TextView(parent.context).apply {
                textSize = 16f
                setPadding((10 * dp).toInt(), (6 * dp).toInt(), (8 * dp).toInt(), (6 * dp).toInt())
            }
            val tvDel = TextView(parent.context).apply {
                text = "🗑"
                textSize = 16f
                setPadding((6 * dp).toInt(), (6 * dp).toInt(), (4 * dp).toInt(), (6 * dp).toInt())
            }

            row.addView(cb)
            row.addView(contentCol)
            row.addView(tvPin)
            row.addView(tvDel)

            return ViewHolder(row, cb, tvText, tvCat, tvTime, tvPin, tvDel)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            val isSelected = selectedIds.contains(item.id)

            holder.checkBox.visibility = if (isSelectionMode) View.VISIBLE else View.GONE
            holder.checkBox.isChecked = isSelected
            holder.tvText.text = item.text
            holder.tvCategory.text = item.category
            holder.tvTime.text = formatTime(item.createdAt)
            holder.tvPin.text = if (item.isPinned) "📌" else "📍"
            holder.tvPin.alpha = if (item.isPinned) 1f else 0.45f

            val fill = when {
                isSelected -> UiKit.SURFACE_ELEVATED
                item.isPinned -> Color.parseColor("#1B2133")
                else -> UiKit.SURFACE_CARD
            }
            val stroke = when {
                isSelected -> UiKit.ACCENT_PRIMARY
                item.isPinned -> Color.argb(110, Color.red(UiKit.ACCENT_PRIMARY), Color.green(UiKit.ACCENT_PRIMARY), Color.blue(UiKit.ACCENT_PRIMARY))
                else -> UiKit.BORDER_SUBTLE
            }
            holder.view.background = UiKit.skeuoCardDrawable(holder.view.context, fill, 16f, stroke, 2f)

            holder.view.setOnClickListener { onItemClick(item) }
            holder.view.setOnLongClickListener {
                onItemLongClick(item)
                true
            }
            holder.tvPin.setOnClickListener { onPinClick(item) }
            holder.tvDelete.setOnClickListener { onDeleteClick(item) }
        }

        override fun getItemCount(): Int = items.size

        private fun formatTime(createdAt: Long): String {
            val diffMin = ((System.currentTimeMillis() - createdAt).coerceAtLeast(0L) / 60_000L)
            return when {
                diffMin < 1L -> "Just now"
                diffMin < 60L -> "${diffMin}m ago"
                diffMin < 1440L -> "${diffMin / 60L}h ago"
                else -> "${diffMin / 1440L}d ago"
            }
        }
    }
}
