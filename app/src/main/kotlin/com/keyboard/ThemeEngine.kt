package com.keyboard

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import org.json.JSONObject

/**
 * Visual Customisation Engine matching Section B.1 and B.2 specifications.
 */
class ThemeEngine(private val context: Context) {

    data class KeybordTheme(
        val name: String,
        val schemaVersion: Int = 1,
        val params: Map<String, String>
    ) {
        fun getParam(key: String, default: String): String = params[key] ?: default
        fun getIntParam(key: String, default: Int): Int = params[key]?.toIntOrNull() ?: default
        fun getFloatParam(key: String, default: Float): Float = params[key]?.toFloatOrNull() ?: default
        fun getBooleanParam(key: String, default: Boolean): Boolean = params[key]?.toBooleanStrictOrNull() ?: default
    }

    private val prefs: SharedPreferences = context.getSharedPreferences("keybord_themes", Context.MODE_PRIVATE)

    var currentTheme: KeybordTheme = builtInThemes.first()
        private set

    init {
        val activeName = prefs.getString("active_theme_name", "Classic Dark") ?: "Classic Dark"
        currentTheme = getThemeByName(activeName) ?: builtInThemes.first()
    }

    fun applyTheme(theme: KeybordTheme, persistAsGlobal: Boolean = true) {
        currentTheme = theme
        val edit = prefs.edit().putString("active_theme_name", theme.name)
        if (persistAsGlobal) {
            edit.putString("global_theme_name", theme.name)
        }
        // Save active parameters to SharedPreferences for quick retrieval by views
        for ((k, v) in theme.params) {
            edit.putString("active_param_$k", v)
        }
        edit.apply()
    }

    fun restoreGlobalTheme() {
        val globalName = prefs.getString("global_theme_name", "Classic Dark") ?: "Classic Dark"
        val theme = getThemeByName(globalName) ?: builtInThemes.first()
        applyTheme(theme, persistAsGlobal = false)
    }

    fun saveTheme(name: String): KeybordTheme {
        val currentParams = currentTheme.params.toMutableMap()
        val theme = KeybordTheme(name = name, schemaVersion = 1, params = currentParams)
        val json = exportTheme(theme)
        prefs.edit().putString("themes/$name", json).apply()
        return theme
    }

    fun exportTheme(theme: KeybordTheme): String {
        val json = JSONObject()
        json.put("name", theme.name)
        json.put("schemaVersion", theme.schemaVersion)
        val paramsObj = JSONObject()
        for ((k, v) in theme.params) {
            paramsObj.put(k, v)
        }
        json.put("params", paramsObj)
        return json.toString(2)
    }

    fun importTheme(json: String): Result<KeybordTheme> {
        return try {
            val root = JSONObject(json)
            val name = root.optString("name", "Imported Theme")
            val schemaVersion = root.optInt("schemaVersion", 1)
            val paramsObj = root.optJSONObject("params") ?: JSONObject()

            val map = mutableMapOf<String, String>()
            val keys = paramsObj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                map[k] = paramsObj.getString(k)
            }

            val theme = KeybordTheme(name, schemaVersion, map)
            saveCustomTheme(theme)
            Result.success(theme)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun saveCustomTheme(theme: KeybordTheme) {
        val json = exportTheme(theme)
        prefs.edit().putString("themes/${theme.name}", json).apply()
    }

    fun getAllThemes(): List<KeybordTheme> {
        val list = mutableListOf<KeybordTheme>()
        list.addAll(builtInThemes)

        // Read user saved themes
        for ((key, value) in prefs.all) {
            if (key.startsWith("themes/") && value is String) {
                importTheme(value).onSuccess { list.add(it) }
            }
        }
        return list
    }

    fun getThemeByName(name: String): KeybordTheme? {
        return getAllThemes().find { it.name.equals(name, ignoreCase = true) }
    }

    val builtInThemes: List<KeybordTheme> get() = Companion.builtInThemes

    companion object {
        fun getDefaultTheme(): KeybordTheme = builtInThemes.first()

        fun getBuiltInThemeByName(name: String): KeybordTheme? =
            builtInThemes.find { it.name.equals(name, ignoreCase = true) }

        // 10 Built-in launch themes strictly required by Section B.2
        val builtInThemes: List<KeybordTheme> = listOf(
        // 1. Classic Dark
        createTheme(
            name = "Classic Dark",
            faceColor = "#313244",
            borderColor = "#45475A",
            pressColor = "#585B70",
            bg = "#181825",
            stripBg = "#1E1E2E",
            labelColor = "#CDD6F4",
            trailColor = "#4285F4"
        ),
        // 2. Classic Light
        createTheme(
            name = "Classic Light",
            faceColor = "#FFFFFF",
            borderColor = "#E0E0E0",
            pressColor = "#D6D6D6",
            bg = "#F5F5F7",
            stripBg = "#EEEEEE",
            labelColor = "#212121",
            trailColor = "#1976D2"
        ),
        // 3. Amoled Black
        createTheme(
            name = "Amoled Black",
            faceColor = "#121212",
            borderColor = "#242424",
            pressColor = "#333333",
            bg = "#000000",
            stripBg = "#0A0A0A",
            labelColor = "#FFFFFF",
            trailColor = "#00E5FF"
        ),
        // 4. Ocean Blue
        createTheme(
            name = "Ocean Blue",
            faceColor = "#1E3A8A",
            borderColor = "#2563EB",
            pressColor = "#1D4ED8",
            bg = "#0F172A",
            stripBg = "#1E293B",
            labelColor = "#E0F2FE",
            trailColor = "#38BDF8"
        ),
        // 5. Forest Green
        createTheme(
            name = "Forest Green",
            faceColor = "#1B4332",
            borderColor = "#2D6A4F",
            pressColor = "#40916C",
            bg = "#081C15",
            stripBg = "#1B4332",
            labelColor = "#D8F3DC",
            trailColor = "#52B788"
        ),
        // 6. Sunset Coral
        createTheme(
            name = "Sunset Coral",
            faceColor = "#E07A5F",
            borderColor = "#F2CC8F",
            pressColor = "#F4A261",
            bg = "#3D405B",
            stripBg = "#2E3147",
            labelColor = "#F4F1DE",
            trailColor = "#E76F51"
        ),
        // 7. Monochrome
        createTheme(
            name = "Monochrome",
            faceColor = "#2B2B2B",
            borderColor = "#3D3D3D",
            pressColor = "#4F4F4F",
            bg = "#1A1A1A",
            stripBg = "#222222",
            labelColor = "#EDEDED",
            trailColor = "#BDBDBD"
        ),
        // 8. Rose Gold
        createTheme(
            name = "Rose Gold",
            faceColor = "#FCEADE",
            borderColor = "#F4ACB7",
            pressColor = "#FFCAD4",
            bg = "#FFE5D9",
            stripBg = "#D8E2DC",
            labelColor = "#6D597A",
            trailColor = "#B56576"
        ),
        // 9. Deep Purple
        createTheme(
            name = "Deep Purple",
            faceColor = "#3C096C",
            borderColor = "#5A189A",
            pressColor = "#7B2CBF",
            bg = "#10002B",
            stripBg = "#240046",
            labelColor = "#E0AAFF",
            trailColor = "#C77DFF"
        ),
        // 10. Nord
        createTheme(
            name = "Nord",
            faceColor = "#3B4252",
            borderColor = "#4C566A",
            pressColor = "#434C5E",
            bg = "#2E3440",
            stripBg = "#3B4252",
            labelColor = "#ECEFF4",
            trailColor = "#88C0D0"
        )
    )

    private fun createTheme(
        name: String,
        faceColor: String,
        borderColor: String,
        pressColor: String,
        bg: String,
        stripBg: String,
        labelColor: String,
        trailColor: String
    ): KeybordTheme {
        val params = mapOf(
            "key_height_dp" to "56",
            "key_corner_radius_dp" to "8",
            "key_gap_horizontal_dp" to "3",
            "key_gap_vertical_dp" to "3",
            "bottom_padding_dp" to "8",
            "one_handed_mode" to "false",
            "one_handed_offset_dp" to "0",
            "one_handed_width_pct" to "75",
            "key_face_color" to faceColor,
            "key_border_color" to borderColor,
            "key_border_width_dp" to "1",
            "key_press_color" to pressColor,
            "keyboard_background" to bg,
            "strip_background_color" to stripBg,
            "strip_text_color" to labelColor,
            "key_label_color" to labelColor,
            "key_label_secondary_color" to "#9399B2",
            "key_label_font" to "Roboto",
            "key_label_size_sp" to "14",
            "trail_color" to trailColor,
            "trail_width_dp" to "14",
            "trail_fade_ms" to "350",
            "key_press_ripple" to "true",
            "key_pop_preview" to "true",
            "key_pop_scale" to "1.4"
        )
        return KeybordTheme(name = name, schemaVersion = 1, params = params)
    }
    }
}
