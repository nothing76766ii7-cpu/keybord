package com.keyboard.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.keyboard.ThemeEngine

/**
 * Data model for per-application keyboard profile overrides (PRD Section F).
 */
data class AppProfile(
    val packageName: String,
    val themeName: String? = null,
    val keyHeightDp: Int? = null,
    val glideEnabled: Boolean? = null,
    val magicEnabled: Boolean? = null,
    val lastUpdated: Long = System.currentTimeMillis()
) {
    /**
     * Converts this app profile into a [ThemeEngine.KeybordTheme] override.
     * Inherits from matching built-in theme or default theme, applying key_height_dp
     * if specified.
     */
    fun toTheme(themeEngine: ThemeEngine? = null): ThemeEngine.KeybordTheme {
        val base = (if (themeName != null && themeEngine != null) {
            themeEngine.getThemeByName(themeName)
        } else null) ?: (themeName?.let { ThemeEngine.getBuiltInThemeByName(it) })
        ?: ThemeEngine.getDefaultTheme()

        val params = base.params.toMutableMap()
        if (keyHeightDp != null) {
            params["key_height_dp"] = keyHeightDp.toString()
        }
        return base.copy(
            name = themeName ?: base.name,
            params = params
        )
    }
}

/**
 * SQLite Database storing per-app keyboard profile configurations (PRD Section F).
 */
class AppProfileDatabase(context: Context) : SQLiteOpenHelper(
    context,
    "keybord_app_profiles.db",
    null,
    1
) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE app_profiles (
                package_name  TEXT PRIMARY KEY,
                theme_name    TEXT,
                key_height_dp INTEGER,
                glide_enabled INTEGER,
                magic_enabled INTEGER,
                last_updated  INTEGER NOT NULL
            );
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS app_profiles")
        onCreate(db)
    }
}
