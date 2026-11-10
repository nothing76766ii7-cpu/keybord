package com.keybord.clipboard

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * SQLite storage engine for Keybord clipboard history with AES-GCM encryption at rest.
 * Enforces user retention policies, capacity caps, and pinned-item immunity.
 */
class EncryptedClipboardStorage(
    context: Context?,
    private val dbName: String? = if (context == null) null else "keybord_clipboard.db",
    customKeyBytes: ByteArray? = null
) : SQLiteOpenHelper(context, dbName, null, 1) {

    private val secretKey: SecretKeySpec
    private val secureRandom = SecureRandom()

    init {
        // Derive or fallback to a deterministic 256-bit key for local encryption
        val keyBytes = customKeyBytes ?: run {
            val seed = (context?.packageName ?: "com.keybord.app.default.key").toByteArray(StandardCharsets.UTF_8)
            val padded = ByteArray(32)
            System.arraycopy(seed, 0, padded, 0, minOf(seed.size, 32))
            if (seed.size < 32) {
                for (i in seed.size until 32) {
                    padded[i] = (i * 31).toByte()
                }
            }
            padded
        }
        secretKey = SecretKeySpec(keyBytes, "AES")
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE clipboard_items (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                encrypted_data BLOB NOT NULL,
                iv BLOB NOT NULL,
                category TEXT NOT NULL DEFAULT 'General',
                is_pinned INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL,
                source_package TEXT,
                is_sensitive INTEGER NOT NULL DEFAULT 0
            );
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE categories (
                name TEXT PRIMARY KEY,
                color_hex TEXT NOT NULL,
                order_index INTEGER NOT NULL
            );
            """.trimIndent()
        )

        db.execSQL("CREATE INDEX idx_created_at ON clipboard_items (created_at DESC);")
        db.execSQL("CREATE INDEX idx_category ON clipboard_items (category);")
        db.execSQL("CREATE INDEX idx_pinned ON clipboard_items (is_pinned);")

        // Seed default categories
        val defaultCategories = listOf(
            ClipboardCategory("General", "#4285F4", 0),
            ClipboardCategory("Code", "#34A853", 1),
            ClipboardCategory("Links", "#FBBC05", 2),
            ClipboardCategory("Notes", "#EA4335", 3)
        )
        for (cat in defaultCategories) {
            val cv = ContentValues().apply {
                put("name", cat.name)
                put("color_hex", cat.colorHex)
                put("order_index", cat.orderIndex)
            }
            db.insert("categories", null, cv)
        }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS clipboard_items")
        db.execSQL("DROP TABLE IF EXISTS categories")
        onCreate(db)
    }

    /**
     * Encrypts plaintext using AES-256-GCM with a random 12-byte IV.
     */
    private fun encrypt(plainText: String): Pair<ByteArray, ByteArray> {
        val iv = ByteArray(12)
        secureRandom.nextBytes(iv)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
        val cipherBytes = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
        return Pair(cipherBytes, iv)
    }

    /**
     * Decrypts ciphertext using AES-256-GCM.
     */
    private fun decrypt(cipherBytes: ByteArray, iv: ByteArray): String {
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
            val plainBytes = cipher.doFinal(cipherBytes)
            String(plainBytes, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            "[Decryption Error]"
        }
    }

    /**
     * Inserts a new clipboard entry with encryption and executes retention pruning.
     */
    fun insertItem(
        text: String,
        category: String = ClipboardItem.DEFAULT_CATEGORY,
        isPinned: Boolean = false,
        sourcePackage: String? = null,
        isSensitive: Boolean = false,
        retentionPolicy: RetentionPolicy = RetentionPolicy.KeepForever,
        capacityLimit: Int = 500
    ): Long {
        if (text.isBlank()) return -1L

        // Check if duplicate of most recent item to avoid identical consecutive spam
        val recentItems = getRecentItems(1)
        if (recentItems.isNotEmpty() && recentItems.first().text == text) {
            // Bump timestamp and return existing
            val existing = recentItems.first()
            val cv = ContentValues().apply {
                put("created_at", System.currentTimeMillis())
            }
            writableDatabase.update("clipboard_items", cv, "id = ?", arrayOf(existing.id.toString()))
            return existing.id
        }

        val (encryptedData, iv) = encrypt(text)
        val cv = ContentValues().apply {
            put("encrypted_data", encryptedData)
            put("iv", iv)
            put("category", category)
            put("is_pinned", if (isPinned) 1 else 0)
            put("created_at", System.currentTimeMillis())
            put("source_package", sourcePackage)
            put("is_sensitive", if (isSensitive) 1 else 0)
        }

        val id = writableDatabase.insert("clipboard_items", null, cv)

        // Apply retention rules
        applyRetentionPolicy(retentionPolicy, capacityLimit)
        return id
    }

    fun getItem(id: Long): ClipboardItem? {
        val cursor = readableDatabase.rawQuery(
            "SELECT id, encrypted_data, iv, category, is_pinned, created_at, source_package, is_sensitive FROM clipboard_items WHERE id = ?",
            arrayOf(id.toString())
        )
        val item = if (cursor.moveToFirst()) {
            val cipherData = cursor.getBlob(1)
            val iv = cursor.getBlob(2)
            ClipboardItem(
                id = cursor.getLong(0),
                text = decrypt(cipherData, iv),
                category = cursor.getString(3),
                isPinned = cursor.getInt(4) == 1,
                createdAt = cursor.getLong(5),
                sourcePackage = cursor.getString(6),
                isSensitive = cursor.getInt(7) == 1
            )
        } else null
        cursor.close()
        return item
    }

    fun getAllItems(categoryFilter: String? = null): List<ClipboardItem> {
        val query = if (categoryFilter.isNullOrBlank() || categoryFilter == "All") {
            "SELECT id, encrypted_data, iv, category, is_pinned, created_at, source_package, is_sensitive FROM clipboard_items ORDER BY is_pinned DESC, created_at DESC"
        } else {
            "SELECT id, encrypted_data, iv, category, is_pinned, created_at, source_package, is_sensitive FROM clipboard_items WHERE category = ? ORDER BY is_pinned DESC, created_at DESC"
        }
        val args = if (categoryFilter.isNullOrBlank() || categoryFilter == "All") null else arrayOf(categoryFilter)

        val cursor = readableDatabase.rawQuery(query, args)
        val items = mutableListOf<ClipboardItem>()
        while (cursor.moveToNext()) {
            val cipherData = cursor.getBlob(1)
            val iv = cursor.getBlob(2)
            items.add(
                ClipboardItem(
                    id = cursor.getLong(0),
                    text = decrypt(cipherData, iv),
                    category = cursor.getString(3),
                    isPinned = cursor.getInt(4) == 1,
                    createdAt = cursor.getLong(5),
                    sourcePackage = cursor.getString(6),
                    isSensitive = cursor.getInt(7) == 1
                )
            )
        }
        cursor.close()
        return items
    }

    fun getPinnedItems(): List<ClipboardItem> {
        val cursor = readableDatabase.rawQuery(
            "SELECT id, encrypted_data, iv, category, is_pinned, created_at, source_package, is_sensitive FROM clipboard_items WHERE is_pinned = 1 ORDER BY created_at DESC",
            null
        )
        val items = mutableListOf<ClipboardItem>()
        while (cursor.moveToNext()) {
            val cipherData = cursor.getBlob(1)
            val iv = cursor.getBlob(2)
            items.add(
                ClipboardItem(
                    id = cursor.getLong(0),
                    text = decrypt(cipherData, iv),
                    category = cursor.getString(3),
                    isPinned = true,
                    createdAt = cursor.getLong(5),
                    sourcePackage = cursor.getString(6),
                    isSensitive = cursor.getInt(7) == 1
                )
            )
        }
        cursor.close()
        return items
    }

    fun getRecentItems(limit: Int = 3): List<ClipboardItem> {
        val cursor = readableDatabase.rawQuery(
            "SELECT id, encrypted_data, iv, category, is_pinned, created_at, source_package, is_sensitive FROM clipboard_items ORDER BY created_at DESC LIMIT ?",
            arrayOf(limit.toString())
        )
        val items = mutableListOf<ClipboardItem>()
        while (cursor.moveToNext()) {
            val cipherData = cursor.getBlob(1)
            val iv = cursor.getBlob(2)
            items.add(
                ClipboardItem(
                    id = cursor.getLong(0),
                    text = decrypt(cipherData, iv),
                    category = cursor.getString(3),
                    isPinned = cursor.getInt(4) == 1,
                    createdAt = cursor.getLong(5),
                    sourcePackage = cursor.getString(6),
                    isSensitive = cursor.getInt(7) == 1
                )
            )
        }
        cursor.close()
        return items
    }

    /**
     * Performs prefix and substring matching across decrypted stored entries.
     */
    fun searchItems(query: String, categoryFilter: String? = null): List<ClipboardItem> {
        val cleanQuery = query.trim().lowercase()
        if (cleanQuery.isEmpty()) return getAllItems(categoryFilter)

        val all = getAllItems(categoryFilter)
        return all.filter { it.text.lowercase().contains(cleanQuery) }
    }

    fun setPinned(id: Long, isPinned: Boolean) {
        val cv = ContentValues().apply {
            put("is_pinned", if (isPinned) 1 else 0)
        }
        writableDatabase.update("clipboard_items", cv, "id = ?", arrayOf(id.toString()))
    }

    fun deleteItem(id: Long) {
        writableDatabase.delete("clipboard_items", "id = ?", arrayOf(id.toString()))
    }

    /**
     * Multi-select bulk deletion.
     */
    fun deleteItems(ids: List<Long>) {
        if (ids.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (id in ids) {
                db.delete("clipboard_items", "id = ?", arrayOf(id.toString()))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Multi-select bulk category assignment.
     */
    fun assignCategory(ids: List<Long>, category: String) {
        if (ids.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            val cv = ContentValues().apply {
                put("category", category)
            }
            for (id in ids) {
                db.update("clipboard_items", cv, "id = ?", arrayOf(id.toString()))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun clearAll(includePinned: Boolean = false) {
        if (includePinned) {
            writableDatabase.delete("clipboard_items", null, null)
        } else {
            writableDatabase.delete("clipboard_items", "is_pinned = 0", null)
        }
    }

    /**
     * Applies user-specified retention policies.
     * CRITICAL RULE: Pinned items are strictly exempt from all retention rules.
     */
    fun applyRetentionPolicy(policy: RetentionPolicy, capacityLimit: Int) {
        val db = writableDatabase
        val now = System.currentTimeMillis()

        db.beginTransaction()
        try {
            // 1. Time-based retention policy
            when (policy) {
                is RetentionPolicy.KeepForever -> {
                    // No age-based pruning
                }
                is RetentionPolicy.DeleteAfterDays -> {
                    val cutoff = now - (policy.days.toLong() * 24L * 3600L * 1000L)
                    db.delete("clipboard_items", "is_pinned = 0 AND created_at < ?", arrayOf(cutoff.toString()))
                }
                is RetentionPolicy.KeepLastCount -> {
                    // Keep up to N unpinned items
                    val cursor = db.rawQuery(
                        "SELECT id FROM clipboard_items WHERE is_pinned = 0 ORDER BY created_at DESC LIMIT -1 OFFSET ?",
                        arrayOf(policy.maxItems.toString())
                    )
                    val idsToDelete = mutableListOf<Long>()
                    while (cursor.moveToNext()) {
                        idsToDelete.add(cursor.getLong(0))
                    }
                    cursor.close()
                    for (delId in idsToDelete) {
                        db.delete("clipboard_items", "id = ?", arrayOf(delId.toString()))
                    }
                }
            }

            // 2. Capacity limit enforcement (if capacityLimit > 0)
            if (capacityLimit > 0) {
                val cursor = db.rawQuery("SELECT COUNT(*) FROM clipboard_items WHERE is_pinned = 0", null)
                var unpinnedCount = 0
                if (cursor.moveToFirst()) {
                    unpinnedCount = cursor.getInt(0)
                }
                cursor.close()

                if (unpinnedCount > capacityLimit) {
                    val excess = unpinnedCount - capacityLimit
                    val pruneCursor = db.rawQuery(
                        "SELECT id FROM clipboard_items WHERE is_pinned = 0 ORDER BY created_at ASC LIMIT ?",
                        arrayOf(excess.toString())
                    )
                    val excessIds = mutableListOf<Long>()
                    while (pruneCursor.moveToNext()) {
                        excessIds.add(pruneCursor.getLong(0))
                    }
                    pruneCursor.close()
                    for (delId in excessIds) {
                        db.delete("clipboard_items", "id = ?", arrayOf(delId.toString()))
                    }
                }
            }

            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    // ==================== Category Operations ====================

    fun getCategories(): List<ClipboardCategory> {
        val cursor = readableDatabase.rawQuery("SELECT name, color_hex, order_index FROM categories ORDER BY order_index ASC", null)
        val list = mutableListOf<ClipboardCategory>()
        while (cursor.moveToNext()) {
            list.add(
                ClipboardCategory(
                    name = cursor.getString(0),
                    colorHex = cursor.getString(1),
                    orderIndex = cursor.getInt(2)
                )
            )
        }
        cursor.close()
        return list
    }

    fun addCategory(category: ClipboardCategory) {
        val cv = ContentValues().apply {
            put("name", category.name)
            put("color_hex", category.colorHex)
            put("order_index", category.orderIndex)
        }
        writableDatabase.insertWithOnConflict("categories", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun deleteCategory(name: String) {
        if (name == ClipboardItem.DEFAULT_CATEGORY) return // Protect default category
        val db = writableDatabase
        db.beginTransaction()
        try {
            // Reset items in this category back to General
            val cv = ContentValues().apply {
                put("category", ClipboardItem.DEFAULT_CATEGORY)
            }
            db.update("clipboard_items", cv, "category = ?", arrayOf(name))
            db.delete("categories", "name = ?", arrayOf(name))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
}
