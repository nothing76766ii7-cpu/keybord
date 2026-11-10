package com.keyboard.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class ClipboardItem(
    val id: Long,
    val content: String,
    val pinned: Boolean,
    val categoryId: Long?,
    val createdAt: Long,
    val lastUsed: Long
)

data class ClipboardCategory(
    val id: Long,
    val label: String,
    val color: String = "#4285F4"
)

/**
 * SQLite Database for Keybord Unlimited Clipboard Manager per PRD Section A.1.
 */
class ClipboardDatabase(context: Context) : SQLiteOpenHelper(
    context,
    "keybord_clipboard.db",
    null,
    1
) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE clipboard_items (
                id          INTEGER PRIMARY KEY AUTOINCREMENT,
                content     TEXT    NOT NULL,
                pinned      INTEGER NOT NULL DEFAULT 0,
                category_id INTEGER,
                created_at  INTEGER NOT NULL,
                last_used   INTEGER NOT NULL
            );
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE clipboard_categories (
                id    INTEGER PRIMARY KEY AUTOINCREMENT,
                label TEXT NOT NULL UNIQUE,
                color TEXT NOT NULL DEFAULT '#4285F4'
            );
            """.trimIndent()
        )

        db.execSQL("CREATE INDEX idx_clipboard_pinned ON clipboard_items (pinned DESC, last_used DESC);")
        db.execSQL("CREATE INDEX idx_clipboard_category ON clipboard_items (category_id);")

        // Seed default categories
        val defaultCategories = listOf(
            "General" to "#4285F4",
            "Code" to "#34A853",
            "Links" to "#FBBC05",
            "Notes" to "#EA4335"
        )
        for ((label, color) in defaultCategories) {
            val cv = ContentValues().apply {
                put("label", label)
                put("color", color)
            }
            db.insert("clipboard_categories", null, cv)
        }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Migration logic for future schema revisions
    }
}
