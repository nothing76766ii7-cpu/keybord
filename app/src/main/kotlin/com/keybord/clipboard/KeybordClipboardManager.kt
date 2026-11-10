package com.keybord.clipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.text.InputType
import android.view.inputmethod.EditorInfo

/**
 * High-level manager coordinating system clipboard monitoring, sensitive content detection,
 * inline suggestion strip feed, and full-screen clipboard management.
 */
class KeybordClipboardManager(
    private val context: Context,
    val storage: EncryptedClipboardStorage = EncryptedClipboardStorage(context)
) : ClipboardManager.OnPrimaryClipChangedListener {

    private val systemClipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    var settings: ClipboardSettings = ClipboardSettings()

    private var currentEditorInfo: EditorInfo? = null
    private val dismissedRecentIds = HashSet<Long>()

    // Listeners for clipboard updates
    private val listeners = mutableListOf<() -> Unit>()

    fun startListening() {
        try {
            systemClipboard?.addPrimaryClipChangedListener(this)
        } catch (e: Exception) {
            // Context or permission restrictions
        }
    }

    fun stopListening() {
        try {
            systemClipboard?.removePrimaryClipChangedListener(this)
        } catch (e: Exception) {
            // Ignore
        }
    }

    fun addChangeListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeChangeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    /**
     * Updates current active editor info so password field detection works seamlessly.
     */
    fun updateCurrentEditorInfo(info: EditorInfo?) {
        this.currentEditorInfo = info
    }

    /**
     * Determines whether the current active input target is a password/sensitive field.
     */
    fun isCurrentFieldPassword(): Boolean {
        val info = currentEditorInfo ?: return false
        val inputType = info.inputType
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        val clazz = inputType and InputType.TYPE_MASK_CLASS

        return when {
            clazz == InputType.TYPE_CLASS_TEXT && (
                variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
            ) -> true
            clazz == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD -> true
            else -> false
        }
    }

    override fun onPrimaryClipChanged() {
        val clip = systemClipboard?.primaryClip ?: return
        if (clip.itemCount == 0) return

        val item = clip.getItemAt(0)
        val text = item.text?.toString() ?: return
        if (text.isBlank()) return

        // Sensitive content handling:
        // 1. Password field active
        val isPasswordField = isCurrentFieldPassword()

        // 2. Android 13+ extra isSensitive
        val isExplicitSensitive = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            clip.description?.extras?.getBoolean("android.content.extra.IS_SENSITIVE") == true
        } else false

        val isSensitive = isPasswordField || isExplicitSensitive

        if (isSensitive && !settings.allowSensitiveCopies) {
            // PRD: "Clipboard does not store copies made from password fields by default"
            return
        }

        // Auto-detect category
        val category = detectCategory(text)

        storage.insertItem(
            text = text,
            category = category,
            isPinned = false,
            sourcePackage = currentEditorInfo?.packageName,
            isSensitive = isSensitive,
            retentionPolicy = settings.retentionPolicy,
            capacityLimit = settings.capacityLimit
        )

        notifyChanged()
    }

    /**
     * Auto-detects basic category: Links, Code, or General.
     */
    private fun detectCategory(text: String): String {
        val trimmed = text.trim()
        return when {
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> "Links"
            trimmed.contains("{") && trimmed.contains("}") || trimmed.contains("fun ") || trimmed.contains("def ") || trimmed.startsWith("SELECT ") -> "Code"
            else -> ClipboardItem.DEFAULT_CATEGORY
        }
    }

    /**
     * Returns up to 3 recent, un-dismissed clipboard items for the suggestion strip.
     */
    fun getRecentItemsForStrip(): List<ClipboardItem> {
        if (!settings.showRecentInSuggestionStrip) return emptyList()
        val recents = storage.getRecentItems(settings.maxRecentInStrip + dismissedRecentIds.size)
        return recents.filter { !dismissedRecentIds.contains(it.id) }.take(settings.maxRecentInStrip)
    }

    fun dismissRecentItem(id: Long) {
        dismissedRecentIds.add(id)
        notifyChanged()
    }

    fun copyToClipboard(text: String, label: String = "Keybord Clip") {
        try {
            val clip = ClipData.newPlainText(label, text)
            systemClipboard?.setPrimaryClip(clip)
        } catch (e: Exception) {
            // Ignore
        }
    }

    private fun notifyChanged() {
        for (listener in listeners) {
            listener.invoke()
        }
    }
}
