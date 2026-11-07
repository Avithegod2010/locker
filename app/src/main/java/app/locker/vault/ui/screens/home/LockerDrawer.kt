package app.locker.vault.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sell
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.locker.vault.core.Formatting
import app.locker.vault.ui.components.DashedAddChip
import app.locker.vault.ui.components.IconTile
import app.locker.vault.ui.components.LockerCard
import app.locker.vault.ui.navigation.Routes
import app.locker.vault.ui.theme.BrandAmber
import app.locker.vault.ui.theme.BrandBlue
import app.locker.vault.ui.theme.BrandCoral
import app.locker.vault.ui.theme.BrandMint
import app.locker.vault.ui.theme.BrandViolet

@Composable
fun DrawerContent(
    profileName: String,
    profileEmail: String,
    itemCount: Int,
    totalBytes: Long,
    onClose: () -> Unit,
    onSearch: () -> Unit,
    onNavigate: (String) -> Unit,
    onLock: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(342.dp)
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) {
                Icon(
                    Icons.Rounded.ArrowBack,
                    contentDescription = "Close menu",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = profileEmail.ifBlank { profileName },
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.4).sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onSearch) {
                Icon(
                    Icons.Rounded.Search,
                    contentDescription = "Search vault",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        Spacer(Modifier.height(10.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 12.dp)
        ) {
            StatsCard(itemCount = itemCount, totalBytes = totalBytes)
            Spacer(Modifier.height(14.dp))

        LockerCard(
            onClick = { onNavigate(Routes.LEGACY) },
            shape = RoundedCornerShape(24.dp),
            contentPadding = PaddingValues(horizontal = 17.dp, vertical = 17.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(
                    icon = Icons.Rounded.FavoriteBorder,
                    tint = BrandCoral,
                    size = 42.dp,
                    iconSize = 21.dp
                )
                Spacer(Modifier.width(14.dp))
                Text(
                    "Legacy",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HalfCard(
                icon = Icons.Rounded.Folder,
                tint = BrandMint,
                title = "Collections",
                onClick = { onNavigate(Routes.COLLECTIONS) },
                modifier = Modifier.weight(1f)
            )
            HalfCard(
                icon = Icons.Rounded.Delete,
                tint = BrandCoral,
                title = "Trash",
                onClick = { onNavigate(Routes.TRASH) },
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(18.dp))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DrawerRow(Icons.Rounded.Person, "Account", { onNavigate(Routes.ACCOUNT) })
            DrawerRow(Icons.Rounded.Sell, "Tags", { onNavigate(Routes.TAGS) })
            DrawerRow(Icons.Rounded.Archive, "Archive", { onNavigate(Routes.ARCHIVE) })
            DrawerRow(Icons.Rounded.PlayCircle, "Photo slideshow", { onNavigate(Routes.SLIDESHOW) })
            DrawerRow(Icons.Rounded.Shield, "Security", { onNavigate(Routes.SECURITY) })
            DrawerRow(Icons.Rounded.Palette, "Appearance", { onNavigate(Routes.APPEARANCE) })
            DrawerRow(Icons.Rounded.Settings, "General", { onNavigate(Routes.GENERAL) })
            DrawerRow(Icons.Rounded.Info, "About", { onNavigate(Routes.ABOUT) })
        }

        Spacer(Modifier.height(18.dp))
        LockerCard(
            onClick = onLock,
            shape = RoundedCornerShape(22.dp),
            contentPadding = PaddingValues(horizontal = 17.dp, vertical = 14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(19.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    "Lock vault now",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Locker 1.5.0 · No network permission",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        }
    }
}

@Composable
private fun StatsCard(itemCount: Int, totalBytes: Long) {
    val progress = (totalBytes / (1024f * 1024f * 1024f)).coerceIn(0.015f, 1f)
    Surface(
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .drawBehind {
                    val step = 22.dp.toPx()
                    val radius = 1.1.dp.toPx()
                    val dotColor = Color.White.copy(alpha = 0.055f)
                    var y = step / 2
                    while (y < size.height) {
                        var x = step / 2
                        while (x < size.width) {
                            drawCircle(dotColor, radius, Offset(x, y))
                            x += step
                        }
                        y += step
                    }
                }
                .padding(20.dp)
        ) {
            Column {
                Text(
                    "Items stored",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "$itemCount",
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 34.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (itemCount == 1) "item" else "items",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
                Spacer(Modifier.height(14.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceContainerHighest,
                            RoundedCornerShape(3.dp)
                        )
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(progress)
                            .height(5.dp)
                            .background(
                                MaterialTheme.colorScheme.primary,
                                RoundedCornerShape(3.dp)
                            )
                    )
                }
                Spacer(Modifier.height(9.dp))
                Text(
                    if (totalBytes > 0) {
                        "${Formatting.bytes(totalBytes)} encrypted on this device"
                    } else {
                        "All items encrypted on this device"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun HalfCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    LockerCard(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 15.dp),
        modifier = modifier
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(21.dp)
            )
            Spacer(Modifier.width(9.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun DrawerRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    onClick: () -> Unit
) {
    val tint = when (title) {
        "Security" -> BrandBlue
        "Appearance" -> BrandViolet
        "General" -> BrandAmber
        "Account" -> BrandMint
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    LockerCard(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        contentPadding = PaddingValues(horizontal = 15.dp, vertical = 10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(icon = icon, tint = tint, size = 38.dp, iconSize = 19.dp)
            Spacer(Modifier.width(13.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
