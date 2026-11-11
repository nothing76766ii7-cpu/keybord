package com.keybord.settings

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.keybord.clipboard.ClipboardActivity
import com.keybord.clipboard.EncryptedClipboardStorage
import com.keybord.clipboard.RetentionPolicy
import com.keybord.ui.UiKit

/**
 * Stitch Screen 08: Clipboard & AES-256 Security Vault
 * Features AES-256 status pill, segmented retention control (24h | 7 Days | 30 Days | Unlimited),
 * Sensitive Content Auto-Mask toggle, Stored Encrypted Clips quick-link, and Keystore privacy footer.
 */
class ClipboardSettingsActivity : AppCompatActivity() {

    private lateinit var storage: EncryptedClipboardStorage
    private val prefs by lazy { getSharedPreferences("keybord_clipboard_prefs", Context.MODE_PRIVATE) }
    private lateinit var tvCapacity: TextView
    private lateinit var vaultStatsTv: TextView

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

        val vaultPill = UiKit.createBadgePill(
            this,
            "🔒 Encrypted",
            UiKit.SUCCESS_BG,
            UiKit.SUCCESS_FG,
            UiKit.SUCCESS_BORDER
        )
        root.addView(
            UiKit.createHeaderBar(
                this,
                title = "Clipboard Vault",
                subtitle = "Offline Secure Vault • Hardware-backed local storage",
                showBack = true,
                trailingView = vaultPill
            )
        )

        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            isVerticalScrollBarEnabled = false
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, (14 * dp).toInt())
        }

        // Card 1: Retention & Security Policies
        val policyCard = UiKit.createCardContainer(this, paddingDp = 16f, radiusDp = 18f, bottomMarginDp = 14f)
        val policyTitleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, (10 * dp).toInt())
        }
        policyTitleRow.addView(
            TextView(this).apply {
                text = "🛡"
                textSize = 16f
                gravity = Gravity.CENTER
                background = UiKit.skeuoIconWellDrawable(this@ClipboardSettingsActivity, UiKit.SURFACE_ELEVATED, 10f)
                val sz = (34 * dp).toInt()
                layoutParams = LinearLayout.LayoutParams(sz, sz).apply { marginEnd = (10 * dp).toInt() }
            }
        )
        policyTitleRow.addView(
            TextView(this).apply {
                text = "Retention & Security Policies"
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(UiKit.TEXT_PRIMARY)
            }
        )
        policyCard.addView(policyTitleRow)

        policyCard.addView(
            UiKit.createSwitchRow(
                this,
                "Save Clipboard History",
                "Cache copied text locally in encrypted enclave",
                prefs.getBoolean("save_clipboard_history", true)
            ) { prefs.edit().putBoolean("save_clipboard_history", it).apply() }
        )
        policyCard.addView(UiKit.createHairlineDivider(this))

        policyCard.addView(
            TextView(this).apply {
                text = "RETENTION POLICY"
                textSize = 11f
                letterSpacing = 0.08f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(UiKit.TEXT_SECONDARY)
                setPadding(0, (10 * dp).toInt(), 0, (8 * dp).toInt())
            }
        )

        val savedPolicyStr = prefs.getString("retention_policy", "KEEP_FOREVER") ?: "KEEP_FOREVER"
        val initialIdx = when {
            savedPolicyStr == "DELETE_AFTER_DAYS:1" -> 0
            savedPolicyStr == "DELETE_AFTER_DAYS:7" -> 1
            savedPolicyStr.startsWith("DELETE_AFTER_DAYS") -> 2
            else -> 3
        }
        policyCard.addView(
            UiKit.createSegmentedControl(
                this,
                listOf("24h", "7 Days", "30 Days", "Unlimited"),
                initialIdx
            ) { idx ->
                val (policyStr, policyObj) = when (idx) {
                    0 -> "DELETE_AFTER_DAYS:1" to RetentionPolicy.DeleteAfterDays(1)
                    1 -> "DELETE_AFTER_DAYS:7" to RetentionPolicy.DeleteAfterDays(7)
                    2 -> "DELETE_AFTER_DAYS:30" to RetentionPolicy.DeleteAfterDays(30)
                    else -> "KEEP_FOREVER" to RetentionPolicy.KeepForever
                }
                prefs.edit().putString("retention_policy", policyStr).apply()
                storage.applyRetentionPolicy(policyObj, getCapacitySetting())
            }.apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = (8 * dp).toInt()
                }
            }
        )

        policyCard.addView(UiKit.createHairlineDivider(this))
        policyCard.addView(
            UiKit.createSwitchRow(
                this,
                "Sensitive Content Auto-Mask",
                "Ignore password fields and OTP sensitive copies",
                prefs.getBoolean("ignore_password_fields", true)
            ) { prefs.edit().putBoolean("ignore_password_fields", it).apply() }
        )
        policyCard.addView(UiKit.createHairlineDivider(this))
        policyCard.addView(
            UiKit.createSwitchRow(
                this,
                "Recent Clips in Suggestion Strip",
                "Surface last 3 copied clips directly on keyboard bar",
                prefs.getBoolean("show_recent_in_strip", true)
            ) { prefs.edit().putBoolean("show_recent_in_strip", it).apply() }
        )
        content.addView(policyCard)

        // Card 2: Storage Capacity & Stored Clips Vault Entry
        val vaultCard = UiKit.createCardContainer(this, paddingDp = 16f, radiusDp = 18f, bottomMarginDp = 14f)
        val capacityRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, (2 * dp).toInt(), 0, (10 * dp).toInt())
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
        capacityRow.addView(tvCapacity)
        capacityRow.addView(UiKit.createBadgePill(this, "Edit", UiKit.ACCENT_SOFT, UiKit.ACCENT_PRIMARY))
        vaultCard.addView(capacityRow)
        vaultCard.addView(UiKit.createHairlineDivider(this))

        vaultStatsTv = TextView(this).apply {
            textSize = 13f
            setTextColor(UiKit.TEXT_SECONDARY)
            setPadding(0, (10 * dp).toInt(), 0, (12 * dp).toInt())
        }
        vaultCard.addView(vaultStatsTv)

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val manageBtn = UiKit.createPillButton(this, "Open Encrypted Clips ↗", filled = true) {
            startActivity(Intent(this@ClipboardSettingsActivity, ClipboardActivity::class.java))
        }.apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.4f).apply {
                marginEnd = (8 * dp).toInt()
            }
        }
        val clearBtn = UiKit.createPillButton(this, "Clear History", filled = false, danger = true) {
            AlertDialog.Builder(this@ClipboardSettingsActivity)
                .setTitle("Clear Clipboard Vault")
                .setMessage("Clear unpinned history, or delete everything including pinned items?")
                .setPositiveButton("Clear Unpinned") { _, _ ->
                    storage.clearAll(includePinned = false)
                    refreshVaultStats()
                    Toast.makeText(this@ClipboardSettingsActivity, "Unpinned history cleared", Toast.LENGTH_SHORT).show()
                }
                .setNeutralButton("Delete All") { _, _ ->
                    storage.clearAll(includePinned = true)
                    refreshVaultStats()
                    Toast.makeText(this@ClipboardSettingsActivity, "All clipboard history cleared", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }.apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        btnRow.addView(manageBtn)
        btnRow.addView(clearBtn)
        vaultCard.addView(btnRow)
        content.addView(vaultCard)

        // Bottom Keystore Privacy Footer (Stitch 08)
        content.addView(
            UiKit.createPrivacyFooter(
                this,
                "Hardware-Backed Local Enclave",
                "Keystrokes and clipboard items stay strictly isolated on this device. Pinned clips never auto-expire."
            )
        )

        scroll.addView(content)
        root.addView(scroll)
        setContentView(root)
        refreshVaultStats()
    }

    override fun onResume() {
        super.onResume()
        if (::vaultStatsTv.isInitialized) {
            refreshVaultStats()
        }
    }

    private fun refreshVaultStats() {
        val all = storage.getAllItems()
        val pinnedCount = all.count { it.isPinned }
        vaultStatsTv.text = "Stored Encrypted Clips: ${all.size} total • $pinnedCount pinned (never expire)"
    }

    private fun getCapacitySetting(): Int = prefs.getInt("clipboard_capacity", 500)

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
