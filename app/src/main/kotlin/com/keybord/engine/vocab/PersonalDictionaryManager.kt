package com.keybord.engine.vocab

import android.content.ContentResolver
import android.content.Context
import android.provider.UserDictionary
import java.util.Locale

/**
 * Manages explicit personal dictionary entries and shortcuts,
 * interfacing with Android's system UserDictionary content provider.
 */
class PersonalDictionaryManager(private val context: Context?) {

    private val localPersonalDict = HashMap<String, WordEntry>()

    /**
     * Inserts or updates an explicit user dictionary word with optional shortcut.
     */
    fun addWord(word: String, shortcut: String? = null, frequency: Int = 250, locale: Locale = Locale.getDefault()) {
        val cleanWord = word.trim()
        if (cleanWord.isEmpty()) return

        localPersonalDict[cleanWord.lowercase()] = WordEntry(
            word = cleanWord,
            count = frequency,
            lastUsedTimestamp = System.currentTimeMillis(),
            isOov = false,
            shortcut = shortcut?.trim()
        )

        if (context != null) {
            try {
                UserDictionary.Words.addWord(
                    context,
                    cleanWord,
                    frequency,
                    shortcut?.trim(),
                    locale
                )
            } catch (e: Exception) {
                // Ignore if READ/WRITE_USER_DICTIONARY permission not granted
            }
        }
    }

    fun deleteWord(word: String) {
        val clean = word.trim().lowercase()
        localPersonalDict.remove(clean)
        if (context != null) {
            try {
                context.contentResolver.delete(
                    UserDictionary.Words.CONTENT_URI,
                    "${UserDictionary.Words.WORD} = ?",
                    arrayOf(word)
                )
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    /**
     * Resolves text shortcuts (e.g. "omw" -> "On my way!").
     */
    fun getShortcutExpansion(shortcut: String): String? {
        val clean = shortcut.trim().lowercase()
        return localPersonalDict.values.find { it.shortcut?.lowercase() == clean }?.word
    }

    /**
     * Synchronizes existing words from Android's UserDictionary provider.
     */
    fun syncFromSystem(contentResolver: ContentResolver?) {
        if (contentResolver == null) return
        try {
            val cursor = contentResolver.query(
                UserDictionary.Words.CONTENT_URI,
                arrayOf(UserDictionary.Words.WORD, UserDictionary.Words.FREQUENCY, UserDictionary.Words.SHORTCUT),
                null, null, null
            ) ?: return

            while (cursor.moveToNext()) {
                val word = cursor.getString(0)
                val freq = cursor.getInt(1)
                val shortcut = cursor.getString(2)
                if (!word.isNullOrBlank()) {
                    localPersonalDict[word.lowercase()] = WordEntry(
                        word = word,
                        count = freq,
                        lastUsedTimestamp = System.currentTimeMillis(),
                        isOov = false,
                        shortcut = shortcut
                    )
                }
            }
            cursor.close()
        } catch (e: Exception) {
            // Permission not granted or provider unavailable
        }
    }

    fun getAllPersonalWords(): List<WordEntry> = localPersonalDict.values.toList()

    fun contains(word: String): Boolean = localPersonalDict.containsKey(word.lowercase().trim())
}
