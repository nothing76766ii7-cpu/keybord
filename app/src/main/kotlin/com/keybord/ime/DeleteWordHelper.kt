package com.keybord.ime

/**
 * Pure word-boundary and surrogate-safe backspace calculator used by both
 * long-press accelerating word deletion and Gboard-style swipe-to-delete.
 */
object DeleteWordHelper {

    /**
     * Returns the UTF-16 code unit count to delete for a single backspace tap/repeat:
     * returns 2 when immediately preceded by a surrogate pair (e.g., emoji), else 1.
     */
    fun computeSingleCharDeleteCount(beforeCursor: CharSequence?): Int {
        if (beforeCursor.isNullOrEmpty()) return 1
        val len = beforeCursor.length
        if (len >= 2 &&
            Character.isLowSurrogate(beforeCursor[len - 1]) &&
            Character.isHighSurrogate(beforeCursor[len - 2])
        ) {
            return 2
        }
        return 1
    }

    /**
     * Returns the UTF-16 character length to delete for one whole word / token before cursor:
     * - Consumes trailing spaces/tabs first, then consumes either a contiguous word token
     *   (letters/digits/underscore + internal apostrophes), a punctuation run, or a single newline.
     */
    fun computeWordDeleteLength(beforeCursor: CharSequence): Int {
        val len = beforeCursor.length
        if (len == 0) return 0

        var idx = len
        if (beforeCursor[idx - 1] == '\n') {
            return 1
        }

        while (idx > 0 && isHorizontalSpace(beforeCursor[idx - 1])) {
            idx--
        }
        if (idx == 0) {
            return len
        }
        if (beforeCursor[idx - 1] == '\n') {
            // Trailing spaces after a newline: remove the spaces first without swallowing the previous line
            return len - idx
        }

        val last = beforeCursor[idx - 1]
        if (isWordCore(last)) {
            while (idx > 0) {
                val c = beforeCursor[idx - 1]
                val isInternalApostrophe = (c == '\'' || c == '’') && idx >= 2 && isWordCore(beforeCursor[idx - 2])
                if (isWordCore(c) || isInternalApostrophe) {
                    idx--
                } else {
                    break
                }
            }
        } else {
            while (idx > 0) {
                val c = beforeCursor[idx - 1]
                if (!isHorizontalSpace(c) && c != '\n' && !isWordCore(c)) {
                    idx--
                } else {
                    break
                }
            }
        }
        return (len - idx).coerceAtLeast(1)
    }

    /**
     * Computes progressive cursor start offsets `[len, offsetAfter1Word, offsetAfter2Words, ...]`
     * walking backward from the end of `beforeCursor`. Index `k` is the selection start for `k` words.
     */
    fun computeWordBoundariesBeforeCursor(beforeCursor: CharSequence, maxWords: Int = 40): IntArray {
        val offsets = ArrayList<Int>(maxWords + 1)
        var pos = beforeCursor.length
        offsets.add(pos)
        while (pos > 0 && offsets.size <= maxWords) {
            val delLen = computeWordDeleteLength(beforeCursor.subSequence(0, pos))
            if (delLen <= 0) break
            pos = (pos - delLen).coerceAtLeast(0)
            offsets.add(pos)
        }
        return offsets.toIntArray()
    }

    private fun isHorizontalSpace(ch: Char): Boolean = ch == ' ' || ch == '\t'

    private fun isWordCore(ch: Char): Boolean = ch.isLetterOrDigit() || ch == '_'
}
