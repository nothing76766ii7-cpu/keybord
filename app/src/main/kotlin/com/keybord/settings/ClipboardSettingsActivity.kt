package com.keybord.settings

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.keybord.clipboard.*

/**
 * Clipboard settings screen for Retention Policy, Capacity, Sensitive Content,
 * and History management (PRD Feature 1).
 */
class ClipboardSettingsActivity : AppCompatActivity() {

    private lateinit var storage: EncryptedClipboardStorage
    private val prefs by lazy { getSharedPreferences("keybord_clipboard_prefs", Context.MODE_PRIVATE) }

    private lateinit var radioGroupRetention: RadioGroup
    private lateinit var rbForever: RadioButton
    private lateinit var rbDays: RadioButton
    private lateinit var rbItems: RadioButton
    private lateinit var tvCapacity: TextView
    private lateinit var swSensitive: Switch
    private lateinit var swRecentStrip: Switch

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        storage = EncryptedClipboardStorage(this)

        val dp = resources.displayMetrics.density
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#12121A"))
            setPadding((16 * dp).toInt(), (16 * dp).toInt(), (16 * dp).toInt(), (16 * dp).toInt())
        }

        // Header
        val title = TextView(this).apply {
            text = "Clipboard Settings"
            textSize = 22f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, (16 * dp).toInt())
        }
        root.addView(title)

        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        // Section: Retention Policy
        addSectionHeader(content, "⏳ Retention Policy")

        // Exemption Notice Banner
        val banner = TextView(this).apply {
            text = "📌 Pinned items are strictly exempt from all retention rules and stay forever."
            setTextColor(Color.parseColor("#81C784"))
            textSize = 13f
            setBackgroundColor(Color.parseColor("#1A2E1A"))
            setPadding((12 * dp).toInt(), (8 * dp).toInt(), (12 * dp).toInt(), (8 * dp).toInt())
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = (12 * dp).toInt()
            }
            layoutParams = lp
        }
        content.addView(banner)

        radioGroupRetention = RadioGroup(this)
        rbForever = RadioButton(this).apply {
            text = "Keep forever (default)"
            setTextColor(Color.WHITE)
        }
        rbDays = RadioButton(this).apply {
            val days = prefs.getInt("retention_days", 30)
            text = "Delete after $days days"
            setTextColor(Color.WHITE)
        }
        rbItems = RadioButton(this).apply {
            val count = prefs.getInt("retention_items", 500)
            text = "Keep last $count items"
            setTextColor(Color.WHITE)
        }
        radioGroupRetention.addView(rbForever)
        radioGroupRetention.addView(rbDays)
        radioGroupRetention.addView(rbItems)

        val savedPolicyStr = prefs.getString("retention_policy", "KEEP_FOREVER") ?: "KEEP_FOREVER"
        when {
            savedPolicyStr.startsWith("DELETE_AFTER_DAYS") -> rbDays.isChecked = true
            savedPolicyStr.startsWith("KEEP_LAST_COUNT") -> rbItems.isChecked = true
            else -> rbForever.isChecked = true
        }

        radioGroupRetention.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                rbForever.id -> {
                    prefs.edit().putString("retention_policy", "KEEP_FOREVER").apply()
                    storage.applyRetentionPolicy(RetentionPolicy.KeepForever, getCapacitySetting())
                }
                rbDays.id -> promptDaysSetting()
                rbItems.id -> promptItemsSetting()
            }
        }
        content.addView(radioGroupRetention)

        // Section: Storage Capacity
        addSectionHeader(content, "💾 Storage Capacity")
        val capacityCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#1B1B26"))
            setPadding((12 * dp).toInt(), (12 * dp).toInt(), (12 * dp).toInt(), (12 * dp).toInt())
            setOnClickListener { promptCapacity() }
        }
        tvCapacity = TextView(this).apply {
            val cap = getCapacitySetting()
            text = if (cap <= 0) "Capacity: Unlimited" else "Capacity: $cap items"
            setTextColor(Color.WHITE)
            textSize = 15f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val editCapBtn = TextView(this).apply {
            text = "Edit"
            setTextColor(Color.parseColor("#4285F4"))
            textSize = 14f
        }
        capacityCard.addView(tvCapacity)
        capacityCard.addView(editCapBtn)
        content.addView(capacityCard)

        // Section: Sensitive Content
        addSectionHeader(content, "🔒 Sensitive Content Handling")
        swSensitive = Switch(this).apply {
            text = "Ignore copies made from password fields"
            setTextColor(Color.WHITE)
            textSize = 14f
            isChecked = prefs.getBoolean("ignore_password_fields", true)
            setPadding(0, (6 * dp).toInt(), 0, (6 * dp).toInt())
            setOnCheckedChangeListener { _, isChecked ->
                prefs.edit().putBoolean("ignore_password_fields", isChecked).apply()
            }
        }
        content.addView(swSensitive)

        // Section: Suggestion Strip Inline Recents
        addSectionHeader(content, "✨ Suggestion Strip Integration")
        swRecentStrip = Switch(this).apply {
            text = "Show recent clips in suggestion strip (last 3)"
            setTextColor(Color.WHITE)
            textSize = 14f
            isChecked = prefs.getBoolean("show_recent_in_strip", true)
            setPadding(0, (6 * dp).toInt(), 0, (6 * dp).toInt())
            setOnCheckedChangeListener { _, isChecked ->
                prefs.edit().putBoolean("show_recent_in_strip", isChecked).apply()
            }
        }
        content.addView(swRecentStrip)

        // Action Buttons
        val btnManage = Button(this).apply {
            text = "Open Full-Screen Clipboard Manager"
            setBackgroundColor(Color.parseColor("#4285F4"))
            setTextColor(Color.WHITE)
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = (20 * dp).toInt()
            }
            layoutParams = lp
            setOnClickListener {
                startActivity(Intent(this@ClipboardSettingsActivity, ClipboardActivity::class.java))
            }
        }
        content.addView(btnManage)

        val btnClear = Button(this).apply {
            text = "Clear Clipboard History"
            setBackgroundColor(Color.parseColor("#2A1E24"))
            setTextColor(Color.parseColor("#FF5555"))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = (10 * dp).toInt()
            }
            layoutParams = lp
            setOnClickListener {
                AlertDialog.Builder(this@ClipboardSettingsActivity)
                    .setTitle("Clear Clipboard")
                    .setMessage("Clear unpinned history, or delete everything including pinned items?")
                    .setPositiveButton("Clear Unpinned Only") { _, _ ->
                        storage.clearAll(includePinned = false)
                        Toast.makeText(this@ClipboardSettingsActivity, "Unpinned history cleared", Toast.LENGTH_SHORT).show()
                    }
                    .setNeutralButton("Delete All (Inc. Pinned)") { _, _ ->
                        storage.clearAll(includePinned = true)
                        Toast.makeText(this@ClipboardSettingsActivity, "All clipboard history cleared", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
        content.addView(btnClear)

        scroll.addView(content)
        root.addView(scroll)
        setContentView(root)
    }

    private fun addSectionHeader(parent: LinearLayout, title: String) {
        val dp = resources.displayMetrics.density
        val tv = TextView(this).apply {
            text = title
            setTextColor(Color.parseColor("#4285F4"))
            textSize = 15f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, (14 * dp).toInt(), 0, (8 * dp).toInt())
        }
        parent.addView(tv)
    }

    private fun getCapacitySetting(): Int = prefs.getInt("clipboard_capacity", 500)

    private fun promptDaysSetting() {
        val input = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(prefs.getInt("retention_days", 30).toString())
        }
        AlertDialog.Builder(this)
            .setTitle("Delete After N Days")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val days = input.text.toString().toIntOrNull() ?: 30
                prefs.edit().putInt("retention_days", days).putString("retention_policy", "DELETE_AFTER_DAYS:$days").apply()
                rbDays.text = "Delete after $days days"
                storage.applyRetentionPolicy(RetentionPolicy.DeleteAfterDays(days), getCapacitySetting())
            }
            .setNegativeButton("Cancel") { _, _ -> rbForever.isChecked = true }
            .show()
    }

    private fun promptItemsSetting() {
        val input = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(prefs.getInt("retention_items", 500).toString())
        }
        AlertDialog.Builder(this)
            .setTitle("Keep Last N Items")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val items = input.text.toString().toIntOrNull() ?: 500
                prefs.edit().putInt("retention_items", items).putString("retention_policy", "KEEP_LAST_COUNT:$items").apply()
                rbItems.text = "Keep last $items items"
                storage.applyRetentionPolicy(RetentionPolicy.KeepLastCount(items), getCapacitySetting())
            }
            .setNegativeButton("Cancel") { _, _ -> rbForever.isChecked = true }
            .show()
    }

    private fun promptCapacity() {
        val input = EditText(this).apply {
            hint = "e.g. 500 (or -1 for unlimited)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_SIGNED
            setText(getCapacitySetting().toString())
        }
        AlertDialog.Builder(this)
            .setTitle("Set Maximum Capacity")
            .setMessage("Set maximum number of stored items (50 to unlimited [-1]):")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val cap = input.text.toString().toIntOrNull() ?: 500
                val validCap = if (cap < 0) -1 else cap.coerceAtLeast(50)
                prefs.edit().putInt("clipboard_capacity", validCap).apply()
                tvCapacity.text = if (validCap <= 0) "Capacity: Unlimited" else "Capacity: $validCap items"
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
