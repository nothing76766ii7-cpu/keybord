package com.keybord.engine

import com.keyboard.engine.magicwrite.MagicWritingCoordinator
import com.keyboard.engine.magicwrite.WritingStyle
import com.keybord.engine.ai.AiWriterClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM tests validating AI Writer BYOK configuration checks,
 * tone/format/action completeness, and offline fallback mappings.
 */
class AiWriterClientLogicTest {

    @Test
    fun testAllRequiredWritingTonesFormatsAndActionsExist() {
        val toneNames = AiWriterClient.WritingTone.values().map { it.label }.toSet()
        assertEquals(
            setOf("Natural", "Formal", "Casual", "Professional", "Friendly", "Concise"),
            toneNames
        )
        assertNull(AiWriterClient.WritingTone.NATURAL.mappedStyle)
        assertEquals(WritingStyle.FORMAL, AiWriterClient.WritingTone.FORMAL.mappedStyle)
        assertEquals(WritingStyle.CASUAL, AiWriterClient.WritingTone.CASUAL.mappedStyle)
        assertEquals(WritingStyle.PROFESSIONAL, AiWriterClient.WritingTone.PROFESSIONAL.mappedStyle)
        assertEquals(WritingStyle.FRIENDLY, AiWriterClient.WritingTone.FRIENDLY.mappedStyle)
        assertEquals(WritingStyle.CONCISE, AiWriterClient.WritingTone.CONCISE.mappedStyle)

        val formatNames = AiWriterClient.WritingFormat.values().map { it.label }.toSet()
        assertTrue(formatNames.containsAll(setOf("Auto", "Message", "Reply", "Paragraph", "Caption", "Email")))

        val actionNames = AiWriterClient.WritingAction.values().map { it.label }.toSet()
        assertTrue(actionNames.containsAll(setOf("Generate", "Rewrite", "Expand", "Shorten", "Correct", "Improve")))
    }

    @Test
    fun testByokCloudConfigurationRequiresProviderEndpointModelAndKey() {
        val noKey = AiWriterClient.AiConfig(
            provider = AiWriterClient.Provider.GEMINI,
            endpointUrl = AiWriterClient.Provider.GEMINI.defaultEndpoint,
            model = AiWriterClient.Provider.GEMINI.defaultModel,
            apiKey = ""
        )
        assertFalse(noKey.isCloudConfigured)

        val offlineWithKey = AiWriterClient.AiConfig(
            provider = AiWriterClient.Provider.OFFLINE_ONLY,
            endpointUrl = "https://example.invalid",
            model = "model",
            apiKey = "secret-not-used-offline"
        )
        assertFalse(offlineWithKey.isCloudConfigured)

        val validGemini = noKey.copy(apiKey = "sample-user-key")
        assertTrue(validGemini.isCloudConfigured)

        val validOpenAi = AiWriterClient.AiConfig(
            provider = AiWriterClient.Provider.OPENAI_COMPATIBLE,
            endpointUrl = AiWriterClient.Provider.OPENAI_COMPATIBLE.defaultEndpoint,
            model = AiWriterClient.Provider.OPENAI_COMPATIBLE.defaultModel,
            apiKey = "sample-user-key"
        )
        assertTrue(validOpenAi.isCloudConfigured)
    }

    @Test
    fun testOfflineMagicCoordinatorHandlesProfessionalFriendlyFormalCasualConcise() {
        val coordinator = MagicWritingCoordinator()
        val draft = "hey i cant join teh call right now"
        val proofread = coordinator.proofread(draft)
        assertTrue(proofread.hasChanges)
        assertNotNull(proofread.correctedText)
        assertTrue(proofread.correctedText.contains("the", ignoreCase = true))

        for (tone in listOf(
            AiWriterClient.WritingTone.FORMAL,
            AiWriterClient.WritingTone.CASUAL,
            AiWriterClient.WritingTone.PROFESSIONAL,
            AiWriterClient.WritingTone.FRIENDLY,
            AiWriterClient.WritingTone.CONCISE
        )) {
            val out = coordinator.rewriteInStyle(draft, tone.mappedStyle!!)
            assertTrue("Rewrite output should not be blank for $tone", out.text.isNotBlank())
        }
    }
}
