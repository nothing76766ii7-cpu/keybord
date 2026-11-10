package com.keyboard

import com.keyboard.data.AppProfile
import org.junit.Assert.*
import org.junit.Test

class AppProfileTest {

    @Test
    fun testAppProfileToThemeWithOverride() {
        val profile = AppProfile(
            packageName = "org.telegram.messenger",
            themeName = "Ocean Blue",
            keyHeightDp = 62,
            glideEnabled = true,
            magicEnabled = true
        )

        val theme = profile.toTheme()
        assertEquals("Ocean Blue", theme.name)
        assertEquals(62, theme.getIntParam("key_height_dp", 56))
        assertEquals("#1E3A8A", theme.getParam("key_face_color", ""))
    }

    @Test
    fun testAppProfileToThemeWithDefaultFallback() {
        val profile = AppProfile(
            packageName = "com.example.plain",
            themeName = null,
            keyHeightDp = null
        )

        val theme = profile.toTheme()
        assertEquals("Classic Dark", theme.name)
        assertEquals(56, theme.getIntParam("key_height_dp", 56))
    }
}
