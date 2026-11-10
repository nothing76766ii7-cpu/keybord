package com.keybord.engine

import android.view.inputmethod.InputConnection
import com.keyboard.engine.magicwrite.MagicWritingCoordinator
import com.keyboard.engine.magicwrite.ProofreadEngine
import com.keyboard.engine.magicwrite.StyleRewriteEngine
import com.keyboard.engine.magicwrite.WritingStyle
import com.keyboard.engine.rambler.ChunkState
import com.keyboard.engine.rambler.RamblerSessionState
import com.keyboard.engine.rambler.RamblerTextProcessor
import com.keyboard.engine.rambler.RamblerUndoStateMachine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

class MagicAndRamblerTest {

    private fun createBufferInputConnection(buffer: StringBuilder): InputConnection {
        return Proxy.newProxyInstance(
            InputConnection::class.java.classLoader,
            arrayOf(InputConnection::class.java)
        ) { _, method, args ->
            when (method.name) {
                "beginBatchEdit", "endBatchEdit" -> true
                "commitText" -> {
                    val text = (args?.getOrNull(0) as? CharSequence)?.toString().orEmpty()
                    buffer.append(text)
                    true
                }
                "deleteSurroundingText" -> {
                    val beforeLen = (args?.getOrNull(0) as? Int) ?: 0
                    val remove = beforeLen.coerceAtMost(buffer.length)
                    if (remove > 0) {
                        buffer.delete(buffer.length - remove, buffer.length)
                    }
                    true
                }
                "getTextBeforeCursor" -> buffer.toString()
                "getSelectedText" -> null
                else -> false
            }
        } as InputConnection
    }

    @Test
    fun testProofreadFixesTyposGrammarAndCapitalization() {
        val engine = ProofreadEngine()
        val result = engine.proofread("i recieve teh update and could of went")
        assertTrue(result.hasChanges)
        assertTrue(result.correctedText.contains("I "))
        assertTrue(result.correctedText.contains("receive"))
        assertTrue(result.correctedText.contains("the"))
        assertTrue(result.correctedText.contains("could have"))
    }

    @Test
    fun testStyleRewriteAllSixStylesProduceValidOutput() {
        val engine = StyleRewriteEngine()
        val sample = "hey I can't come to the meeting today"
        for (style in WritingStyle.values()) {
            val draft = engine.transformStyle(sample, style)
            assertEquals(style, draft.style)
            assertTrue("Draft for $style should not be blank", draft.text.isNotBlank())
        }
        val formal = engine.transformStyle("i won't make it", WritingStyle.FORMAL)
        assertTrue(formal.text.contains("will not", ignoreCase = true))
    }

    @Test
    fun testMagicCoordinatorApplyAndUndoRestoresOriginalText() {
        val buffer = StringBuilder("teh quick update")
        val ic = createBufferInputConnection(buffer)
        val coordinator = MagicWritingCoordinator()

        val rewritten = "The quick update."
        assertTrue(coordinator.applyReplacement(ic, "teh quick update", rewritten, hadSelection = false))
        assertEquals(rewritten, buffer.toString())
        assertTrue(coordinator.hasUndo)

        assertTrue(coordinator.revertLastChange(ic))
        assertEquals("teh quick update", buffer.toString())
        assertFalse(coordinator.hasUndo)
    }

    @Test
    fun testRamblerCleansDisfluenciesStuttersNumbersAndSpokenPunctuation() {
        val processor = RamblerTextProcessor()
        val cleaned = processor.processRawStream(
            "um i want want five dollars comma and ten percent question mark",
            isFinalPause = true
        )
        assertFalse("Should strip 'um'", cleaned.contains("um ", ignoreCase = true))
        assertTrue("Should dedup stutter 'want want'", cleaned.contains("want ") && !cleaned.contains("want want"))
        assertTrue("Should normalize dollars -> $5", cleaned.contains("$5"))
        assertTrue("Should normalize percent -> 10%", cleaned.contains("10%"))
        assertTrue("Should convert spoken comma & question mark", cleaned.contains(",") && cleaned.endsWith("?"))
    }

    @Test
    fun testRamblerUndoStateMachineToggleAndExternalMutationReset() {
        val sm = RamblerUndoStateMachine()
        sm.startSession()
        assertEquals(RamblerSessionState.STREAMING, sm.sessionState)

        val chunk = sm.registerChunk("um hello world", "Hello world.", startOffset = 0)
        assertEquals(ChunkState.CLEANED_REWRITTEN, chunk.currentState)
        assertEquals(RamblerSessionState.STATE_A_REWRITTEN, sm.sessionState)

        val reverted = sm.revertToRawTranscript()
        assertNotNull(reverted)
        assertEquals("um hello world", reverted!!.second)
        assertEquals(RamblerSessionState.STATE_B_TRANSCRIPT, sm.sessionState)

        val restored = sm.restoreCleanedText()
        assertNotNull(restored)
        assertEquals("Hello world.", restored!!.second)
        assertEquals(RamblerSessionState.STATE_A_REWRITTEN, sm.sessionState)

        // Mutating cursor outside chunk bounds resets session cleanly
        sm.handleTextMutation(cursorPosition = 99)
        assertEquals(RamblerSessionState.IDLE, sm.sessionState)
        assertNull(sm.activeChunk)
    }
}
