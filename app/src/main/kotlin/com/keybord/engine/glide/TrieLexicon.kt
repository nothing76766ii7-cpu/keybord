package com.keybord.engine.glide

import android.content.Context

/**
 * Prefix tree (Trie) for dictionary candidate lookups and beam pruning.
 */
class TrieLexicon {

    class Node {
        val children = HashMap<Char, Node>()
        var isWord: Boolean = false
        var frequency: Float = 0f
        var maxSubtreeFreq: Float = 0f
        var word: String? = null
    }

    val root = Node()
    var wordCount = 0
        private set
    var maxFrequency = 1000f
        private set

    /**
     * Inserts a word with a base frequency into the Trie, propagating subtree max frequency.
     */
    fun insert(word: String, frequency: Float = 1.0f) {
        if (word.isBlank()) return
        val cleanWord = word.trim().lowercase()
        root.maxSubtreeFreq = maxOf(root.maxSubtreeFreq, frequency)
        var current = root
        for (char in cleanWord) {
            current = current.children.getOrPut(char) { Node() }
            current.maxSubtreeFreq = maxOf(current.maxSubtreeFreq, frequency)
        }
        if (!current.isWord) {
            wordCount++
        }
        current.isWord = true
        current.frequency = maxOf(current.frequency, frequency)
        maxFrequency = maxOf(maxFrequency, current.frequency)
        current.word = cleanWord
    }

    /**
     * Checks if a word exists in the dictionary.
     */
    fun contains(word: String): Boolean {
        var current = root
        for (char in word.lowercase()) {
            current = current.children[char] ?: return false
        }
        return current.isWord
    }

    /**
     * Retrieves the unigram frequency of a word (or 0 if not present).
     */
    fun getFrequency(word: String): Float {
        var current = root
        for (char in word.lowercase()) {
            current = current.children[char] ?: return 0f
        }
        return if (current.isWord) current.frequency else 0f
    }

    /**
     * Loads tab-separated `word<TAB>frequency` or rank-ordered one-word-per-line entries.
     */
    fun loadFromSequence(lines: Sequence<String>): Int {
        var rank = 0
        var inserted = 0
        for (raw in lines) {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) continue
            rank++
            val parts = trimmed.split('\t')
            val word = parts.getOrNull(0) ?: continue
            val freq = parts.getOrNull(1)?.trim()?.toFloatOrNull()
                ?: (10000 - rank).coerceAtLeast(1).toFloat()
            if (word.isNotEmpty()) {
                insert(word, freq)
                inserted++
            }
        }
        return inserted
    }

    /**
     * Loads lexicon from assets/wordlist.txt, falling back to starter vocabulary only if missing/empty.
     */
    fun loadFromAssets(context: Context) {
        val loaded = try {
            context.assets.open("wordlist.txt")
                .bufferedReader()
                .useLines { lines -> loadFromSequence(lines) }
        } catch (_: Exception) {
            0
        }
        if (loaded == 0) {
            loadDefaultVocabulary()
        }
    }

    /**
     * Helper to populate standard English starter vocabulary.
     */
    fun loadDefaultVocabulary() {
        val defaultWords = mapOf(
            "the" to 1000f, "be" to 800f, "to" to 750f, "of" to 700f, "and" to 680f,
            "a" to 650f, "in" to 600f, "that" to 550f, "have" to 500f, "i" to 480f,
            "it" to 470f, "for" to 460f, "not" to 450f, "on" to 440f, "with" to 430f,
            "he" to 420f, "as" to 410f, "you" to 400f, "do" to 390f, "at" to 380f,
            "this" to 370f, "but" to 360f, "his" to 350f, "by" to 340f, "from" to 330f,
            "they" to 320f, "we" to 310f, "say" to 300f, "her" to 290f, "she" to 280f,
            "or" to 270f, "an" to 260f, "will" to 250f, "my" to 240f, "one" to 230f,
            "all" to 220f, "would" to 210f, "there" to 200f, "their" to 190f, "what" to 180f,
            "so" to 170f, "up" to 160f, "out" to 150f, "if" to 140f, "about" to 130f,
            "who" to 120f, "get" to 110f, "which" to 100f, "go" to 95f, "me" to 90f,
            "when" to 85f, "make" to 80f, "can" to 75f, "like" to 70f, "time" to 68f,
            "no" to 65f, "just" to 62f, "him" to 60f, "know" to 58f, "take" to 55f,
            "people" to 52f, "into" to 50f, "year" to 48f, "your" to 46f, "good" to 44f,
            "some" to 42f, "could" to 40f, "them" to 38f, "see" to 36f, "other" to 34f,
            "than" to 32f, "then" to 30f, "now" to 28f, "look" to 26f, "only" to 24f,
            "come" to 22f, "its" to 20f, "over" to 18f, "think" to 16f, "also" to 14f,
            "back" to 12f, "after" to 10f, "use" to 8f, "two" to 6f, "how" to 5f,
            "our" to 5f, "work" to 4f, "first" to 4f, "well" to 4f, "way" to 3f,
            "even" to 3f, "new" to 3f, "want" to 3f, "because" to 3f, "any" to 2f,
            "these" to 2f, "give" to 2f, "day" to 2f, "most" to 2f, "us" to 2f,
            "hello" to 250f, "keyboard" to 150f, "glide" to 120f, "typing" to 140f,
            "android" to 200f, "google" to 250f, "world" to 180f, "quick" to 100f,
            "brown" to 80f, "fox" to 70f, "jumps" to 60f, "lazy" to 50f, "dog" to 50f,
            "keybord" to 300f, "custom" to 200f, "theme" to 180f, "clipboard" to 220f,
            "fast" to 150f, "swipe" to 140f, "great" to 250f, "love" to 300f, "thanks" to 280f
        )
        for ((word, freq) in defaultWords) {
            insert(word, freq)
        }
        listOf(
            "was", "are", "had", "were", "said", "each", "many", "write", "long", "thing",
            "more", "did", "number", "sound", "water", "call", "may", "down", "side", "been",
            "find", "part", "place", "made", "where", "little", "round", "man", "came", "show",
            "every", "under", "name", "very", "through", "form", "much", "help", "low", "line",
            "before", "turn", "cause", "same", "mean", "differ", "move", "right", "boy", "old",
            "too", "does", "tell", "sentence", "set", "three", "air", "play", "small", "end",
            "put", "home", "read", "hand", "port", "large", "spell", "add", "land", "here",
            "must", "big", "high", "such", "follow", "act", "why", "ask", "men", "change",
            "went", "light", "kind", "off", "need", "house", "picture", "try", "again", "animal",
            "point", "mother", "near", "build", "self", "earth", "father", "head", "stand", "own",
            "page", "open", "seem", "together", "next", "white", "children", "begin", "got",
            "walk", "example", "ease", "paper", "often", "always", "music", "those", "both",
            "mark", "book", "letter", "until", "mile", "river", "car", "feet", "care", "second",
            "enough", "plain", "girl", "usual", "young", "ready", "above", "ever", "red", "list",
            "though", "feel", "talk", "bird", "soon", "body", "family", "direct", "pose", "leave",
            "song", "measure", "door", "product", "black", "short", "numeral", "class", "wind",
            "question", "happen", "complete", "ship", "area", "half", "rock", "order", "fire",
            "south", "problem", "piece", "told", "knew", "pass", "top", "whole", "king", "space",
            "heard", "best", "hour", "better", "true", "during", "hundred", "five", "remember",
            "step", "early", "hold", "west", "ground", "interest", "reach", "verb", "sing",
            "listen", "six", "table", "travel", "less", "morning", "ten", "simple", "several",
            "vowel", "toward", "power", "town", "fine", "drive", "sang", "close", "dark",
            "grown", "draw", "yet", "left", "late", "real", "run", "case", "stop", "raise",
            "base", "sense"
        ).forEach { w -> insert(w, 200f) }
    }
}
