package app.locker.vault

import app.locker.vault.data.BackupItemSnapshot
import app.locker.vault.data.RestoreDiffer
import app.locker.vault.data.RestoreMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RestoreDiffTest {

    private fun item(
        id: String,
        title: String = id,
        updatedAt: Long = 100L,
        type: String = "SECRET"
    ) = BackupItemSnapshot(id = id, title = title, type = type, updatedAt = updatedAt)

    @Test
    fun mergeAddsMissingReplacesNewerAndKeepsOlder() {
        val backup = listOf(
            item("a", updatedAt = 100L),
            item("b", updatedAt = 300L),
            item("c", updatedAt = 100L),
            item("d", updatedAt = 200L)
        )
        val current = listOf(
            item("b", updatedAt = 200L),
            item("c", updatedAt = 300L),
            item("d", updatedAt = 200L)
        )

        val diff = RestoreDiffer.diff(backup, current, RestoreMode.MERGE)

        assertEquals(listOf("a"), diff.added.map { it.id })
        assertEquals(listOf("b"), diff.replaced.map { it.id })
        assertEquals(emptyList<String>(), diff.removed.map { it.id })
        assertEquals(2, diff.unchangedCount)
        assertTrue(diff.hasChanges)
    }

    @Test
    fun replaceMarksEveryExistingRowAndRemovesCurrentOnlyItems() {
        val backup = listOf(
            item("a", updatedAt = 100L),
            item("b", updatedAt = 100L)
        )
        val current = listOf(
            item("b", updatedAt = 300L),
            item("c", updatedAt = 300L),
            item("d", updatedAt = 300L)
        )

        val diff = RestoreDiffer.diff(backup, current, RestoreMode.REPLACE)

        assertEquals(listOf("a"), diff.added.map { it.id })
        assertEquals(listOf("b"), diff.replaced.map { it.id })
        assertEquals(listOf("c", "d"), diff.removed.map { it.id })
        assertEquals(0, diff.unchangedCount)
    }

    @Test
    fun identicalVaultHasNoChangesInMergeMode() {
        val current = listOf(item("a", updatedAt = 100L), item("b", updatedAt = 100L))
        val diff = RestoreDiffer.diff(current, current, RestoreMode.MERGE)

        assertTrue(diff.added.isEmpty())
        assertTrue(diff.replaced.isEmpty())
        assertTrue(diff.removed.isEmpty())
        assertEquals(2, diff.unchangedCount)
        assertFalse(diff.hasChanges)
    }

    @Test
    fun diffListsAreSortedCaseInsensitively() {
        val backup = listOf(
            item("z", title = "zebra"),
            item("b", title = "Banana"),
            item("a", title = "apple")
        )

        val diff = RestoreDiffer.diff(backup, emptyList(), RestoreMode.MERGE)

        assertEquals(listOf("apple", "Banana", "zebra"), diff.added.map { it.title })
    }
}
