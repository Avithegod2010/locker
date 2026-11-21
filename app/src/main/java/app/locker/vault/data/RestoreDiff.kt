package app.locker.vault.data

/**
 * A decryptable, display-only snapshot of one item in a backup or in the
 * current vault. Only the title and timestamps are held in memory for the
 * restore preview; payloads are never kept here.
 */
data class BackupItemSnapshot(
    val id: String,
    val title: String,
    val type: String,
    val updatedAt: Long
)

/** One item row shown in the restore review. */
data class BackupItemChange(
    val id: String,
    val title: String,
    val type: String
) {
    val typeLabel: String
        get() = runCatching { VaultType.valueOf(type).label }.getOrDefault("Item")
}

/**
 * What a restore would do, item by item. [removed] is only non-empty for
 * [RestoreMode.REPLACE]; merge never deletes anything.
 */
data class RestoreDiff(
    val mode: RestoreMode,
    val added: List<BackupItemChange>,
    val replaced: List<BackupItemChange>,
    val removed: List<BackupItemChange>,
    val unchangedCount: Int
) {
    val addedCount: Int get() = added.size
    val replacedCount: Int get() = replaced.size
    val removedCount: Int get() = removed.size

    val hasChanges: Boolean
        get() = added.isNotEmpty() || replaced.isNotEmpty() || removed.isNotEmpty()
}

/**
 * Pure diff used by the restore preview. It mirrors the decisions in
 * BackupManager.restore so the review cannot promise something different
 * from what the commit will do:
 *
 * - merge adds missing ids and replaces only rows whose backup copy is newer;
 * - replace rewrites every backup row and removes current rows that are not
 *   in the archive.
 */
object RestoreDiffer {
    fun diff(
        backup: List<BackupItemSnapshot>,
        current: List<BackupItemSnapshot>,
        mode: RestoreMode
    ): RestoreDiff {
        val currentById = current.associateBy { it.id }
        val backupIds = backup.mapTo(HashSet(backup.size)) { it.id }
        val added = mutableListOf<BackupItemChange>()
        val replaced = mutableListOf<BackupItemChange>()
        var unchanged = 0

        backup.forEach { item ->
            val existing = currentById[item.id]
            if (existing == null) {
                added += item.toChange()
            } else if (mode == RestoreMode.REPLACE || item.updatedAt > existing.updatedAt) {
                replaced += item.toChange()
            } else {
                unchanged++
            }
        }

        val removed = if (mode == RestoreMode.REPLACE) {
            current.filter { it.id !in backupIds }.map { it.toChange() }
        } else {
            emptyList()
        }

        return RestoreDiff(
            mode = mode,
            added = added.sortedByTitle(),
            replaced = replaced.sortedByTitle(),
            removed = removed.sortedByTitle(),
            unchangedCount = unchanged
        )
    }

    private fun BackupItemSnapshot.toChange() = BackupItemChange(
        id = id,
        title = title,
        type = type
    )

    private fun List<BackupItemChange>.sortedByTitle(): List<BackupItemChange> =
        sortedWith(
            compareBy<BackupItemChange> { it.title.lowercase() }
                .thenBy { it.title }
                .thenBy { it.id }
        )
}
