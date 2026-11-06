package app.locker.vault.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Work
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import app.locker.vault.ui.theme.BrandAmber
import app.locker.vault.ui.theme.BrandBlue
import app.locker.vault.ui.theme.BrandCoral
import app.locker.vault.ui.theme.BrandMint
import app.locker.vault.ui.theme.BrandViolet

data class CollectionIconOption(val key: String, val icon: ImageVector)

val collectionIcons = listOf(
    CollectionIconOption("folder", Icons.Rounded.Folder),
    CollectionIconOption("star", Icons.Rounded.Star),
    CollectionIconOption("lock", Icons.Rounded.Lock),
    CollectionIconOption("work", Icons.Rounded.Work),
    CollectionIconOption("person", Icons.Rounded.Person),
    CollectionIconOption("key", Icons.Rounded.Key),
    CollectionIconOption("image", Icons.Rounded.Image),
    CollectionIconOption("note", Icons.Rounded.Description)
)

val collectionColors = listOf(
    BrandBlue,
    BrandMint,
    BrandAmber,
    BrandViolet,
    BrandCoral,
    Color(0xFF7FD4E8),
    Color(0xFFF08FC8),
    Color(0xFFB7C46B)
)

fun collectionIcon(key: String): ImageVector =
    collectionIcons.firstOrNull { it.key == key }?.icon ?: Icons.Rounded.Folder
