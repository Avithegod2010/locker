package app.locker.vault.ui.screens.home

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sell
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StickyNote2
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import app.locker.vault.core.Formatting
import app.locker.vault.core.SharedContent
import app.locker.vault.core.SortMode
import app.locker.vault.data.VaultItem
import app.locker.vault.data.VaultType
import app.locker.vault.ui.LocalAppContainer
import app.locker.vault.ui.components.AnimatedCheck
import app.locker.vault.ui.components.DashedAddChip
import app.locker.vault.ui.components.EmptyState
import app.locker.vault.ui.components.FilterPill
import app.locker.vault.ui.components.HeaderGradient
import app.locker.vault.ui.components.ItemIcon
import app.locker.vault.ui.components.LockerCard
import app.locker.vault.ui.components.LockerSearchField
import app.locker.vault.ui.components.LockerTextField
import app.locker.vault.core.SavedSearches
import app.locker.vault.ui.components.LockerTopBar
import app.locker.vault.ui.components.RoundIconButton
import app.locker.vault.ui.components.accent
import app.locker.vault.ui.components.icon
import app.locker.vault.ui.navigation.Routes
import app.locker.vault.ui.theme.BrandAmber
import app.locker.vault.ui.theme.BrandBlue
import app.locker.vault.ui.theme.BrandMint
import app.locker.vault.ui.theme.BrandViolet
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(navController: NavController) {
    val app = LocalAppContainer.current
    val context = LocalContext.current
    val viewModel: VaultViewModel = viewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by app.preferences.settings.collectAsStateWithLifecycle()
    val pendingShare by app.pendingShare.collectAsStateWithLifecycle()
    val pendingRoute by app.pendingRoute.collectAsStateWithLifecycle()
    val pendingSearch by app.pendingSearch.collectAsStateWithLifecycle()
    val savedSearches = remember(settings.savedSearchesJson) {
        SavedSearches.parse(settings.savedSearchesJson)
    }
    val homeSearches = savedSearches.filter { it.pinned }
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val snackbarHostState = remember { SnackbarHostState() }
    val searchFocus = remember { FocusRequester() }

    var showAddSheet by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }
    var showPrivacySheet by remember { mutableStateOf(false) }
    var showSaveSearch by remember { mutableStateOf(false) }
    var saveSearchName by remember { mutableStateOf("") }
    var pickerFor by remember { mutableStateOf<VaultItem?>(null) }
    var importing by remember { mutableStateOf(false) }
    var pendingCameraFile by remember { mutableStateOf<File?>(null) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var bulkCollectionOpen by remember { mutableStateOf(false) }
    var bulkTagOpen by remember { mutableStateOf(false) }
    var bulkDeleteConfirm by remember { mutableStateOf(false) }

    val selectedItems = remember(selectedIds, state.allItems) {
        state.allItems.filter { it.id in selectedIds }
    }

    fun toggleSelection(item: VaultItem) {
        selectionMode = true
        selectedIds = if (item.id in selectedIds) selectedIds - item.id else selectedIds + item.id
        if (selectedIds.isEmpty()) selectionMode = false
    }

    fun exitSelection() {
        selectionMode = false
        selectedIds = emptySet()
        bulkCollectionOpen = false
        bulkTagOpen = false
        bulkDeleteConfirm = false
    }

    fun closeDrawer() = scope.launch { drawerState.close() }

    fun navigate(route: String) {
        closeDrawer()
        if (route != Routes.HOME) {
            navController.navigate(route) { launchSingleTop = true }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        app.session.endExternalActivity()
        val file = pendingCameraFile
        val uri = pendingCameraUri
        pendingCameraFile = null
        pendingCameraUri = null
        if (success && file != null && uri != null) {
            importing = true
            scope.launch {
                val result = app.repository.importFile(
                    uri = uri,
                    type = VaultType.PHOTO,
                    title = Formatting.photoTitle(),
                    collectionIds = emptyList()
                )
                file.delete()
                importing = false
                snackbarHostState.showSnackbar(
                    result.fold(
                        onSuccess = { "Photo encrypted and saved" },
                        onFailure = { "Could not save photo" }
                    )
                )
            }
        } else {
            file?.delete()
        }
    }

    fun launchCamera() {
        app.session.beginExternalActivity()
        runCatching {
            val file = app.fileStore.createCameraTempFile()
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.files",
                file
            )
            pendingCameraFile = file
            pendingCameraUri = uri
            cameraLauncher.launch(uri)
        }.onFailure {
            app.session.endExternalActivity()
            scope.launch { snackbarHostState.showSnackbar("Camera is unavailable") }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is VaultEvent.Message -> snackbarHostState.showSnackbar(event.text)
                is VaultEvent.Deleted -> {
                    val result = snackbarHostState.showSnackbar(
                        message = "\"${event.item.title}\" moved to trash",
                        actionLabel = "Undo",
                        withDismissAction = true
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.undoDelete(event.item)
                    }
                }
            }
        }
    }

    LaunchedEffect(pendingRoute) {
        val route = pendingRoute ?: return@LaunchedEffect
        app.pendingRoute.value = null
        navController.navigate(route) { launchSingleTop = true }
    }

    LaunchedEffect(pendingSearch) {
        val query = pendingSearch ?: return@LaunchedEffect
        app.pendingSearch.value = null
        viewModel.setQuery(query)
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(342.dp),
                drawerShape = RoundedCornerShape(topEnd = 30.dp, bottomEnd = 30.dp),
                drawerContainerColor = MaterialTheme.colorScheme.background,
                windowInsets = WindowInsets(0)
            ) {
                DrawerContent(
                    profileName = state.profile.name,
                    profileEmail = state.profile.email,
                    itemCount = state.activeCount,
                    totalBytes = state.totalBytes,
                    onClose = { closeDrawer() },
                    onSearch = {
                        scope.launch {
                            drawerState.close()
                            delay(260)
                            searchFocus.requestFocus()
                        }
                    },
                    onNavigate = { navigate(it) },
                    onLock = {
                        closeDrawer()
                        scope.launch {
                            delay(180)
                            app.lockNow()
                        }
                    }
                )
            }
        }
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0),
            snackbarHost = {
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(bottom = 8.dp)
                )
            },
            floatingActionButton = {
                Box(modifier = Modifier.navigationBarsPadding()) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.padding(end = 18.dp, bottom = 10.dp)
                    ) {
                        Surface(
                            onClick = { navController.navigate(Routes.CAMERA) { launchSingleTop = true } },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = androidx.compose.foundation.BorderStroke(
                                1.3.dp,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                            ),
                            modifier = Modifier.size(58.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Rounded.PhotoCamera,
                                    contentDescription = "Take a private photo",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(25.dp)
                                )
                            }
                        }
                        FloatingActionButton(
                            onClick = { showAddSheet = true },
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            shape = CircleShape,
                            modifier = Modifier
                                .size(66.dp)
                                .shadow(14.dp, CircleShape)
                        ) {
                            Icon(
                                Icons.Rounded.Add,
                                contentDescription = "Add to Locker",
                                modifier = Modifier.size(31.dp)
                            )
                        }
                    }
                }
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                HeaderGradient(modifier = Modifier.align(Alignment.TopCenter))
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                ) {
                    LockerTopBar(
                        onMenu = { scope.launch { drawerState.open() } },
                        onShield = { showPrivacySheet = true }
                    )

                    if (settings.tamperDetected) {
                        TamperWarningBanner(
                            onDismiss = { app.preferences.setTamperDetected(false) }
                        )
                    }

                    if (selectionMode) {
                        SelectionBar(
                            count = selectedIds.size,
                            onClose = { exitSelection() },
                            onFavorite = {
                                viewModel.bulkFavorite(selectedItems, true)
                                exitSelection()
                            },
                            onMove = { bulkCollectionOpen = true },
                            onTag = { bulkTagOpen = true },
                            onDelete = { bulkDeleteConfirm = true }
                        )
                    }

                    LockerSearchField(
                        query = state.filter.query,
                        onQueryChange = viewModel::setQuery,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        focusRequester = searchFocus
                    )

                    Spacer(Modifier.height(5.dp))
                    FilterRow(
                        state = state,
                        onTune = { showFilterSheet = true },
                        onFavorites = viewModel::toggleFavorites,
                        onCollection = viewModel::selectCollection,
                        onUncategorized = viewModel::selectUncategorized
                    )
                    Spacer(Modifier.height(8.dp))

                    if (state.filter.query.isBlank() && homeSearches.isNotEmpty()) {
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(homeSearches, key = { it.id }) { search ->
                                Surface(
                                    onClick = { viewModel.setQuery(search.query) },
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                                ) {
                                    Row(
                                        Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Rounded.Bookmark,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            search.name,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                    }

                    if (state.filter.query.isNotBlank()) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = {
                                saveSearchName = state.filter.query
                                showSaveSearch = true
                            }) {
                                Icon(
                                    Icons.Rounded.Bookmark,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("Save search")
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = settings.recoveryUsed,
                        enter = slideInVertically { -it } + fadeIn(),
                        exit = fadeOut()
                    ) {
                        RecoveryBanner(
                            onOpenSecurity = {
                                navController.navigate(Routes.SECURITY) { launchSingleTop = true }
                            },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }

                    if (state.loading) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 2.5.dp
                            )
                        }
                    } else {
                        VaultList(
                            state = state,
                            onOpen = { navController.navigate(Routes.item(it.id)) },
                            onFavorite = viewModel::toggleFavorite,
                            onDelete = viewModel::delete,
                            onPickCollections = { pickerFor = it },
                            onClearFilters = viewModel::clearAll,
                            selectionMode = selectionMode,
                            selectedIds = selectedIds,
                            onToggleSelect = ::toggleSelection,
                            onLongPress = { item ->
                                if (!selectionMode) {
                                    selectionMode = true
                                    selectedIds = setOf(item.id)
                                } else {
                                    toggleSelection(item)
                                }
                            }
                        )
                    }
                }

                AnimatedVisibility(
                    visible = importing,
                    enter = fadeIn(tween(180)),
                    exit = fadeOut(tween(160))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Column(
                                modifier = Modifier.padding(28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.primary,
                                    strokeWidth = 2.5.dp
                                )
                                Spacer(Modifier.height(14.dp))
                                Text(
                                    "Encrypting photo…",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSaveSearch) {
        AlertDialog(
            onDismissRequest = { showSaveSearch = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(28.dp),
            title = { Text("Save smart folder") },
            text = {
                Column {
                    Text(
                        "This runs \"${state.filter.query}\" every time you open it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    LockerTextField(
                        value = saveSearchName,
                        onValueChange = { saveSearchName = it },
                        placeholder = "Banks",
                        label = "Name"
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    app.preferences.setSavedSearchesJson(
                        SavedSearches.add(
                            settings.savedSearchesJson,
                            saveSearchName,
                            state.filter.query
                        )
                    )
                    showSaveSearch = false
                    scope.launch { snackbarHostState.showSnackbar("Smart folder saved") }
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showSaveSearch = false }) { Text("Cancel") }
            }
        )
    }

    if (showAddSheet) {
        SaveToLockerSheet(
            onDismiss = { showAddSheet = false },
            onSelect = { kind ->
                when (kind) {
                    AddKind.PHOTO -> navController.navigate(Routes.IMPORT_PHOTO)
                    AddKind.FILE -> navController.navigate(Routes.IMPORT_FILE)
                    AddKind.NOTE -> navController.navigate(Routes.new(VaultType.NOTE))
                    AddKind.THING -> navController.navigate(Routes.new(VaultType.THING))
                    AddKind.SECRET -> navController.navigate(Routes.new(VaultType.SECRET))
                    AddKind.VOICE -> navController.navigate(Routes.RECORDER)
                    AddKind.SCAN -> navController.navigate(Routes.SCANNER)
                }
            }
        )
    }

    if (showFilterSheet) {
        FilterSheet(
            state = state,
            onDismiss = { showFilterSheet = false },
            onType = viewModel::setType,
            onSort = viewModel::setSortMode,
            onTag = viewModel::setTag,
            onReset = viewModel::clearFilters
        )
    }

    if (showPrivacySheet) {
        PrivacySheet(onDismiss = { showPrivacySheet = false })
    }

    pickerFor?.let { item ->
        CollectionPickerDialog(
            item = item,
            allCollections = state.collections,
            onDismiss = { pickerFor = null },
            onSave = { ids ->
                viewModel.moveToCollection(item, ids)
                pickerFor = null
            }
        )
    }

    if (bulkCollectionOpen) {
        BulkCollectionDialog(
            collections = state.collections,
            onDismiss = { bulkCollectionOpen = false },
            onSave = { ids ->
                viewModel.bulkMove(selectedItems, ids)
                bulkCollectionOpen = false
                exitSelection()
            }
        )
    }

    if (bulkTagOpen) {
        BulkTagDialog(
            onDismiss = { bulkTagOpen = false },
            onSave = { tag ->
                viewModel.bulkTag(selectedItems, tag)
                bulkTagOpen = false
                exitSelection()
            }
        )
    }

    if (bulkDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { bulkDeleteConfirm = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(28.dp),
            title = { Text("Move ${selectedItems.size} items to trash?") },
            text = {
                Text(
                    "They will stay in Trash for 30 days before being deleted permanently.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.bulkDelete(selectedItems)
                    bulkDeleteConfirm = false
                    exitSelection()
                }) {
                    Text("Move to trash", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { bulkDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }

    pendingShare?.let { content ->
        ShareInboxDialog(
            content = content,
            onDismiss = { app.pendingShare.value = null },
            onSaveText = {
                scope.launch {
                    val title = content.text.orEmpty()
                        .lineSequence()
                        .firstOrNull { it.isNotBlank() }
                        ?.trim()
                        ?.take(80)
                        ?.ifBlank { "Shared note" }
                        ?: "Shared note"
                    app.repository.createTextItem(
                        type = VaultType.NOTE,
                        title = title,
                        body = content.text.orEmpty(),
                        tags = listOf("inbox"),
                        collectionIds = emptyList()
                    )
                    app.pendingShare.value = null
                    snackbarHostState.showSnackbar("Saved to your encrypted inbox")
                }
            },
            onSaveFiles = {
                val visual = content.uris.all { uri ->
                    val mime = context.contentResolver.getType(uri).orEmpty()
                    mime.startsWith("image/") || mime.startsWith("video/")
                }
                app.pendingImport.value = content.uris
                app.pendingShare.value = null
                navController.navigate(
                    if (visual) Routes.IMPORT_PHOTO else Routes.IMPORT_FILE
                ) { launchSingleTop = true }
            }
        )
    }
}

@Composable
private fun FilterRow(
    state: VaultUiState,
    onTune: () -> Unit,
    onFavorites: () -> Unit,
    onCollection: (String) -> Unit,
    onUncategorized: () -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        item {
            Surface(
                onClick = onTune,
                shape = CircleShape,
                color = if (state.filter.type != null) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainer
                },
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.Tune,
                        contentDescription = "Filter and sort",
                        tint = if (state.filter.type != null) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(21.dp)
                    )
                }
            }
        }
        item {
            FilterPill(
                label = "Important",
                selected = state.filter.favoritesOnly,
                onClick = onFavorites
            )
        }
        items(state.collections, key = { it.id }) { collection ->
            FilterPill(
                label = collection.name,
                selected = state.filter.collectionId == collection.id,
                onClick = { onCollection(collection.id) }
            )
        }
        item {
            FilterPill(
                label = "Uncategorized",
                selected = state.filter.uncategorizedOnly,
                onClick = onUncategorized
            )
        }
    }
}

@Composable
private fun SelectionBar(
    count: Int,
    onClose: () -> Unit,
    onFavorite: () -> Unit,
    onMove: () -> Unit,
    onTag: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Rounded.Close, contentDescription = "Exit selection")
            }
            Text(
                "$count selected",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onFavorite) {
                Icon(Icons.Rounded.Star, contentDescription = "Mark important")
            }
            IconButton(onClick = onMove) {
                Icon(Icons.Rounded.Folder, contentDescription = "Move to collection")
            }
            IconButton(onClick = onTag) {
                Icon(Icons.Rounded.Sell, contentDescription = "Add tag")
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Rounded.Delete,
                    contentDescription = "Move to trash",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun VaultList(
    state: VaultUiState,
    onOpen: (VaultItem) -> Unit,
    onFavorite: (VaultItem) -> Unit,
    onDelete: (VaultItem) -> Unit,
    onPickCollections: (VaultItem) -> Unit,
    onClearFilters: () -> Unit,
    selectionMode: Boolean,
    selectedIds: Set<String>,
    onToggleSelect: (VaultItem) -> Unit,
    onLongPress: (VaultItem) -> Unit
) {
    if (state.items.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(36.dp))
            if (state.hasActiveFilter) {
                EmptyState(
                    icon = Icons.Rounded.Search,
                    title = if (state.filter.query.isNotBlank()) "No matches" else "Nothing here yet",
                    subtitle = if (state.filter.query.isNotBlank()) {
                        "Try a different word, or clear your filters."
                    } else {
                        "No items match this filter."
                    }
                )
                TextButton(onClick = onClearFilters) {
                    Text("Clear filters", color = MaterialTheme.colorScheme.primary)
                }
            } else {
                EmptyState(
                    icon = Icons.Rounded.Lock,
                    title = "Your vault is empty",
                    subtitle = "Tap + to add a secret, note, photo, or file. Everything is encrypted before it touches the disk."
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 166.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        if (state.hasActiveFilter) {
            item(key = "filter-summary") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        buildString {
                            if (state.filter.favoritesOnly) append("Important · ")
                            if (state.filter.collectionId != null) {
                                append(state.selectedCollection?.name ?: "Collection")
                                append(" · ")
                            }
                            if (state.filter.uncategorizedOnly) append("Uncategorized · ")
                            if (state.filter.type != null) append(state.filter.type?.label).append(" · ")
                            if (state.filter.tag != null) append("#").append(state.filter.tag).append(" · ")
                            append("${state.items.size} shown")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        itemsIndexed(state.items, key = { _, item -> item.id }) { index, item ->
            RevealItem(index = index) {
                VaultItemRow(
                    item = item,
                    onOpen = {
                        if (selectionMode) onToggleSelect(item) else onOpen(item)
                    },
                    onFavorite = { onFavorite(item) },
                    onDelete = { onDelete(item) },
                    onPickCollections = { onPickCollections(item) },
                    selectionMode = selectionMode,
                    selected = item.id in selectedIds,
                    onToggleSelect = { onToggleSelect(item) },
                    onLongPress = { onLongPress(item) }
                )
            }
        }
    }
}

@Composable
private fun RevealItem(index: Int, content: @Composable () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (index < 14) delay(index * 26L)
        visible = true
    }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(300),
        label = "revealAlpha"
    )
    val offset by animateFloatAsState(
        targetValue = if (visible) 0f else 18f,
        animationSpec = tween(340),
        label = "revealOffset"
    )
    Box(
        Modifier
            .graphicsLayer {
                this.alpha = alpha
                translationY = offset.dp.toPx()
            }
    ) {
        content()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VaultItemRow(
    item: VaultItem,
    onOpen: () -> Unit,
    onFavorite: () -> Unit,
    onDelete: () -> Unit,
    onPickCollections: () -> Unit,
    selectionMode: Boolean,
    selected: Boolean,
    onToggleSelect: () -> Unit,
    onLongPress: () -> Unit
) {
    var menuOpen by remember(item.id) { mutableStateOf(false) }
    LockerCard(
        onClick = null,
        shape = RoundedCornerShape(24.dp),
        contentPadding = PaddingValues(horizontal = 13.dp, vertical = 12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    if (selectionMode) onToggleSelect() else onOpen()
                },
                onLongClick = onLongPress
            )
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ItemIcon(item = item, size = 52.dp)
            Spacer(Modifier.width(13.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val subtitle = item.subtitle
                if (subtitle.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (selectionMode) {
                AnimatedCheck(visible = selected)
                Spacer(Modifier.width(8.dp))
            } else {
                if (item.favorite) {
                    Icon(
                        Icons.Rounded.Star,
                        contentDescription = "Important",
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .size(16.dp)
                    )
                }
                Box {
                    IconButton(
                        onClick = { menuOpen = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Rounded.MoreVert,
                            contentDescription = "Item actions",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        DropdownMenuItem(
                            text = { Text("Open") },
                            onClick = { menuOpen = false; onOpen() }
                        )
                        DropdownMenuItem(
                            text = { Text("Select") },
                            leadingIcon = { Icon(Icons.Rounded.Check, contentDescription = null) },
                            onClick = { menuOpen = false; onLongPress() }
                        )
                        DropdownMenuItem(
                            text = { Text(if (item.favorite) "Remove from Important" else "Mark as important") },
                            leadingIcon = {
                                Icon(
                                    if (item.favorite) Icons.Rounded.Star else Icons.Rounded.FavoriteBorder,
                                    contentDescription = null
                                )
                            },
                            onClick = { menuOpen = false; onFavorite() }
                        )
                        DropdownMenuItem(
                            text = { Text("Add to collection") },
                            leadingIcon = { Icon(Icons.Rounded.Folder, contentDescription = null) },
                            onClick = { menuOpen = false; onPickCollections() }
                        )
                        DropdownMenuItem(
                            text = { Text("Move to trash", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = {
                                Icon(
                                    Icons.Rounded.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            },
                            onClick = { menuOpen = false; onDelete() }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FilterSheet(
    state: VaultUiState,
    onDismiss: () -> Unit,
    onType: (VaultType?) -> Unit,
    onSort: (SortMode) -> Unit,
    onTag: (String?) -> Unit,
    onReset: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 22.dp, end = 22.dp, bottom = 24.dp)
        ) {
            Text(
                "Filter & sort",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(22.dp))
            Text(
                "TYPE",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterPill(
                    label = "All",
                    selected = state.filter.type == null,
                    onClick = { onType(null) }
                )
                VaultType.entries.forEach { type ->
                    FilterPill(
                        label = type.label,
                        selected = state.filter.type == type,
                        onClick = { onType(if (state.filter.type == type) null else type) },
                        leadingIcon = type.icon()
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            if (state.allTags.isNotEmpty()) {
                Text(
                    "TAG",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    state.allTags.forEach { tag ->
                        FilterPill(
                            label = "#$tag",
                            selected = state.filter.tag == tag,
                            onClick = { onTag(if (state.filter.tag == tag) null else tag) }
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
            Text(
                "SORT BY",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SortMode.entries.forEach { mode ->
                    FilterPill(
                        label = when (mode) {
                            SortMode.NEWEST -> "Newest"
                            SortMode.OLDEST -> "Oldest"
                            SortMode.NAME -> "A–Z"
                        },
                        selected = state.sortMode == mode,
                        onClick = { onSort(mode) }
                    )
                }
            }
            Spacer(Modifier.height(26.dp))
            TextButton(onClick = { onReset(); onDismiss() }) {
                Text("Reset filters", color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PrivacySheet(onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 22.dp, end = 22.dp, bottom = 28.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.13f),
                    modifier = Modifier.size(52.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(27.dp)
                        )
                    }
                }
                Spacer(Modifier.width(14.dp))
                Text(
                    "Private by design",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "Locker never talks to the internet. There is no account, no sync, no analytics, and no cloud copy of your data.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 21.sp
            )
            Spacer(Modifier.height(20.dp))
            SecurityLine("AES-256-GCM", "Every item and file chunk is authenticated and encrypted.")
            SecurityLine("Argon2id", "Your password becomes a key with 96 MB of memory-hard hashing.")
            SecurityLine("Per-file keys", "Each file gets its own random key, wrapped by your vault key.")
            SecurityLine("Android Keystore", "Biometric unlock uses a hardware-backed, auth-required key.")
            SecurityLine("No network permission", "The app literally cannot open a network connection.")
            Spacer(Modifier.height(12.dp))
            Text(
                "Your password and recovery key are never stored. If you lose both, nobody can recover the vault.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
private fun SecurityLine(title: String, subtitle: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            Icons.Rounded.Check,
            contentDescription = null,
            tint = BrandMint,
            modifier = Modifier
                .padding(top = 1.dp)
                .size(17.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 17.sp
            )
        }
    }
}

@Composable
private fun RecoveryBanner(onOpenSecurity: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = BrandAmber.copy(alpha = 0.11f),
        border = androidx.compose.foundation.BorderStroke(1.dp, BrandAmber.copy(alpha = 0.28f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Rounded.Key,
                contentDescription = null,
                tint = BrandAmber,
                modifier = Modifier.size(19.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Recovered with your recovery key",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "Set a new vault password now.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onOpenSecurity) {
                Text("Fix", color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun CollectionPickerDialog(
    item: VaultItem,
    allCollections: List<app.locker.vault.data.VaultCollection>,
    onDismiss: () -> Unit,
    onSave: (List<String>) -> Unit
) {
    var selected by remember(item.id) { mutableStateOf(item.collectionIds.toSet()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(28.dp),
        title = { Text("Add to collection", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column {
                if (allCollections.isEmpty()) {
                    Text(
                        "No collections yet. Create one from the Collections screen.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    allCollections.forEach { collection ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val isSelected = collection.id in selected
                            Surface(
                                onClick = {
                                    selected = if (isSelected) selected - collection.id
                                    else selected + collection.id
                                },
                                shape = RoundedCornerShape(16.dp),
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainer
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Rounded.Folder,
                                        contentDescription = null,
                                        tint = Color(collection.color),
                                        modifier = Modifier.size(19.dp)
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        collection.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )
                                    AnimatedCheck(visible = isSelected)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(selected.toList()) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    )
}

@Composable
private fun ShareInboxDialog(
    content: SharedContent,
    onDismiss: () -> Unit,
    onSaveText: () -> Unit,
    onSaveFiles: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(28.dp),
        icon = { Icon(Icons.Rounded.Shield, contentDescription = null, tint = BrandMint) },
        title = { Text("Save to Locker", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column {
                Text(
                    "This content is waiting in memory. It will be encrypted and tagged #inbox.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!content.text.isNullOrBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer
                    ) {
                        Text(
                            content.text.orEmpty().take(600),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 8,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
                if (content.uris.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "${content.uris.size} file${if (content.uris.size == 1) "" else "s"} attached",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        },
        confirmButton = {
            Row {
                if (!content.text.isNullOrBlank()) {
                    TextButton(onClick = onSaveText) { Text("Save note") }
                }
                if (content.uris.isNotEmpty()) {
                    TextButton(onClick = onSaveFiles) { Text("Save items") }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun BulkCollectionDialog(
    collections: List<app.locker.vault.data.VaultCollection>,
    onDismiss: () -> Unit,
    onSave: (List<String>) -> Unit
) {
    var selected by remember { mutableStateOf(emptyList<String>()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(28.dp),
        title = { Text("Move to collection", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column {
                if (collections.isEmpty()) {
                    Text(
                        "No collections yet. Create one from the Collections screen.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    collections.forEach { collection ->
                        val isSelected = collection.id in selected
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                onClick = {
                                    selected = if (isSelected) selected - collection.id
                                    else selected + collection.id
                                },
                                shape = RoundedCornerShape(16.dp),
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainer
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Rounded.Folder,
                                        contentDescription = null,
                                        tint = Color(collection.color),
                                        modifier = Modifier.size(19.dp)
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        collection.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )
                                    AnimatedCheck(visible = isSelected)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(selected) }, enabled = selected.isNotEmpty()) {
                Text("Move")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun BulkTagDialog(
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var tag by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(28.dp),
        title = { Text("Add a tag", style = MaterialTheme.typography.headlineSmall) },
        text = {
            LockerTextField(
                value = tag,
                onValueChange = { tag = it },
                placeholder = "e.g. travel, work, family",
                label = "Tag",
                required = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(tag) },
                enabled = tag.isNotBlank()
            ) { Text("Add tag") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun TamperWarningBanner(onDismiss: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = BrandAmber.copy(alpha = 0.14f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Rounded.Warning,
                contentDescription = null,
                tint = BrandAmber,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Database integrity warning",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "vault.db differs from the last clean lock. It may have been replaced, restored, or rolled back.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }
            TextButton(onClick = onDismiss) { Text("Dismiss") }
        }
    }
}
