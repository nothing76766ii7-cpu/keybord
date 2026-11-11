package com.keybord.settings

import android.app.AlertDialog
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.keybord.engine.vocab.PersonalDictionaryManager
import com.keybord.engine.vocab.WordEntry
import com.keybord.ui.UiKit

/**
 * Personal Dictionary settings screen:
 * - View / Delete learned & custom words
 * - Add word manually (word + shortcut)
 * - Sync with Android system UserDictionary
 */
class PersonalDictionaryActivity : AppCompatActivity() {

    private lateinit var dictManager: PersonalDictionaryManager
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dictManager = PersonalDictionaryManager(this)

        val dp = resources.displayMetrics.density
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(UiKit.BG_CANVAS)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        UiKit.applyEdgeToEdgePadding(root, leftDp = 16f, topDp = 14f, rightDp = 16f, bottomDp = 16f)

        val trailingActions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val syncBtn = UiKit.createPillButton(this, "Sync", filled = false) {
            dictManager.syncFromSystem(contentResolver)
            Toast.makeText(this@PersonalDictionaryActivity, "Synchronized with Android UserDictionary", Toast.LENGTH_SHORT).show()
            refreshList()
        }.apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = (6 * dp).toInt()
            }
        }
        val addBtn = UiKit.createPillButton(this, "+ Add", filled = true) {
            showAddWordDialog()
        }
        trailingActions.addView(syncBtn)
        trailingActions.addView(addBtn)

        root.addView(
            UiKit.createHeaderBar(
                this,
                title = "Personal Dictionary",
                subtitle = "Custom vocabulary & text shortcuts",
                showBack = true,
                trailingView = trailingActions
            )
        )

        emptyText = TextView(this).apply {
            text = "No custom words yet.\nTap '+ Add' or keep typing — repeated words auto-learn into suggestions & Glide!"
            textSize = 14f
            setTextColor(UiKit.TEXT_SECONDARY)
            gravity = Gravity.CENTER
            background = UiKit.roundedDrawable(this@PersonalDictionaryActivity, UiKit.SURFACE_CARD, 16f, UiKit.BORDER_SUBTLE, 1f)
            setPadding((20 * dp).toInt(), (28 * dp).toInt(), (20 * dp).toInt(), (28 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = (16 * dp).toInt()
            }
        }
        root.addView(emptyText)

        recyclerView = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@PersonalDictionaryActivity)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }
        root.addView(recyclerView)

        setContentView(root)
        refreshList()
    }

    private fun refreshList() {
        val words = dictManager.getAllPersonalWords()
        if (words.isEmpty()) {
            emptyText.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            emptyText.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
            recyclerView.adapter = WordsAdapter(words) { wordEntry ->
                AlertDialog.Builder(this)
                    .setTitle("Delete '${wordEntry.word}'")
                    .setMessage("Remove from personal dictionary?")
                    .setPositiveButton("Delete") { _, _ ->
                        dictManager.deleteWord(wordEntry.word)
                        refreshList()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
    }

    private fun showAddWordDialog() {
        val dp = resources.displayMetrics.density
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * dp).toInt(), (10 * dp).toInt(), (20 * dp).toInt(), (10 * dp).toInt())
        }

        val wordInput = EditText(this).apply {
            hint = "Word (e.g. On my way!)"
        }
        val shortcutInput = EditText(this).apply {
            hint = "Optional Shortcut (e.g. omw)"
        }
        container.addView(wordInput)
        container.addView(shortcutInput)

        AlertDialog.Builder(this)
            .setTitle("Add Word to Dictionary")
            .setView(container)
            .setPositiveButton("Add") { _, _ ->
                val word = wordInput.text.toString().trim()
                val shortcut = shortcutInput.text.toString().trim().ifBlank { null }
                if (word.isNotEmpty()) {
                    dictManager.addWord(word, shortcut)
                    refreshList()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private class WordsAdapter(
        private val words: List<WordEntry>,
        private val onDelete: (WordEntry) -> Unit
    ) : RecyclerView.Adapter<WordsAdapter.ViewHolder>() {

        class ViewHolder(val view: View, val tvWord: TextView, val tvShortcut: TextView, val tvDelete: TextView) :
            RecyclerView.ViewHolder(view)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val dp = parent.context.resources.displayMetrics.density
            val row = LinearLayout(parent.context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = UiKit.skeuoCardDrawable(parent.context, UiKit.SURFACE_CARD, 14f, UiKit.BORDER_SUBTLE, 2f)
                layoutParams = MarginLayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = (8 * dp).toInt()
                }
                setPadding((14 * dp).toInt(), (12 * dp).toInt(), (12 * dp).toInt(), (12 * dp).toInt())
            }

            val col = LinearLayout(parent.context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }

            val tvWord = TextView(parent.context).apply {
                setTextColor(UiKit.TEXT_PRIMARY)
                textSize = 15.5f
                typeface = Typeface.DEFAULT_BOLD
            }

            val tvShortcut = TextView(parent.context).apply {
                setTextColor(UiKit.ACCENT_PRIMARY)
                textSize = 12f
                background = UiKit.roundedDrawable(parent.context, UiKit.ACCENT_SOFT, 8f)
                setPadding((8 * dp).toInt(), (2 * dp).toInt(), (8 * dp).toInt(), (2 * dp).toInt())
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = (4 * dp).toInt()
                }
                visibility = View.GONE
            }

            col.addView(tvWord)
            col.addView(tvShortcut)

            val tvDelete = TextView(parent.context).apply {
                text = "✕"
                setTextColor(UiKit.DANGER_FG)
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                background = UiKit.roundedDrawable(parent.context, UiKit.DANGER_BG, 10f)
                val sz = (32 * dp).toInt()
                layoutParams = LinearLayout.LayoutParams(sz, sz)
            }

            row.addView(col)
            row.addView(tvDelete)
            return ViewHolder(row, tvWord, tvShortcut, tvDelete)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = words[position]
            holder.tvWord.text = item.word
            if (!item.shortcut.isNullOrBlank()) {
                holder.tvShortcut.text = "Shortcut: ${item.shortcut}"
                holder.tvShortcut.visibility = View.VISIBLE
            } else {
                holder.tvShortcut.visibility = View.GONE
            }
            holder.tvDelete.setOnClickListener { onDelete(item) }
        }

        override fun getItemCount(): Int = words.size
    }
}
