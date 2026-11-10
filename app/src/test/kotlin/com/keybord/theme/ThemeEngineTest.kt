package com.keybord.theme

import org.junit.Assert.*
import org.junit.Test

class ThemeEngineTest {

    @Test
    fun testAllBuiltInAndExtractedThemesExistAndValid() {
        assertEquals("PRD requires 10 core launch themes", 10, BuiltInThemes.CORE_BUILT_IN.size)
        assertTrue("Extracted theme collection should be populated", BuiltInThemes.EXTRACTED_THEMES.size >= 40)

        val themes = BuiltInThemes.ALL_BUILT_IN
        val uniqueIds = themes.map { it.id }.toSet()
        assertEquals("Each built-in/extracted theme must have a unique ID", themes.size, uniqueIds.size)

        val effectStyles = themes.map { it.effectStyle }.toSet()
        assertTrue(effectStyles.contains(EffectStyle.MECH_GLOW))
        assertTrue(effectStyles.contains(EffectStyle.RIPPLE))
        assertTrue(effectStyles.contains(EffectStyle.CYBER_NEON))
        assertTrue(effectStyles.contains(EffectStyle.KEY_PULSE))
        assertTrue(effectStyles.contains(EffectStyle.GLOW_FLOW))

        for (theme in themes) {
            assertTrue("Built-in flag must be true for ${theme.id}", theme.isBuiltIn)
            assertTrue("Name must not be blank for ${theme.id}", theme.name.isNotBlank())
            assertTrue("keyHeightDp must be within [40..80]", theme.keyHeightDp in 40f..80f)
            assertTrue("keyCornerRadiusDp must be within [0..24]", theme.keyCornerRadiusDp in 0f..24f)
            assertTrue("keyGapHorizontalDp must be within [0..8]", theme.keyGapHorizontalDp in 0f..8f)
            assertTrue("keyGapVerticalDp must be within [0..8]", theme.keyGapVerticalDp in 0f..8f)
            assertTrue("keyLabelSizeSp must be within [10..18]", theme.keyLabelSizeSp in 10f..18f)
            assertTrue("trailWidthDp must be within [4..24]", theme.trailWidthDp in 4f..24f)
            assertTrue("trailFadeDurationMs must be within [100..800]", theme.trailFadeDurationMs in 100L..800L)
        }
    }

    @Test
    fun testThemeJsonRoundTrip() {
        for (original in listOf(BuiltInThemes.MECH_GLOW, BuiltInThemes.AURORA_GLOW_FLOW, BuiltInThemes.RIPPLE_DARK, BuiltInThemes.CYBER_CYAN)) {
            val json = ThemeSerializer.toJson(original)
            assertNotNull(json)
            assertTrue(json.contains("\"schemaVersion\": 1"))
            assertTrue(json.contains("\"id\": \"${original.id}\""))
            assertTrue(json.contains("\"effectStyle\": \"${original.effectStyle.name}\""))

            val restored = ThemeSerializer.fromJson(json)
            assertEquals(original.id, restored.id)
            assertEquals(original.name, restored.name)
            assertEquals(original.keyHeightDp, restored.keyHeightDp, 0.01f)
            assertEquals(original.keyCornerRadiusDp, restored.keyCornerRadiusDp, 0.01f)
            assertEquals(original.trailColor, restored.trailColor)
            assertEquals(original.keyLabelFont, restored.keyLabelFont)
            assertEquals(original.effectStyle, restored.effectStyle)
            assertEquals(original.category, restored.category)
            assertNotNull(restored.functionalKeyColor)
            assertNotNull(restored.accentKeyColor)
        }
    }

    @Test
    fun testLegacySchemaCompatibilityWithoutEffectFields() {
        val legacyJson = """
            {
                "schemaVersion": 1,
                "id": "legacy_custom",
                "name": "Legacy",
                "geometry": { "keyHeightDp": 52.0 },
                "surface": {},
                "gestureTrail": { "trailWidthDp": 10.0, "trailFadeDurationMs": 300 }
            }
        """.trimIndent()
        val parsed = ThemeSerializer.fromJson(legacyJson)
        assertEquals("legacy_custom", parsed.id)
        assertEquals(EffectStyle.NONE, parsed.effectStyle)
        assertNull(parsed.functionalKeyColor)
        assertNull(parsed.accentKeyColor)
        assertEquals(ThemeCategory.DARK, parsed.category)
    }

    @Test(expected = ThemeValidationException::class)
    fun testRejectMalformedJson() {
        val badJson = "{ invalid_json: 123 "
        ThemeSerializer.fromJson(badJson)
    }

    @Test(expected = ThemeValidationException::class)
    fun testRejectMissingSchemaVersion() {
        val noSchemaJson = """
            {
                "id": "test_theme",
                "name": "Test"
            }
        """.trimIndent()
        ThemeSerializer.fromJson(noSchemaJson)
    }

    @Test(expected = ThemeValidationException::class)
    fun testRejectOutOfRangeKeyHeight() {
        val badHeightJson = """
            {
                "schemaVersion": 1,
                "id": "bad_theme",
                "name": "Bad Height",
                "geometry": {
                    "keyHeightDp": 150.0
                }
            }
        """.trimIndent()
        ThemeSerializer.fromJson(badHeightJson)
    }

    @Test(expected = ThemeValidationException::class)
    fun testRejectOutOfRangeTrailWidth() {
        val badTrailJson = """
            {
                "schemaVersion": 1,
                "id": "bad_trail",
                "name": "Bad Trail",
                "geometry": {
                    "keyHeightDp": 54.0
                },
                "surface": {},
                "gestureTrail": {
                    "trailWidthDp": 100.0
                }
            }
        """.trimIndent()
        ThemeSerializer.fromJson(badTrailJson)
    }
}
