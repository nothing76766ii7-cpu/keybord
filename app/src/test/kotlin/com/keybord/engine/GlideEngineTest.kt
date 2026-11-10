package com.keybord.engine

import android.graphics.RectF
import com.keybord.engine.glide.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class GlideEngineTest {

    private lateinit var keyPositions: MutableMap<Char, KeyPosition>
    private lateinit var lexicon: TrieLexicon
    private lateinit var decoder: GlideDecoder

    @Before
    fun setUp() {
        keyPositions = HashMap()
        val row1 = "qwertyuiop"
        val row2 = "asdfghjkl"
        val row3 = "zxcvbnm"

        val keyWidth = 100f
        val keyHeight = 120f

        row1.forEachIndexed { i, c ->
            keyPositions[c] = KeyPosition(c, RectF(i * keyWidth, 0f, (i + 1) * keyWidth, keyHeight))
        }
        row2.forEachIndexed { i, c ->
            val left = (i + 0.5f) * keyWidth
            keyPositions[c] = KeyPosition(c, RectF(left, keyHeight, left + keyWidth, 2 * keyHeight))
        }
        row3.forEachIndexed { i, c ->
            val left = (i + 1.5f) * keyWidth
            keyPositions[c] = KeyPosition(c, RectF(left, 2 * keyHeight, left + keyWidth, 3 * keyHeight))
        }

        lexicon = TrieLexicon().apply {
            loadDefaultVocabulary()
        }
        decoder = GlideDecoder(keyPositions, lexicon)
    }

    @Test
    fun testThrottleShortDistance() {
        val points = listOf(
            TouchPoint(100f, 100f, 1000L),
            TouchPoint(102f, 101f, 1200L)
        )
        val candidates = decoder.decodeGesture(points)
        assertTrue("Short distance must be throttled", candidates.isEmpty())
    }

    @Test
    fun testThrottleWithinOneKey() {
        val keyH = keyPositions['h']!!.bounds
        val points = listOf(
            TouchPoint(keyH.left + 5f, keyH.top + 5f, 1000L),
            TouchPoint(keyH.left + 15f, keyH.top + 20f, 1150L),
            TouchPoint(keyH.left + 10f, keyH.top + 30f, 1300L)
        )
        val candidates = decoder.decodeGesture(points)
        assertTrue("Movement completely within a single key must be throttled", candidates.isEmpty())
    }

    @Test
    fun testDecodeHelloTrajectory() {
        val h = keyPositions['h']!!
        val e = keyPositions['e']!!
        val l = keyPositions['l']!!
        val o = keyPositions['o']!!

        val points = mutableListOf<TouchPoint>()
        var t = 1000L
        fun addWaypoints(p1: KeyPosition, p2: KeyPosition, steps: Int) {
            for (i in 0..steps) {
                val frac = i / steps.toFloat()
                points.add(
                    TouchPoint(
                        p1.centerX + frac * (p2.centerX - p1.centerX),
                        p1.centerY + frac * (p2.centerY - p1.centerY),
                        t
                    )
                )
                t += 40L
            }
        }

        addWaypoints(h, e, 6)
        addWaypoints(e, l, 8)
        addWaypoints(l, o, 6)

        val candidates = decoder.decodeGesture(points)
        assertFalse("Should produce candidates for valid swipe", candidates.isEmpty())
        val topWords = candidates.map { it.word }
        assertTrue("Candidate list should include 'hello'", topWords.contains("hello"))
    }

    @Test
    fun testDoubleLetterHandlingWithoutLooping() {
        // Test word "look": l -> o -> k (single swipe through 'o')
        val l = keyPositions['l']!!
        val o = keyPositions['o']!!
        val k = keyPositions['k']!!

        val points = mutableListOf<TouchPoint>()
        var t = 1000L
        fun addWaypoints(p1: KeyPosition, p2: KeyPosition, steps: Int) {
            for (i in 0..steps) {
                val frac = i / steps.toFloat()
                points.add(
                    TouchPoint(
                        p1.centerX + frac * (p2.centerX - p1.centerX),
                        p1.centerY + frac * (p2.centerY - p1.centerY),
                        t
                    )
                )
                t += 40L
            }
        }

        addWaypoints(l, o, 7)
        addWaypoints(o, k, 7)

        val candidates = decoder.decodeGesture(points)
        assertFalse("Should recognize double-letter words without looping back", candidates.isEmpty())
        val words = candidates.map { it.word }
        assertTrue("Candidate list should include 'look'", words.contains("look"))
    }
}
