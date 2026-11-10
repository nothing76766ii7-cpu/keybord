package com.keybord.theme

import android.content.Context

/**
 * Persistent global layout, keyboard height, button geometry, and tactile customisation preferences.
 * Applied cleanly across all built-in and custom themes without mutating theme preset definitions.
 */
class KeyboardLayoutPrefs(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var customGeometryEnabled: Boolean
        get() = prefs.getBoolean(KEY_CUSTOM_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_CUSTOM_ENABLED, value).apply()

    var keyHeightDp: Float
        get() = prefs.getFloat(KEY_HEIGHT_DP, 54f).coerceIn(36f, 82f)
        set(value) = prefs.edit().putFloat(KEY_HEIGHT_DP, value.coerceIn(36f, 82f)).putBoolean(KEY_CUSTOM_ENABLED, true).apply()

    var bottomPaddingDp: Float
        get() = prefs.getFloat(KEY_BOTTOM_PADDING_DP, 12f).coerceIn(0f, 44f)
        set(value) = prefs.edit().putFloat(KEY_BOTTOM_PADDING_DP, value.coerceIn(0f, 44f)).putBoolean(KEY_CUSTOM_ENABLED, true).apply()

    var cornerRadiusDp: Float
        get() = prefs.getFloat(KEY_CORNER_RADIUS_DP, 8f).coerceIn(0f, 26f)
        set(value) = prefs.edit().putFloat(KEY_CORNER_RADIUS_DP, value.coerceIn(0f, 26f)).putBoolean(KEY_CUSTOM_ENABLED, true).apply()

    var gapHorizontalDp: Float
        get() = prefs.getFloat(KEY_GAP_H_DP, 4f).coerceIn(0f, 10f)
        set(value) = prefs.edit().putFloat(KEY_GAP_H_DP, value.coerceIn(0f, 10f)).putBoolean(KEY_CUSTOM_ENABLED, true).apply()

    var gapVerticalDp: Float
        get() = prefs.getFloat(KEY_GAP_V_DP, 6f).coerceIn(0f, 10f)
        set(value) = prefs.edit().putFloat(KEY_GAP_V_DP, value.coerceIn(0f, 10f)).putBoolean(KEY_CUSTOM_ENABLED, true).apply()

    var borderWidthDp: Float
        get() = prefs.getFloat(KEY_BORDER_WIDTH_DP, 1f).coerceIn(0f, 4f)
        set(value) = prefs.edit().putFloat(KEY_BORDER_WIDTH_DP, value.coerceIn(0f, 4f)).putBoolean(KEY_CUSTOM_ENABLED, true).apply()

    var labelSizeSp: Float
        get() = prefs.getFloat(KEY_LABEL_SIZE_SP, 16f).coerceIn(11f, 22f)
        set(value) = prefs.edit().putFloat(KEY_LABEL_SIZE_SP, value.coerceIn(11f, 22f)).putBoolean(KEY_CUSTOM_ENABLED, true).apply()

    var showNumberRow: Boolean
        get() = prefs.getBoolean(KEY_SHOW_NUMBER_ROW, false)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_NUMBER_ROW, value).apply()

    var showSecondarySymbols: Boolean
        get() = prefs.getBoolean(KEY_SHOW_SECONDARY_SYMBOLS, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_SECONDARY_SYMBOLS, value).apply()

    var popPreviewEnabled: Boolean
        get() = prefs.getBoolean(KEY_POP_PREVIEW_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_POP_PREVIEW_ENABLED, value).apply()

    var popPreviewSizeOrdinal: Int
        get() = prefs.getInt(KEY_POP_PREVIEW_SIZE, PopPreviewSize.MEDIUM.ordinal)
            .coerceIn(0, PopPreviewSize.values().lastIndex)
        set(value) = prefs.edit().putInt(KEY_POP_PREVIEW_SIZE, value.coerceIn(0, PopPreviewSize.values().lastIndex)).apply()

    var vibrationDurationMs: Long
        get() = prefs.getLong(KEY_VIBRATION_MS, 14L).coerceIn(0L, 40L)
        set(value) = prefs.edit().putLong(KEY_VIBRATION_MS, value.coerceIn(0L, 40L)).apply()

    var oneHandedEnabled: Boolean
        get() = prefs.getBoolean(KEY_ONE_HANDED_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ONE_HANDED_ENABLED, value).apply()

    var oneHandedGravityRight: Boolean
        get() = prefs.getBoolean(KEY_ONE_HANDED_RIGHT, true)
        set(value) = prefs.edit().putBoolean(KEY_ONE_HANDED_RIGHT, value).apply()

    var oneHandedWidthPercent: Float
        get() = prefs.getFloat(KEY_ONE_HANDED_WIDTH_PCT, 84f).coerceIn(68f, 96f)
        set(value) = prefs.edit().putFloat(KEY_ONE_HANDED_WIDTH_PCT, value.coerceIn(68f, 96f)).apply()

    /**
     * Merges user layout/button customisations onto the supplied ThemeModel.
     */
    fun applyTo(baseTheme: ThemeModel): ThemeModel {
        val useCustom = customGeometryEnabled
        val oneHanded = OneHandedConfig(
            enabled = if (oneHandedEnabled) true else baseTheme.oneHandedMode.enabled,
            gravityRight = if (oneHandedEnabled) oneHandedGravityRight else baseTheme.oneHandedMode.gravityRight,
            widthPercent = if (oneHandedEnabled) oneHandedWidthPercent else baseTheme.oneHandedMode.widthPercent
        )
        return baseTheme.copy(
            keyHeightDp = if (useCustom) keyHeightDp else baseTheme.keyHeightDp,
            bottomPaddingDp = if (useCustom) bottomPaddingDp else baseTheme.bottomPaddingDp,
            keyCornerRadiusDp = if (useCustom) cornerRadiusDp else baseTheme.keyCornerRadiusDp,
            keyGapHorizontalDp = if (useCustom) gapHorizontalDp else baseTheme.keyGapHorizontalDp,
            keyGapVerticalDp = if (useCustom) gapVerticalDp else baseTheme.keyGapVerticalDp,
            keyBorderWidthDp = if (useCustom) borderWidthDp else baseTheme.keyBorderWidthDp,
            keyLabelSizeSp = if (useCustom) labelSizeSp else baseTheme.keyLabelSizeSp,
            keyPopPreviewEnabled = popPreviewEnabled,
            keyPopPreviewSize = PopPreviewSize.values()[popPreviewSizeOrdinal],
            oneHandedMode = oneHanded
        )
    }

    fun resetDefaults() {
        prefs.edit().clear().apply()
    }

    companion object {
        const val PREFS_NAME = "keybord_layout_prefs"
        private const val KEY_CUSTOM_ENABLED = "custom_geometry_enabled"
        private const val KEY_HEIGHT_DP = "key_height_dp"
        private const val KEY_BOTTOM_PADDING_DP = "bottom_padding_dp"
        private const val KEY_CORNER_RADIUS_DP = "corner_radius_dp"
        private const val KEY_GAP_H_DP = "gap_h_dp"
        private const val KEY_GAP_V_DP = "gap_v_dp"
        private const val KEY_BORDER_WIDTH_DP = "border_width_dp"
        private const val KEY_LABEL_SIZE_SP = "label_size_sp"
        private const val KEY_SHOW_NUMBER_ROW = "show_number_row"
        private const val KEY_SHOW_SECONDARY_SYMBOLS = "show_secondary_symbols"
        private const val KEY_POP_PREVIEW_ENABLED = "pop_preview_enabled"
        private const val KEY_POP_PREVIEW_SIZE = "pop_preview_size"
        private const val KEY_VIBRATION_MS = "vibration_ms"
        private const val KEY_ONE_HANDED_ENABLED = "one_handed_enabled"
        private const val KEY_ONE_HANDED_RIGHT = "one_handed_right"
        private const val KEY_ONE_HANDED_WIDTH_PCT = "one_handed_width_pct"
    }
}
