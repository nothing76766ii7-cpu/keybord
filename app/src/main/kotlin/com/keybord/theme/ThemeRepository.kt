package com.keybord.theme

import android.content.Context
import android.content.SharedPreferences
import java.io.File

/**
 * Manages active theme selection, persistence of custom themes, and file import/export.
 */
class ThemeRepository(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("keybord_themes", Context.MODE_PRIVATE)
    private val customThemesDir = File(context.filesDir, "custom_themes").apply { mkdirs() }

    private val listeners = mutableListOf<(ThemeModel) -> Unit>()

    fun addOnThemeChangeListener(listener: (ThemeModel) -> Unit) {
        listeners.add(listener)
    }

    fun removeOnThemeChangeListener(listener: (ThemeModel) -> Unit) {
        listeners.remove(listener)
    }

    /**
     * Retrieves the current active ThemeModel.
     */
    fun getActiveTheme(): ThemeModel {
        val activeId = prefs.getString("active_theme_id", BuiltInThemes.MATERIAL_MIDNIGHT.id) ?: BuiltInThemes.MATERIAL_MIDNIGHT.id
        return getThemeById(activeId) ?: BuiltInThemes.MATERIAL_MIDNIGHT
    }

    /**
     * Sets the active theme and notifies listeners within the <200ms performance budget.
     */
    fun setActiveTheme(themeId: String) {
        prefs.edit().putString("active_theme_id", themeId).apply()
        val theme = getActiveTheme()
        for (l in listeners) {
            l.invoke(theme)
        }
    }

    fun getThemeById(themeId: String): ThemeModel? {
        val builtIn = BuiltInThemes.ALL_BUILT_IN.find { it.id == themeId }
        if (builtIn != null) return builtIn

        // Check custom themes directory
        val file = File(customThemesDir, "$themeId.keybordtheme")
        if (file.exists()) {
            return try {
                ThemeSerializer.fromJson(file.readText())
            } catch (e: Exception) {
                null
            }
        }
        return null
    }

    fun getAllThemes(): List<ThemeModel> {
        val all = mutableListOf<ThemeModel>()
        all.addAll(BuiltInThemes.ALL_BUILT_IN)

        val customFiles = customThemesDir.listFiles { _, name -> name.endsWith(".keybordtheme") }
        if (customFiles != null) {
            for (f in customFiles) {
                try {
                    val theme = ThemeSerializer.fromJson(f.readText())
                    all.add(theme)
                } catch (e: Exception) {
                    // Skip corrupt file
                }
            }
        }
        return all
    }

    fun saveCustomTheme(theme: ThemeModel) {
        val json = ThemeSerializer.toJson(theme)
        val file = File(customThemesDir, "${theme.id}.keybordtheme")
        file.writeText(json)
    }

    fun deleteCustomTheme(themeId: String): Boolean {
        val file = File(customThemesDir, "$themeId.keybordtheme")
        val deleted = if (file.exists()) file.delete() else false
        if (prefs.getString("active_theme_id", null) == themeId) {
            setActiveTheme(BuiltInThemes.MATERIAL_MIDNIGHT.id)
        }
        return deleted
    }

    fun importThemeFromFile(file: File): ThemeModel {
        val json = file.readText()
        return importThemeFromJson(json)
    }

    fun importThemeFromJson(json: String): ThemeModel {
        val theme = ThemeSerializer.fromJson(json)
        saveCustomTheme(theme)
        return theme
    }

    fun exportThemeToFile(theme: ThemeModel, targetFile: File) {
        val json = ThemeSerializer.toJson(theme)
        targetFile.writeText(json)
    }
}
