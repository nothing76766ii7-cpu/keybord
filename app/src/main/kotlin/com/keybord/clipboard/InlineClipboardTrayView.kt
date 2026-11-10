package com.keybord.clipboard

import android.content.Context
import android.graphics.Color
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * Inline clipboard tray rendered directly on top of the keyboard surface
 * when the user taps the clipboard icon in the suggestion strip.
 */
class InlineClipboardTrayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private lateinit var categoryContainer: LinearLayout
    private lateinit var searchInput: EditText
    private lateinit var closeButton: TextView
    private lateinit var itemsList: RecyclerView
    private lateinit var emptyText: TextView

    private var clipboardManager: KeybordClipboardManager? = null
    private var onItemSelected: ((String) -> Unit)? = null
    private var onCloseRequested: (() -> Unit)? = null

    private var activeCategory: String = "All"
    private var currentSearchQuery: String = ""
    private var adapter: ClipboardAdapter? = null

    init {
        orientation = VERTICAL
        setBackgroundColor(Color.parseColor("#1E1E2E"))
        setupViews()
    }

    private fun setupViews() {
        val dp = context.resources.displayMetrics.density

        // Header: Search + Close
        val header = LinearLayout(context).apply {
            orientation = HORIZONTAL
            setPadding((12 * dp).toInt(), (8 * dp).toInt(), (12 * dp).toInt(), (4 * dp).toInt())
            gravity = Gravity.CENTER_VERTICAL
        }

        searchInput = EditText(context).apply {
            hint = "Search clipboard history..."
            setHintTextColor(Color.parseColor("#8888AA"))
            setTextColor(Color.WHITE)
            textSize = 14f
            background = null
            setPadding((8 * dp).toInt(), (6 * dp).toInt(), (8 * dp).toInt(), (6 * dp).toInt())
            layoutParams = LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    currentSearchQuery = s?.toString() ?: ""
                    refreshItems()
                }
                override fun afterTextChanged(s: Editable?) {}
            })
        }

        closeButton = TextView(context).apply {
            text = "✕"
            setTextColor(Color.parseColor("#AAACB0"))
            textSize = 18f
            setPadding((8 * dp).toInt(), (4 * dp).toInt(), (8 * dp).toInt(), (4 * dp).toInt())
            setOnClickListener { onCloseRequested?.invoke() }
        }

        header.addView(searchInput)
        header.addView(closeButton)
        addView(header)

        // Categories Scroll
        val categoryScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setPadding((8 * dp).toInt(), (2 * dp).toInt(), (8 * dp).toInt(), (6 * dp).toInt())
        }

        categoryContainer = LinearLayout(context).apply {
            orientation = HORIZONTAL
        }
        categoryScroll.addView(categoryContainer)
        addView(categoryScroll)

        // Empty View
        emptyText = TextView(context).apply {
            text = "No clipboard items yet"
            setTextColor(Color.parseColor("#666688"))
            textSize = 14f
            gravity = Gravity.CENTER
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (120 * dp).toInt())
            visibility = View.GONE
        }
        addView(emptyText)

        // Items RecyclerView
        itemsList = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (140 * dp).toInt())
        }
        addView(itemsList)
    }

    fun attachClipboard(
        manager: KeybordClipboardManager,
        onSelect: (String) -> Unit,
        onClose: () -> Unit
    ) {
        this.clipboardManager = manager
        this.onItemSelected = onSelect
        this.onCloseRequested = onClose

        setupCategories()
        refreshItems()
    }

    private fun setupCategories() {
        categoryContainer.removeAllViews()
        val dp = context.resources.displayMetrics.density

        val categories = mutableListOf("All")
        clipboardManager?.storage?.getCategories()?.map { it.name }?.let {
            categories.addAll(it)
        }

        for (cat in categories) {
            val chip = TextView(context).apply {
                text = cat
                textSize = 12f
                val isSelected = (cat == activeCategory)
                setTextColor(if (isSelected) Color.WHITE else Color.parseColor("#AAACB0"))
                setBackgroundColor(if (isSelected) Color.parseColor("#4285F4") else Color.parseColor("#2A2B3D"))
                setPadding((10 * dp).toInt(), (4 * dp).toInt(), (10 * dp).toInt(), (4 * dp).toInt())
                val lp = LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    marginEnd = (6 * dp).toInt()
                }
                layoutParams = lp

                setOnClickListener {
                    activeCategory = cat
                    setupCategories()
                    refreshItems()
                }
            }
            categoryContainer.addView(chip)
        }
    }

    fun refreshItems() {
        val storage = clipboardManager?.storage ?: return
        val items = if (currentSearchQuery.isNotBlank()) {
            storage.searchItems(currentSearchQuery, if (activeCategory == "All") null else activeCategory)
        } else {
            storage.getAllItems(if (activeCategory == "All") null else activeCategory)
        }

        if (items.isEmpty()) {
            emptyText.visibility = View.VISIBLE
            itemsList.visibility = View.GONE
        } else {
            emptyText.visibility = View.GONE
            itemsList.visibility = View.VISIBLE
            adapter = ClipboardAdapter(items, { item ->
                onItemSelected?.invoke(item.text)
            }, { item ->
                storage.setPinned(item.id, !item.isPinned)
                refreshItems()
            }, { item ->
                storage.deleteItem(item.id)
                refreshItems()
            })
            itemsList.adapter = adapter
        }
    }

    private class ClipboardAdapter(
        private val items: List<ClipboardItem>,
        private val onClick: (ClipboardItem) -> Unit,
        private val onPinToggle: (ClipboardItem) -> Unit,
        private val onDelete: (ClipboardItem) -> Unit
    ) : RecyclerView.Adapter<ClipboardAdapter.ViewHolder>() {

        class ViewHolder(val view: View, val tvText: TextView, val tvPin: TextView, val tvDelete: TextView) :
            RecyclerView.ViewHolder(view)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val dp = parent.context.resources.displayMetrics.density
            val card = LinearLayout(parent.context).apply {
                orientation = VERTICAL
                setBackgroundColor(Color.parseColor("#282A3A"))
                layoutParams = MarginLayoutParams((180 * dp).toInt(), (120 * dp).toInt()).apply {
                    setMargins((6 * dp).toInt(), (4 * dp).toInt(), (6 * dp).toInt(), (8 * dp).toInt())
                }
                setPadding((8 * dp).toInt(), (6 * dp).toInt(), (8 * dp).toInt(), (6 * dp).toInt())
            }

            val topRow = LinearLayout(parent.context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.END
            }

            val pinBtn = TextView(parent.context).apply {
                textSize = 14f
                setPadding((4 * dp).toInt(), (2 * dp).toInt(), (4 * dp).toInt(), (2 * dp).toInt())
            }
            val delBtn = TextView(parent.context).apply {
                text = "🗑"
                textSize = 14f
                setPadding((4 * dp).toInt(), (2 * dp).toInt(), (4 * dp).toInt(), (2 * dp).toInt())
            }
            topRow.addView(pinBtn)
            topRow.addView(delBtn)

            val tvText = TextView(parent.context).apply {
                setTextColor(Color.WHITE)
                textSize = 13f
                maxLines = 4
                layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            }

            card.addView(topRow)
            card.addView(tvText)

            return ViewHolder(card, tvText, pinBtn, delBtn)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.tvText.text = item.text
            holder.tvPin.text = if (item.isPinned) "📌" else "⚪"
            holder.view.setOnClickListener { onClick(item) }
            holder.tvPin.setOnClickListener { onPinToggle(item) }
            holder.tvDelete.setOnClickListener { onDelete(item) }
        }

        override fun getItemCount(): Int = items.size
    }
}
