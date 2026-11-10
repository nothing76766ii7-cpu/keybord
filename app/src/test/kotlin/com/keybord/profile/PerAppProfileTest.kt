package com.keybord.profile

import com.keybord.theme.OneHandedConfig
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class PerAppProfileTest {

    private lateinit var profileRepo: AppProfileRepository

    @Before
    fun setUp() {
        profileRepo = AppProfileRepository(context = null, inMemory = true)
    }

    @Test
    fun testSaveAndRetrievePerAppProfile() {
        val profile = AppProfile(
            packageName = "org.telegram.messenger",
            themeIdOverride = "builtin_cyberpunk",
            keyHeightDpOverride = 60f,
            oneHandedOverride = OneHandedConfig(enabled = true, gravityRight = false, widthPercent = 80f),
            glideTypingOverride = true
        )
        profileRepo.saveProfile(profile)

        val retrieved = profileRepo.getProfileForPackage("org.telegram.messenger")
        assertNotNull("Profile must be retrievable", retrieved)
        assertEquals("builtin_cyberpunk", retrieved!!.themeIdOverride)
        assertEquals(60f, retrieved.keyHeightDpOverride!!, 0.01f)
        assertNotNull(retrieved.oneHandedOverride)
        assertTrue(retrieved.oneHandedOverride!!.enabled)
        assertFalse(retrieved.oneHandedOverride!!.gravityRight)
        assertEquals(80f, retrieved.oneHandedOverride!!.widthPercent, 0.01f)
    }

    @Test
    fun testFallbackForUnconfiguredApp() {
        val retrieved = profileRepo.getProfileForPackage("com.unconfigured.app")
        assertNull("Unconfigured app must return null so IME uses global defaults", retrieved)
    }

    @Test
    fun testDeleteProfile() {
        val profile = AppProfile(
            packageName = "com.twitter.android",
            themeIdOverride = "builtin_dracula"
        )
        profileRepo.saveProfile(profile)
        assertNotNull(profileRepo.getProfileForPackage("com.twitter.android"))

        profileRepo.deleteProfile("com.twitter.android")
        assertNull(profileRepo.getProfileForPackage("com.twitter.android"))
    }
}
