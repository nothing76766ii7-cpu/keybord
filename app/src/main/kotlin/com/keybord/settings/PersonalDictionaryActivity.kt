package com.keybord.settings

import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.keybord.engine.vocab.PersonalDictionaryManager
import com.keybord.engine.vocab.WordEntry

/**
 * Personal Dictionary settings screen conforming to PRD Section 4.2:
 * - View / Edit learned words
 * - Add word manually (word, shortcut, frequency)
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
            setBackgroundColor(Color.parseColor("#12121A"))
            setPadding((16 * dp).toInt(), (16 * dp).toInt(), (16 * dp).toInt(), (16 * dp).toInt())
        }

        // Top Header
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, (12 * dp).toInt())
        }

        val title = TextView(this).apply {
            text = "Personal Dictionary"
            textSize = 20f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val syncBtn = Button(this).apply {
            text = "Sync System"
            textSize = 12f
            setOnClickListener {
                dictManager.syncFromSystem(contentResolver)
                Toast.makeText(this@PersonalDictionaryActivity, "Synchronized with Android UserDictionary", Toast.LENGTH_SHORT).show()
                refreshList()
            }
        }

        val addBtn = Button(this).apply {
            text = "+ Add Word"
            textSize = 12f
            setBackgroundColor(Color.parseColor("#4285F4"))
            setTextColor(Color.WHITE)
            setOnClickListener { showAddWordDialog() }
        }

        header.addView(title)
        header.addView(syncBtn)
        header.addView(addBtn)
        root.addView(header)

        emptyText = TextView(this).apply {
            text = "No custom personal words yet.\nTap '+ Add Word' to add custom vocabulary and shortcuts."
            textSize = 14f
            setTextColor(Color.parseColor("#666688"))
            gravity = Gravity.CENTER
            setPadding(0, (40 * dp).toInt(), 0, 0)
        }
        root.addView(emptyText)

        recyclerView = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@PersonalDictionaryActivity)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
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
                setBackgroundColor(Color.parseColor("#1B1B26"))
                layoutParams = MarginLayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = (8 * dp).toInt()
                }
                setPadding((14 * dp).toInt(), (12 * dp).toInt(), (14 * dp).toInt(), (12 * dp).toInt())
            }

            val col = LinearLayout(parent.context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }

            val tvWord = TextView(parent.context).apply {
                setTextColor(Color.WHITE)
                textSize = 15f
            }

            val tvShortcut = TextView(parent.context).apply {
                setTextColor(Color.parseColor("#4285F4"))
                textSize = 12f
            }

            val tvDelete = TextView(parent.context).apply {
                text = "🗑"
                textSize = 16f
                setPadding((10 * dp).toInt(), (4 * dp).toInt(), (4 * dp).toInt(), (4 * dp).toInt())
            }

            col.addView(tvWord)
            col.addView(tvShortcut)
            row.addView(col)
            row.addView(tvDelete)

            return ViewHolder(row, tvWord, tvShortcut, tvDelete)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = words[position]
            holder.tvWord.text = item.word
            holder.tvShortcut.text = if (item.shortcut != null) "Shortcut: ${item.shortcut}" else "No shortcut"
            holder.tvDelete.setOnClickListener { onDelete(item) }
        }

        override fun getItemCount(): Int = words.size
    }
}
