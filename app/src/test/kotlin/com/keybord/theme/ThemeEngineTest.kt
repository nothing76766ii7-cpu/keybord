package com.keybord.theme

import org.junit.Assert.*
import org.junit.Test

class ThemeEngineTest {

    @Test
    fun testAllTenBuiltInThemesExistAndValid() {
        val themes = BuiltInThemes.ALL_BUILT_IN
        assertEquals("PRD requires exactly 10 built-in themes at launch", 10, themes.size)

        val uniqueIds = themes.map { it.id }.toSet()
        assertEquals("Each built-in theme must have a unique ID", 10, uniqueIds.size)

        for (theme in themes) {
            assertTrue("Built-in flag must be true", theme.isBuiltIn)
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
        val original = BuiltInThemes.CYBERPUNK_NEON
        val json = ThemeSerializer.toJson(original)

        assertNotNull(json)
        assertTrue(json.contains("\"schemaVersion\": 1"))
        assertTrue(json.contains("\"id\": \"builtin_cyberpunk\""))

        val restored = ThemeSerializer.fromJson(json)
        assertEquals(original.id, restored.id)
        assertEquals(original.name, restored.name)
        assertEquals(original.keyHeightDp, restored.keyHeightDp, 0.01f)
        assertEquals(original.keyCornerRadiusDp, restored.keyCornerRadiusDp, 0.01f)
        assertEquals(original.trailColor, restored.trailColor)
        assertEquals(original.keyLabelFont, restored.keyLabelFont)
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
