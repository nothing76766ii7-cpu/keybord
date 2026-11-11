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

    data class BucketEntry(
        val word: String,
        val dedupChars: List<Char>,
        var frequency: Float
    )

    val root = Node()
    val endpointBuckets = HashMap<Pair<Char, Char>, MutableList<BucketEntry>>()
    private val wordFrequencies = HashMap<String, Float>()
    private val correctionBuckets = HashMap<Pair<Int, Char>, MutableList<String>>()
    var wordCount = 0
        private set
    var maxFrequency = 1000f
        private set

    /**
     * Inserts a word with a base frequency into the Trie and SHARK2 endpoint buckets.
     * Indexes over ASCII alphabetic key characters so apostrophe contractions (e.g. don't, I'm)
     * are natively swipeable without needing to lift onto an apostrophe key.
     */
    @Synchronized
    fun insert(word: String, frequency: Float = 1.0f) {
        if (word.isBlank()) return
        val cleanWord = word.trim().lowercase()
        val keyChars = ArrayList<Char>(cleanWord.length)
        for (ch in cleanWord) {
            if (ch in 'a'..'z') {
                keyChars.add(ch)
            }
        }
        if (keyChars.isEmpty()) return
        val display = canonicalizeDisplayWord(cleanWord)

        root.maxSubtreeFreq = maxOf(root.maxSubtreeFreq, frequency)
        var current = root
        for (ch in keyChars) {
            current = current.children.getOrPut(ch) { Node() }
            current.maxSubtreeFreq = maxOf(current.maxSubtreeFreq, frequency)
        }
        val isNew = !current.isWord
        if (isNew) {
            wordCount++
        }
        if (keyChars.size >= 2) {
            val dedup = ArrayList<Char>(keyChars.size)
            for (ch in keyChars) {
                if (dedup.isEmpty() || dedup.last() != ch) {
                    dedup.add(ch)
                }
            }
            if (dedup.size >= 2) {
                val keyPair = keyChars.first() to keyChars.last()
                val bucket = endpointBuckets.getOrPut(keyPair) { ArrayList() }
                val existing = bucket.firstOrNull { it.word == display }
                if (existing != null) {
                    existing.frequency = maxOf(existing.frequency, frequency)
                } else {
                    bucket.add(BucketEntry(display, dedup, frequency))
                }
            }
        }
        val prevFreq = wordFrequencies[display]
        wordFrequencies[display] = maxOf(prevFreq ?: 0f, frequency)
        wordFrequencies[cleanWord] = maxOf(wordFrequencies[cleanWord] ?: 0f, frequency)
        if (prevFreq == null && !display.contains('\'') && display.length >= 2 && display[0] in 'a'..'z') {
            correctionBuckets.getOrPut(display.length to display[0]) { ArrayList() }.add(display)
        }

        current.isWord = true
        val prevNodeFreq = current.frequency
        current.frequency = maxOf(prevNodeFreq, frequency)
        maxFrequency = maxOf(maxFrequency, current.frequency)
        if (isNew || frequency >= prevNodeFreq) {
            current.word = display
        }
    }

    /**
     * Checks if a word or contraction exists in the dictionary.
     */
    @Synchronized
    fun contains(word: String): Boolean {
        val clean = word.trim().lowercase()
        if (wordFrequencies.containsKey(clean) || wordFrequencies.containsKey(canonicalizeDisplayWord(clean))) {
            return true
        }
        var current = root
        for (ch in clean) {
            if (ch !in 'a'..'z') continue
            current = current.children[ch] ?: return false
        }
        return current.isWord
    }

    /**
     * Retrieves the unigram frequency of a word or contraction (or 0 if not present).
     */
    @Synchronized
    fun getFrequency(word: String): Float {
        val clean = word.trim().lowercase()
        wordFrequencies[canonicalizeDisplayWord(clean)]?.let { return it }
        wordFrequencies[clean]?.let { return it }
        var current = root
        for (ch in clean) {
            if (ch !in 'a'..'z') continue
            current = current.children[ch] ?: return 0f
        }
        return if (current.isWord) current.frequency else 0f
    }

    @Synchronized
    fun clear() {
        root.children.clear()
        root.isWord = false
        root.frequency = 0f
        root.maxSubtreeFreq = 0f
        root.word = null
        endpointBuckets.clear()
        wordFrequencies.clear()
        correctionBuckets.clear()
        wordCount = 0
        maxFrequency = 1000f
    }

    /**
     * Returns top-frequency prefix completions from the static dictionary + synced personal/learned words.
     */
    @Synchronized
    fun searchPrefix(prefix: String, limit: Int = 5): List<Pair<String, Float>> {
        val clean = prefix.trim().lowercase().filter { it in 'a'..'z' }
        if (clean.isEmpty()) return emptyList()
        var node = root
        for (ch in clean) {
            node = node.children[ch] ?: return emptyList()
        }
        val matches = ArrayList<Pair<String, Float>>(16)
        val pq = java.util.PriorityQueue<Node>(32) { a, b -> b.maxSubtreeFreq.compareTo(a.maxSubtreeFreq) }
        pq.add(node)
        var visited = 0
        while (pq.isNotEmpty() && visited < 160) {
            val curr = pq.poll() ?: break
            visited++
            val w = curr.word
            if (curr.isWord && w != null) {
                matches.add(w to curr.frequency)
            }
            for (child in curr.children.values) {
                if (matches.size >= limit * 3 && child.maxSubtreeFreq < (matches.lastOrNull()?.second ?: 0f)) {
                    continue
                }
                pq.add(child)
            }
        }
        matches.sortByDescending { it.second }
        return matches.distinctBy { it.first }.take(limit)
    }

    /**
     * Fast bucketed edit-distance <= 1 spell correction (substitution, insertion, deletion, adjacent transposition)
     * plus common English & Hinglish shorthand typo map. Never alters known English/Hinglish words.
     */
    @Synchronized
    fun findBestCorrection(rawWord: String, hinglishEnabled: Boolean = true): String? {
        val lower = rawWord.trim().lowercase()
        if (lower.length < 2 || contains(lower)) return null
        if (!lower.all { it in 'a'..'z' }) return null

        COMMON_TYPO_MAP[lower]?.let { mapped ->
            if (contains(mapped)) return preserveInputCase(rawWord, mapped)
        }

        // Preserve plausible Romanized Hindi / Hinglish phonetics rather than forcing English substitutions
        if (hinglishEnabled && isLikelyIntentionalHinglish(lower)) {
            return null
        }

        val targetLen = lower.length
        var bestWord: String? = null
        var bestScore = -1f
        val c0 = lower[0]
        val c1 = if (lower.length > 1) lower[1] else c0
        val startChars = if (c1 != c0) charArrayOf(c0, c1) else charArrayOf(c0)

        for (len in (targetLen - 1).coerceAtLeast(2)..(targetLen + 1)) {
            for (sc in startChars) {
                val bucket = correctionBuckets[len to sc] ?: continue
                var checked = 0
                for (candidate in bucket) {
                    if (checked++ > 220) break
                    val freq = wordFrequencies[candidate] ?: continue
                    if (freq < 250f) continue

                    if (isOneEditAway(lower, candidate)) {
                        val boost = if (candidate[0] == c0) 1.35f else 1.0f
                        val score = freq * boost
                        if (score > bestScore) {
                            bestScore = score
                            bestWord = candidate
                        }
                    }
                }
            }
        }
        return bestWord?.let { preserveInputCase(rawWord, it) }
    }

    fun isLikelyIntentionalHinglish(word: String): Boolean {
        val lower = word.trim().lowercase()
        if (HINGLISH_PROTECTED.contains(lower)) return true
        // Common Latinized Hindi long vowels & unambiguous Hinglish endings
        if (lower.contains("aa") || lower.contains("ee") || lower.contains("oo") || lower.contains("ii")) return true
        val suffixes = arrayOf("wala", "wali", "wale", "mein", "haan", "nhi", "kya", "hoon")
        if (lower.length >= 4 && suffixes.any { lower.endsWith(it) }) return true
        return false
    }

    private fun isOneEditAway(a: String, b: String): Boolean {
        val la = a.length
        val lb = b.length
        if (la == lb) {
            var diff1 = -1
            var diff2 = -1
            for (i in 0 until la) {
                if (a[i] != b[i]) {
                    if (diff1 < 0) diff1 = i
                    else if (diff2 < 0) diff2 = i
                    else return false
                }
            }
            if (diff2 < 0) return diff1 >= 0 // 1 substitution
            // Adjacent transposition ("teh" <-> "the", "bhia" <-> "bhai")
            return diff2 == diff1 + 1 && a[diff1] == b[diff2] && a[diff2] == b[diff1]
        }
        val shorter = if (la < lb) a else b
        val longer = if (la < lb) b else a
        var i = 0
        var j = 0
        var skipped = false
        while (i < shorter.length && j < longer.length) {
            if (shorter[i] == longer[j]) {
                i++
                j++
            } else {
                if (skipped) return false
                skipped = true
                j++
            }
        }
        return true
    }

    private fun preserveInputCase(original: String, corrected: String): String {
        return when {
            original.all { it.isUpperCase() } -> corrected.uppercase()
            original.firstOrNull()?.isUpperCase() == true -> corrected.replaceFirstChar { it.uppercase() }
            else -> corrected
        }
    }

    companion object {
        private val COMMON_TYPO_MAP = mapOf(
            "teh" to "the", "recieve" to "receive", "seperate" to "separate",
            "definately" to "definitely", "untill" to "until", "occured" to "occurred",
            "wierd" to "weird", "acheive" to "achieve", "tommorrow" to "tomorrow",
            "bhia" to "bhai", "kyaa" to "kya", "nhi" to "nahi",
            "thk" to "theek", "haann" to "haan", "achaa" to "achha"
        )

        private val HINGLISH_PROTECTED = setOf(
            "kal", "aaj", "par", "hai", "hain", "aana", "jaana", "baad", "mein", "mujhe",
            "pasand", "bahut", "theek", "thik", "accha", "achha", "kya", "kyun", "nahi",
            "haan", "bhai", "yaar", "dost", "tujhe", "humein", "kuch", "sab", "yahan",
            "wahan", "abhi", "phir", "lekin", "magar", "kyunki", "matlab", "bilkul"
        )

        private val HINGLISH_FALLBACK = mapOf(
            "hai" to 920f, "hain" to 905f, "kya" to 925f, "kyun" to 880f, "kyu" to 870f,
            "nahi" to 920f, "nhi" to 880f, "haan" to 915f, "bhai" to 920f, "yaar" to 915f,
            "theek" to 915f, "thik" to 885f, "achha" to 910f, "accha" to 910f, "bahut" to 915f,
            "badiya" to 895f, "badhiya" to 895f, "mast" to 890f, "kaise" to 910f, "kahan" to 905f,
            "kab" to 895f, "kaun" to 890f, "chalo" to 905f, "karo" to 895f, "raha" to 895f,
            "hoon" to 905f, "mein" to 895f, "tum" to 905f, "aap" to 905f, "hum" to 890f,
            "woh" to 890f, "yeh" to 895f, "sab" to 905f, "kuch" to 905f, "namaste" to 900f,
            "shukriya" to 895f, "abhi" to 905f, "kal" to 900f, "aaj" to 905f, "ghar" to 905f,
            "kaam" to 900f, "khana" to 895f, "pani" to 895f, "chai" to 900f, "pyaar" to 895f,
            "dost" to 895f, "zindagi" to 895f, "khush" to 890f, "matlab" to 905f, "bilkul" to 905f,
            "zaroor" to 895f, "jaldi" to 900f, "thoda" to 895f, "zyada" to 895f, "sahi" to 905f,
            "galat" to 895f, "chahiye" to 900f, "mera" to 905f, "meri" to 900f, "tera" to 895f,
            "apna" to 900f, "saath" to 895f, "hamesha" to 895f, "milte" to 895f, "batao" to 895f,
            "mujhe" to 920f, "pasand" to 905f, "par" to 895f, "aana" to 885f, "baad" to 895f
        )

        private val NEXT_WORD_HINTS = mapOf(
            "kya" to listOf("hai", "haal", "kar"),
            "theek" to listOf("hai", "hoon", "tha"),
            "thik" to listOf("hai", "hoon", "tha"),
            "koi" to listOf("baat", "nahi", "kuch"),
            "bahut" to listOf("pasand", "achha", "badiya"),
            "pasand" to listOf("hai", "aaya", "tha"),
            "baad" to listOf("mein", "aana", "milte"),
            "mujhe" to listOf("this", "bahut", "bhi"),
            "sab" to listOf("theek", "badhiya", "kuch"),
            "kahan" to listOf("ho", "hai", "ja"),
            "kaise" to listOf("ho", "hain", "hai"),
            "kal" to listOf("meeting", "milte", "subah"),
            "aaj" to listOf("weather", "kya", "ka"),
            "time" to listOf("par", "hai", "pe"),
            "par" to listOf("aana", "hai", "milte"),
            "chalo" to listOf("theek", "phir", "ghar"),
            "bhai" to listOf("kya", "kaise", "yaar"),
            "yaar" to listOf("kya", "chal", "bhai"),
            "main" to listOf("theek", "ghar", "abhi"),
            "aap" to listOf("kaise", "kahan", "theek"),
            "haan" to listOf("bhai", "theek", "bilkul"),
            "nahi" to listOf("bhai", "yaar", "hai"),
            "good" to listOf("morning", "night", "luck"),
            "thank" to listOf("you", "god", "you!"),
            "see" to listOf("you", "the", "that"),
            "how" to listOf("are", "about", "is"),
            "can" to listOf("you", "help", "we"),
            "help" to listOf("me", "you", "with"),
            "will" to listOf("call", "be", "see"),
            "call" to listOf("you", "me", "back"),
            "i" to listOf("will", "am", "have"),
            "i'm" to listOf("good", "theek", "here")
        )

        fun getContextualFallbackPredictions(prevWord: String): List<String> =
            NEXT_WORD_HINTS[prevWord.lowercase().trim()] ?: emptyList()

        fun canonicalizeDisplayWord(cleanLower: String): String = when (cleanLower) {
            "i" -> "I"
            "i'm" -> "I'm"
            "i've" -> "I've"
            "i'll" -> "I'll"
            "i'd" -> "I'd"
            else -> cleanLower
        }
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
     * Loads lexicon from assets/wordlist.txt and optional assets/wordlist_hinglish.txt.
     */
    fun loadFromAssets(context: Context, includeHinglish: Boolean = true) {
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
        if (includeHinglish) {
            val hinglishLoaded = try {
                context.assets.open("wordlist_hinglish.txt")
                    .bufferedReader()
                    .useLines { lines -> loadFromSequence(lines) }
            } catch (_: Exception) {
                0
            }
            if (hinglishLoaded == 0) {
                loadHinglishDefaults()
            }
        }
    }

    fun loadHinglishDefaults() {
        for ((word, freq) in HINGLISH_FALLBACK) {
            insert(word, freq)
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
