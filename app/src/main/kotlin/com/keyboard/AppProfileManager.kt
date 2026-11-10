package com.keyboard

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.keyboard.data.AppProfile
import com.keyboard.data.AppProfileDatabase

/**
 * Manages per-application keyboard profile overrides (PRD Section F).
 * Provides persistence and querying with seamless fallback to global defaults.
 */
class AppProfileManager(context: Context) {

    private val dbHelper = AppProfileDatabase(context.applicationContext)

    /**
     * Returns the [AppProfile] override for the specified package, or null
     * if no override exists (in which case the IME uses global defaults).
     */
    fun getProfileForPackage(packageName: String): AppProfile? {
        if (packageName.isBlank()) return null
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            "app_profiles",
            arrayOf("package_name", "theme_name", "key_height_dp", "glide_enabled", "magic_enabled", "last_updated"),
            "package_name = ?",
            arrayOf(packageName),
            null,
            null,
            null
        )

        cursor.use { c ->
            if (c.moveToFirst()) {
                val themeName = if (c.isNull(c.getColumnIndexOrThrow("theme_name"))) null else c.getString(c.getColumnIndexOrThrow("theme_name"))
                val keyHeightDp = if (c.isNull(c.getColumnIndexOrThrow("key_height_dp"))) null else c.getInt(c.getColumnIndexOrThrow("key_height_dp"))
                val glideEnabled = if (c.isNull(c.getColumnIndexOrThrow("glide_enabled"))) null else c.getInt(c.getColumnIndexOrThrow("glide_enabled")) == 1
                val magicEnabled = if (c.isNull(c.getColumnIndexOrThrow("magic_enabled"))) null else c.getInt(c.getColumnIndexOrThrow("magic_enabled")) == 1
                val lastUpdated = c.getLong(c.getColumnIndexOrThrow("last_updated"))

                return AppProfile(
                    packageName = packageName,
                    themeName = themeName,
                    keyHeightDp = keyHeightDp,
                    glideEnabled = glideEnabled,
                    magicEnabled = magicEnabled,
                    lastUpdated = lastUpdated
                )
            }
        }
        return null
    }

    /**
     * Persists or replaces a profile override for the given package.
     */
    fun setProfileForPackage(packageName: String, profile: AppProfile) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("package_name", packageName)
            put("theme_name", profile.themeName)
            put("key_height_dp", profile.keyHeightDp)
            put("glide_enabled", profile.glideEnabled?.let { if (it) 1 else 0 })
            put("magic_enabled", profile.magicEnabled?.let { if (it) 1 else 0 })
            put("last_updated", System.currentTimeMillis())
        }
        db.insertWithOnConflict("app_profiles", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    /**
     * Removes the profile override for the given package, reverting it to global defaults.
     */
    fun clearProfile(packageName: String) {
        val db = dbHelper.writableDatabase
        db.delete("app_profiles", "package_name = ?", arrayOf(packageName))
    }

    /**
     * Returns all stored per-app profiles.
     */
    fun getAllProfiles(): List<AppProfile> {
        val db = dbHelper.readableDatabase
        val list = mutableListOf<AppProfile>()
        val cursor = db.query(
            "app_profiles",
            arrayOf("package_name", "theme_name", "key_height_dp", "glide_enabled", "magic_enabled", "last_updated"),
            null,
            null,
            null,
            null,
            "last_updated DESC"
        )
        cursor.use { c ->
            val pkgIdx = c.getColumnIndexOrThrow("package_name")
            val themeIdx = c.getColumnIndexOrThrow("theme_name")
            val heightIdx = c.getColumnIndexOrThrow("key_height_dp")
            val glideIdx = c.getColumnIndexOrThrow("glide_enabled")
            val magicIdx = c.getColumnIndexOrThrow("magic_enabled")
            val updatedIdx = c.getColumnIndexOrThrow("last_updated")

            while (c.moveToNext()) {
                val pkg = c.getString(pkgIdx)
                val themeName = if (c.isNull(themeIdx)) null else c.getString(themeIdx)
                val keyHeightDp = if (c.isNull(heightIdx)) null else c.getInt(heightIdx)
                val glideEnabled = if (c.isNull(glideIdx)) null else c.getInt(glideIdx) == 1
                val magicEnabled = if (c.isNull(magicIdx)) null else c.getInt(magicIdx) == 1
                val lastUpdated = c.getLong(updatedIdx)

                list.add(
                    AppProfile(
                        packageName = pkg,
                        themeName = themeName,
                        keyHeightDp = keyHeightDp,
                        glideEnabled = glideEnabled,
                        magicEnabled = magicEnabled,
                        lastUpdated = lastUpdated
                    )
                )
            }
        }
        return list
    }

    /**
     * Clears all per-app profile overrides.
     */
    fun clearAllProfiles() {
        val db = dbHelper.writableDatabase
        db.delete("app_profiles", null, null)
    }
}
