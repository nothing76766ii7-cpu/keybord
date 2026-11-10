package com.keybord.profile

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.keybord.theme.OneHandedConfig

/**
 * SQLite-backed storage repository for per-app profiles.
 */
class AppProfileRepository(
    context: Context?,
    inMemory: Boolean = (context == null)
) : SQLiteOpenHelper(context, if (inMemory) null else "keybord_profiles.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE app_profiles (
                package_name TEXT PRIMARY KEY,
                theme_id TEXT,
                key_height_override REAL,
                one_handed_enabled INTEGER,
                one_handed_right INTEGER,
                one_handed_width REAL,
                glide_override INTEGER
            );
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS app_profiles")
        onCreate(db)
    }

    fun getProfileForPackage(packageName: String): AppProfile? {
        val cursor = readableDatabase.rawQuery(
            "SELECT package_name, theme_id, key_height_override, one_handed_enabled, one_handed_right, one_handed_width, glide_override FROM app_profiles WHERE package_name = ?",
            arrayOf(packageName)
        )
        val profile = if (cursor.moveToFirst()) {
            val themeId = cursor.getString(1)
            val keyHeight = if (cursor.isNull(2)) null else cursor.getFloat(2)
            val oneHanded = if (!cursor.isNull(3)) {
                OneHandedConfig(
                    enabled = cursor.getInt(3) == 1,
                    gravityRight = cursor.getInt(4) == 1,
                    widthPercent = cursor.getFloat(5)
                )
            } else null
            val glide = if (cursor.isNull(6)) null else (cursor.getInt(6) == 1)

            AppProfile(
                packageName = cursor.getString(0),
                themeIdOverride = themeId,
                keyHeightDpOverride = keyHeight,
                oneHandedOverride = oneHanded,
                glideTypingOverride = glide
            )
        } else null
        cursor.close()
        return profile
    }

    fun saveProfile(profile: AppProfile) {
        val cv = ContentValues().apply {
            put("package_name", profile.packageName)
            put("theme_id", profile.themeIdOverride)
            put("key_height_override", profile.keyHeightDpOverride)
            if (profile.oneHandedOverride != null) {
                put("one_handed_enabled", if (profile.oneHandedOverride.enabled) 1 else 0)
                put("one_handed_right", if (profile.oneHandedOverride.gravityRight) 1 else 0)
                put("one_handed_width", profile.oneHandedOverride.widthPercent)
            } else {
                putNull("one_handed_enabled")
                putNull("one_handed_right")
                putNull("one_handed_width")
            }
            if (profile.glideTypingOverride != null) {
                put("glide_override", if (profile.glideTypingOverride) 1 else 0)
            } else {
                putNull("glide_override")
            }
        }
        writableDatabase.insertWithOnConflict("app_profiles", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun deleteProfile(packageName: String) {
        writableDatabase.delete("app_profiles", "package_name = ?", arrayOf(packageName))
    }

    fun getAllProfiles(): List<AppProfile> {
        val cursor = readableDatabase.rawQuery(
            "SELECT package_name, theme_id, key_height_override, one_handed_enabled, one_handed_right, one_handed_width, glide_override FROM app_profiles",
            null
        )
        val list = mutableListOf<AppProfile>()
        while (cursor.moveToNext()) {
            val themeId = cursor.getString(1)
            val keyHeight = if (cursor.isNull(2)) null else cursor.getFloat(2)
            val oneHanded = if (!cursor.isNull(3)) {
                OneHandedConfig(
                    enabled = cursor.getInt(3) == 1,
                    gravityRight = cursor.getInt(4) == 1,
                    widthPercent = cursor.getFloat(5)
                )
            } else null
            val glide = if (cursor.isNull(6)) null else (cursor.getInt(6) == 1)

            list.add(
                AppProfile(
                    packageName = cursor.getString(0),
                    themeIdOverride = themeId,
                    keyHeightDpOverride = keyHeight,
                    oneHandedOverride = oneHanded,
                    glideTypingOverride = glide
                )
            )
        }
        cursor.close()
        return list
    }
}
