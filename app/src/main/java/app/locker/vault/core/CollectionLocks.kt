package app.locker.vault.core

import app.locker.vault.data.VaultCollection
import app.locker.vault.data.VaultItem

/**
 * Per-collection locks.
 *
 * A locked collection is still visible in the sidebar (so it can be unlocked again) but its
 * contents are withheld from every list, search, autofill request, and home-screen surface until
 * the owner unlocks it for the current session. Unlocking is deliberately in-memory only: the
 * set is cleared whenever the vault locks, so a stolen unlocked phone does not stay open.
 *
 * The functions here are pure so the inheritance rules can be unit tested without Android.
 */
object CollectionLocks {
    /**
     * True when [collectionId] or any of its ancestors requires a lock that is not currently
     * unlocked. Cycles in a corrupted hierarchy are tolerated.
     */
    fun isHidden(
        collectionId: String,
        collections: List<VaultCollection>,
        unlocked: Set<String>
    ): Boolean {
        val byId = collections.associateBy { it.id }
        val seen = mutableSetOf<String>()
        var current = byId[collectionId]
        while (current != null && seen.add(current.id)) {
            if (current.requireLock && current.id !in unlocked) return true
            if (current.parentId.isBlank()) return false
            current = byId[current.parentId]
        }
        return false
    }

    /** True when the item sits in at least one hidden collection. */
    fun isItemHidden(
        item: VaultItem,
        collections: List<VaultCollection>,
        unlocked: Set<String>
    ): Boolean {
        if (item.collectionIds.isEmpty()) return false
        // Build the lookup once per item, not once per collection id.
        return item.collectionIds.any { isHidden(it, collections, unlocked) }
    }

    /** Items that may be shown without first unlocking a collection. */
    fun visibleItems(
        items: List<VaultItem>,
        collections: List<VaultCollection>,
        unlocked: Set<String>
    ): List<VaultItem> {
        if (collections.none { it.requireLock }) return items
        return items.filterNot { isItemHidden(it, collections, unlocked) }
    }

    /** The collection ids that are locked and not unlocked right now. */
    fun activeLockIds(
        collections: List<VaultCollection>,
        unlocked: Set<String>
    ): Set<String> = collections
        .filter { it.requireLock && it.id !in unlocked }
        .map { it.id }
        .toSet()
}
