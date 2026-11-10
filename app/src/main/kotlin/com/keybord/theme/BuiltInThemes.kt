package com.keybord.theme

import android.graphics.Color
import android.graphics.drawable.GradientDrawable

/**
 * 10 Built-in launch themes shipped with Keybord v1.0.
 */
object BuiltInThemes {

    val MATERIAL_MIDNIGHT = ThemeModel(
        id = "builtin_midnight",
        name = "Material Midnight",
        isBuiltIn = true,
        keyHeightDp = 54f,
        keyCornerRadiusDp = 8f,
        keyGapHorizontalDp = 4f,
        keyGapVerticalDp = 6f,
        bottomPaddingDp = 12f,
        keyFaceColor = ColorSpec.solid(Color.parseColor("#262938")),
        keyBorderColor = Color.parseColor("#22FFFFFF"),
        keyBorderWidthDp = 1f,
        keyPressHighlightColor = Color.parseColor("#444285F4"),
        keyboardBackground = BackgroundSpec.solid(Color.parseColor("#13151F")),
        suggestionStripBgColor = Color.parseColor("#1B1D2B"),
        suggestionStripTextColor = Color.parseColor("#E0E2EC"),
        keyLabelColorPrimary = Color.WHITE,
        keyLabelColorSecondary = Color.parseColor("#8E92A2"),
        keyLabelFont = FontChoice.ROBOTO,
        keyLabelSizeSp = 16f,
        trailColor = Color.parseColor("#4285F4"),
        trailWidthDp = 12f,
        trailFadeDurationMs = 350L,
        keyPressRippleEnabled = true,
        keyPopPreviewEnabled = true,
        keyPopPreviewSize = PopPreviewSize.MEDIUM
    )

    val CLEAN_PORCELAIN = ThemeModel(
        id = "builtin_porcelain",
        name = "Clean Porcelain",
        isBuiltIn = true,
        keyHeightDp = 54f,
        keyCornerRadiusDp = 8f,
        keyGapHorizontalDp = 4f,
        keyGapVerticalDp = 6f,
        bottomPaddingDp = 12f,
        keyFaceColor = ColorSpec.solid(Color.parseColor("#FFFFFF")),
        keyBorderColor = Color.parseColor("#15000000"),
        keyBorderWidthDp = 1f,
        keyPressHighlightColor = Color.parseColor("#22000000"),
        keyboardBackground = BackgroundSpec.solid(Color.parseColor("#ECEFF1")),
        suggestionStripBgColor = Color.parseColor("#F5F7F8"),
        suggestionStripTextColor = Color.parseColor("#263238"),
        keyLabelColorPrimary = Color.parseColor("#212121"),
        keyLabelColorSecondary = Color.parseColor("#78909C"),
        keyLabelFont = FontChoice.INTER,
        keyLabelSizeSp = 16f,
        trailColor = Color.parseColor("#1E88E5"),
        trailWidthDp = 10f,
        trailFadeDurationMs = 300L,
        keyPressRippleEnabled = true,
        keyPopPreviewEnabled = true,
        keyPopPreviewSize = PopPreviewSize.MEDIUM
    )

    val CYBERPUNK_NEON = ThemeModel(
        id = "builtin_cyberpunk",
        name = "Cyberpunk Neon",
        isBuiltIn = true,
        keyHeightDp = 56f,
        keyCornerRadiusDp = 4f,
        keyGapHorizontalDp = 3f,
        keyGapVerticalDp = 5f,
        bottomPaddingDp = 12f,
        keyFaceColor = ColorSpec.gradient(
            Color.parseColor("#1A0933"),
            Color.parseColor("#0C001C"),
            GradientDrawable.Orientation.TOP_BOTTOM
        ),
        keyBorderColor = Color.parseColor("#FF007F"), // Neon Pink Border
        keyBorderWidthDp = 1.5f,
        keyPressHighlightColor = Color.parseColor("#6600FFFF"),
        keyboardBackground = BackgroundSpec.solid(Color.parseColor("#05000A")),
        suggestionStripBgColor = Color.parseColor("#120024"),
        suggestionStripTextColor = Color.parseColor("#00FFFF"), // Cyan text
        keyLabelColorPrimary = Color.parseColor("#00FFFF"),
        keyLabelColorSecondary = Color.parseColor("#FF007F"),
        keyLabelFont = FontChoice.JETBRAINS_MONO,
        keyLabelSizeSp = 15f,
        trailColor = Color.parseColor("#00FFFF"), // Glowing electric cyan
        trailWidthDp = 14f,
        trailFadeDurationMs = 450L,
        keyPressRippleEnabled = true,
        keyPopPreviewEnabled = true,
        keyPopPreviewSize = PopPreviewSize.LARGE
    )

    val NORD_FROST = ThemeModel(
        id = "builtin_nord",
        name = "Nord Frost",
        isBuiltIn = true,
        keyHeightDp = 54f,
        keyCornerRadiusDp = 10f,
        keyGapHorizontalDp = 4f,
        keyGapVerticalDp = 6f,
        bottomPaddingDp = 12f,
        keyFaceColor = ColorSpec.solid(Color.parseColor("#3B4252")),
        keyBorderColor = Color.parseColor("#4C566A"),
        keyBorderWidthDp = 1f,
        keyPressHighlightColor = Color.parseColor("#4488C0D0"),
        keyboardBackground = BackgroundSpec.solid(Color.parseColor("#2E3440")),
        suggestionStripBgColor = Color.parseColor("#3B4252"),
        suggestionStripTextColor = Color.parseColor("#ECEFF4"),
        keyLabelColorPrimary = Color.parseColor("#ECEFF4"),
        keyLabelColorSecondary = Color.parseColor("#88C0D0"),
        keyLabelFont = FontChoice.SANS_SERIF_MEDIUM,
        keyLabelSizeSp = 16f,
        trailColor = Color.parseColor("#88C0D0"), // Glacier Blue
        trailWidthDp = 11f,
        trailFadeDurationMs = 320L,
        keyPressRippleEnabled = true,
        keyPopPreviewEnabled = true,
        keyPopPreviewSize = PopPreviewSize.MEDIUM
    )

    val DRACULA_NIGHT = ThemeModel(
        id = "builtin_dracula",
        name = "Dracula Night",
        isBuiltIn = true,
        keyHeightDp = 54f,
        keyCornerRadiusDp = 8f,
        keyGapHorizontalDp = 4f,
        keyGapVerticalDp = 6f,
        bottomPaddingDp = 12f,
        keyFaceColor = ColorSpec.solid(Color.parseColor("#44475A")),
        keyBorderColor = Color.parseColor("#6272A4"),
        keyBorderWidthDp = 1f,
        keyPressHighlightColor = Color.parseColor("#55BD93F9"),
        keyboardBackground = BackgroundSpec.solid(Color.parseColor("#282A36")),
        suggestionStripBgColor = Color.parseColor("#383A59"),
        suggestionStripTextColor = Color.parseColor("#F8F8F2"),
        keyLabelColorPrimary = Color.parseColor("#F8F8F2"),
        keyLabelColorSecondary = Color.parseColor("#FF79C6"),
        keyLabelFont = FontChoice.ROBOTO,
        keyLabelSizeSp = 16f,
        trailColor = Color.parseColor("#BD93F9"), // Dracula Purple
        trailWidthDp = 12f,
        trailFadeDurationMs = 350L,
        keyPressRippleEnabled = true,
        keyPopPreviewEnabled = true,
        keyPopPreviewSize = PopPreviewSize.MEDIUM
    )

    val RETRO_TERMINAL = ThemeModel(
        id = "builtin_terminal",
        name = "Retro Terminal",
        isBuiltIn = true,
        keyHeightDp = 55f,
        keyCornerRadiusDp = 2f,
        keyGapHorizontalDp = 4f,
        keyGapVerticalDp = 5f,
        bottomPaddingDp = 10f,
        keyFaceColor = ColorSpec.solid(Color.parseColor("#0A140A")),
        keyBorderColor = Color.parseColor("#00FF66"), // Phosphor Green
        keyBorderWidthDp = 1.2f,
        keyPressHighlightColor = Color.parseColor("#4400FF66"),
        keyboardBackground = BackgroundSpec.solid(Color.parseColor("#000800")),
        suggestionStripBgColor = Color.parseColor("#051005"),
        suggestionStripTextColor = Color.parseColor("#00FF66"),
        keyLabelColorPrimary = Color.parseColor("#00FF66"),
        keyLabelColorSecondary = Color.parseColor("#009933"),
        keyLabelFont = FontChoice.MONOSPACE,
        keyLabelSizeSp = 16f,
        trailColor = Color.parseColor("#00FF66"),
        trailWidthDp = 8f,
        trailFadeDurationMs = 280L,
        keyPressRippleEnabled = true,
        keyPopPreviewEnabled = true,
        keyPopPreviewSize = PopPreviewSize.SMALL
    )

    val SUNSET_GRADIENT = ThemeModel(
        id = "builtin_sunset",
        name = "Sunset Gradient",
        isBuiltIn = true,
        keyHeightDp = 54f,
        keyCornerRadiusDp = 12f,
        keyGapHorizontalDp = 4f,
        keyGapVerticalDp = 6f,
        bottomPaddingDp = 12f,
        keyFaceColor = ColorSpec.gradient(
            Color.parseColor("#44332A55"),
            Color.parseColor("#44FF6B6B"),
            GradientDrawable.Orientation.TL_BR
        ),
        keyBorderColor = Color.parseColor("#33FFFFFF"),
        keyBorderWidthDp = 1f,
        keyPressHighlightColor = Color.parseColor("#66FFA07A"),
        keyboardBackground = BackgroundSpec.gradient(
            Color.parseColor("#2C1654"), // Deep Dusk Purple
            Color.parseColor("#FD5E53")  // Sunset Coral
        ),
        suggestionStripBgColor = Color.parseColor("#3B1D5A"),
        suggestionStripTextColor = Color.WHITE,
        keyLabelColorPrimary = Color.WHITE,
        keyLabelColorSecondary = Color.parseColor("#FFD166"),
        keyLabelFont = FontChoice.SANS_SERIF_MEDIUM,
        keyLabelSizeSp = 16f,
        trailColor = Color.parseColor("#FFA07A"),
        trailWidthDp = 13f,
        trailFadeDurationMs = 400L,
        keyPressRippleEnabled = true,
        keyPopPreviewEnabled = true,
        keyPopPreviewSize = PopPreviewSize.MEDIUM
    )

    val FOREST_MOSS = ThemeModel(
        id = "builtin_forest",
        name = "Forest Moss",
        isBuiltIn = true,
        keyHeightDp = 54f,
        keyCornerRadiusDp = 9f,
        keyGapHorizontalDp = 4f,
        keyGapVerticalDp = 6f,
        bottomPaddingDp = 12f,
        keyFaceColor = ColorSpec.solid(Color.parseColor("#263B2E")),
        keyBorderColor = Color.parseColor("#3D5A46"),
        keyBorderWidthDp = 1f,
        keyPressHighlightColor = Color.parseColor("#448FBC8F"),
        keyboardBackground = BackgroundSpec.solid(Color.parseColor("#142119")),
        suggestionStripBgColor = Color.parseColor("#1C2E23"),
        suggestionStripTextColor = Color.parseColor("#D4E4D8"),
        keyLabelColorPrimary = Color.parseColor("#EAF4EC"),
        keyLabelColorSecondary = Color.parseColor("#8FBC8F"),
        keyLabelFont = FontChoice.ROBOTO,
        keyLabelSizeSp = 16f,
        trailColor = Color.parseColor("#66BB6A"),
        trailWidthDp = 12f,
        trailFadeDurationMs = 350L,
        keyPressRippleEnabled = true,
        keyPopPreviewEnabled = true,
        keyPopPreviewSize = PopPreviewSize.MEDIUM
    )

    val AMOLED_ONYX = ThemeModel(
        id = "builtin_amoled",
        name = "AMOLED Onyx",
        isBuiltIn = true,
        keyHeightDp = 54f,
        keyCornerRadiusDp = 6f,
        keyGapHorizontalDp = 4f,
        keyGapVerticalDp = 6f,
        bottomPaddingDp = 12f,
        keyFaceColor = ColorSpec.solid(Color.parseColor("#101010")),
        keyBorderColor = Color.parseColor("#282828"),
        keyBorderWidthDp = 1f,
        keyPressHighlightColor = Color.parseColor("#44FFFFFF"),
        keyboardBackground = BackgroundSpec.solid(Color.parseColor("#000000")),
        suggestionStripBgColor = Color.parseColor("#080808"),
        suggestionStripTextColor = Color.WHITE,
        keyLabelColorPrimary = Color.WHITE,
        keyLabelColorSecondary = Color.parseColor("#777777"),
        keyLabelFont = FontChoice.ROBOTO,
        keyLabelSizeSp = 16f,
        trailColor = Color.parseColor("#E0E0E0"),
        trailWidthDp = 10f,
        trailFadeDurationMs = 300L,
        keyPressRippleEnabled = true,
        keyPopPreviewEnabled = true,
        keyPopPreviewSize = PopPreviewSize.MEDIUM
    )

    val LAVENDER_DREAM = ThemeModel(
        id = "builtin_lavender",
        name = "Lavender Dream",
        isBuiltIn = true,
        keyHeightDp = 54f,
        keyCornerRadiusDp = 12f,
        keyGapHorizontalDp = 4f,
        keyGapVerticalDp = 6f,
        bottomPaddingDp = 12f,
        keyFaceColor = ColorSpec.solid(Color.parseColor("#E8E0F2")),
        keyBorderColor = Color.parseColor("#D0C4E4"),
        keyBorderWidthDp = 1f,
        keyPressHighlightColor = Color.parseColor("#449C27B0"),
        keyboardBackground = BackgroundSpec.solid(Color.parseColor("#F4EEFB")),
        suggestionStripBgColor = Color.parseColor("#EEE5F8"),
        suggestionStripTextColor = Color.parseColor("#4A235A"),
        keyLabelColorPrimary = Color.parseColor("#3D1C4E"),
        keyLabelColorSecondary = Color.parseColor("#8E6E9E"),
        keyLabelFont = FontChoice.INTER,
        keyLabelSizeSp = 16f,
        trailColor = Color.parseColor("#AB47BC"),
        trailWidthDp = 12f,
        trailFadeDurationMs = 360L,
        keyPressRippleEnabled = true,
        keyPopPreviewEnabled = true,
        keyPopPreviewSize = PopPreviewSize.MEDIUM
    )

    val ALL_BUILT_IN = listOf(
        MATERIAL_MIDNIGHT,
        CLEAN_PORCELAIN,
        CYBERPUNK_NEON,
        NORD_FROST,
        DRACULA_NIGHT,
        RETRO_TERMINAL,
        SUNSET_GRADIENT,
        FOREST_MOSS,
        AMOLED_ONYX,
        LAVENDER_DREAM
    )

    fun getById(id: String): ThemeModel {
        return ALL_BUILT_IN.find { it.id == id } ?: MATERIAL_MIDNIGHT
    }
}
