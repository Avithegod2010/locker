package app.locker.vault

import app.locker.vault.data.CustomField
import app.locker.vault.data.ItemHistory
import app.locker.vault.data.ItemHistoryEntry
import app.locker.vault.data.VaultItem
import app.locker.vault.data.VaultType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ItemHistoryTest {
    private fun item(
        title: String = "Mail",
        body: String = "notes",
        password: String = "secret"
    ) = VaultItem(
        id = "id",
        type = VaultType.SECRET,
        title = title,
        body = body,
        password = password
    )

    @Test
    fun appendKeepsNewestFirstAndCapturesThePreviousVersion() {
        val previous = item(title = "Mail", body = "old notes", password = "old")
        val history = ItemHistory.append(emptyList(), previous, 1_000L)

        assertEquals(1, history.size)
        assertEquals("Mail", history[0].title)
        assertEquals("old notes", history[0].body)
        assertEquals("old", history[0].password)
        assertEquals(1_000L, history[0].changedAt)
    }

    @Test
    fun appendSkipsAnEntryIdenticalToTheNewestSnapshot() {
        val previous = item()
        val history = ItemHistory.append(emptyList(), previous, 1_000L)
        val again = ItemHistory.append(history, previous, 2_000L)

        assertEquals(1, again.size)
        assertEquals(1_000L, again[0].changedAt)
    }

    @Test
    fun appendCapsTheHistoryAtTenEntries() {
        var history = emptyList<ItemHistoryEntry>()
        repeat(14) { index ->
            history = ItemHistory.append(
                history,
                item(title = "Version $index", body = "body $index", password = "p$index"),
                index.toLong()
            )
        }

        assertEquals(ItemHistory.MAX_ENTRIES, history.size)
        assertEquals("Version 13", history.first().title)
        assertEquals("Version 4", history.last().title)
    }

    @Test
    fun contentChangedIgnoresTagsCollectionsFavouriteAndExpiry() {
        val original = item()
        val metadataOnly = original.copy(
            tags = listOf("work"),
            collectionIds = listOf("collection"),
            favorite = true,
            expiresAt = 123L
        )
        assertFalse(ItemHistory.contentChanged(original, metadataOnly))

        assertTrue(ItemHistory.contentChanged(original, original.copy(body = "new notes")))
        assertTrue(ItemHistory.contentChanged(original, original.copy(password = "new")))
        assertTrue(
            ItemHistory.contentChanged(
                original,
                original.copy(fields = listOf(CustomField("PIN", "1234")))
            )
        )
    }
}
