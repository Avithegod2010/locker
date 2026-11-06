package app.locker.vault.ui.screens.viewer

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.locker.vault.ui.LocalAppContainer
import app.locker.vault.ui.components.decodeSampledBitmap
import app.locker.vault.ui.theme.BrandMint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun SlideshowScreen(onBack: () -> Unit) {
    val app = LocalAppContainer.current
    val items by app.repository.items.collectAsStateWithLifecycle()
    val photos = remember(items) {
        items.filter { it.deletedAt == null && it.isImage && it.isFileBacked }
            .sortedByDescending { it.createdAt }
    }
    if (photos.isEmpty()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No photos to show yet.", color = Color.White)
                Spacer(Modifier.height(12.dp))
                IconButton(onClick = onBack) {
                    Icon(Icons.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
            }
        }
        return
    }

    val pagerState = rememberPagerState(pageCount = { photos.size })
    LaunchedEffect(pagerState.currentPage, photos.size) {
        delay(4_500)
        if (pagerState.currentPage < photos.lastIndex) {
            pagerState.animateScrollToPage(pagerState.currentPage + 1)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val item = photos[page]
            var bitmap by remember(item.id) { mutableStateOf<Bitmap?>(null) }
            var failed by remember(item.id) { mutableStateOf(false) }
            LaunchedEffect(item.id) {
                val bytes = runCatching { app.repository.readImage(item) }.getOrNull()
                bitmap = bytes?.let { withContext(Dispatchers.Default) { decodeSampledBitmap(it, 2200, 2200) } }
                failed = bitmap == null
            }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val current = bitmap
                if (current != null) {
                    Image(
                        bitmap = current.asImageBitmap(),
                        contentDescription = item.title,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (failed) {
                    Text("Could not open this photo", color = Color.White.copy(alpha = 0.7f))
                } else {
                    CircularProgressIndicator(color = BrandMint, strokeWidth = 2.5.dp)
                }
                Text(
                    item.title,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.35f))
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                )
            }
        }

        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 36.dp, start = 12.dp)
                .background(Color.Black.copy(alpha = 0.4f), CircleShape)
        ) {
            Icon(Icons.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White)
        }

        Text(
            "${pagerState.currentPage + 1} / ${photos.size}",
            color = Color.White.copy(alpha = 0.75f),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 54.dp, end = 22.dp)
        )
    }
}
