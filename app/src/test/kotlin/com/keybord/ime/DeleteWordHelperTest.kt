package com.keybord.ime

import org.junit.Assert.*
import org.junit.Test

class DeleteWordHelperTest {

    @Test
    fun testSingleCharAndSurrogatePairCount() {
        assertEquals(1, DeleteWordHelper.computeSingleCharDeleteCount(""))
        assertEquals(1, DeleteWordHelper.computeSingleCharDeleteCount("hello"))
        assertEquals(2, DeleteWordHelper.computeSingleCharDeleteCount("hi 😀"))
        assertEquals(1, DeleteWordHelper.computeSingleCharDeleteCount("😀a"))
    }

    @Test
    fun testWordDeleteLengthBasicAndSpacing() {
        assertEquals(0, DeleteWordHelper.computeWordDeleteLength(""))
        assertEquals(5, DeleteWordHelper.computeWordDeleteLength("hello"))
        assertEquals(5, DeleteWordHelper.computeWordDeleteLength("hello world"))
        assertEquals(6, DeleteWordHelper.computeWordDeleteLength("hello world "))
        assertEquals(4, DeleteWordHelper.computeWordDeleteLength("    "))
    }

    @Test
    fun testWordDeletePreservesApostropheContractionsAndNewlines() {
        assertEquals(5, DeleteWordHelper.computeWordDeleteLength("I don't"))
        assertEquals(3, DeleteWordHelper.computeWordDeleteLength("hi I’m"))
        assertEquals(1, DeleteWordHelper.computeWordDeleteLength("hello\n"))
        assertEquals(3, DeleteWordHelper.computeWordDeleteLength("hello\n   "))
        assertEquals(4, DeleteWordHelper.computeWordDeleteLength("hi 'test"))
    }

    @Test
    fun testWordBoundariesWalkToEmptyCleanly() {
        val text = "one two three four"
        val boundaries = DeleteWordHelper.computeWordBoundariesBeforeCursor(text)

        assertEquals(text.length, boundaries.first())
        assertEquals(0, boundaries.last())
        assertEquals(5, boundaries.size) // [len, before "four", before "three ", before "two ", 0]
        assertEquals("one two three ", text.substring(0, boundaries[1]))
        assertEquals("one two ", text.substring(0, boundaries[2]))
        assertEquals("one ", text.substring(0, boundaries[3]))
        assertEquals("", text.substring(0, boundaries[4]))

        // Verify strictly monotonic decrease
        for (i in 0 until boundaries.size - 1) {
            assertTrue(boundaries[i] > boundaries[i + 1])
        }
    }

    @Test
    fun testPunctuationAndMaxWordsClamp() {
        val punct = "hello, world!!!"
        val b = DeleteWordHelper.computeWordBoundariesBeforeCursor(punct)
        assertEquals("hello, world", punct.substring(0, b[1]))
        assertEquals("hello, ", punct.substring(0, b[2]))
        assertEquals("hello", punct.substring(0, b[3]))
        assertEquals("", punct.substring(0, b[4]))

        val clamped = DeleteWordHelper.computeWordBoundariesBeforeCursor("a b c d e", maxWords = 2)
        assertEquals(3, clamped.size)
    }
}
