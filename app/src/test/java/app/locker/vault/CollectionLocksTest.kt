package app.locker.vault

import app.locker.vault.core.CollectionLocks
import app.locker.vault.data.VaultCollection
import app.locker.vault.data.VaultItem
import app.locker.vault.data.VaultType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectionLocksTest {
    private fun collection(
        id: String,
        parentId: String = "",
        locked: Boolean = false
    ) = VaultCollection(
        id = id,
        name = id,
        color = 0,
        icon = "folder",
        parentId = parentId,
        requireLock = locked
    )

    private fun item(id: String, vararg collectionIds: String) = VaultItem(
        id = id,
        type = VaultType.NOTE,
        title = id,
        collectionIds = collectionIds.toList()
    )

    @Test
    fun anUnlockedCollectionIsNeverHidden() {
        val collections = listOf(collection("a"))
        assertFalse(CollectionLocks.isHidden("a", collections, emptySet()))
    }

    @Test
    fun aLockedCollectionHidesItsContentsUntilUnlocked() {
        val collections = listOf(collection("a", locked = true))
        assertTrue(CollectionLocks.isHidden("a", collections, emptySet()))
        assertFalse(CollectionLocks.isHidden("a", collections, setOf("a")))
    }

    @Test
    fun locksAreInheritedByChildCollections() {
        val collections = listOf(
            collection("parent", locked = true),
            collection("child", parentId = "parent"),
            collection("grandchild", parentId = "child"),
            collection("lockedChild", parentId = "parent", locked = true)
        )
        assertTrue(CollectionLocks.isHidden("child", collections, emptySet()))
        assertTrue(CollectionLocks.isHidden("grandchild", collections, emptySet()))
        // Unlocking the child does not unlock the locked ancestor.
        assertTrue(CollectionLocks.isHidden("child", collections, setOf("child")))
        assertFalse(CollectionLocks.isHidden("child", collections, setOf("parent")))
        assertTrue(CollectionLocks.isHidden("lockedChild", collections, setOf("parent")))
    }

    @Test
    fun aFreedParentDoesNotUnlockALockedChild() {
        val collections = listOf(
            collection("parent"),
            collection("child", parentId = "parent", locked = true)
        )
        assertTrue(CollectionLocks.isHidden("child", collections, setOf("parent")))
        assertFalse(CollectionLocks.isHidden("child", collections, setOf("parent", "child")))
    }

    @Test
    fun anItemIsHiddenWhenAnyOfItsCollectionsIsHidden() {
        val collections = listOf(
            collection("open"),
            collection("secret", locked = true)
        )
        assertFalse(CollectionLocks.isItemHidden(item("i", "open"), collections, emptySet()))
        assertTrue(CollectionLocks.isItemHidden(item("i", "open", "secret"), collections, emptySet()))
        assertFalse(
            CollectionLocks.isItemHidden(item("i", "open", "secret"), collections, setOf("secret"))
        )
        // Items outside every collection are always visible.
        assertFalse(CollectionLocks.isItemHidden(item("loose"), collections, emptySet()))
    }

    @Test
    fun visibleItemsKeepsOrderAndDropsOnlyHiddenOnes() {
        val collections = listOf(collection("secret", locked = true))
        val items = listOf(
            item("one"),
            item("two", "secret"),
            item("three"),
            item("four", "secret")
        )
        assertEquals(
            listOf("one", "three"),
            CollectionLocks.visibleItems(items, collections, emptySet()).map { it.id }
        )
        assertEquals(
            listOf("one", "two", "three", "four"),
            CollectionLocks.visibleItems(items, collections, setOf("secret")).map { it.id }
        )
    }

    @Test
    fun noLocksMeansNoFilteringWork() {
        val collections = listOf(collection("a"), collection("b", parentId = "a"))
        val items = listOf(item("x", "a"), item("y", "b"))
        assertEquals(items, CollectionLocks.visibleItems(items, collections, emptySet()))
        assertEquals(emptySet<String>(), CollectionLocks.activeLockIds(collections, emptySet()))
    }

    @Test
    fun activeLockIdsReportsOnlyLockedAndStillSealed() {
        val collections = listOf(
            collection("a", locked = true),
            collection("b", locked = true),
            collection("c")
        )
        assertEquals(setOf("a", "b"), CollectionLocks.activeLockIds(collections, emptySet()))
        assertEquals(setOf("b"), CollectionLocks.activeLockIds(collections, setOf("a")))
    }

    @Test
    fun aCorruptCycleTerminates() {
        val collections = listOf(
            collection("a", parentId = "b", locked = true),
            collection("b", parentId = "a")
        )
        assertTrue(CollectionLocks.isHidden("b", collections, emptySet()))
        assertFalse(CollectionLocks.isHidden("b", collections, setOf("a")))
    }
}
