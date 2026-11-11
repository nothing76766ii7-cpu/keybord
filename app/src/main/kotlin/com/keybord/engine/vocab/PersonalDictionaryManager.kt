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

    private val dictLock = Any()
    private val localPersonalDict = HashMap<String, WordEntry>()

    init {
        loadFromPrefs()
    }

    /**
     * Inserts or updates an explicit user dictionary word with optional shortcut.
     */
    fun addWord(word: String, shortcut: String? = null, frequency: Int = 250, locale: Locale = Locale.getDefault()) {
        val cleanWord = word.trim()
        if (cleanWord.isEmpty()) return
        val cleanShortcut = shortcut?.trim()?.takeIf { it.isNotEmpty() }

        synchronized(dictLock) {
            localPersonalDict[cleanWord.lowercase()] = WordEntry(
                word = cleanWord,
                count = frequency,
                lastUsedTimestamp = System.currentTimeMillis(),
                isOov = false,
                shortcut = cleanShortcut
            )
            saveToPrefsLocked()
        }

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
        synchronized(dictLock) {
            localPersonalDict.remove(clean)
            saveToPrefsLocked()
        }
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
        if (clean.isEmpty()) return null
        return synchronized(dictLock) {
            localPersonalDict.values.find { it.shortcut?.lowercase() == clean }?.word
        }
    }

    /**
     * Synchronizes existing words from Android's UserDictionary provider and local persistence.
     */
    fun syncFromSystem(contentResolver: ContentResolver?) {
        loadFromPrefs()
        if (contentResolver == null) return
        try {
            val systemEntries = ArrayList<WordEntry>()
            contentResolver.query(
                UserDictionary.Words.CONTENT_URI,
                arrayOf(UserDictionary.Words.WORD, UserDictionary.Words.FREQUENCY, UserDictionary.Words.SHORTCUT),
                null, null, null
            )?.use { cursor ->
                val now = System.currentTimeMillis()
                while (cursor.moveToNext()) {
                    val word = cursor.getString(0)
                    val freq = cursor.getInt(1)
                    val shortcut = cursor.getString(2)
                    if (!word.isNullOrBlank()) {
                        systemEntries.add(
                            WordEntry(
                                word = word,
                                count = freq,
                                lastUsedTimestamp = now,
                                isOov = false,
                                shortcut = shortcut
                            )
                        )
                    }
                }
            } ?: return

            if (systemEntries.isNotEmpty()) {
                synchronized(dictLock) {
                    for (entry in systemEntries) {
                        localPersonalDict[entry.word.lowercase()] = entry
                    }
                    saveToPrefsLocked()
                }
            }
        } catch (e: Exception) {
            // Permission not granted or provider unavailable
        }
    }

    private fun saveToPrefsLocked() {
        val ctx = context ?: return
        try {
            val prefs = ctx.getSharedPreferences("keybord_personal_dict", Context.MODE_PRIVATE) ?: return
            val arr = org.json.JSONArray()
            for (entry in localPersonalDict.values) {
                arr.put(
                    org.json.JSONObject().apply {
                        put("word", entry.word)
                        put("count", entry.count)
                        if (!entry.shortcut.isNullOrBlank()) put("shortcut", entry.shortcut)
                    }
                )
            }
            prefs.edit().putString("entries_json", arr.toString()).apply()
        } catch (_: Exception) {
            // Safe fallback in unit tests
        }
    }

    private fun loadFromPrefs() {
        val ctx = context ?: return
        try {
            val prefs = ctx.getSharedPreferences("keybord_personal_dict", Context.MODE_PRIVATE) ?: return
            val raw = prefs.getString("entries_json", null) ?: return
            val parsed = HashMap<String, WordEntry>()
            val arr = org.json.JSONArray(raw)
            val now = System.currentTimeMillis()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val word = obj.optString("word", "").trim()
                if (word.isNotEmpty()) {
                    parsed[word.lowercase()] = WordEntry(
                        word = word,
                        count = obj.optInt("count", 250),
                        lastUsedTimestamp = now,
                        isOov = false,
                        shortcut = obj.optString("shortcut", "").takeIf { it.isNotBlank() }
                    )
                }
            }
            synchronized(dictLock) {
                localPersonalDict.clear()
                localPersonalDict.putAll(parsed)
            }
        } catch (_: Exception) {
            // Safe fallback in unit tests
        }
    }

    fun getAllPersonalWords(): List<WordEntry> = synchronized(dictLock) {
        localPersonalDict.values.toList()
    }

    fun contains(word: String): Boolean {
        val clean = word.lowercase().trim()
        return synchronized(dictLock) {
            localPersonalDict.containsKey(clean)
        }
    }
}
