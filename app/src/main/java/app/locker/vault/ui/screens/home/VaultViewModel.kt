package app.locker.vault.ui.screens.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.locker.vault.LockerApp
import app.locker.vault.core.SortMode
import app.locker.vault.core.ThemeMode
import app.locker.vault.data.VaultCollection
import app.locker.vault.data.VaultItem
import app.locker.vault.data.VaultProfile
import app.locker.vault.data.VaultType
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class VaultFilter(
    val query: String = "",
    val favoritesOnly: Boolean = false,
    val collectionId: String? = null,
    val uncategorizedOnly: Boolean = false,
    val type: VaultType? = null,
    val tag: String? = null
)

data class VaultUiState(
    val allItems: List<VaultItem> = emptyList(),
    val items: List<VaultItem> = emptyList(),
    val collections: List<VaultCollection> = emptyList(),
    val filter: VaultFilter = VaultFilter(),
    val sortMode: SortMode = SortMode.NEWEST,
    val profile: VaultProfile = VaultProfile(),
    val activeCount: Int = 0,
    val totalBytes: Long = 0L,
    val allTags: List<String> = emptyList(),
    val loading: Boolean = true
) {
    val selectedCollection: VaultCollection?
        get() = filter.collectionId?.let { id -> collections.firstOrNull { it.id == id } }

    val hasActiveFilter: Boolean
        get() = filter.query.isNotBlank() || filter.favoritesOnly ||
            filter.collectionId != null || filter.uncategorizedOnly || filter.type != null ||
            filter.tag != null
}

sealed interface VaultEvent {
    data class Message(val text: String) : VaultEvent
    data class Deleted(val item: VaultItem) : VaultEvent
}

class VaultViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as LockerApp).container
    private val repository = container.repository

    private val filter = MutableStateFlow(VaultFilter())
    private val loaded = MutableStateFlow(false)
    private val _events = MutableSharedFlow<VaultEvent>(extraBufferCapacity = 4)
    val events = _events.asSharedFlow()

    val uiState: StateFlow<VaultUiState> = combine(
        combine(repository.items, loaded) { items, isLoaded -> items to isLoaded },
        repository.collections,
        filter,
        container.preferences.settings,
        repository.profile
    ) { itemsAndLoaded, collections, activeFilter, settings, profile ->
        val items = itemsAndLoaded.first
        val isLoaded = itemsAndLoaded.second
        val active = items.filter {
            it.deletedAt == null && it.archivedAt == null && it.parentId.isEmpty()
        }
        val filtered = active.filter { item ->
            val collectionMatch = when {
                activeFilter.collectionId != null -> activeFilter.collectionId in item.collectionIds
                activeFilter.uncategorizedOnly -> item.collectionIds.isEmpty()
                else -> true
            }
            val typeMatch = activeFilter.type == null || item.type == activeFilter.type
            val favoriteMatch = !activeFilter.favoritesOnly || item.favorite
            val tagMatch = activeFilter.tag == null ||
                item.tags.any { it.equals(activeFilter.tag, ignoreCase = true) }
            val queryMatch = activeFilter.query.isBlank() || matches(item, activeFilter.query, collections)
            collectionMatch && typeMatch && favoriteMatch && tagMatch && queryMatch
        }
        val sorted = when (settings.sortMode) {
            SortMode.NEWEST -> filtered.sortedByDescending { it.updatedAt }
            SortMode.OLDEST -> filtered.sortedBy { it.createdAt }
            SortMode.NAME -> filtered.sortedBy { it.title.lowercase() }
        }
        VaultUiState(
            allItems = active,
            items = sorted,
            collections = collections,
            filter = activeFilter,
            sortMode = settings.sortMode,
            profile = profile,
            activeCount = active.size,
            totalBytes = active.sumOf { it.sizeBytes },
            allTags = active.flatMap { it.tags }.distinct().sortedBy { it.lowercase() },
            loading = !isLoaded
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        VaultUiState()
    )

    init {
        viewModelScope.launch {
            runCatching { repository.refresh() }
            runCatching { repository.purgeExpiredTrash() }
            loaded.value = true
        }
    }

    fun setQuery(value: String) {
        filter.value = filter.value.copy(query = value)
    }

    fun clearFilters() {
        filter.value = VaultFilter(query = filter.value.query)
    }

    fun clearAll() {
        filter.value = VaultFilter()
    }

    fun toggleFavorites() {
        filter.value = filter.value.copy(
            favoritesOnly = !filter.value.favoritesOnly,
            collectionId = null,
            uncategorizedOnly = false
        )
    }

    fun selectCollection(id: String) {
        filter.value = filter.value.copy(
            collectionId = if (filter.value.collectionId == id) null else id,
            favoritesOnly = false,
            uncategorizedOnly = false
        )
    }

    fun selectUncategorized() {
        filter.value = filter.value.copy(
            uncategorizedOnly = !filter.value.uncategorizedOnly,
            favoritesOnly = false,
            collectionId = null
        )
    }

    fun setType(type: VaultType?) {
        filter.value = filter.value.copy(type = type)
    }

    fun setTag(tag: String?) {
        filter.value = filter.value.copy(tag = tag)
    }

    fun setSortMode(mode: SortMode) {
        container.preferences.setSortMode(mode)
    }

    fun toggleFavorite(item: VaultItem) {
        viewModelScope.launch {
            repository.toggleFavorite(item)
        }
    }

    fun delete(item: VaultItem) {
        viewModelScope.launch {
            repository.softDelete(item)
                .onSuccess { _events.emit(VaultEvent.Deleted(item)) }
                .onFailure { _events.emit(VaultEvent.Message("Could not move item to trash")) }
        }
    }

    fun undoDelete(item: VaultItem) {
        viewModelScope.launch {
            repository.restore(item)
                .onSuccess { _events.emit(VaultEvent.Message("Restored")) }
                .onFailure { _events.emit(VaultEvent.Message("Could not restore item")) }
        }
    }

    fun moveToCollection(item: VaultItem, collectionIds: List<String>) {
        viewModelScope.launch {
            repository.setCollections(item.id, collectionIds)
                .onSuccess { _events.emit(VaultEvent.Message("Collections updated")) }
                .onFailure { _events.emit(VaultEvent.Message("Could not update collections")) }
        }
    }

    fun bulkFavorite(items: List<VaultItem>, favorite: Boolean) {
        viewModelScope.launch {
            repository.bulkSetFavorite(items, favorite)
                .onSuccess { _events.emit(VaultEvent.Message(if (favorite) "Marked important" else "Removed from Important")) }
                .onFailure { _events.emit(VaultEvent.Message("Could not update items")) }
        }
    }

    fun bulkMove(items: List<VaultItem>, collectionIds: List<String>) {
        viewModelScope.launch {
            repository.bulkMove(items, collectionIds)
                .onSuccess { _events.emit(VaultEvent.Message("Moved to collection")) }
                .onFailure { _events.emit(VaultEvent.Message("Could not move items")) }
        }
    }

    fun bulkDelete(items: List<VaultItem>) {
        viewModelScope.launch {
            repository.bulkDelete(items)
                .onSuccess { _events.emit(VaultEvent.Message("${items.size} items moved to trash")) }
                .onFailure { _events.emit(VaultEvent.Message("Could not delete items")) }
        }
    }

    fun bulkTag(items: List<VaultItem>, tag: String) {
        viewModelScope.launch {
            repository.bulkAddTag(items, tag)
                .onSuccess { _events.emit(VaultEvent.Message("Tag added")) }
                .onFailure { _events.emit(VaultEvent.Message("Could not add tag")) }
        }
    }

    private fun matches(
        item: VaultItem,
        query: String,
        collections: List<VaultCollection>
    ): Boolean {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return true
        val operatorRegex = Regex("""(type|tag|before|after):(\S+)""", RegexOption.IGNORE_CASE)
        val operators = operatorRegex.findAll(q).associate {
            it.groupValues[1].lowercase() to it.groupValues[2]
        }
        val textQuery = q.replace(operatorRegex, " ").replace(Regex("\\s+"), " ").trim()
        val typeMatch = operators["type"]?.let { value ->
            item.type.name.equals(value, ignoreCase = true) ||
                item.type.label.equals(value, ignoreCase = true)
        } ?: true
        val tagMatch = operators["tag"]?.let { value ->
            item.tags.any { it.equals(value, ignoreCase = true) }
        } ?: true
        val beforeMatch = operators["before"]?.toIntOrNull()?.let { year ->
            item.createdAt < januaryFirst(year)
        } ?: true
        val afterMatch = operators["after"]?.toIntOrNull()?.let { year ->
            item.createdAt >= januaryFirst(year)
        } ?: true
        if (!typeMatch || !tagMatch || !beforeMatch || !afterMatch) return false
        if (textQuery.isEmpty()) return true
        val collectionNames = collections
            .filter { it.id in item.collectionIds }
            .joinToString(" ") { it.name }
        return listOf(
            item.title,
            item.body,
            item.username,
            item.location,
            item.website,
            item.ocrText,
            item.fileName,
            item.mimeType,
            collectionNames,
            item.tags.joinToString(" "),
            item.fields.joinToString(" ") { it.label + " " + it.value },
            item.type.label
        ).any { it.lowercase().contains(textQuery) }
    }

    private fun januaryFirst(year: Int): Long = runCatching {
        java.util.Calendar.getInstance().apply {
            clear()
            set(year, java.util.Calendar.JANUARY, 1)
        }.timeInMillis
    }.getOrDefault(0L)
}
