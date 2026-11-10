package com.keyboard.ui

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.text.TextUtils
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.*
import com.keyboard.ClipboardManager
import com.keyboard.ClipboardManagerActivity
import com.keyboard.data.ClipboardItem

/**
 * Inline Clipboard Tray matching Section A.3 specifications.
 */
class ClipboardTrayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val clipboardManager = ClipboardManager(context)
    private val itemsContainer: LinearLayout
    var onItemPastedListener: ((String) -> Unit)? = null
    var onDismissListener: (() -> Unit)? = null

    init {
        orientation = VERTICAL
        setBackgroundColor(Color.parseColor("#1E1E2E"))
        setPadding(16, 16, 16, 16)

        // Header with title and close button
        val header = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }

        val title = TextView(context).apply {
            text = "Clipboard"
            setTextColor(Color.WHITE)
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1.0f)
        }
        header.addView(title)

        val closeBtn = TextView(context).apply {
            text = "✕"
            setTextColor(Color.LTGRAY)
            textSize = 16f
            setPadding(16, 8, 16, 8)
            setOnClickListener { onDismissListener?.invoke() }
        }
        header.addView(closeBtn)
        addView(header)

        // Scrollable item list
        val scrollView = HorizontalScrollView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            isHorizontalScrollBarEnabled = false
        }

        itemsContainer = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        }
        scrollView.addView(itemsContainer)
        addView(scrollView)

        // Footer link to full manager
        val footerLink = TextView(context).apply {
            text = "Open full manager →"
            setTextColor(Color.parseColor("#89B4FA"))
            textSize = 12f
            setPadding(8, 12, 8, 4)
            setOnClickListener {
                val intent = Intent(context, ClipboardManagerActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                onDismissListener?.invoke()
            }
        }
        addView(footerLink)

        refreshItems()
    }

    fun refreshItems() {
        itemsContainer.removeAllViews()

        val pinned = clipboardManager.getPinnedItems()
        val recent = clipboardManager.getRecentItems(3)
        val allItems = mutableListOf<ClipboardItem>()
        allItems.addAll(pinned)
        for (r in recent) {
            if (allItems.none { it.id == r.id }) {
                allItems.add(r)
            }
        }

        if (allItems.isEmpty()) {
            val empty = TextView(context).apply {
                text = "Clipboard is empty"
                setTextColor(Color.GRAY)
                textSize = 13f
                setPadding(24, 32, 24, 32)
            }
            itemsContainer.addView(empty)
            return
        }

        for (item in allItems) {
            val itemCard = LinearLayout(context).apply {
                orientation = VERTICAL
                setBackgroundColor(Color.parseColor("#313244"))
                layoutParams = LayoutParams(320, LayoutParams.WRAP_CONTENT).apply {
                    setMargins(8, 8, 8, 8)
                }
                setPadding(16, 12, 16, 12)
            }

            // Top row with pin icon and delete button
            val topRow = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val pinBadge = TextView(context).apply {
                text = if (item.pinned) "📌" else "📋"
                textSize = 12f
                layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
            }
            topRow.addView(pinBadge)

            val delBtn = TextView(context).apply {
                text = "🗑"
                textSize = 12f
                setOnClickListener {
                    clipboardManager.deleteItem(item.id)
                    refreshItems()
                }
            }
            topRow.addView(delBtn)
            itemCard.addView(topRow)

            // Content text: truncated to max 2 lines
            val contentTv = TextView(context).apply {
                text = item.content
                setTextColor(Color.WHITE)
                textSize = 13f
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                setPadding(0, 8, 0, 4)
            }
            itemCard.addView(contentTv)

            // Tap item -> commitText and dismiss
            itemCard.setOnClickListener {
                onItemPastedListener?.invoke(item.content)
                onDismissListener?.invoke()
            }

            // Long press item -> Context menu: Pin/Unpin/Delete
            itemCard.setOnLongClickListener {
                showContextMenu(it, item)
                true
            }

            itemsContainer.addView(itemCard)
        }
    }

    private fun showContextMenu(anchor: View, item: ClipboardItem) {
        val popup = PopupMenu(context, anchor)
        if (item.pinned) {
            popup.menu.add("Unpin").setOnMenuItemClickListener {
                clipboardManager.unpinItem(item.id)
                refreshItems()
                true
            }
        } else {
            popup.menu.add("Pin").setOnMenuItemClickListener {
                clipboardManager.pinItem(item.id)
                refreshItems()
                true
            }
        }
        popup.menu.add("Delete").setOnMenuItemClickListener {
            clipboardManager.deleteItem(item.id)
            refreshItems()
            true
        }
        popup.show()
    }
}
