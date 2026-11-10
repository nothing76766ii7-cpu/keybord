package com.keybord.engine

import android.graphics.RectF
import com.keybord.engine.glide.GlideDecoder
import com.keybord.engine.glide.GlideEngineConfig
import com.keybord.engine.glide.KeyPosition
import com.keybord.engine.glide.SpatialModel
import com.keybord.engine.glide.TouchPoint
import com.keybord.engine.glide.TrieLexicon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * JVM-safe accuracy tests for the upgraded com.keybord Glide Typing Engine.
 * Supplies explicit centerX/centerY so stubbed RectF under unitTests.isReturnDefaultValues=true
 * does not collapse key geometry.
 */
class GlideAccuracyTest {

    private fun buildQwertyPositions(): Map<Char, KeyPosition> {
        val map = HashMap<Char, KeyPosition>()
        val row1 = "qwertyuiop"
        val row2 = "asdfghjkl"
        val row3 = "zxcvbnm"
        val keyW = 100f
        val keyH = 120f

        row1.forEachIndexed { i, c ->
            val left = i * keyW
            map[c] = KeyPosition(c, RectF(left, 0f, left + keyW, keyH), left + keyW * 0.5f, keyH * 0.5f)
        }
        row2.forEachIndexed { i, c ->
            val left = (i + 0.5f) * keyW
            map[c] = KeyPosition(c, RectF(left, keyH, left + keyW, 2f * keyH), left + keyW * 0.5f, 1.5f * keyH)
        }
        row3.forEachIndexed { i, c ->
            val left = (i + 1.5f) * keyW
            map[c] = KeyPosition(c, RectF(left, 2f * keyH, left + keyW, 3f * keyH), left + keyW * 0.5f, 2.5f * keyH)
        }
        return map
    }

    private fun loadWordlist(): TrieLexicon {
        val lex = TrieLexicon()
        val candidates = listOf(
            File("src/main/assets/wordlist.txt"),
            File("app/src/main/assets/wordlist.txt")
        )
        val file = candidates.firstOrNull { it.exists() }
        if (file != null) {
            file.bufferedReader().useLines { lex.loadFromSequence(it) }
        } else {
            lex.loadDefaultVocabulary()
        }
        return lex
    }

    @Test
    fun testAnisotropicGaussianPrefersHorizontalOverVerticalOffset() {
        val positions = buildQwertyPositions()
        val spatial = SpatialModel(positions, GlideEngineConfig())
        val h = positions['h']!!

        val dxScore = spatial.pointKeyDistanceScore(TouchPoint(h.centerX + 35f, h.centerY, 1000L), 'h', 0f)
        val dyScore = spatial.pointKeyDistanceScore(TouchPoint(h.centerX, h.centerY + 35f, 1000L), 'h', 0f)
        assertTrue("Horizontal offset score ($dxScore) must exceed equal vertical offset ($dyScore)", dxScore > dyScore)
    }

    @Test
    fun testAnchorGateAndDirectionScore() {
        val positions = buildQwertyPositions()
        val spatial = SpatialModel(positions, GlideEngineConfig())
        val q = positions['q']!!
        val p = positions['p']!!

        assertEquals(0f, spatial.anchorScore(TouchPoint(q.centerX + 250f, q.centerY, 1000L), q), 1e-6f)
        assertEquals(1f, spatial.anchorScore(TouchPoint(p.centerX, p.centerY, 1000L), p), 1e-5f)

        val aligned = spatial.directionScore(
            TouchPoint(50f, 60f, 1000L),
            TouchPoint(150f, 60f, 1050L),
            q,
            p
        )
        val reversed = spatial.directionScore(
            TouchPoint(150f, 60f, 1000L),
            TouchPoint(50f, 60f, 1050L),
            q,
            p
        )
        assertEquals(1.0f, aligned, 1e-5f)
        assertEquals(0.4f, reversed, 1e-5f)
    }

    @Test
    fun testBenchmarkWordsTop1AccuracyWithAssetWordlist() {
        val positions = buildQwertyPositions()
        val lex = loadWordlist()
        val decoder = GlideDecoder(positions, lex, GlideEngineConfig())

        fun swipe(word: String): List<TouchPoint> {
            val dedup = SpatialModel.dedupConsecutiveChars(word)
            val pts = mutableListOf<TouchPoint>()
            var t = 1000L
            val first = positions[dedup.first()]!!
            pts.add(TouchPoint(first.centerX + 8f, first.centerY + 10f, t))
            for (k in 0 until dedup.size - 1) {
                val a = positions[dedup[k]]!!
                val b = positions[dedup[k + 1]]!!
                for (step in 1..6) {
                    val f = step / 6f
                    t += 22L
                    pts.add(
                        TouchPoint(
                            a.centerX + 8f + f * (b.centerX - a.centerX),
                            a.centerY + 10f + f * (b.centerY - a.centerY),
                            t
                        )
                    )
                }
            }
            val last = positions[dedup.last()]!!
            pts.add(TouchPoint(last.centerX + 10f, last.centerY + 12f, t + 20L))
            return pts
        }

        val benchmark = listOf(
            "hello", "world", "quick", "brown", "phone",
            "there", "about", "which", "their", "would",
            "keyboard", "typing", "finger", "swipe", "should",
            "because", "through", "different", "something",
            "the", "that", "with", "from"
        )

        var hits = 0
        val misses = mutableListOf<String>()
        for (word in benchmark) {
            val top = decoder.decodeGesture(swipe(word)).firstOrNull()?.word
            if (top == word) {
                hits++
            } else {
                misses.add("$word -> $top")
            }
        }
        assertTrue(
            "Expected >= 85% top-1 accuracy on JVM benchmark, got $hits/${benchmark.size} ($misses)",
            hits * 100 >= benchmark.size * 85
        )
    }

    @Test
    fun testCurvedSloppySwipesTop1Accuracy() {
        val positions = buildQwertyPositions()
        val lex = loadWordlist()
        val decoder = GlideDecoder(positions, lex, GlideEngineConfig())

        fun curvedSwipe(word: String): List<TouchPoint> {
            val dedup = SpatialModel.dedupConsecutiveChars(word)
            val rawWaypoints = dedup.map { c ->
                val k = positions[c]!!
                FloatArray(2).apply {
                    this[0] = k.centerX + 8f
                    this[1] = k.centerY + 14f
                }
            }.toMutableList()
            if (rawWaypoints.size >= 3) {
                val orig = rawWaypoints.map { it.copyOf() }
                for (i in 1 until rawWaypoints.size - 1) {
                    val midX = 0.5f * (orig[i - 1][0] + orig[i + 1][0])
                    val midY = 0.5f * (orig[i - 1][1] + orig[i + 1][1])
                    rawWaypoints[i][0] = 0.68f * orig[i][0] + 0.32f * midX
                    rawWaypoints[i][1] = 0.68f * orig[i][1] + 0.32f * midY
                }
            }
            rawWaypoints[0][0] -= 18f
            rawWaypoints[0][1] += 18f
            rawWaypoints[rawWaypoints.lastIndex][0] += 20f
            rawWaypoints[rawWaypoints.lastIndex][1] += 18f

            val pts = mutableListOf<TouchPoint>()
            var t = 1000L
            pts.add(TouchPoint(rawWaypoints[0][0], rawWaypoints[0][1], t))
            for (k in 0 until rawWaypoints.size - 1) {
                val a = rawWaypoints[k]
                val b = rawWaypoints[k + 1]
                for (step in 1..5) {
                    val f = step / 5f
                    t += 18L
                    pts.add(TouchPoint(a[0] + f * (b[0] - a[0]), a[1] + f * (b[1] - a[1]), t))
                }
            }
            return pts
        }

        val words = listOf(
            "the", "that", "with", "from", "this", "have", "what", "when", "your", "just",
            "hello", "world", "quick", "brown", "phone", "there", "about", "which", "their", "would",
            "keyboard", "typing", "finger", "swipe", "should", "because", "through", "different", "something",
            "people", "little", "better", "letter", "tomorrow", "probably", "important", "remember", "together",
            "question", "everyone", "everything", "understand", "available", "experience", "actually", "awesome",
            "coffee", "banana", "friend", "family", "morning", "message", "please", "always", "never"
        )

        var hits = 0
        val misses = mutableListOf<String>()
        for (word in words) {
            val top = decoder.decodeGesture(curvedSwipe(word)).firstOrNull()?.word
            if (top == word) {
                hits++
            } else {
                misses.add("$word -> $top")
            }
        }
        assertTrue(
            "Expected >= 88% top-1 accuracy on curved sloppy swipes, got $hits/${words.size} ($misses)",
            hits * 100 >= words.size * 88
        )
    }

    @Test
    fun testContractionsSwipeTop1() {
        val positions = buildQwertyPositions()
        val lex = loadWordlist()
        val decoder = GlideDecoder(positions, lex, GlideEngineConfig())

        fun swipeKeys(keys: String): List<TouchPoint> {
            val pts = mutableListOf<TouchPoint>()
            var t = 1000L
            for (i in 0 until keys.length - 1) {
                val k0 = positions[keys[i]]!!
                val k1 = positions[keys[i + 1]]!!
                for (s in 0..4) {
                    val f = s / 4f
                    t += 20L
                    pts.add(TouchPoint(k0.centerX + f * (k1.centerX - k0.centerX), k0.centerY + f * (k1.centerY - k0.centerY), t))
                }
            }
            return pts
        }

        val cases = listOf(
            "dont" to "don't",
            "cant" to "can't",
            "wont" to "won't",
            "didnt" to "didn't",
            "im" to "I'm",
            "youre" to "you're"
        )
        for ((keys, expected) in cases) {
            val top = decoder.decodeGesture(swipeKeys(keys)).firstOrNull()?.word
            assertEquals("Expected '$expected' for '$keys'", expected, top)
        }
    }
}
