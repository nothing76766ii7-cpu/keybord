package com.keybord.settings

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.keybord.clipboard.ClipboardActivity
import com.keybord.clipboard.EncryptedClipboardStorage
import com.keybord.clipboard.RetentionPolicy
import com.keybord.ui.UiKit

/**
 * Clipboard settings screen for Retention Policy, Capacity, Sensitive Content,
 * and History management with unified Keybord dark UI cards.
 */
class ClipboardSettingsActivity : AppCompatActivity() {

    private lateinit var storage: EncryptedClipboardStorage
    private val prefs by lazy { getSharedPreferences("keybord_clipboard_prefs", Context.MODE_PRIVATE) }

    private lateinit var radioGroupRetention: RadioGroup
    private lateinit var rbForever: RadioButton
    private lateinit var rbDays: RadioButton
    private lateinit var rbItems: RadioButton
    private lateinit var tvCapacity: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        storage = EncryptedClipboardStorage(this)

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

        val manageBtn = UiKit.createPillButton(this, "History ↗", filled = false) {
            startActivity(Intent(this, ClipboardActivity::class.java))
        }
        root.addView(
            UiKit.createHeaderBar(
                this,
                title = "Clipboard Rules",
                subtitle = "Retention policy, capacity & privacy protection",
                showBack = true,
                trailingView = manageBtn
            )
        )

        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            isVerticalScrollBarEnabled = false
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, (16 * dp).toInt())
        }

        // Exemption Notice Banner
        val banner = TextView(this).apply {
            text = "📌 Pinned clips are strictly exempt from all cleanup rules and never expire."
            setTextColor(UiKit.SUCCESS_FG)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            background = UiKit.roundedDrawable(this@ClipboardSettingsActivity, UiKit.SUCCESS_BG, 14f, Color.parseColor("#2A5945"), 1f)
            setPadding((14 * dp).toInt(), (11 * dp).toInt(), (14 * dp).toInt(), (11 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = (8 * dp).toInt()
            }
        }
        content.addView(banner)

        // Section: Retention Policy
        content.addView(UiKit.createSectionLabel(this, "Retention Policy"))

        val retentionCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = UiKit.roundedDrawable(this@ClipboardSettingsActivity, UiKit.SURFACE_CARD, 16f, UiKit.BORDER_SUBTLE, 1f)
            setPadding((12 * dp).toInt(), (8 * dp).toInt(), (12 * dp).toInt(), (8 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = (8 * dp).toInt()
            }
        }
        val radioTint = ColorStateList.valueOf(UiKit.ACCENT_PRIMARY)
        radioGroupRetention = RadioGroup(this)
        rbForever = RadioButton(this).apply {
            text = "Keep forever (default)"
            setTextColor(UiKit.TEXT_PRIMARY)
            buttonTintList = radioTint
            setPadding((6 * dp).toInt(), (6 * dp).toInt(), 0, (6 * dp).toInt())
        }
        rbDays = RadioButton(this).apply {
            val days = prefs.getInt("retention_days", 30)
            text = "Delete after $days days"
            setTextColor(UiKit.TEXT_PRIMARY)
            buttonTintList = radioTint
            setPadding((6 * dp).toInt(), (6 * dp).toInt(), 0, (6 * dp).toInt())
        }
        rbItems = RadioButton(this).apply {
            val count = prefs.getInt("retention_items", 500)
            text = "Keep last $count items"
            setTextColor(UiKit.TEXT_PRIMARY)
            buttonTintList = radioTint
            setPadding((6 * dp).toInt(), (6 * dp).toInt(), 0, (6 * dp).toInt())
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
        retentionCard.addView(radioGroupRetention)
        content.addView(retentionCard)

        // Section: Storage Capacity
        content.addView(UiKit.createSectionLabel(this, "Storage Capacity"))
        val capacityCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = UiKit.roundedDrawable(this@ClipboardSettingsActivity, UiKit.SURFACE_CARD, 16f, UiKit.BORDER_SUBTLE, 1f)
            setPadding((16 * dp).toInt(), (14 * dp).toInt(), (14 * dp).toInt(), (14 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = (8 * dp).toInt()
            }
            setOnClickListener { promptCapacity() }
        }
        tvCapacity = TextView(this).apply {
            val cap = getCapacitySetting()
            text = if (cap <= 0) "Capacity: Unlimited" else "Capacity: $cap items"
            setTextColor(UiKit.TEXT_PRIMARY)
            typeface = Typeface.DEFAULT_BOLD
            textSize = 15f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val editCapBtn = TextView(this).apply {
            text = "Edit"
            textSize = 12.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(UiKit.ACCENT_PRIMARY)
            background = UiKit.roundedDrawable(this@ClipboardSettingsActivity, UiKit.ACCENT_SOFT, 10f)
            setPadding((10 * dp).toInt(), (5 * dp).toInt(), (10 * dp).toInt(), (5 * dp).toInt())
        }
        capacityCard.addView(tvCapacity)
        capacityCard.addView(editCapBtn)
        content.addView(capacityCard)

        // Section: Privacy & Strip
        content.addView(UiKit.createSectionLabel(this, "Privacy & Suggestion Strip"))
        content.addView(
            UiKit.createSwitchCard(
                this,
                title = "Ignore Password Fields",
                subtitle = "Skip saving clipboard copies made inside sensitive password inputs",
                checked = prefs.getBoolean("ignore_password_fields", true)
            ) { isChecked ->
                prefs.edit().putBoolean("ignore_password_fields", isChecked).apply()
            }
        )
        content.addView(
            UiKit.createSwitchCard(
                this,
                title = "Recent Clips in Strip",
                subtitle = "Show up to 3 latest copied chips directly in the keyboard strip",
                checked = prefs.getBoolean("show_recent_in_strip", true)
            ) { isChecked ->
                prefs.edit().putBoolean("show_recent_in_strip", isChecked).apply()
            }
        )

        // Action Buttons
        val btnManage = UiKit.createPillButton(this, "Open Full-Screen Clipboard Manager", filled = true) {
            startActivity(Intent(this@ClipboardSettingsActivity, ClipboardActivity::class.java))
        }.apply {
            textSize = 14.5f
            setPadding(0, (13 * dp).toInt(), 0, (13 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = (14 * dp).toInt()
            }
        }
        content.addView(btnManage)

        val btnClear = UiKit.createPillButton(this, "Clear Clipboard History", filled = false, danger = true) {
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
        }.apply {
            textSize = 14f
            setPadding(0, (13 * dp).toInt(), 0, (13 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = (10 * dp).toInt()
            }
        }
        content.addView(btnClear)

        scroll.addView(content)
        root.addView(scroll)
        setContentView(root)
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
