package com.keyboard

import android.content.ContentValues
import android.content.Context
import android.preference.PreferenceManager
import com.keyboard.data.ClipboardCategory
import com.keyboard.data.ClipboardDatabase
import com.keyboard.data.ClipboardItem

/**
 * Unlimited Clipboard Manager strictly conforming to Section A.2.
 */
class ClipboardManager(private val context: Context) {

    private val dbHelper = ClipboardDatabase(context)
    private val prefs = context.getSharedPreferences("keybord_prefs", Context.MODE_PRIVATE)

    fun saveItem(content: String, isSensitive: Boolean) {
        val clean = content.trim()
        if (clean.isEmpty()) return

        // Sensitive content gate (Section A.1)
        val blockPasswords = prefs.getBoolean("clipboard_block_passwords", true)
        if (isSensitive && blockPasswords) {
            return
        }

        val db = dbHelper.writableDatabase
        val now = System.currentTimeMillis()

        // Check if identical unpinned item exists, update last_used
        val cursor = db.rawQuery(
            "SELECT id FROM clipboard_items WHERE content = ? LIMIT 1",
            arrayOf(clean)
        )
        if (cursor.moveToFirst()) {
            val existingId = cursor.getLong(0)
            cursor.close()
            val cv = ContentValues().apply {
                put("last_used", now)
            }
            db.update("clipboard_items", cv, "id = ?", arrayOf(existingId.toString()))
        } else {
            cursor.close()
            val cv = ContentValues().apply {
                put("content", clean)
                put("pinned", 0)
                put("created_at", now)
                put("last_used", now)
            }
            db.insert("clipboard_items", null, cv)
        }

        applyRetentionPolicy()
    }

    fun getRecentItems(limit: Int = 3): List<ClipboardItem> {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery(
            "SELECT id, content, pinned, category_id, created_at, last_used FROM clipboard_items WHERE pinned = 0 ORDER BY last_used DESC LIMIT ?",
            arrayOf(limit.toString())
        )
        val list = mutableListOf<ClipboardItem>()
        while (cursor.moveToNext()) {
            list.add(
                ClipboardItem(
                    id = cursor.getLong(0),
                    content = cursor.getString(1),
                    pinned = cursor.getInt(2) == 1,
                    categoryId = if (cursor.isNull(3)) null else cursor.getLong(3),
                    createdAt = cursor.getLong(4),
                    lastUsed = cursor.getLong(5)
                )
            )
        }
        cursor.close()
        return list
    }

    fun getPinnedItems(): List<ClipboardItem> {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery(
            "SELECT id, content, pinned, category_id, created_at, last_used FROM clipboard_items WHERE pinned = 1 ORDER BY last_used DESC",
            null
        )
        val list = mutableListOf<ClipboardItem>()
        while (cursor.moveToNext()) {
            list.add(
                ClipboardItem(
                    id = cursor.getLong(0),
                    content = cursor.getString(1),
                    pinned = true,
                    categoryId = if (cursor.isNull(3)) null else cursor.getLong(3),
                    createdAt = cursor.getLong(4),
                    lastUsed = cursor.getLong(5)
                )
            )
        }
        cursor.close()
        return list
    }

    fun getAllItems(categoryId: Long? = null): List<ClipboardItem> {
        val db = dbHelper.readableDatabase
        val query = if (categoryId != null && categoryId > 0) {
            "SELECT id, content, pinned, category_id, created_at, last_used FROM clipboard_items WHERE category_id = ? ORDER BY pinned DESC, last_used DESC"
        } else {
            "SELECT id, content, pinned, category_id, created_at, last_used FROM clipboard_items ORDER BY pinned DESC, last_used DESC"
        }
        val args = if (categoryId != null && categoryId > 0) arrayOf(categoryId.toString()) else null
        val cursor = db.rawQuery(query, args)
        val list = mutableListOf<ClipboardItem>()
        while (cursor.moveToNext()) {
            list.add(
                ClipboardItem(
                    id = cursor.getLong(0),
                    content = cursor.getString(1),
                    pinned = cursor.getInt(2) == 1,
                    categoryId = if (cursor.isNull(3)) null else cursor.getLong(3),
                    createdAt = cursor.getLong(4),
                    lastUsed = cursor.getLong(5)
                )
            )
        }
        cursor.close()
        return list
    }

    fun searchItems(query: String): List<ClipboardItem> {
        val clean = query.trim()
        if (clean.isEmpty()) return getAllItems()

        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery(
            "SELECT id, content, pinned, category_id, created_at, last_used FROM clipboard_items WHERE content LIKE ? ORDER BY pinned DESC, last_used DESC LIMIT 50",
            arrayOf("$clean%")
        )
        val list = mutableListOf<ClipboardItem>()
        while (cursor.moveToNext()) {
            list.add(
                ClipboardItem(
                    id = cursor.getLong(0),
                    content = cursor.getString(1),
                    pinned = cursor.getInt(2) == 1,
                    categoryId = if (cursor.isNull(3)) null else cursor.getLong(3),
                    createdAt = cursor.getLong(4),
                    lastUsed = cursor.getLong(5)
                )
            )
        }
        cursor.close()
        return list
    }

    fun pinItem(id: Long) {
        val db = dbHelper.writableDatabase
        val cv = ContentValues().apply { put("pinned", 1) }
        db.update("clipboard_items", cv, "id = ?", arrayOf(id.toString()))
    }

    fun unpinItem(id: Long) {
        val db = dbHelper.writableDatabase
        val cv = ContentValues().apply { put("pinned", 0) }
        db.update("clipboard_items", cv, "id = ?", arrayOf(id.toString()))
    }

    fun deleteItem(id: Long) {
        val db = dbHelper.writableDatabase
        db.delete("clipboard_items", "id = ?", arrayOf(id.toString()))
    }

    fun deleteAll() {
        // Pinned items (pinned = 1) strictly survive deleteAll
        val db = dbHelper.writableDatabase
        db.delete("clipboard_items", "pinned = 0", null)
    }

    fun assignCategory(itemId: Long, categoryId: Long) {
        val db = dbHelper.writableDatabase
        val cv = ContentValues().apply { put("category_id", categoryId) }
        db.update("clipboard_items", cv, "id = ?", arrayOf(itemId.toString()))
    }

    fun createCategory(label: String, color: String = "#4285F4"): Long {
        val db = dbHelper.writableDatabase
        val cv = ContentValues().apply {
            put("label", label.trim())
            put("color", color.trim())
        }
        return db.insertWithOnConflict("clipboard_categories", null, cv, android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE)
    }

    fun getCategories(): List<ClipboardCategory> {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery("SELECT id, label, color FROM clipboard_categories ORDER BY id ASC", null)
        val list = mutableListOf<ClipboardCategory>()
        while (cursor.moveToNext()) {
            list.add(
                ClipboardCategory(
                    id = cursor.getLong(0),
                    label = cursor.getString(1),
                    color = cursor.getString(2)
                )
            )
        }
        cursor.close()
        return list
    }

    fun applyRetentionPolicy() {
        val mode = prefs.getString("clipboard_retention_mode", "forever") ?: "forever"
        val db = dbHelper.writableDatabase
        val now = System.currentTimeMillis()

        when {
            mode.startsWith("days_") -> {
                val days = mode.removePrefix("days_").toIntOrNull() ?: return
                val cutoff = now - (days.toLong() * 24 * 3600 * 1000)
                db.delete("clipboard_items", "pinned = 0 AND created_at < ?", arrayOf(cutoff.toString()))
            }
            mode.startsWith("count_") -> {
                val count = mode.removePrefix("count_").toIntOrNull() ?: return
                db.execSQL(
                    "DELETE FROM clipboard_items WHERE pinned = 0 AND id NOT IN (" +
                    "SELECT id FROM clipboard_items WHERE pinned = 0 ORDER BY last_used DESC, id DESC LIMIT ?" +
                    ")",
                    arrayOf(count.toString())
                )
            }
            else -> {
                // "forever" -> Keep indefinitely
            }
        }
    }
}
