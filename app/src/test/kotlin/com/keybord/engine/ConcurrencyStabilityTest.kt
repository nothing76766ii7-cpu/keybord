package com.keybord.engine

import android.graphics.RectF
import com.keybord.engine.glide.GlideDecoder
import com.keybord.engine.glide.KeyPosition
import com.keybord.engine.glide.TouchPoint
import com.keybord.engine.glide.TrieLexicon
import com.keybord.engine.vocab.PersonalDictionaryManager
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Multi-threaded concurrency stress tests covering continuous multiline typing,
 * background dictionary sync, SHARK2 glide decoding, and personal dictionary mutations.
 */
class ConcurrencyStabilityTest {

    private fun buildQwertyPositions(): Map<Char, KeyPosition> {
        val rows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
        val map = HashMap<Char, KeyPosition>()
        rows.forEachIndexed { rowIdx, chars ->
            chars.forEachIndexed { colIdx, ch ->
                val x = 50f + colIdx * 90f + rowIdx * 25f
                val y = 60f + rowIdx * 110f
                map[ch] = KeyPosition(
                    character = ch,
                    bounds = RectF(x - 43f, y - 52f, x + 43f, y + 52f),
                    centerX = x,
                    centerY = y
                )
            }
        }
        return map
    }

    @Test
    fun testTrieLexiconConcurrentReadWriteAndGlideDecode() {
        val lexicon = TrieLexicon().apply { loadDefaultVocabulary() }
        val positions = buildQwertyPositions()
        val decoder = GlideDecoder(positions, lexicon)

        // Swipe trajectory spanning h -> e -> l -> o plus NaN resilience test
        val validSwipe = listOf(
            TouchPoint(525f, 170f, 100L),
            TouchPoint(250f, 60f, 150L),
            TouchPoint(795f, 170f, 210L),
            TouchPoint(770f, 60f, 270L)
        )
        val mixedNaNSwipe = listOf(
            TouchPoint(Float.NaN, 170f, 100L),
            TouchPoint(525f, 170f, 120L),
            TouchPoint(770f, 60f, 200L)
        )

        val workers = 4
        val pool = Executors.newFixedThreadPool(workers)
        val startGate = CountDownLatch(1)
        val doneLatch = CountDownLatch(workers)
        val errors = Collections.synchronizedList(mutableListOf<Throwable>())

        // Thread 1: heavy dictionary & user vocabulary mutation (simulates syncUserVocabularyInto)
        pool.submit {
            try {
                startGate.await()
                for (i in 0 until 800) {
                    lexicon.insert("word$i", (i % 900 + 10).toFloat())
                    lexicon.insert("bahut", 700f)
                    if (i % 50 == 0) {
                        lexicon.insert("nanword", Float.NaN)
                        lexicon.loadHinglishDefaults()
                    }
                }
            } catch (t: Throwable) {
                errors.add(t)
            } finally {
                doneLatch.countDown()
            }
        }

        // Thread 2 & 3: continuous typing prefix + autocorrect lookups
        repeat(2) { workerIdx ->
            pool.submit {
                try {
                    startGate.await()
                    val queries = listOf("he", "bah", "kal", "wor", "th", "muj", "key")
                    for (i in 0 until 800) {
                        val q = queries[(i + workerIdx) % queries.size]
                        lexicon.contains(q)
                        lexicon.getFrequency(q)
                        lexicon.searchPrefix(q, limit = 5)
                        lexicon.findBestCorrection("teh", hinglishEnabled = true)
                    }
                } catch (t: Throwable) {
                    errors.add(t)
                } finally {
                    doneLatch.countDown()
                }
            }
        }

        // Thread 4: concurrent SHARK2 glide decoding on bucket snapshots
        pool.submit {
            try {
                startGate.await()
                for (i in 0 until 400) {
                    decoder.decodeGesture(validSwipe, lastTapTs = 0L)
                    if (i % 10 == 0) {
                        decoder.decodeGesture(mixedNaNSwipe, lastTapTs = 0L)
                    }
                }
            } catch (t: Throwable) {
                errors.add(t)
            } finally {
                doneLatch.countDown()
            }
        }

        startGate.countDown()
        assertTrue("Workers timed out", doneLatch.await(15, TimeUnit.SECONDS))
        pool.shutdownNow()
        assertTrue("Unexpected concurrency exceptions: $errors", errors.isEmpty())
    }

    @Test
    fun testPersonalDictionaryConcurrentMutationAndLookup() {
        val dict = PersonalDictionaryManager(context = null)
        val workers = 4
        val pool = Executors.newFixedThreadPool(workers)
        val startGate = CountDownLatch(1)
        val doneLatch = CountDownLatch(workers)
        val errors = Collections.synchronizedList(mutableListOf<Throwable>())

        // Mutator threads (adds, deletes, syncs)
        repeat(2) { id ->
            pool.submit {
                try {
                    startGate.await()
                    for (i in 0 until 600) {
                        val w = "custom_${id}_${i % 80}"
                        dict.addWord(w, shortcut = "sc${i % 20}", frequency = 250)
                        if (i % 4 == 0) {
                            dict.deleteWord("custom_${id}_${(i - 1).coerceAtLeast(0) % 80}")
                        }
                        if (i % 25 == 0) {
                            dict.syncFromSystem(null)
                        }
                    }
                } catch (t: Throwable) {
                    errors.add(t)
                } finally {
                    doneLatch.countDown()
                }
            }
        }

        // Reader threads (contains, shortcut lookup, getAllPersonalWords snapshots)
        repeat(2) {
            pool.submit {
                try {
                    startGate.await()
                    for (i in 0 until 800) {
                        dict.contains("custom_0_${i % 80}")
                        dict.getShortcutExpansion("sc${i % 20}")
                        dict.getAllPersonalWords().size
                    }
                } catch (t: Throwable) {
                    errors.add(t)
                } finally {
                    doneLatch.countDown()
                }
            }
        }

        startGate.countDown()
        assertTrue("Workers timed out", doneLatch.await(15, TimeUnit.SECONDS))
        pool.shutdownNow()
        assertTrue("Unexpected personal dict concurrency exceptions: $errors", errors.isEmpty())
    }
}
