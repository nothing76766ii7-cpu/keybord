package com.keybord.clipboard

/**
 * Retention policy for stored clipboard history.
 * Pinned items are strictly exempt from all retention rules.
 */
sealed class RetentionPolicy {
    object KeepForever : RetentionPolicy() {
        override fun toString(): String = "KEEP_FOREVER"
    }

    data class DeleteAfterDays(val days: Int) : RetentionPolicy() {
        override fun toString(): String = "DELETE_AFTER_DAYS:$days"
    }

    data class KeepLastCount(val maxItems: Int) : RetentionPolicy() {
        override fun toString(): String = "KEEP_LAST_COUNT:$maxItems"
    }

    companion object {
        fun fromString(str: String): RetentionPolicy {
            return when {
                str == "KEEP_FOREVER" -> KeepForever
                str.startsWith("DELETE_AFTER_DAYS:") -> {
                    val days = str.substringAfter(":").toIntOrNull() ?: 30
                    DeleteAfterDays(days)
                }
                str.startsWith("KEEP_LAST_COUNT:") -> {
                    val count = str.substringAfter(":").toIntOrNull() ?: 500
                    KeepLastCount(count)
                }
                else -> KeepForever
            }
        }
    }
}

/**
 * A decrypted clipboard history item.
 */
data class ClipboardItem(
    val id: Long = 0L,
    val text: String,
    val category: String = DEFAULT_CATEGORY,
    val isPinned: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val sourcePackage: String? = null,
    val isSensitive: Boolean = false
) {
    companion object {
        const val DEFAULT_CATEGORY = "General"
    }
}

/**
 * User-defined category for organizing clipboard items.
 */
data class ClipboardCategory(
    val name: String,
    val colorHex: String = "#4285F4",
    val orderIndex: Int = 0
)

/**
 * User preferences for clipboard retention, capacity, and security.
 */
data class ClipboardSettings(
    val retentionPolicy: RetentionPolicy = RetentionPolicy.KeepForever,
    val capacityLimit: Int = 500, // 50 to -1 (unlimited)
    val allowSensitiveCopies: Boolean = false, // Password field copy protection
    val showRecentInSuggestionStrip: Boolean = true,
    val maxRecentInStrip: Int = 3
) {
    val isUnlimitedCapacity: Boolean get() = capacityLimit <= 0
}
