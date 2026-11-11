package com.keybord.theme

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable

/**
 * 6 Bundled font options for key labels.
 */
enum class FontChoice(val displayName: String) {
    ROBOTO("Roboto"),
    MONOSPACE("Monospace"),
    SERIF("Serif"),
    SANS_SERIF_MEDIUM("Sans-Serif Medium"),
    JETBRAINS_MONO("JetBrains Mono"),
    INTER("Inter");

    fun toTypeface(): Typeface {
        return when (this) {
            ROBOTO -> Typeface.SANS_SERIF
            MONOSPACE, JETBRAINS_MONO -> Typeface.MONOSPACE
            SERIF -> Typeface.SERIF
            SANS_SERIF_MEDIUM, INTER -> Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
    }
}

/**
 * Key pop preview size options.
 */
enum class PopPreviewSize(val scaleFactor: Float) {
    SMALL(1.1f),
    MEDIUM(1.35f),
    LARGE(1.6f)
}

/**
 * One-handed mode configuration.
 */
data class OneHandedConfig(
    val enabled: Boolean = false,
    val gravityRight: Boolean = true, // true = right offset, false = left offset
    val widthPercent: Float = 85f // range 70..95
)

/**
 * Color specification supporting solid color or a 2-stop linear gradient.
 */
data class ColorSpec(
    val isGradient: Boolean = false,
    val primaryColor: Int = Color.parseColor("#2A2B3D"),
    val secondaryColor: Int = Color.parseColor("#1E1E2E"),
    val gradientOrientation: GradientDrawable.Orientation = GradientDrawable.Orientation.TOP_BOTTOM
) {
    companion object {
        fun solid(color: Int): ColorSpec = ColorSpec(false, color, color)
        fun gradient(
            startColor: Int,
            endColor: Int,
            orientation: GradientDrawable.Orientation = GradientDrawable.Orientation.TOP_BOTTOM
        ): ColorSpec = ColorSpec(true, startColor, endColor, orientation)
    }
}

/**
 * Visual key-press and edge lighting effect modes.
 */
enum class EffectStyle(val displayName: String, val badge: String) {
    NONE("None", ""),
    RIPPLE("Ripple Waves", "Ripple"),
    MECH_GLOW("Mech Glow", "Mech Glow"),
    CYBER_NEON("Cyber Neon Glow", "Cyber Glow"),
    KEY_PULSE("Key Pulse", "Key Pulse"),
    GLOW_FLOW("Glow Flow", "Glow Flow")
}

/**
 * Physical surface finish rendered across keycaps, housing decks, and settings panels.
 */
enum class MaterialFinish(val displayName: String) {
    MATTE("Matte Tactile"),
    DARK_GRAPHITE("Dark Graphite"),
    BLACK_GLASS("Black Glass"),
    WHITE_CERAMIC("Soft White Ceramic"),
    BRUSHED_METAL("Brushed Metal"),
    LEATHER("Premium Leather"),
    MECHANICAL("Classic Mechanical")
}

/**
 * Gallery grouping categories for built-in and extracted themes.
 */
enum class ThemeCategory(val label: String) {
    ALL("All"),
    SKEUOMORPHIC("Materials"),
    EFFECTS("Effects"),
    CYBER("Cyber"),
    GRADIENTS("Gradients"),
    DARK("Dark"),
    LIGHT("Light"),
    COLORFUL("Colorful"),
    HIGH_CONTRAST("Contrast")
}

/**
 * Keyboard background specification: solid color, 2- or 3-stop gradient, or image URI.
 */
data class BackgroundSpec(
    val type: Type = Type.SOLID,
    val colorStart: Int = Color.parseColor("#12121A"),
    val colorEnd: Int = Color.parseColor("#181824"),
    val imageUri: String? = null,
    val colorMid: Int? = null,
    val gradientAngleDeg: Float = 90f
) {
    enum class Type { SOLID, GRADIENT, IMAGE }

    companion object {
        fun solid(color: Int) = BackgroundSpec(Type.SOLID, color, color, null)
        fun gradient(start: Int, end: Int, angleDeg: Float = 90f) =
            BackgroundSpec(Type.GRADIENT, start, end, null, null, angleDeg)
        fun gradient(start: Int, mid: Int, end: Int, angleDeg: Float = 135f) =
            BackgroundSpec(Type.GRADIENT, start, end, null, mid, angleDeg)
        fun image(uri: String) = BackgroundSpec(Type.IMAGE, Color.BLACK, Color.BLACK, uri)
    }
}

/**
 * Master Keybord Theme Model exposing 20+ visual customization parameters plus extracted effects.
 */
data class ThemeModel(
    val id: String,
    val name: String,
    val isBuiltIn: Boolean = false,
    val schemaVersion: Int = 1,

    // --- Layout Geometry (dp) ---
    val keyHeightDp: Float = 54f,           // range 40..80
    val keyCornerRadiusDp: Float = 8f,       // range 0..24
    val keyGapHorizontalDp: Float = 4f,      // range 0..8
    val keyGapVerticalDp: Float = 6f,        // range 0..8
    val bottomPaddingDp: Float = 12f,        // navigation bar clearance
    val oneHandedMode: OneHandedConfig = OneHandedConfig(),

    // --- Colour and Surface ---
    val keyFaceColor: ColorSpec = ColorSpec.solid(Color.parseColor("#252736")),
    val keyBorderColor: Int = Color.parseColor("#1AFFFFFF"),
    val keyBorderWidthDp: Float = 1f,        // range 0..4
    val keyPressHighlightColor: Int = Color.parseColor("#33FFFFFF"),
    val keyboardBackground: BackgroundSpec = BackgroundSpec.solid(Color.parseColor("#12121A")),
    val suggestionStripBgColor: Int = Color.parseColor("#1A1A24"),
    val suggestionStripTextColor: Int = Color.parseColor("#E0E0E8"),
    val keyLabelColorPrimary: Int = Color.WHITE,
    val keyLabelColorSecondary: Int = Color.parseColor("#8E929E"),
    val keyLabelFont: FontChoice = FontChoice.ROBOTO,
    val keyLabelSizeSp: Float = 16f,         // range 10..18

    // --- Gesture Trail ---
    val trailColor: Int = Color.parseColor("#4285F4"),
    val trailWidthDp: Float = 12f,           // range 4..24
    val trailFadeDurationMs: Long = 350L,    // range 100..800

    // --- Animations ---
    val keyPressRippleEnabled: Boolean = true,
    val keyPopPreviewEnabled: Boolean = true,
    val keyPopPreviewSize: PopPreviewSize = PopPreviewSize.MEDIUM,

    // --- Extended Key Role & Effect Options (v1 backward-compatible defaults) ---
    val functionalKeyColor: Int? = null,
    val accentKeyColor: Int? = null,
    val accentKeyTextColor: Int? = null,
    val effectStyle: EffectStyle = EffectStyle.NONE,
    val effectColors: List<Int> = emptyList(),
    val category: ThemeCategory = ThemeCategory.DARK,
    val materialFinish: MaterialFinish = MaterialFinish.MATTE
) {
    fun copyWithId(newId: String, newName: String): ThemeModel {
        return this.copy(id = newId, name = newName, isBuiltIn = false)
    }
}
