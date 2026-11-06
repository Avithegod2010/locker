package app.locker.vault.ui.screens.viewer

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.locker.vault.ui.LocalAppContainer
import app.locker.vault.ui.components.BackTopBar
import app.locker.vault.ui.components.EmptyState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun PdfViewerScreen(itemId: String, onBack: () -> Unit) {
    val app = LocalAppContainer.current
    val items by app.repository.items.collectAsStateWithLifecycle()
    val item = remember(itemId, items) { items.firstOrNull { it.id == itemId } }
    var pages by remember { mutableStateOf<List<Bitmap>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var totalPages by remember { mutableStateOf(0) }
    var tempFile by remember { mutableStateOf<File?>(null) }

    LaunchedEffect(itemId) {
        val current = item
        if (current == null) {
            error = "This document isn't available."
            return@LaunchedEffect
        }
        val file = withContext(Dispatchers.IO) {
            runCatching { app.repository.decryptToShareCache(current) }.getOrNull()
        }
        if (file == null) {
            error = "Could not decrypt this PDF."
            return@LaunchedEffect
        }
        tempFile = file
        val result = withContext(Dispatchers.IO) {
            runCatching {
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                    PdfRenderer(descriptor).use { renderer ->
                        totalPages = renderer.pageCount
                        val output = mutableListOf<Bitmap>()
                        val limit = minOf(renderer.pageCount, MAX_PAGES)
                        for (index in 0 until limit) {
                            renderer.openPage(index).use { page ->
                                val width = 1000
                                val height = (width.toFloat() / page.width * page.height).toInt()
                                    .coerceAtLeast(1)
                                val bitmap = Bitmap.createBitmap(
                                    width,
                                    height,
                                    Bitmap.Config.ARGB_8888
                                )
                                bitmap.eraseColor(Color.WHITE)
                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                output += bitmap
                            }
                        }
                        output
                    }
                }
            }
        }
        result.onSuccess { pages = it; error = null }
            .onFailure { error = it.message ?: "Could not render this PDF" }
    }

    DisposableEffect(Unit) {
        onDispose {
            tempFile?.delete()
            pages?.forEach { it.recycle() }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        BackTopBar(title = item?.title ?: "PDF", onBack = onBack)
        when {
            error != null -> EmptyState(
                icon = Icons.Rounded.Description,
                title = "Could not open PDF",
                subtitle = error ?: ""
            )

            pages == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }

            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(pages ?: emptyList()) { index, bitmap ->
                    androidx.compose.foundation.layout.Column {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Page ${index + 1}",
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceContainer)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Page ${index + 1}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }
                if (totalPages > MAX_PAGES) {
                    item {
                        Text(
                            "Showing the first $MAX_PAGES of $totalPages pages.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }
        }
    }
}

private const val MAX_PAGES = 10
