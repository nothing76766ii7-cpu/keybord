package com.keybord.engine.glide

/**
 * Prefix tree (Trie) for dictionary candidate lookups and beam pruning.
 */
class TrieLexicon {

    class Node {
        val children = HashMap<Char, Node>()
        var isWord: Boolean = false
        var frequency: Float = 0f
        var word: String? = null
    }

    val root = Node()
    var wordCount = 0
        private set

    /**
     * Inserts a word with a base frequency into the Trie.
     */
    fun insert(word: String, frequency: Float = 1.0f) {
        if (word.isBlank()) return
        val cleanWord = word.trim().lowercase()
        var current = root
        for (char in cleanWord) {
            current = current.children.getOrPut(char) { Node() }
        }
        current.isWord = true
        current.frequency = maxOf(current.frequency, frequency)
        current.word = cleanWord
        wordCount++
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
            "hello" to 25f, "keyboard" to 15f, "glide" to 12f, "typing" to 14f,
            "android" to 20f, "google" to 25f, "world" to 18f, "quick" to 10f,
            "brown" to 8f, "fox" to 7f, "jumps" to 6f, "lazy" to 5f, "dog" to 5f,
            "keybord" to 30f, "custom" to 20f, "theme" to 18f, "clipboard" to 22f,
            "fast" to 15f, "swipe" to 14f, "great" to 25f, "love" to 30f, "thanks" to 28f
        )
        for ((word, freq) in defaultWords) {
            insert(word, freq)
        }
    }
}
