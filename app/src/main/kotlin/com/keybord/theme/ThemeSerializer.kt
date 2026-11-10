package com.keybord.theme

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import org.json.JSONException
import org.json.JSONObject

class ThemeValidationException(message: String) : Exception(message)

/**
 * Handles serialization, parsing, and strict schema validation for .keybordtheme files.
 */
object ThemeSerializer {

    const val CURRENT_SCHEMA_VERSION = 1

    /**
     * Serializes a ThemeModel into a valid .keybordtheme JSON string.
     */
    fun toJson(theme: ThemeModel): String {
        val root = JSONObject()
        root.put("schemaVersion", CURRENT_SCHEMA_VERSION)
        root.put("id", theme.id)
        root.put("name", theme.name)
        root.put("isBuiltIn", theme.isBuiltIn)

        // Geometry
        val geo = JSONObject()
        geo.put("keyHeightDp", theme.keyHeightDp.toDouble())
        geo.put("keyCornerRadiusDp", theme.keyCornerRadiusDp.toDouble())
        geo.put("keyGapHorizontalDp", theme.keyGapHorizontalDp.toDouble())
        geo.put("keyGapVerticalDp", theme.keyGapVerticalDp.toDouble())
        geo.put("bottomPaddingDp", theme.bottomPaddingDp.toDouble())

        val oneHanded = JSONObject()
        oneHanded.put("enabled", theme.oneHandedMode.enabled)
        oneHanded.put("gravityRight", theme.oneHandedMode.gravityRight)
        oneHanded.put("widthPercent", theme.oneHandedMode.widthPercent.toDouble())
        geo.put("oneHandedMode", oneHanded)
        root.put("geometry", geo)

        // Surface & Colors
        val surface = JSONObject()
        val keyFace = JSONObject()
        keyFace.put("isGradient", theme.keyFaceColor.isGradient)
        keyFace.put("primaryColor", String.format("#%08X", theme.keyFaceColor.primaryColor))
        keyFace.put("secondaryColor", String.format("#%08X", theme.keyFaceColor.secondaryColor))
        keyFace.put("gradientOrientation", theme.keyFaceColor.gradientOrientation.name)
        surface.put("keyFaceColor", keyFace)

        surface.put("keyBorderColor", String.format("#%08X", theme.keyBorderColor))
        surface.put("keyBorderWidthDp", theme.keyBorderWidthDp.toDouble())
        surface.put("keyPressHighlightColor", String.format("#%08X", theme.keyPressHighlightColor))

        val bg = JSONObject()
        bg.put("type", theme.keyboardBackground.type.name)
        bg.put("colorStart", String.format("#%08X", theme.keyboardBackground.colorStart))
        bg.put("colorEnd", String.format("#%08X", theme.keyboardBackground.colorEnd))
        theme.keyboardBackground.colorMid?.let { bg.put("colorMid", String.format("#%08X", it)) }
        bg.put("gradientAngleDeg", theme.keyboardBackground.gradientAngleDeg.toDouble())
        if (theme.keyboardBackground.imageUri != null) {
            bg.put("imageUri", theme.keyboardBackground.imageUri)
        }
        surface.put("keyboardBackground", bg)

        theme.functionalKeyColor?.let { surface.put("functionalKeyColor", String.format("#%08X", it)) }
        theme.accentKeyColor?.let { surface.put("accentKeyColor", String.format("#%08X", it)) }
        theme.accentKeyTextColor?.let { surface.put("accentKeyTextColor", String.format("#%08X", it)) }
        surface.put("suggestionStripBgColor", String.format("#%08X", theme.suggestionStripBgColor))
        surface.put("suggestionStripTextColor", String.format("#%08X", theme.suggestionStripTextColor))
        surface.put("keyLabelColorPrimary", String.format("#%08X", theme.keyLabelColorPrimary))
        surface.put("keyLabelColorSecondary", String.format("#%08X", theme.keyLabelColorSecondary))
        surface.put("keyLabelFont", theme.keyLabelFont.name)
        surface.put("keyLabelSizeSp", theme.keyLabelSizeSp.toDouble())
        root.put("surface", surface)

        // Gesture Trail
        val trail = JSONObject()
        trail.put("trailColor", String.format("#%08X", theme.trailColor))
        trail.put("trailWidthDp", theme.trailWidthDp.toDouble())
        trail.put("trailFadeDurationMs", theme.trailFadeDurationMs)
        root.put("gestureTrail", trail)

        // Animations & Visual Effects
        val anim = JSONObject()
        anim.put("keyPressRippleEnabled", theme.keyPressRippleEnabled)
        anim.put("keyPopPreviewEnabled", theme.keyPopPreviewEnabled)
        anim.put("keyPopPreviewSize", theme.keyPopPreviewSize.name)
        anim.put("effectStyle", theme.effectStyle.name)
        if (theme.effectColors.isNotEmpty()) {
            val arr = org.json.JSONArray()
            theme.effectColors.forEach { arr.put(String.format("#%08X", it)) }
            anim.put("effectColors", arr)
        }
        root.put("animations", anim)
        root.put("category", theme.category.name)

        return root.toString(2)
    }

    /**
     * Parses and validates a .keybordtheme JSON string.
     * Throws ThemeValidationException on malformed input to prevent crashes.
     */
    fun fromJson(jsonStr: String): ThemeModel {
        val root = try {
            JSONObject(jsonStr)
        } catch (e: JSONException) {
            throw ThemeValidationException("Malformed JSON: ${e.message}")
        }

        // Schema validation
        val schemaVer = root.optInt("schemaVersion", -1)
        if (schemaVer < 1) {
            throw ThemeValidationException("Missing or unsupported schemaVersion: $schemaVer")
        }

        val id = root.optString("id")
        if (id.isBlank()) throw ThemeValidationException("Theme ID is required and cannot be empty")

        val name = root.optString("name")
        if (name.isBlank()) throw ThemeValidationException("Theme name is required and cannot be empty")

        val isBuiltIn = root.optBoolean("isBuiltIn", false)

        // Geometry validation
        val geo = root.optJSONObject("geometry") ?: throw ThemeValidationException("Missing 'geometry' section")
        val keyHeightDp = geo.optDouble("keyHeightDp", 54.0).toFloat()
        if (keyHeightDp !in 40f..80f) {
            throw ThemeValidationException("keyHeightDp ($keyHeightDp) out of allowed range [40..80]")
        }

        val keyCornerRadiusDp = geo.optDouble("keyCornerRadiusDp", 8.0).toFloat()
        if (keyCornerRadiusDp !in 0f..24f) {
            throw ThemeValidationException("keyCornerRadiusDp ($keyCornerRadiusDp) out of allowed range [0..24]")
        }

        val keyGapHorizontalDp = geo.optDouble("keyGapHorizontalDp", 4.0).toFloat()
        if (keyGapHorizontalDp !in 0f..8f) {
            throw ThemeValidationException("keyGapHorizontalDp ($keyGapHorizontalDp) out of allowed range [0..8]")
        }

        val keyGapVerticalDp = geo.optDouble("keyGapVerticalDp", 6.0).toFloat()
        if (keyGapVerticalDp !in 0f..8f) {
            throw ThemeValidationException("keyGapVerticalDp ($keyGapVerticalDp) out of allowed range [0..8]")
        }

        val bottomPaddingDp = geo.optDouble("bottomPaddingDp", 12.0).toFloat()
        if (bottomPaddingDp !in 0f..64f) {
            throw ThemeValidationException("bottomPaddingDp ($bottomPaddingDp) out of allowed range [0..64]")
        }

        val oneHandedObj = geo.optJSONObject("oneHandedMode")
        val oneHandedConfig = if (oneHandedObj != null) {
            OneHandedConfig(
                enabled = oneHandedObj.optBoolean("enabled", false),
                gravityRight = oneHandedObj.optBoolean("gravityRight", true),
                widthPercent = oneHandedObj.optDouble("widthPercent", 85.0).toFloat().coerceIn(50f, 100f)
            )
        } else OneHandedConfig()

        // Surface & Colors validation
        val surface = root.optJSONObject("surface") ?: throw ThemeValidationException("Missing 'surface' section")

        val keyFaceObj = surface.optJSONObject("keyFaceColor")
        val keyFaceColor = if (keyFaceObj != null) {
            val isGrad = keyFaceObj.optBoolean("isGradient", false)
            val pCol = parseColorSafe(keyFaceObj.optString("primaryColor"), Color.parseColor("#252736"))
            val sCol = parseColorSafe(keyFaceObj.optString("secondaryColor"), pCol)
            val orientName = keyFaceObj.optString("gradientOrientation", "TOP_BOTTOM")
            val orient = try {
                GradientDrawable.Orientation.valueOf(orientName)
            } catch (e: Exception) {
                GradientDrawable.Orientation.TOP_BOTTOM
            }
            ColorSpec(isGrad, pCol, sCol, orient)
        } else {
            ColorSpec.solid(Color.parseColor("#252736"))
        }

        val keyBorderColor = parseColorSafe(surface.optString("keyBorderColor"), Color.parseColor("#1AFFFFFF"))
        val keyBorderWidthDp = surface.optDouble("keyBorderWidthDp", 1.0).toFloat().coerceIn(0f, 4f)
        val keyPressHighlightColor = parseColorSafe(surface.optString("keyPressHighlightColor"), Color.parseColor("#33FFFFFF"))

        val bgObj = surface.optJSONObject("keyboardBackground")
        val keyboardBackground = if (bgObj != null) {
            val typeName = bgObj.optString("type", "SOLID")
            val type = try { BackgroundSpec.Type.valueOf(typeName) } catch (e: Exception) { BackgroundSpec.Type.SOLID }
            val cStart = parseColorSafe(bgObj.optString("colorStart"), Color.parseColor("#12121A"))
            val cEnd = parseColorSafe(bgObj.optString("colorEnd"), cStart)
            val cMid = parseColorOptional(bgObj.optString("colorMid"))
            val angleDeg = bgObj.optDouble("gradientAngleDeg", 90.0).toFloat()
            val imgUri = if (bgObj.has("imageUri")) bgObj.optString("imageUri") else null
            BackgroundSpec(type, cStart, cEnd, imgUri, cMid, angleDeg)
        } else {
            BackgroundSpec.solid(Color.parseColor("#12121A"))
        }

        val functionalKeyColor = parseColorOptional(surface.optString("functionalKeyColor"))
        val accentKeyColor = parseColorOptional(surface.optString("accentKeyColor"))
        val accentKeyTextColor = parseColorOptional(surface.optString("accentKeyTextColor"))
        val suggestionStripBgColor = parseColorSafe(surface.optString("suggestionStripBgColor"), Color.parseColor("#1A1A24"))
        val suggestionStripTextColor = parseColorSafe(surface.optString("suggestionStripTextColor"), Color.parseColor("#E0E0E8"))
        val keyLabelColorPrimary = parseColorSafe(surface.optString("keyLabelColorPrimary"), Color.WHITE)
        val keyLabelColorSecondary = parseColorSafe(surface.optString("keyLabelColorSecondary"), Color.parseColor("#8E929E"))

        val fontName = surface.optString("keyLabelFont", "ROBOTO")
        val keyLabelFont = try { FontChoice.valueOf(fontName) } catch (e: Exception) { FontChoice.ROBOTO }

        val keyLabelSizeSp = surface.optDouble("keyLabelSizeSp", 16.0).toFloat()
        if (keyLabelSizeSp !in 10f..18f) {
            throw ThemeValidationException("keyLabelSizeSp ($keyLabelSizeSp) out of allowed range [10..18]")
        }

        // Gesture Trail validation
        val trail = root.optJSONObject("gestureTrail") ?: throw ThemeValidationException("Missing 'gestureTrail' section")
        val trailColor = parseColorSafe(trail.optString("trailColor"), Color.parseColor("#4285F4"))
        val trailWidthDp = trail.optDouble("trailWidthDp", 12.0).toFloat()
        if (trailWidthDp !in 4f..24f) {
            throw ThemeValidationException("trailWidthDp ($trailWidthDp) out of allowed range [4..24]")
        }
        val trailFadeDurationMs = trail.optLong("trailFadeDurationMs", 350L)
        if (trailFadeDurationMs !in 100L..800L) {
            throw ThemeValidationException("trailFadeDurationMs ($trailFadeDurationMs) out of allowed range [100..800]")
        }

        // Animations & Visual Effects validation
        val anim = root.optJSONObject("animations") ?: JSONObject()
        val keyPressRippleEnabled = anim.optBoolean("keyPressRippleEnabled", true)
        val keyPopPreviewEnabled = anim.optBoolean("keyPopPreviewEnabled", true)
        val popSizeName = anim.optString("keyPopPreviewSize", "MEDIUM")
        val keyPopPreviewSize = try { PopPreviewSize.valueOf(popSizeName) } catch (e: Exception) { PopPreviewSize.MEDIUM }
        val effectName = anim.optString("effectStyle", "NONE")
        val effectStyle = try { EffectStyle.valueOf(effectName) } catch (e: Exception) { EffectStyle.NONE }

        val effectColors = mutableListOf<Int>()
        val effectColorsArr = anim.optJSONArray("effectColors")
        if (effectColorsArr != null) {
            for (i in 0 until effectColorsArr.length()) {
                parseColorOptional(effectColorsArr.optString(i))?.let { effectColors.add(it) }
            }
        }

        val catName = root.optString("category", "DARK")
        val category = try { ThemeCategory.valueOf(catName) } catch (e: Exception) { ThemeCategory.DARK }

        return ThemeModel(
            id = id,
            name = name,
            isBuiltIn = isBuiltIn,
            schemaVersion = schemaVer,
            keyHeightDp = keyHeightDp,
            keyCornerRadiusDp = keyCornerRadiusDp,
            keyGapHorizontalDp = keyGapHorizontalDp,
            keyGapVerticalDp = keyGapVerticalDp,
            bottomPaddingDp = bottomPaddingDp,
            oneHandedMode = oneHandedConfig,
            keyFaceColor = keyFaceColor,
            keyBorderColor = keyBorderColor,
            keyBorderWidthDp = keyBorderWidthDp,
            keyPressHighlightColor = keyPressHighlightColor,
            keyboardBackground = keyboardBackground,
            suggestionStripBgColor = suggestionStripBgColor,
            suggestionStripTextColor = suggestionStripTextColor,
            keyLabelColorPrimary = keyLabelColorPrimary,
            keyLabelColorSecondary = keyLabelColorSecondary,
            keyLabelFont = keyLabelFont,
            keyLabelSizeSp = keyLabelSizeSp,
            trailColor = trailColor,
            trailWidthDp = trailWidthDp,
            trailFadeDurationMs = trailFadeDurationMs,
            keyPressRippleEnabled = keyPressRippleEnabled,
            keyPopPreviewEnabled = keyPopPreviewEnabled,
            keyPopPreviewSize = keyPopPreviewSize,
            functionalKeyColor = functionalKeyColor,
            accentKeyColor = accentKeyColor,
            accentKeyTextColor = accentKeyTextColor,
            effectStyle = effectStyle,
            effectColors = effectColors,
            category = category
        )
    }

    private fun parseColorSafe(hexStr: String, fallback: Int): Int {
        if (hexStr.isBlank()) return fallback
        return try {
            Color.parseColor(hexStr)
        } catch (e: Exception) {
            fallback
        }
    }

    private fun parseColorOptional(hexStr: String): Int? {
        if (hexStr.isBlank()) return null
        return try {
            Color.parseColor(hexStr)
        } catch (e: Exception) {
            null
        }
    }
}
