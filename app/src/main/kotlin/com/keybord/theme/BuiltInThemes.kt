package com.keybord.theme

import android.graphics.Color
import android.graphics.drawable.GradientDrawable

/**
 * Built-in themes shipped with Keybord: 10 core launch themes plus the complete
 * extracted collection of themes and animated visual effects.
 */
object BuiltInThemes {

    private fun c(hex: String): Int = Color.parseColor(hex)

    private fun solidExtracted(
        id: String,
        name: String,
        category: ThemeCategory,
        surfaceHex: String,
        keyHex: String,
        funcHex: String,
        accentHex: String,
        primaryTextHex: String,
        secondaryTextHex: String,
        borderHex: String,
        trailHex: String,
        accentTextHex: String? = null,
        borderWidthDp: Float = 1f,
        cornerRadiusDp: Float = 6f,
        effectStyle: EffectStyle = EffectStyle.NONE,
        font: FontChoice = FontChoice.ROBOTO
    ): ThemeModel {
        val bg = c(surfaceHex)
        val keyCol = c(keyHex)
        val fnCol = c(funcHex)
        val accCol = c(accentHex)
        val txtPrimary = c(primaryTextHex)
        val accTxt = accentTextHex?.let { c(it) }
            ?: if (Color.luminance(accCol) > 0.55f) Color.BLACK else Color.WHITE
        val isLightSurface = Color.luminance(bg) > 0.5f
        val stripBg = if (isLightSurface) {
            Color.rgb(
                (Color.red(bg) * 0.96f).toInt().coerceIn(0, 255),
                (Color.green(bg) * 0.96f).toInt().coerceIn(0, 255),
                (Color.blue(bg) * 0.96f).toInt().coerceIn(0, 255)
            )
        } else {
            bg
        }
        val stripText = if (isLightSurface) c("#FF1C1E21") else Color.WHITE
        val pressHighlight = if (isLightSurface) c("#24000000") else c("#38FFFFFF")

        return ThemeModel(
            id = id,
            name = name,
            isBuiltIn = true,
            keyHeightDp = 54f,
            keyCornerRadiusDp = cornerRadiusDp,
            keyGapHorizontalDp = 4f,
            keyGapVerticalDp = 6f,
            bottomPaddingDp = 12f,
            keyFaceColor = ColorSpec.solid(keyCol),
            keyBorderColor = c(borderHex),
            keyBorderWidthDp = borderWidthDp,
            keyPressHighlightColor = pressHighlight,
            keyboardBackground = BackgroundSpec.solid(bg),
            suggestionStripBgColor = stripBg,
            suggestionStripTextColor = stripText,
            keyLabelColorPrimary = txtPrimary,
            keyLabelColorSecondary = c(secondaryTextHex),
            keyLabelFont = font,
            keyLabelSizeSp = 16f,
            trailColor = c(trailHex),
            trailWidthDp = 12f,
            trailFadeDurationMs = 340L,
            keyPressRippleEnabled = true,
            keyPopPreviewEnabled = true,
            keyPopPreviewSize = PopPreviewSize.MEDIUM,
            functionalKeyColor = fnCol,
            accentKeyColor = accCol,
            accentKeyTextColor = accTxt,
            effectStyle = effectStyle,
            category = category
        )
    }

    private fun cyberTheme(
        id: String,
        name: String,
        rgbHex: String // e.g. "00E5FF"
    ): ThemeModel {
        val neon = c("#FF$rgbHex")
        return ThemeModel(
            id = id,
            name = name,
            isBuiltIn = true,
            keyHeightDp = 54f,
            keyCornerRadiusDp = 6f,
            keyGapHorizontalDp = 4f,
            keyGapVerticalDp = 6f,
            bottomPaddingDp = 12f,
            keyFaceColor = ColorSpec.solid(c("#1A$rgbHex")),
            keyBorderColor = c("#4D$rgbHex"),
            keyBorderWidthDp = 1f,
            keyPressHighlightColor = c("#4D$rgbHex"),
            keyboardBackground = BackgroundSpec.solid(c("#FF0A0E17")),
            suggestionStripBgColor = c("#FF080C14"),
            suggestionStripTextColor = neon,
            keyLabelColorPrimary = neon,
            keyLabelColorSecondary = c("#80FFFFFF"),
            keyLabelFont = FontChoice.JETBRAINS_MONO,
            keyLabelSizeSp = 15.5f,
            trailColor = neon,
            trailWidthDp = 12f,
            trailFadeDurationMs = 420L,
            keyPressRippleEnabled = true,
            keyPopPreviewEnabled = true,
            keyPopPreviewSize = PopPreviewSize.MEDIUM,
            functionalKeyColor = c("#0D$rgbHex"),
            accentKeyColor = neon,
            accentKeyTextColor = c("#FF0A0E17"),
            effectStyle = EffectStyle.CYBER_NEON,
            category = ThemeCategory.CYBER
        )
    }

    private fun effectTheme(
        id: String,
        name: String,
        isLight: Boolean,
        effectStyle: EffectStyle,
        palette: List<Int> = emptyList()
    ): ThemeModel {
        val trail = if (isLight) c("#FF3B82F6") else c("#FF38BDF8")
        return ThemeModel(
            id = id,
            name = name,
            isBuiltIn = true,
            keyHeightDp = 54f,
            keyCornerRadiusDp = 6f,
            keyGapHorizontalDp = 4f,
            keyGapVerticalDp = 6f,
            bottomPaddingDp = 12f,
            keyFaceColor = ColorSpec.solid(if (isLight) c("#FFFFFFFF") else c("#1FFFFFFF")),
            keyBorderColor = if (isLight) c("#1A000000") else c("#33FFFFFF"),
            keyBorderWidthDp = if (effectStyle == EffectStyle.MECH_GLOW) 1.25f else 1f,
            keyPressHighlightColor = if (isLight) c("#24000000") else c("#33FFFFFF"),
            keyboardBackground = BackgroundSpec.solid(if (isLight) c("#FFE6EAED") else c("#FF030303")),
            suggestionStripBgColor = if (isLight) c("#FFDCE1E6") else c("#FF050507"),
            suggestionStripTextColor = if (isLight) Color.BLACK else Color.WHITE,
            keyLabelColorPrimary = if (isLight) Color.BLACK else Color.WHITE,
            keyLabelColorSecondary = if (isLight) c("#80000000") else c("#80FFFFFF"),
            keyLabelFont = FontChoice.ROBOTO,
            keyLabelSizeSp = 16f,
            trailColor = trail,
            trailWidthDp = 12f,
            trailFadeDurationMs = 360L,
            keyPressRippleEnabled = true,
            keyPopPreviewEnabled = true,
            keyPopPreviewSize = PopPreviewSize.MEDIUM,
            functionalKeyColor = if (isLight) c("#FFCBCFD6") else c("#12FFFFFF"),
            accentKeyColor = trail,
            accentKeyTextColor = if (isLight) Color.WHITE else c("#FF030303"),
            effectStyle = effectStyle,
            effectColors = palette,
            category = ThemeCategory.EFFECTS
        )
    }

    private fun gradientExtracted(
        id: String,
        name: String,
        topSectionHex: String,
        startHex: String,
        endHex: String,
        midHex: String? = null,
        isLightText: Boolean = true,
        accentHex: String = topSectionHex,
        angleDeg: Float = 135f
    ): ThemeModel {
        val bgSpec = if (midHex != null) {
            BackgroundSpec.gradient(c(startHex), c(midHex), c(endHex), angleDeg)
        } else {
            BackgroundSpec.gradient(c(startHex), c(endHex), angleDeg)
        }
        val keyFill = if (isLightText) c("#2EFFFFFF") else c("#B8FFFFFF")
        val fnFill = if (isLightText) c("#1AFFFFFF") else c("#8AFFFFFF")
        val border = if (isLightText) c("#28FFFFFF") else c("#20000000")
        val textPrimary = if (isLightText) Color.WHITE else c("#FF1E1E24")
        val textSecondary = if (isLightText) c("#A8FFFFFF") else c("#80000000")
        val accCol = c(accentHex)
        val accText = if (Color.luminance(accCol) > 0.55f) Color.BLACK else Color.WHITE
        val topCol = c(topSectionHex)
        val stripText = if (Color.luminance(topCol) > 0.55f) c("#FF1E1E24") else Color.WHITE

        return ThemeModel(
            id = id,
            name = name,
            isBuiltIn = true,
            keyHeightDp = 54f,
            keyCornerRadiusDp = 8f,
            keyGapHorizontalDp = 4f,
            keyGapVerticalDp = 6f,
            bottomPaddingDp = 12f,
            keyFaceColor = ColorSpec.solid(keyFill),
            keyBorderColor = border,
            keyBorderWidthDp = 1f,
            keyPressHighlightColor = if (isLightText) c("#44FFFFFF") else c("#26000000"),
            keyboardBackground = bgSpec,
            suggestionStripBgColor = topCol,
            suggestionStripTextColor = stripText,
            keyLabelColorPrimary = textPrimary,
            keyLabelColorSecondary = textSecondary,
            keyLabelFont = FontChoice.INTER,
            keyLabelSizeSp = 16f,
            trailColor = if (isLightText) c(endHex) else accCol,
            trailWidthDp = 12f,
            trailFadeDurationMs = 360L,
            keyPressRippleEnabled = true,
            keyPopPreviewEnabled = true,
            keyPopPreviewSize = PopPreviewSize.MEDIUM,
            functionalKeyColor = fnFill,
            accentKeyColor = accCol,
            accentKeyTextColor = accText,
            effectStyle = EffectStyle.NONE,
            category = ThemeCategory.GRADIENTS
        )
    }

    // ==================== Original Core Built-In Themes ====================

    val MATERIAL_MIDNIGHT = ThemeModel(
        id = "builtin_midnight",
        name = "Material Midnight",
        isBuiltIn = true,
        keyHeightDp = 54f,
        keyCornerRadiusDp = 10f,
        keyGapHorizontalDp = 4f,
        keyGapVerticalDp = 6f,
        bottomPaddingDp = 12f,
        keyFaceColor = ColorSpec.solid(Color.parseColor("#212534")),
        keyBorderColor = Color.parseColor("#18FFFFFF"),
        keyBorderWidthDp = 1f,
        keyPressHighlightColor = Color.parseColor("#444F8BFF"),
        keyboardBackground = BackgroundSpec.solid(Color.parseColor("#13151F")),
        suggestionStripBgColor = Color.parseColor("#181B28"),
        suggestionStripTextColor = Color.parseColor("#F4F6FB"),
        keyLabelColorPrimary = Color.parseColor("#F4F6FB"),
        keyLabelColorSecondary = Color.parseColor("#8B91A7"),
        keyLabelFont = FontChoice.INTER,
        keyLabelSizeSp = 16f,
        trailColor = Color.parseColor("#4F8BFF"),
        trailWidthDp = 12f,
        trailFadeDurationMs = 350L,
        keyPressRippleEnabled = true,
        keyPopPreviewEnabled = true,
        keyPopPreviewSize = PopPreviewSize.MEDIUM,
        functionalKeyColor = Color.parseColor("#191C28"),
        accentKeyColor = Color.parseColor("#4F8BFF"),
        accentKeyTextColor = Color.WHITE,
        category = ThemeCategory.DARK
    )

    val CLEAN_PORCELAIN = ThemeModel(
        id = "builtin_porcelain",
        name = "Clean Porcelain",
        isBuiltIn = true,
        keyHeightDp = 54f,
        keyCornerRadiusDp = 10f,
        keyGapHorizontalDp = 4f,
        keyGapVerticalDp = 6f,
        bottomPaddingDp = 12f,
        keyFaceColor = ColorSpec.solid(Color.parseColor("#FFFFFF")),
        keyBorderColor = Color.parseColor("#14101828"),
        keyBorderWidthDp = 1f,
        keyPressHighlightColor = Color.parseColor("#223B6FF5"),
        keyboardBackground = BackgroundSpec.solid(Color.parseColor("#EDEFF5")),
        suggestionStripBgColor = Color.parseColor("#E4E8F2"),
        suggestionStripTextColor = Color.parseColor("#141720"),
        keyLabelColorPrimary = Color.parseColor("#141720"),
        keyLabelColorSecondary = Color.parseColor("#697086"),
        keyLabelFont = FontChoice.INTER,
        keyLabelSizeSp = 16f,
        trailColor = Color.parseColor("#3B6FF5"),
        trailWidthDp = 10f,
        trailFadeDurationMs = 300L,
        keyPressRippleEnabled = true,
        keyPopPreviewEnabled = true,
        keyPopPreviewSize = PopPreviewSize.MEDIUM,
        functionalKeyColor = Color.parseColor("#DCE1EC"),
        accentKeyColor = Color.parseColor("#3B6FF5"),
        accentKeyTextColor = Color.WHITE,
        category = ThemeCategory.LIGHT
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
        keyPopPreviewSize = PopPreviewSize.LARGE,
        effectStyle = EffectStyle.CYBER_NEON,
        category = ThemeCategory.CYBER
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
        keyPopPreviewSize = PopPreviewSize.MEDIUM,
        category = ThemeCategory.DARK
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
        keyPopPreviewSize = PopPreviewSize.MEDIUM,
        category = ThemeCategory.DARK
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
        keyPopPreviewSize = PopPreviewSize.SMALL,
        category = ThemeCategory.DARK
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
        keyPopPreviewSize = PopPreviewSize.MEDIUM,
        category = ThemeCategory.GRADIENTS
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
        keyPopPreviewSize = PopPreviewSize.MEDIUM,
        category = ThemeCategory.COLORFUL
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
        keyPopPreviewSize = PopPreviewSize.MEDIUM,
        category = ThemeCategory.DARK
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
        keyPopPreviewSize = PopPreviewSize.MEDIUM,
        category = ThemeCategory.LIGHT
    )

    // ==================== Extracted Visual Effect Themes ====================

    val RIPPLE_DARK = effectTheme("ext_ripple_dark", "Ripple Dark", isLight = false, EffectStyle.RIPPLE, KeyboardEffectEngine.RIPPLE_DARK_PALETTE)
    val RIPPLE_LIGHT = effectTheme("ext_ripple_light", "Ripple Light", isLight = true, EffectStyle.RIPPLE, KeyboardEffectEngine.RIPPLE_LIGHT_PALETTE)
    val MECH_GLOW = effectTheme("ext_mech_glow", "Mech Glow", isLight = false, EffectStyle.MECH_GLOW)
    val MECH_GLOW_LIGHT = effectTheme("ext_mech_glow_light", "Mech Glow Light", isLight = true, EffectStyle.MECH_GLOW)
    val AURORA_GLOW_FLOW = effectTheme("ext_aurora_flow", "Aurora Glow Flow", isLight = false, EffectStyle.GLOW_FLOW, KeyboardEffectEngine.GLOW_FLOW_AURORA_PALETTE)
    val EMERALD_GLOW_FLOW = effectTheme("ext_emerald_flow", "Emerald Glow Flow", isLight = false, EffectStyle.GLOW_FLOW, KeyboardEffectEngine.GLOW_FLOW_EMERALD_PALETTE)

    // ==================== Extracted Cyber Neon Themes ====================

    val CYBER_CYAN = cyberTheme("ext_cyber_cyan", "Cyber Cyan", "00E5FF")
    val CYBER_GREEN = cyberTheme("ext_cyber_green", "Cyber Green", "00FF9C")
    val CYBER_ORANGE = cyberTheme("ext_cyber_orange", "Cyber Orange", "FF8C00")
    val CYBER_RED = cyberTheme("ext_cyber_red", "Cyber Red", "FF2A6D")
    val CYBER_YELLOW = cyberTheme("ext_cyber_yellow", "Cyber Yellow", "FFE600")

    // ==================== Extracted Light & Classic Themes ====================

    val CLASSIC = solidExtracted(
        "ext_classic", "Classic", ThemeCategory.LIGHT,
        "#FFE6EAED", "#FFFFFFFF", "#FFCBCFD6", "#FF616466",
        "#FF000000", "#80000000", "#1A000000", "#FF616466", accentTextHex = "#FFFFFFFF"
    )
    val CLASSIC_BORDERLESS = solidExtracted(
        "ext_classic_borderless", "Classic Minimal", ThemeCategory.LIGHT,
        "#FFF8F9FA", "#FFCBCFD6", "#FFBDC2CA", "#FF616466",
        "#FF000000", "#80000000", "#00000000", "#FF616466", accentTextHex = "#FFFFFFFF", borderWidthDp = 0f
    )
    val IPHONE_CLASSIC = solidExtracted(
        "ext_iphone", "iOS Clean", ThemeCategory.LIGHT,
        "#FFD2D5DB", "#FFFFFFFF", "#FFAFB5BE", "#FF000000",
        "#FF000000", "#80000000", "#1A000000", "#FF000000", accentTextHex = "#FFFFFFFF"
    )
    val DESH_LIGHT = solidExtracted(
        "ext_desh_light", "Emerald Light", ThemeCategory.LIGHT,
        "#FFE6EAED", "#FFFFFFFF", "#1A087A50", "#FF4C8179",
        "#FF000000", "#80000000", "#1A000000", "#FF4C8179"
    )
    val MANGLISH_LIGHT = solidExtracted(
        "ext_manglish_light", "Sage Light", ThemeCategory.LIGHT,
        "#FFE6EAED", "#FFFFFFFF", "#335F9154", "#FF6E9A65",
        "#FF000000", "#80000000", "#1A000000", "#FF6E9A65"
    )
    val FUTURISTIC = solidExtracted(
        "ext_futuristic", "Futuristic Pulse", ThemeCategory.LIGHT,
        "#FFE6EAED", "#FFFFFFFF", "#FFCBCFD6", "#FF1A73E8",
        "#FF000000", "#80000000", "#1A000000", "#FF1A73E8", effectStyle = EffectStyle.KEY_PULSE
    )
    val PREMIUM_TEAL = solidExtracted(
        "ext_premium", "Premium Teal", ThemeCategory.LIGHT,
        "#FFE6EAED", "#FFFFFFFF", "#1A000000", "#FF26A69A",
        "#FF000000", "#80000000", "#1A000000", "#FF26A69A"
    )
    val FUNKY_CORAL = solidExtracted(
        "ext_funky", "Funky Coral", ThemeCategory.LIGHT,
        "#FFE6EAED", "#FFFFFFFF", "#FFEDD0D3", "#FFF45B69",
        "#FF000000", "#80000000", "#1A000000", "#FFF45B69"
    )

    // ==================== Extracted Dark & Moody Themes ====================

    val DESH_DARK = solidExtracted(
        "ext_desh_dark", "Deep Pine Dark", ThemeCategory.DARK,
        "#FF0B1A19", "#1AFFFFFF", "#08FFFFFF", "#FF4C8179",
        "#FFFFFFFF", "#80FFFFFF", "#1AFFFFFF", "#FF73B9AE"
    )
    val DARK_OLIVE = solidExtracted(
        "ext_dark", "Dark Olive", ThemeCategory.DARK,
        "#FF161616", "#1AFFFFFF", "#0DFFFFFF", "#FF6E9A65",
        "#FFFFFFFF", "#80FFFFFF", "#1AFFFFFF", "#FF6E9A65"
    )
    val BLACK_PITCH = solidExtracted(
        "ext_black", "Pitch Black", ThemeCategory.DARK,
        "#FF030303", "#1FFFFFFF", "#12FFFFFF", "#FF383838",
        "#FFFFFFFF", "#80FFFFFF", "#33FFFFFF", "#FFFFFFFF"
    )
    val TRANSPARENT_DARK = solidExtracted(
        "ext_transparent_dark", "Glass Dark", ThemeCategory.DARK,
        "#FF161616", "#40FFFFFF", "#26FFFFFF", "#FF52525B",
        "#FFFFFFFF", "#80FFFFFF", "#1AFFFFFF", "#FFFFFFFF"
    )
    val NIGHT_MINT = solidExtracted(
        "ext_night", "Night Mint", ThemeCategory.DARK,
        "#FF2F2F2F", "#1AFFFFFF", "#08FFFFFF", "#FF4ED383",
        "#FFFFFFFF", "#80FFFFFF", "#33FFFFFF", "#FF4ED383", accentTextHex = "#FF0E2918"
    )
    val AUTUMN_SLATE = solidExtracted(
        "ext_autumn", "Autumn Slate", ThemeCategory.DARK,
        "#FF37363D", "#1AFFFFFF", "#08FFFFFF", "#FFE3874F",
        "#FFFFFFFF", "#80FFFFFF", "#33FFFFFF", "#FFE3874F", accentTextHex = "#FF1E140E"
    )
    val HAUNTED_CRIMSON = solidExtracted(
        "ext_haunted", "Haunted Crimson", ThemeCategory.DARK,
        "#FF2C2C2C", "#12FFFFFF", "#08FFFFFF", "#FFC62828",
        "#FFFFFFFF", "#80FFFFFF", "#33FFFFFF", "#FFC62828"
    )
    val SHADOW_SLATE = solidExtracted(
        "ext_shadow", "Shadow Slate", ThemeCategory.DARK,
        "#FF273238", "#1AFFFFFF", "#0DFFFFFF", "#FF455A64",
        "#FFFFFFFF", "#80FFFFFF", "#33FFFFFF", "#FFFFFFFF"
    )
    val SERENE_SLATE = solidExtracted(
        "ext_serene", "Serene Blue-Grey", ThemeCategory.DARK,
        "#FF455A64", "#1AFFFFFF", "#0DFFFFFF", "#FF607D8B",
        "#FFFFFFFF", "#80FFFFFF", "#33FFFFFF", "#FFFFFFFF"
    )

    // ==================== Extracted Colorful Vivid Themes ====================

    val COBALT_BLUE = solidExtracted(
        "ext_cobalt", "Cobalt", ThemeCategory.COLORFUL,
        "#FF1565C0", "#40FFFFFF", "#1AFFFFFF", "#FF0D47A1",
        "#FFFFFFFF", "#80FFFFFF", "#1AFFFFFF", "#FFFFFFFF"
    )
    val GREEN_EMERALD = solidExtracted(
        "ext_green", "Emerald Pulse", ThemeCategory.COLORFUL,
        "#FF2E7D32", "#40FFFFFF", "#1AFFFFFF", "#FF1B5E20",
        "#FFFFFFFF", "#80FFFFFF", "#33FFFFFF", "#FFFFFFFF", effectStyle = EffectStyle.KEY_PULSE
    )
    val RED_CRIMSON = solidExtracted(
        "ext_red", "Crimson Red", ThemeCategory.COLORFUL,
        "#FFC62828", "#40FFFFFF", "#1AFFFFFF", "#FF8E0000",
        "#FFFFFFFF", "#80FFFFFF", "#1AFFFFFF", "#FFFFFFFF"
    )
    val ASCEND_MAGENTA = solidExtracted(
        "ext_ascend", "Ascend Magenta", ThemeCategory.COLORFUL,
        "#FFAD1457", "#40FFFFFF", "#1AFFFFFF", "#FF78002E",
        "#FFFFFFFF", "#80FFFFFF", "#33FFFFFF", "#FFFFFFFF"
    )
    val BLUEGREEN_TEAL = solidExtracted(
        "ext_bluegreen", "Bluegreen", ThemeCategory.COLORFUL,
        "#FF26A69A", "#4DFFFFFF", "#1AFFFFFF", "#FF00766C",
        "#FFFFFFFF", "#80FFFFFF", "#33FFFFFF", "#FFFFFFFF"
    )

    // ==================== Extracted High Contrast Accessibility Themes ====================

    val HC_YELLOW = solidExtracted(
        "ext_hc_yellow", "High Contrast Yellow", ThemeCategory.HIGH_CONTRAST,
        "#FF2C303F", "#FFFFFFFF", "#FFF0E442", "#FFF0E442",
        "#FF000000", "#FF4A4E5A", "#33FFFFFF", "#FFF0E442", accentTextHex = "#FF000000"
    )
    val HC_BLUE = solidExtracted(
        "ext_hc_blue", "High Contrast Cyan", ThemeCategory.HIGH_CONTRAST,
        "#FF2C303F", "#FFFFFFFF", "#FF00FFFF", "#FF00FFFF",
        "#FF000000", "#FF4A4E5A", "#33FFFFFF", "#FF00FFFF", accentTextHex = "#FF000000"
    )

    // ==================== Extracted Gradient & Atmospheric Themes ====================

    val GRADIENT_EMERALD = gradientExtracted(
        "ext_gradient", "Aqua Emerald", "#FF01764F",
        "#FF20E2AD", "#FF0EB9DD", midHex = "#FF17CEC5", isLightText = true, angleDeg = 90f
    )
    val GRADIENT_MYSTIC_GREEN = gradientExtracted(
        "ext_gradient_1", "Mystic Green", "#FF216241",
        "#FF3FB878", "#FF48CE9B", midHex = "#FF42BE82", isLightText = true
    )
    val GRADIENT_CORAL_GREEN = gradientExtracted(
        "ext_gradient_2", "Coral Cyan", "#FF027372",
        "#FF05E8D9", "#FF00D6CB", midHex = "#FF01E0D7", isLightText = false, accentHex = "#FF027372"
    )
    val GRADIENT_PURPLE_PINK = gradientExtracted(
        "ext_gradient_4", "Purple Pink", "#FF1E042E",
        "#FF37026F", "#FFF100D0", midHex = "#FF861BAF", isLightText = true, accentHex = "#FFF100D0"
    )
    val GRADIENT_SPECTRUM = gradientExtracted(
        "ext_gradient_5", "Spectrum Flare", "#FF15004D",
        "#FF7902C9", "#FFE84719", midHex = "#FF721076", isLightText = true, accentHex = "#FFE84719"
    )
    val GRADIENT_BLUISH = gradientExtracted(
        "ext_gradient_6", "Deep Bluish", "#FF174A68",
        "#FF238DCE", "#FF021C7F", midHex = "#FF0250AC", isLightText = true, accentHex = "#FF238DCE"
    )
    val GRADIENT_AURA_PASTEL = gradientExtracted(
        "ext_gradient_7", "Aura Pastel", "#FF826B6C",
        "#FFFAD2D2", "#FFCDE9E8", midHex = "#FFEBD6C0", isLightText = false, accentHex = "#FF826B6C"
    )
    val GRADIENT_BLUE_PINK = gradientExtracted(
        "ext_gradient_8", "Pastel Lavender", "#FF465975",
        "#FFB1EAFE", "#FFD9B6FE", midHex = "#FFCAD0FE", isLightText = false, accentHex = "#FF465975"
    )
    val GRADIENT_SAKURA_PINK = gradientExtracted(
        "ext_gradient_9", "Sakura Pink", "#FF7F507F",
        "#FFFE9FFA", "#FFFD79D9", midHex = "#FFFE9AF8", isLightText = false, accentHex = "#FF7F507F"
    )
    val GRADIENT_ABSTRACT_AURA = gradientExtracted(
        "ext_abstract", "Abstract Prism", "#FF763A7D",
        "#FFE662F5", "#FF245189", midHex = "#FF4A5898", isLightText = true, accentHex = "#FF9B51E0"
    )
    val GRADIENT_FROSTED_GLASS = gradientExtracted(
        "ext_frosted_glass", "Frosted Glass", "#FF141416",
        "#FF4A4C52", "#FF12161A", midHex = "#FF2C3033", isLightText = true, accentHex = "#FF6B7280", angleDeg = 90f
    )
    val GRADIENT_MODERN_ART = gradientExtracted(
        "ext_modern_art", "Modern Art", "#FF00193A",
        "#FF135A83", "#FF694C3B", midHex = "#FF3A6961", isLightText = true, accentHex = "#FF135A83"
    )
    val GRADIENT_STARS = gradientExtracted(
        "ext_stars", "Starry Teal", "#FF031515",
        "#FF012A33", "#FF039DB8", midHex = "#FF025B6D", isLightText = true, accentHex = "#FF039DB8", angleDeg = 90f
    )
    val GRADIENT_OCEAN = gradientExtracted(
        "ext_ocean", "Ocean Horizon", "#FF021E21",
        "#FF28797D", "#FFE3BFB1", midHex = "#FFA7B4B8", isLightText = false, accentHex = "#FF28797D", angleDeg = 90f
    )
    val GRADIENT_BLOSSOM = gradientExtracted(
        "ext_flower", "Blossom Ember", "#FF361608",
        "#FF8E4826", "#FF1B1515", midHex = "#FF2F201A", isLightText = true, accentHex = "#FF8E4826", angleDeg = 90f
    )
    val GRADIENT_TURQUOISE_WALL = gradientExtracted(
        "ext_turquoise_wall", "Turquoise Wall", "#FF24504A",
        "#FF3AA298", "#FF579F96", midHex = "#FF3BA199", isLightText = true, accentHex = "#FF24504A"
    )
    val GRADIENT_CHETAK_OBSIDIAN = gradientExtracted(
        "ext_chetak", "Obsidian Bronze", "#FF000000",
        "#FF0E0C0B", "#FF3D332F", midHex = "#FF342C28", isLightText = true, accentHex = "#FF6A5C53"
    )

    // ==================== Skeuomorphic Physical Material Themes ====================

    val SKEUO_DARK_GRAPHITE = ThemeModel(
        id = "builtin_dark_graphite",
        name = "Dark Graphite",
        isBuiltIn = true,
        keyHeightDp = 54f,
        keyCornerRadiusDp = 9f,
        keyGapHorizontalDp = 4f,
        keyGapVerticalDp = 6f,
        bottomPaddingDp = 12f,
        keyFaceColor = ColorSpec.gradient(c("#FF2E3443"), c("#FF1E222D")),
        keyBorderColor = c("#2CFFFFFF"),
        keyBorderWidthDp = 1f,
        keyPressHighlightColor = c("#364F8BFF"),
        keyboardBackground = BackgroundSpec.gradient(c("#FF11141B"), c("#FF191D27"), 180f),
        suggestionStripBgColor = c("#FF151923"),
        suggestionStripTextColor = c("#FFF1F5F9"),
        keyLabelColorPrimary = c("#FFF1F5F9"),
        keyLabelColorSecondary = c("#FF8B93A7"),
        keyLabelFont = FontChoice.INTER,
        keyLabelSizeSp = 16f,
        trailColor = c("#FF4F8BFF"),
        trailWidthDp = 12f,
        trailFadeDurationMs = 340L,
        functionalKeyColor = c("#FF1A1E28"),
        accentKeyColor = c("#FF3B82F6"),
        accentKeyTextColor = Color.WHITE,
        category = ThemeCategory.SKEUOMORPHIC,
        materialFinish = MaterialFinish.DARK_GRAPHITE
    )

    val SKEUO_BLACK_GLASS = ThemeModel(
        id = "builtin_black_glass",
        name = "Black Glass",
        isBuiltIn = true,
        keyHeightDp = 54f,
        keyCornerRadiusDp = 10f,
        keyGapHorizontalDp = 4f,
        keyGapVerticalDp = 6f,
        bottomPaddingDp = 12f,
        keyFaceColor = ColorSpec.gradient(c("#FF252D3D"), c("#FF11151E")),
        keyBorderColor = c("#387DD3FC"),
        keyBorderWidthDp = 1f,
        keyPressHighlightColor = c("#4038BDF8"),
        keyboardBackground = BackgroundSpec.gradient(c("#FF06080E"), c("#FF101522"), 155f),
        suggestionStripBgColor = c("#FF090C15"),
        suggestionStripTextColor = c("#FFE0F2FE"),
        keyLabelColorPrimary = c("#FFF8FAFC"),
        keyLabelColorSecondary = c("#FF7C8CA3"),
        keyLabelFont = FontChoice.INTER,
        keyLabelSizeSp = 16f,
        trailColor = c("#FF38BDF8"),
        trailWidthDp = 11f,
        trailFadeDurationMs = 360L,
        functionalKeyColor = c("#FF0E131D"),
        accentKeyColor = c("#FF0EA5E9"),
        accentKeyTextColor = Color.WHITE,
        category = ThemeCategory.SKEUOMORPHIC,
        materialFinish = MaterialFinish.BLACK_GLASS
    )

    val SKEUO_WHITE_CERAMIC = ThemeModel(
        id = "builtin_white_ceramic",
        name = "Soft White Ceramic",
        isBuiltIn = true,
        keyHeightDp = 54f,
        keyCornerRadiusDp = 10f,
        keyGapHorizontalDp = 4.5f,
        keyGapVerticalDp = 6f,
        bottomPaddingDp = 12f,
        keyFaceColor = ColorSpec.gradient(c("#FFFFFFFF"), c("#FFECEFF4")),
        keyBorderColor = c("#2894A3B8"),
        keyBorderWidthDp = 1f,
        keyPressHighlightColor = c("#200F172A"),
        keyboardBackground = BackgroundSpec.gradient(c("#FFE2E6EE"), c("#FFD4DAE4"), 180f),
        suggestionStripBgColor = c("#FFE8ECF2"),
        suggestionStripTextColor = c("#FF1E293B"),
        keyLabelColorPrimary = c("#FF18202C"),
        keyLabelColorSecondary = c("#FF64748B"),
        keyLabelFont = FontChoice.INTER,
        keyLabelSizeSp = 16f,
        trailColor = c("#FF2563EB"),
        trailWidthDp = 12f,
        trailFadeDurationMs = 330L,
        functionalKeyColor = c("#FFDDE2EC"),
        accentKeyColor = c("#FF2563EB"),
        accentKeyTextColor = Color.WHITE,
        category = ThemeCategory.SKEUOMORPHIC,
        materialFinish = MaterialFinish.WHITE_CERAMIC
    )

    val SKEUO_BRUSHED_METAL = ThemeModel(
        id = "builtin_brushed_metal",
        name = "Brushed Metal",
        isBuiltIn = true,
        keyHeightDp = 54f,
        keyCornerRadiusDp = 8f,
        keyGapHorizontalDp = 4f,
        keyGapVerticalDp = 6f,
        bottomPaddingDp = 12f,
        keyFaceColor = ColorSpec.gradient(c("#FF555E6F"), c("#FF333946")),
        keyBorderColor = c("#48E2E8F0"),
        keyBorderWidthDp = 1.1f,
        keyPressHighlightColor = c("#38FBBF24"),
        keyboardBackground = BackgroundSpec.gradient(c("#FF222731"), c("#FF343B48"), c("#FF282E38"), 135f),
        suggestionStripBgColor = c("#FF272D38"),
        suggestionStripTextColor = c("#FFF1F5F9"),
        keyLabelColorPrimary = c("#FFF8FAFC"),
        keyLabelColorSecondary = c("#FFA8B2C2"),
        keyLabelFont = FontChoice.SANS_SERIF_MEDIUM,
        keyLabelSizeSp = 15.5f,
        trailColor = c("#FFF59E0B"),
        trailWidthDp = 11f,
        trailFadeDurationMs = 340L,
        functionalKeyColor = c("#FF2C323E"),
        accentKeyColor = c("#FFF59E0B"),
        accentKeyTextColor = c("#FF111827"),
        category = ThemeCategory.SKEUOMORPHIC,
        materialFinish = MaterialFinish.BRUSHED_METAL
    )

    val SKEUO_PREMIUM_LEATHER = ThemeModel(
        id = "builtin_premium_leather",
        name = "Premium Leather",
        isBuiltIn = true,
        keyHeightDp = 54f,
        keyCornerRadiusDp = 9f,
        keyGapHorizontalDp = 4.5f,
        keyGapVerticalDp = 6f,
        bottomPaddingDp = 12f,
        keyFaceColor = ColorSpec.gradient(c("#FF422E25"), c("#FF2B1D17")),
        keyBorderColor = c("#3AF5D0A9"),
        keyBorderWidthDp = 1f,
        keyPressHighlightColor = c("#3CD97706"),
        keyboardBackground = BackgroundSpec.gradient(c("#FF1C120E"), c("#FF2A1B14"), 180f),
        suggestionStripBgColor = c("#FF221611"),
        suggestionStripTextColor = c("#FFF7EDE2"),
        keyLabelColorPrimary = c("#FFFAF0E6"),
        keyLabelColorSecondary = c("#FFC4A484"),
        keyLabelFont = FontChoice.SERIF,
        keyLabelSizeSp = 16f,
        trailColor = c("#FFD97706"),
        trailWidthDp = 12f,
        trailFadeDurationMs = 350L,
        functionalKeyColor = c("#FF241712"),
        accentKeyColor = c("#FFD97706"),
        accentKeyTextColor = Color.WHITE,
        category = ThemeCategory.SKEUOMORPHIC,
        materialFinish = MaterialFinish.LEATHER
    )

    val SKEUO_CLASSIC_MECHANICAL = ThemeModel(
        id = "builtin_classic_mechanical",
        name = "Classic Mechanical",
        isBuiltIn = true,
        keyHeightDp = 55f,
        keyCornerRadiusDp = 7f,
        keyGapHorizontalDp = 4f,
        keyGapVerticalDp = 6f,
        bottomPaddingDp = 12f,
        keyFaceColor = ColorSpec.gradient(c("#FFF7F4EC"), c("#FFE1DDD2")),
        keyBorderColor = c("#5578766E"),
        keyBorderWidthDp = 1.1f,
        keyPressHighlightColor = c("#28000000"),
        keyboardBackground = BackgroundSpec.gradient(c("#FFB7B4A9"), c("#FF9E9B90"), 180f),
        suggestionStripBgColor = c("#FFACA99E"),
        suggestionStripTextColor = c("#FF1E2022"),
        keyLabelColorPrimary = c("#FF1E2022"),
        keyLabelColorSecondary = c("#FF5C5B55"),
        keyLabelFont = FontChoice.JETBRAINS_MONO,
        keyLabelSizeSp = 15.5f,
        trailColor = c("#FFD95338"),
        trailWidthDp = 11f,
        trailFadeDurationMs = 340L,
        functionalKeyColor = c("#FF908E84"),
        accentKeyColor = c("#FFD95338"),
        accentKeyTextColor = Color.WHITE,
        category = ThemeCategory.SKEUOMORPHIC,
        materialFinish = MaterialFinish.MECHANICAL
    )

    val SKEUOMORPHIC_THEMES = listOf(
        SKEUO_DARK_GRAPHITE,
        SKEUO_BLACK_GLASS,
        SKEUO_WHITE_CERAMIC,
        SKEUO_BRUSHED_METAL,
        SKEUO_PREMIUM_LEATHER,
        SKEUO_CLASSIC_MECHANICAL
    )

    val CORE_BUILT_IN = listOf(
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

    val EXTRACTED_THEMES = listOf(
        // Visual Effects first so users discover animated effects immediately
        MECH_GLOW,
        MECH_GLOW_LIGHT,
        RIPPLE_DARK,
        RIPPLE_LIGHT,
        AURORA_GLOW_FLOW,
        EMERALD_GLOW_FLOW,
        // Cyber Neon series
        CYBER_CYAN,
        CYBER_GREEN,
        CYBER_ORANGE,
        CYBER_RED,
        CYBER_YELLOW,
        // Light & Classic
        CLASSIC,
        CLASSIC_BORDERLESS,
        IPHONE_CLASSIC,
        DESH_LIGHT,
        MANGLISH_LIGHT,
        FUTURISTIC,
        PREMIUM_TEAL,
        FUNKY_CORAL,
        // Dark & Moody
        DESH_DARK,
        DARK_OLIVE,
        BLACK_PITCH,
        TRANSPARENT_DARK,
        NIGHT_MINT,
        AUTUMN_SLATE,
        HAUNTED_CRIMSON,
        SHADOW_SLATE,
        SERENE_SLATE,
        // Colorful Vivid
        COBALT_BLUE,
        GREEN_EMERALD,
        RED_CRIMSON,
        ASCEND_MAGENTA,
        BLUEGREEN_TEAL,
        // High Contrast
        HC_YELLOW,
        HC_BLUE,
        // Gradients & Atmospheric
        GRADIENT_EMERALD,
        GRADIENT_MYSTIC_GREEN,
        GRADIENT_CORAL_GREEN,
        GRADIENT_PURPLE_PINK,
        GRADIENT_SPECTRUM,
        GRADIENT_BLUISH,
        GRADIENT_AURA_PASTEL,
        GRADIENT_BLUE_PINK,
        GRADIENT_SAKURA_PINK,
        GRADIENT_ABSTRACT_AURA,
        GRADIENT_FROSTED_GLASS,
        GRADIENT_MODERN_ART,
        GRADIENT_STARS,
        GRADIENT_OCEAN,
        GRADIENT_BLOSSOM,
        GRADIENT_TURQUOISE_WALL,
        GRADIENT_CHETAK_OBSIDIAN
    )

    val ALL_BUILT_IN = SKEUOMORPHIC_THEMES + CORE_BUILT_IN + EXTRACTED_THEMES

    fun getById(id: String): ThemeModel {
        return ALL_BUILT_IN.find { it.id == id } ?: SKEUO_DARK_GRAPHITE
    }
}
