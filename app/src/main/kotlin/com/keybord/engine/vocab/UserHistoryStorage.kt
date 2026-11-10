package com.keybord.engine.vocab

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlin.math.exp
import kotlin.math.ln

/**
 * SQLite-backed dynamic language model storage for user history,
 * n-grams, recency tracking, and 30-day half-life decay pruning.
 */
class UserHistoryStorage(
    context: Context?,
    private val config: AdaptiveVocabConfig = AdaptiveVocabConfig(),
    inMemory: Boolean = (context == null)
) : SQLiteOpenHelper(context, if (inMemory) null else "keybord_user_history.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE unigrams (
                word TEXT PRIMARY KEY,
                count INTEGER NOT NULL,
                last_used INTEGER NOT NULL,
                is_oov INTEGER NOT NULL
            );
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE bigrams (
                w1 TEXT NOT NULL,
                w2 TEXT NOT NULL,
                count INTEGER NOT NULL,
                last_used INTEGER NOT NULL,
                PRIMARY KEY (w1, w2)
            );
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE trigrams (
                w1 TEXT NOT NULL,
                w2 TEXT NOT NULL,
                w3 TEXT NOT NULL,
                count INTEGER NOT NULL,
                last_used INTEGER NOT NULL,
                PRIMARY KEY (w1, w2, w3)
            );
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE rejections (
                word TEXT PRIMARY KEY,
                penalty INTEGER NOT NULL
            );
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_bigrams_w1 ON bigrams (w1);")
        db.execSQL("CREATE INDEX idx_trigrams_ctx ON trigrams (w1, w2);")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS unigrams")
        db.execSQL("DROP TABLE IF EXISTS bigrams")
        db.execSQL("DROP TABLE IF EXISTS trigrams")
        db.execSQL("DROP TABLE IF EXISTS rejections")
        onCreate(db)
    }

    fun recordUnigram(word: String, countDelta: Int = 1, timestamp: Long = System.currentTimeMillis(), isOov: Boolean = false) {
        val cleanWord = word.lowercase().trim()
        if (cleanWord.isEmpty()) return
        val db = writableDatabase

        db.beginTransaction()
        try {
            val cursor = db.rawQuery("SELECT count, is_oov FROM unigrams WHERE word = ?", arrayOf(cleanWord))
            if (cursor.moveToFirst()) {
                val currentCount = cursor.getInt(0)
                val currentOov = cursor.getInt(1) == 1
                val newCount = currentCount + countDelta
                // If typed >= threshold, promote OOV
                val updatedOov = if (newCount >= config.oovPromotionThreshold) false else currentOov

                val cv = ContentValues().apply {
                    put("count", newCount)
                    put("last_used", timestamp)
                    put("is_oov", if (updatedOov) 1 else 0)
                }
                db.update("unigrams", cv, "word = ?", arrayOf(cleanWord))
            } else {
                val cv = ContentValues().apply {
                    put("word", cleanWord)
                    put("count", countDelta)
                    put("last_used", timestamp)
                    put("is_oov", if (isOov && countDelta < config.oovPromotionThreshold) 1 else 0)
                }
                db.insertWithOnConflict("unigrams", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            }
            cursor.close()
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }

        pruneUnigramsIfNeeded()
    }

    fun recordBigram(w1: String, w2: String, countDelta: Int = 1, timestamp: Long = System.currentTimeMillis()) {
        val c1 = w1.lowercase().trim()
        val c2 = w2.lowercase().trim()
        if (c1.isEmpty() || c2.isEmpty()) return
        val db = writableDatabase

        db.beginTransaction()
        try {
            val cursor = db.rawQuery("SELECT count FROM bigrams WHERE w1 = ? AND w2 = ?", arrayOf(c1, c2))
            if (cursor.moveToFirst()) {
                val currentCount = cursor.getInt(0)
                val cv = ContentValues().apply {
                    put("count", currentCount + countDelta)
                    put("last_used", timestamp)
                }
                db.update("bigrams", cv, "w1 = ? AND w2 = ?", arrayOf(c1, c2))
            } else {
                val cv = ContentValues().apply {
                    put("w1", c1)
                    put("w2", c2)
                    put("count", countDelta)
                    put("last_used", timestamp)
                }
                db.insertWithOnConflict("bigrams", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            }
            cursor.close()
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun recordTrigram(w1: String, w2: String, w3: String, countDelta: Int = 1, timestamp: Long = System.currentTimeMillis()) {
        val c1 = w1.lowercase().trim()
        val c2 = w2.lowercase().trim()
        val c3 = w3.lowercase().trim()
        if (c1.isEmpty() || c2.isEmpty() || c3.isEmpty()) return
        val db = writableDatabase

        db.beginTransaction()
        try {
            val cursor = db.rawQuery("SELECT count FROM trigrams WHERE w1 = ? AND w2 = ? AND w3 = ?", arrayOf(c1, c2, c3))
            if (cursor.moveToFirst()) {
                val currentCount = cursor.getInt(0)
                val cv = ContentValues().apply {
                    put("count", currentCount + countDelta)
                    put("last_used", timestamp)
                }
                db.update("trigrams", cv, "w1 = ? AND w2 = ? AND w3 = ?", arrayOf(c1, c2, c3))
            } else {
                val cv = ContentValues().apply {
                    put("w1", c1)
                    put("w2", c2)
                    put("w3", c3)
                    put("count", countDelta)
                    put("last_used", timestamp)
                }
                db.insertWithOnConflict("trigrams", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            }
            cursor.close()
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun recordRejection(word: String, penalty: Int = config.rejectionPenalty) {
        val cleanWord = word.lowercase().trim()
        val db = writableDatabase
        db.execSQL(
            """
            INSERT INTO rejections (word, penalty) VALUES (?, ?)
            ON CONFLICT(word) DO UPDATE SET penalty = penalty + ?
            """.trimIndent(),
            arrayOf(cleanWord, penalty, penalty)
        )
    }

    fun getRejectionPenalty(word: String): Int {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT penalty FROM rejections WHERE word = ?", arrayOf(word.lowercase().trim()))
        val penalty = if (cursor.moveToFirst()) cursor.getInt(0) else 0
        cursor.close()
        return penalty
    }

    fun getUnigram(word: String): WordEntry? {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT word, count, last_used, is_oov FROM unigrams WHERE word = ?",
            arrayOf(word.lowercase().trim())
        )
        val entry = if (cursor.moveToFirst()) {
            WordEntry(
                word = cursor.getString(0),
                count = cursor.getInt(1),
                lastUsedTimestamp = cursor.getLong(2),
                isOov = cursor.getInt(3) == 1
            )
        } else null
        cursor.close()
        return entry
    }

    fun getBigramCompletions(w1: String, limit: Int = 10): List<Pair<String, Int>> {
        val db = readableDatabase
        val list = mutableListOf<Pair<String, Int>>()
        val cursor = db.rawQuery(
            "SELECT w2, count FROM bigrams WHERE w1 = ? ORDER BY count DESC, last_used DESC LIMIT ?",
            arrayOf(w1.lowercase().trim(), limit.toString())
        )
        while (cursor.moveToNext()) {
            list.add(cursor.getString(0) to cursor.getInt(1))
        }
        cursor.close()
        return list
    }

    /**
     * Returns normalized bigram likelihood P(word | prevWord) in [0.0, 1.0].
     */
    fun getBigramScore(prevWord: String, word: String): Float {
        val c1 = prevWord.lowercase().trim()
        val c2 = word.lowercase().trim()
        if (c1.isEmpty() || c2.isEmpty()) return 0f
        return try {
            val db = readableDatabase
            val pairCount = db.rawQuery(
                "SELECT count FROM bigrams WHERE w1 = ? AND w2 = ?",
                arrayOf(c1, c2)
            ).use { cursor ->
                if (cursor.moveToFirst()) cursor.getInt(0) else 0
            }
            if (pairCount <= 0) return 0f
            val totalCount = db.rawQuery(
                "SELECT SUM(count) FROM bigrams WHERE w1 = ?",
                arrayOf(c1)
            ).use { cursor ->
                if (cursor.moveToFirst()) cursor.getInt(0) else 0
            }
            if (totalCount <= 0) return 0f
            val conditional = pairCount.toFloat() / totalCount.toFloat()
            val saturation = (pairCount.toFloat() / 4.0f).coerceIn(0f, 1f)
            (conditional * saturation).coerceIn(0f, 1f)
        } catch (_: Exception) {
            0f
        }
    }

    fun getTrigramCompletions(w1: String, w2: String, limit: Int = 10): List<Pair<String, Int>> {
        val db = readableDatabase
        val list = mutableListOf<Pair<String, Int>>()
        val cursor = db.rawQuery(
            "SELECT w3, count FROM trigrams WHERE w1 = ? AND w2 = ? ORDER BY count DESC, last_used DESC LIMIT ?",
            arrayOf(w1.lowercase().trim(), w2.lowercase().trim(), limit.toString())
        )
        while (cursor.moveToNext()) {
            list.add(cursor.getString(0) to cursor.getInt(1))
        }
        cursor.close()
        return list
    }

    fun searchPrefix(prefix: String, limit: Int = 15): List<WordEntry> {
        val cleanPrefix = prefix.lowercase().trim()
        val db = readableDatabase
        val list = mutableListOf<WordEntry>()
        val cursor = db.rawQuery(
            """
            SELECT word, count, last_used, is_oov FROM unigrams 
            WHERE word LIKE ? AND is_oov = 0 
            ORDER BY count DESC, last_used DESC LIMIT ?
            """.trimIndent(),
            arrayOf("$cleanPrefix%", limit.toString())
        )
        while (cursor.moveToNext()) {
            list.add(
                WordEntry(
                    word = cursor.getString(0),
                    count = cursor.getInt(1),
                    lastUsedTimestamp = cursor.getLong(2),
                    isOov = cursor.getInt(3) == 1
                )
            )
        }
        cursor.close()
        return list
    }

    /**
     * Deletes a word and all associated transitions from user history.
     * Used for PRD Section 4.3 candidate deletion on long-press.
     */
    fun deleteWord(word: String) {
        val clean = word.lowercase().trim()
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("unigrams", "word = ?", arrayOf(clean))
            db.delete("bigrams", "w1 = ? OR w2 = ?", arrayOf(clean, clean))
            db.delete("trigrams", "w1 = ? OR w2 = ? OR w3 = ?", arrayOf(clean, clean, clean))
            db.delete("rejections", "word = ?", arrayOf(clean))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Implements 30-day half-life exponential decay retention pruning:
     * When size exceeds maxUnigramEntries, calculates retention scores:
     * RetentionScore = Count * 2^(-age / halfLife).
     * Prunes the lowest 15% scored entries.
     */
    private fun pruneUnigramsIfNeeded() {
        val db = writableDatabase
        val countCursor = db.rawQuery("SELECT COUNT(*) FROM unigrams", null)
        var total = 0
        if (countCursor.moveToFirst()) {
            total = countCursor.getInt(0)
        }
        countCursor.close()

        if (total <= config.maxUnigramEntries) return

        val now = System.currentTimeMillis()
        val halfLifeMillis = config.halfLifeDays * 24 * 3600 * 1000f

        val cursor = db.rawQuery("SELECT word, count, last_used FROM unigrams", null)
        val scoredList = mutableListOf<Pair<String, Float>>()
        while (cursor.moveToNext()) {
            val word = cursor.getString(0)
            val count = cursor.getInt(1)
            val lastUsed = cursor.getLong(2)
            val ageMillis = (now - lastUsed).coerceAtLeast(0L)

            // Exponential half-life decay
            val decay = exp(-ln(2.0f) * (ageMillis / halfLifeMillis))
            val retentionScore = count * decay
            scoredList.add(word to retentionScore)
        }
        cursor.close()

        scoredList.sortBy { it.second }
        val pruneCount = (total * 0.15f).toInt().coerceAtLeast(1)
        val toPrune = scoredList.take(pruneCount).map { it.first }

        db.beginTransaction()
        try {
            for (word in toPrune) {
                db.delete("unigrams", "word = ?", arrayOf(word))
                db.delete("bigrams", "w1 = ? OR w2 = ?", arrayOf(word, word))
                db.delete("trigrams", "w1 = ? OR w2 = ? OR w3 = ?", arrayOf(word, word, word))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
}
