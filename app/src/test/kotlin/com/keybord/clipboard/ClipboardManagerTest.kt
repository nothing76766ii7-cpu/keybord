package com.keybord.clipboard

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ClipboardManagerTest {

    private lateinit var storage: EncryptedClipboardStorage

    @Before
    fun setUp() {
        // In-memory test instance with mock key
        storage = EncryptedClipboardStorage(context = null, dbName = null, customKeyBytes = ByteArray(32) { 7 })
    }

    @Test
    fun testEncryptionAtRest() {
        val sampleText = "SuperSecretPassword123"
        val id = storage.insertItem(sampleText)

        // Verify retrieval decrypts accurately
        val item = storage.getItem(id)
        assertNotNull("Item must be retrievable", item)
        assertEquals("Decrypted text must match original", sampleText, item!!.text)

        // Verify raw blob in SQLite is encrypted and does not contain plain string
        val cursor = storage.readableDatabase.rawQuery("SELECT encrypted_data FROM clipboard_items WHERE id = ?", arrayOf(id.toString()))
        assertTrue(cursor.moveToFirst())
        val rawBlob = cursor.getBlob(0)
        cursor.close()

        val rawBlobString = String(rawBlob)
        assertFalse("Raw SQLite blob must not contain plaintext", rawBlobString.contains(sampleText))
    }

    @Test
    fun testPinnedItemsExemptFromRetentionRules() {
        val now = System.currentTimeMillis()

        // Insert pinned item and unpinned item
        val pinnedId = storage.insertItem("Pinned Note", isPinned = true)
        val unpinnedId = storage.insertItem("Temporary Note", isPinned = false)

        // Simulate item aging (e.g., 40 days ago)
        val fortyDaysAgo = now - (40L * 24L * 3600L * 1000L)
        val cv = android.content.ContentValues().apply {
            put("created_at", fortyDaysAgo)
        }
        storage.writableDatabase.update("clipboard_items", cv, null, null)

        // Apply DeleteAfterDays(30) policy
        storage.applyRetentionPolicy(RetentionPolicy.DeleteAfterDays(30), capacityLimit = 500)

        // Pinned item MUST remain; unpinned item must be deleted
        assertNotNull("Pinned item must be exempt from age retention rule", storage.getItem(pinnedId))
        assertNull("Unpinned aged item must be pruned", storage.getItem(unpinnedId))
    }

    @Test
    fun testKeepLastCountRetentionPolicy() {
        val pinnedId = storage.insertItem("Pinned Header", isPinned = true)

        val unpinnedIds = mutableListOf<Long>()
        for (i in 1..10) {
            unpinnedIds.add(storage.insertItem("Item $i", isPinned = false))
            Thread.sleep(2)
        }

        // Apply KeepLastCount(5) policy
        storage.applyRetentionPolicy(RetentionPolicy.KeepLastCount(5), capacityLimit = 500)

        // Pinned item is exempt
        assertNotNull("Pinned item must remain", storage.getItem(pinnedId))

        // Oldest 5 unpinned items (1..5) should be pruned, newest 5 (6..10) kept
        for (i in 0 until 5) {
            assertNull("Old item ${i + 1} should be pruned", storage.getItem(unpinnedIds[i]))
        }
        for (i in 5 until 10) {
            assertNotNull("Recent item ${i + 1} must be kept", storage.getItem(unpinnedIds[i]))
        }
    }

    @Test
    fun testCapacityLimitPruning() {
        val pinnedId = storage.insertItem("Forever Pinned", isPinned = true)

        // Insert 6 unpinned items with capacity limit 3
        val itemIds = mutableListOf<Long>()
        for (i in 1..6) {
            itemIds.add(storage.insertItem("Note $i", isPinned = false, capacityLimit = 3))
            Thread.sleep(2)
        }

        val allItems = storage.getAllItems()
        // Pinned item (1) + 3 unpinned items = 4 total items
        assertEquals("Total items must be 1 pinned + 3 unpinned", 4, allItems.size)
        assertTrue("Pinned item must remain regardless of capacity", allItems.any { it.id == pinnedId })
    }

    @Test
    fun testCategoryAssignmentAndFilter() {
        val id1 = storage.insertItem("https://antigravity.google.com", category = "Links")
        val id2 = storage.insertItem("SELECT * FROM users;", category = "Code")
        val id3 = storage.insertItem("Meeting at 3pm", category = "General")

        val codeItems = storage.getAllItems(categoryFilter = "Code")
        assertEquals(1, codeItems.size)
        assertEquals("SELECT * FROM users;", codeItems.first().text)

        val linkItems = storage.getAllItems(categoryFilter = "Links")
        assertEquals(1, linkItems.size)
        assertEquals("https://antigravity.google.com", linkItems.first().text)

        // Bulk assign category
        storage.assignCategory(listOf(id1, id3), "Important")
        val importantItems = storage.getAllItems(categoryFilter = "Important")
        assertEquals(2, importantItems.size)
    }

    @Test
    fun testPrefixAndSubstringSearch() {
        storage.insertItem("Buy groceries: milk, eggs, bread")
        storage.insertItem("Remember to review PRD")
        storage.insertItem("Docker run -p 8080:8080")

        val results1 = storage.searchItems("groceries")
        assertEquals(1, results1.size)
        assertTrue(results1.first().text.contains("milk"))

        val results2 = storage.searchItems("docker")
        assertEquals(1, results2.size)

        val resultsAll = storage.searchItems("")
        assertEquals(3, resultsAll.size)
    }

    @Test
    fun testBulkDelete() {
        val id1 = storage.insertItem("Item A")
        val id2 = storage.insertItem("Item B")
        val id3 = storage.insertItem("Item C")

        storage.deleteItems(listOf(id1, id3))
        assertNull(storage.getItem(id1))
        assertNotNull(storage.getItem(id2))
        assertNull(storage.getItem(id3))
    }
}
