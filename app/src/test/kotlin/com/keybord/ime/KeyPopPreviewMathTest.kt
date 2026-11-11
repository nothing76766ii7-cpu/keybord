package com.keybord.ime

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for KeyPopPreviewView popup caret clamping across narrow keys,
 * large rounded corners (e.g. up to 26dp), and edge positions.
 */
class KeyPopPreviewMathTest {

    @Test
    fun testClampCaretXNeverThrowsWhenRadiusExceedsHalfPopupWidth() {
        // Reproduces exact crash geometry from SDK 33 trace where popRect.left + radius > popRect.right - radius
        val popLeft = 700f
        val popRight = 802.6939f
        val radius = 79.02655f // 2 * radius (~158px) > popWidth (~102.7px)
        val caretHalfWidth = 18f

        val caretX = KeyPopPreviewView.clampCaretX(
            centerX = 751.34f,
            popLeft = popLeft,
            popRight = popRight,
            radius = radius,
            caretHalfWidth = caretHalfWidth
        )
        assertTrue("caretX ($caretX) must stay within [$popLeft, $popRight]", caretX in popLeft..popRight)
        assertTrue(caretX.isFinite())
    }

    @Test
    fun testClampCaretXHandlesZeroWidthAndEdgeKeys() {
        val zeroWidthResult = KeyPopPreviewView.clampCaretX(
            centerX = 500f,
            popLeft = 500f,
            popRight = 500f,
            radius = 40f,
            caretHalfWidth = 12f
        )
        assertTrue(zeroWidthResult == 500f)

        val leftEdgeResult = KeyPopPreviewView.clampCaretX(
            centerX = 8f,
            popLeft = 12f,
            popRight = 140f,
            radius = 28f,
            caretHalfWidth = 16f
        )
        assertTrue(leftEdgeResult in 12f..140f)

        val rightEdgeResult = KeyPopPreviewView.clampCaretX(
            centerX = 1070f,
            popLeft = 940f,
            popRight = 1068f,
            radius = 28f,
            caretHalfWidth = 16f
        )
        assertTrue(rightEdgeResult in 940f..1068f)
    }
}
