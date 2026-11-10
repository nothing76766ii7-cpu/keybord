package com.keybord.profile

import com.keybord.theme.OneHandedConfig

/**
 * Data model for per-application keyboard profile overrides.
 * Built into the foundation from day one (PRD Feature 5).
 */
data class AppProfile(
    val packageName: String,
    val themeIdOverride: String? = null,
    val keyHeightDpOverride: Float? = null,
    val oneHandedOverride: OneHandedConfig? = null,
    val glideTypingOverride: Boolean? = null
)
