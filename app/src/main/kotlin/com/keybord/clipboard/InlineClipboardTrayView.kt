package com.keybord.clipboard

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.keybord.theme.BuiltInThemes
import com.keybord.theme.KeyboardEffectEngine
import com.keybord.theme.ThemeModel
import com.keybord.ui.UiKit

/**
 * Modern inline clipboard tray matching active keyboard theme with rounded cards,
 * search pill, category chips, pin/delete controls, and 1-tap full manager shortcut.
 */
class InlineClipboardTrayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    var theme: ThemeModel = BuiltInThemes.SKEUO_DARK_GRAPHITE
        set(value) {
            field = value
            applyTheme()
        }

    private lateinit var searchBox: LinearLayout
    private lateinit var categoryContainer: LinearLayout
    private lateinit var searchInput: EditText
    private lateinit var openAllButton: TextView
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
        setupViews()
        applyTheme()
    }

    private fun setupViews() {
        val dp = context.resources.displayMetrics.density

        // Header: Search pill + Open All + Close
        val header = LinearLayout(context).apply {
            orientation = HORIZONTAL
            setPadding((10 * dp).toInt(), (8 * dp).toInt(), (10 * dp).toInt(), (4 * dp).toInt())
            gravity = Gravity.CENTER_VERTICAL
        }

        searchBox = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((10 * dp).toInt(), (4 * dp).toInt(), (10 * dp).toInt(), (4 * dp).toInt())
            layoutParams = LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = (8 * dp).toInt()
            }
        }
        val searchIcon = TextView(context).apply {
            text = "🔍"
            textSize = 13f
            setPadding(0, 0, (6 * dp).toInt(), 0)
        }
        searchInput = EditText(context).apply {
            hint = "Search clips..."
            textSize = 13.5f
            background = null
            setSingleLine(true)
            setPadding(0, (4 * dp).toInt(), 0, (4 * dp).toInt())
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
        searchBox.addView(searchIcon)
        searchBox.addView(searchInput)

        openAllButton = TextView(context).apply {
            text = "Manage ↗"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding((10 * dp).toInt(), (6 * dp).toInt(), (10 * dp).toInt(), (6 * dp).toInt())
            layoutParams = LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = (6 * dp).toInt()
            }
            setOnClickListener {
                context.startActivity(
                    Intent(context, ClipboardActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                )
            }
        }

        closeButton = TextView(context).apply {
            text = "✕"
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding((10 * dp).toInt(), (5 * dp).toInt(), (10 * dp).toInt(), (5 * dp).toInt())
            setOnClickListener { onCloseRequested?.invoke() }
        }

        header.addView(searchBox)
        header.addView(openAllButton)
        header.addView(closeButton)
        addView(header)

        // Categories Scroll
        val categoryScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setPadding((10 * dp).toInt(), (4 * dp).toInt(), (10 * dp).toInt(), (6 * dp).toInt())
        }
        categoryContainer = LinearLayout(context).apply {
            orientation = HORIZONTAL
        }
        categoryScroll.addView(categoryContainer)
        addView(categoryScroll)

        // Empty View
        emptyText = TextView(context).apply {
            text = "No saved clips yet — copy any text to keep it here."
            textSize = 13.5f
            gravity = Gravity.CENTER
            setPadding((24 * dp).toInt(), (16 * dp).toInt(), (24 * dp).toInt(), (16 * dp).toInt())
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (148 * dp).toInt())
            visibility = View.GONE
        }
        addView(emptyText)

        // Items RecyclerView
        itemsList = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (152 * dp).toInt())
            setPadding((6 * dp).toInt(), 0, (6 * dp).toInt(), (6 * dp).toInt())
            clipToPadding = false
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

    private fun applyTheme() {
        val bg = theme.keyboardBackground.colorStart
        setBackgroundColor(bg)
        val accent = theme.effectColors.firstOrNull() ?: theme.accentKeyColor ?: theme.trailColor
        val cardBg = KeyboardEffectEngine.compositeOver(theme.keyFaceColor.primaryColor, bg)
        val wellBg = KeyboardEffectEngine.compositeOver(theme.suggestionStripBgColor, bg)
        searchBox.background = UiKit.skeuoWellDrawable(context, wellBg, 12f, theme.keyBorderColor)
        searchInput.setTextColor(theme.keyLabelColorPrimary)
        searchInput.setHintTextColor(theme.keyLabelColorSecondary)
        emptyText.setTextColor(theme.keyLabelColorSecondary)

        openAllButton.background = UiKit.skeuoButtonDrawable(context, accent, 10f)
        openAllButton.setTextColor(Color.WHITE)
        closeButton.background = UiKit.skeuoButtonDrawable(context, cardBg, 10f, theme.keyBorderColor)
        closeButton.setTextColor(theme.keyLabelColorSecondary)

        if (::categoryContainer.isInitialized) {
            setupCategories()
            refreshItems()
        }
    }

    private fun setupCategories() {
        categoryContainer.removeAllViews()
        val dp = context.resources.displayMetrics.density
        val accent = theme.effectColors.firstOrNull() ?: theme.accentKeyColor ?: theme.trailColor
        val bg = theme.keyboardBackground.colorStart
        val chipInactiveBg = KeyboardEffectEngine.compositeOver(theme.keyFaceColor.primaryColor, bg)

        val categories = mutableListOf("All")
        clipboardManager?.storage?.getCategories()?.map { it.name }?.let {
            categories.addAll(it)
        }

        for (cat in categories) {
            val isSelected = (cat == activeCategory)
            val chip = TextView(context).apply {
                text = cat
                textSize = 12f
                typeface = if (isSelected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                setTextColor(if (isSelected) Color.WHITE else theme.keyLabelColorSecondary)
                background = if (isSelected) {
                    UiKit.skeuoButtonDrawable(context, accent, 12f)
                } else {
                    UiKit.skeuoCardDrawable(context, chipInactiveBg, 12f, theme.keyBorderColor, 1f)
                }
                setPadding((12 * dp).toInt(), (5 * dp).toInt(), (12 * dp).toInt(), (5 * dp).toInt())
                layoutParams = LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    marginEnd = (6 * dp).toInt()
                }
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
            adapter = ClipboardAdapter(
                items = items,
                theme = theme,
                onClick = { item -> onItemSelected?.invoke(item.text) },
                onPinToggle = { item ->
                    storage.setPinned(item.id, !item.isPinned)
                    refreshItems()
                },
                onDelete = { item ->
                    storage.deleteItem(item.id)
                    refreshItems()
                }
            )
            itemsList.adapter = adapter
        }
    }

    private class ClipboardAdapter(
        private val items: List<ClipboardItem>,
        private val theme: ThemeModel,
        private val onClick: (ClipboardItem) -> Unit,
        private val onPinToggle: (ClipboardItem) -> Unit,
        private val onDelete: (ClipboardItem) -> Unit
    ) : RecyclerView.Adapter<ClipboardAdapter.ViewHolder>() {

        class ViewHolder(
            val card: LinearLayout,
            val tvCategory: TextView,
            val tvPin: TextView,
            val tvDelete: TextView,
            val tvText: TextView,
            val tvTime: TextView
        ) : RecyclerView.ViewHolder(card)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val dp = parent.context.resources.displayMetrics.density
            val card = LinearLayout(parent.context).apply {
                orientation = VERTICAL
                layoutParams = MarginLayoutParams((196 * dp).toInt(), (134 * dp).toInt()).apply {
                    setMargins((5 * dp).toInt(), (4 * dp).toInt(), (5 * dp).toInt(), (6 * dp).toInt())
                }
                setPadding((11 * dp).toInt(), (8 * dp).toInt(), (11 * dp).toInt(), (8 * dp).toInt())
            }

            val topRow = LinearLayout(parent.context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 0, 0, (4 * dp).toInt())
            }

            val catBadge = TextView(parent.context).apply {
                textSize = 10.5f
                typeface = Typeface.DEFAULT_BOLD
                setPadding((6 * dp).toInt(), (2 * dp).toInt(), (6 * dp).toInt(), (2 * dp).toInt())
            }
            val spacer = View(parent.context).apply {
                layoutParams = LayoutParams(0, 1, 1f)
            }
            val pinBtn = TextView(parent.context).apply {
                textSize = 13.5f
                setPadding((6 * dp).toInt(), (2 * dp).toInt(), (6 * dp).toInt(), (2 * dp).toInt())
            }
            val delBtn = TextView(parent.context).apply {
                text = "🗑"
                textSize = 13.5f
                setPadding((4 * dp).toInt(), (2 * dp).toInt(), (2 * dp).toInt(), (2 * dp).toInt())
            }
            topRow.addView(catBadge)
            topRow.addView(spacer)
            topRow.addView(pinBtn)
            topRow.addView(delBtn)

            val tvText = TextView(parent.context).apply {
                textSize = 13f
                maxLines = 4
                layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            }

            val tvTime = TextView(parent.context).apply {
                textSize = 10.5f
                setPadding(0, (4 * dp).toInt(), 0, 0)
            }

            card.addView(topRow)
            card.addView(tvText)
            card.addView(tvTime)
            return ViewHolder(card, catBadge, pinBtn, delBtn, tvText, tvTime)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            val accent = theme.effectColors.firstOrNull() ?: theme.accentKeyColor ?: theme.trailColor
            val bg = theme.keyboardBackground.colorStart
            val cardColor = KeyboardEffectEngine.compositeOver(theme.keyFaceColor.primaryColor, bg)
            val stroke = if (item.isPinned) accent else theme.keyBorderColor

            holder.card.background = UiKit.skeuoCardDrawable(holder.card.context, cardColor, 14f, stroke, if (item.isPinned) 2.5f else 1.5f)
            holder.tvCategory.text = item.category
            holder.tvCategory.setTextColor(accent)
            holder.tvCategory.background = UiKit.roundedDrawable(
                holder.card.context,
                Color.argb(38, Color.red(accent), Color.green(accent), Color.blue(accent)),
                8f
            )
            holder.tvText.setTextColor(theme.keyLabelColorPrimary)
            holder.tvText.text = item.text
            holder.tvPin.text = if (item.isPinned) "📌" else "📍"
            holder.tvPin.alpha = if (item.isPinned) 1f else 0.45f
            holder.tvTime.setTextColor(theme.keyLabelColorSecondary)
            holder.tvTime.text = formatRelativeTime(item.createdAt)

            holder.card.setOnClickListener { onClick(item) }
            holder.tvPin.setOnClickListener { onPinToggle(item) }
            holder.tvDelete.setOnClickListener { onDelete(item) }
        }

        override fun getItemCount(): Int = items.size

        private fun formatRelativeTime(createdAt: Long): String {
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
