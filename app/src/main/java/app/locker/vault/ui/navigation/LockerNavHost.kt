package app.locker.vault.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.locker.vault.data.VaultType
import app.locker.vault.ui.screens.archive.ArchiveScreen
import app.locker.vault.ui.screens.collections.CollectionDetailScreen
import app.locker.vault.ui.screens.collections.CollectionsScreen
import app.locker.vault.ui.screens.camera.SecureCameraScreen
import app.locker.vault.ui.screens.detail.ItemDetailScreen
import app.locker.vault.ui.screens.editor.ImportScreen
import app.locker.vault.ui.screens.editor.ImageEditorScreen
import app.locker.vault.ui.screens.editor.TextEditorScreen
import app.locker.vault.ui.screens.home.HomeScreen
import app.locker.vault.ui.screens.legacy.LegacyScreen
import app.locker.vault.ui.screens.settings.AboutScreen
import app.locker.vault.ui.screens.settings.AccountScreen
import app.locker.vault.ui.screens.settings.AppearanceSettingsScreen
import app.locker.vault.ui.screens.settings.BackupScreen
import app.locker.vault.ui.screens.settings.GeneralSettingsScreen
import app.locker.vault.ui.screens.settings.PasswordHealthScreen
import app.locker.vault.ui.screens.settings.SecuritySettingsScreen
import app.locker.vault.ui.screens.trash.TrashScreen
import app.locker.vault.ui.screens.viewer.PhotoViewerScreen
import app.locker.vault.ui.screens.viewer.PdfViewerScreen
import app.locker.vault.ui.screens.viewer.SlideshowScreen
import app.locker.vault.ui.screens.viewer.VideoPlayerScreen

@Composable
fun LockerNavHost() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        enterTransition = {
            slideInHorizontally(tween(300)) { it / 7 } + fadeIn(tween(260))
        },
        exitTransition = {
            slideOutHorizontally(tween(260)) { -it / 12 } + fadeOut(tween(180))
        },
        popEnterTransition = {
            slideInHorizontally(tween(300)) { -it / 7 } + fadeIn(tween(240))
        },
        popExitTransition = {
            slideOutHorizontally(tween(260)) { it / 12 } + fadeOut(tween(180))
        }
    ) {
        composable(Routes.HOME) {
            HomeScreen(navController = navController)
        }
        composable(Routes.COLLECTIONS) {
            CollectionsScreen(
                onBack = { navController.popBackStack() },
                onOpenCollection = { id -> navController.navigate("collection/$id") }
            )
        }
        composable(
            route = "collection/{id}",
            arguments = listOf(navArgument("id") { type = NavType.StringType })
        ) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            CollectionDetailScreen(
                collectionId = id,
                onBack = { navController.popBackStack() },
                onOpenItem = { itemId -> navController.navigate(Routes.item(itemId)) }
            )
        }
        composable(Routes.TRASH) {
            TrashScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.ARCHIVE) {
            ArchiveScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.LEGACY) {
            LegacyScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.ACCOUNT) {
            AccountScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SECURITY) {
            SecuritySettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenBackup = { navController.navigate(Routes.BACKUP) },
                onOpenPasswordHealth = { navController.navigate(Routes.PASSWORD_HEALTH) }
            )
        }
        composable(Routes.BACKUP) {
            BackupScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.PASSWORD_HEALTH) {
            PasswordHealthScreen(
                onBack = { navController.popBackStack() },
                onOpenItem = { navController.navigate(Routes.item(it.id)) }
            )
        }
        composable(Routes.APPEARANCE) {
            AppearanceSettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.GENERAL) {
            GeneralSettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.ABOUT) {
            AboutScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.IMPORT_PHOTO) {
            ImportScreen(
                type = VaultType.PHOTO,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.IMPORT_FILE) {
            ImportScreen(
                type = VaultType.FILE,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.CAMERA) {
            SecureCameraScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SLIDESHOW) {
            SlideshowScreen(onBack = { navController.popBackStack() })
        }
        composable(
            route = Routes.NEW,
            arguments = listOf(navArgument("type") { type = NavType.StringType })
        ) { entry ->
            val type = runCatching {
                VaultType.valueOf(entry.arguments?.getString("type").orEmpty())
            }.getOrDefault(VaultType.NOTE)
            TextEditorScreen(
                type = type,
                itemId = null,
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            route = Routes.EDIT,
            arguments = listOf(navArgument("id") { type = NavType.StringType })
        ) { entry ->
            TextEditorScreen(
                type = VaultType.SECRET,
                itemId = entry.arguments?.getString("id"),
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            route = Routes.ITEM,
            arguments = listOf(navArgument("id") { type = NavType.StringType })
        ) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            ItemDetailScreen(
                itemId = id,
                onBack = { navController.popBackStack() },
                onEdit = { navController.navigate(Routes.edit(it.id)) },
                onViewPhoto = { navController.navigate(Routes.viewer(it.id)) },
                onViewVideo = { navController.navigate(Routes.video(it.id)) },
                onViewPdf = { navController.navigate(Routes.pdf(it.id)) },
                onEditImage = { navController.navigate(Routes.editImage(it.id)) },
                onOpenAttachment = { navController.navigate(Routes.item(it.id)) }
            )
        }
        composable(
            route = Routes.VIDEO,
            arguments = listOf(navArgument("id") { type = NavType.StringType })
        ) { entry ->
            VideoPlayerScreen(
                itemId = entry.arguments?.getString("id").orEmpty(),
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            route = Routes.PDF,
            arguments = listOf(navArgument("id") { type = NavType.StringType })
        ) { entry ->
            PdfViewerScreen(
                itemId = entry.arguments?.getString("id").orEmpty(),
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            route = Routes.EDIT_IMAGE,
            arguments = listOf(navArgument("id") { type = NavType.StringType })
        ) { entry ->
            ImageEditorScreen(
                itemId = entry.arguments?.getString("id").orEmpty(),
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            route = Routes.VIEWER,
            arguments = listOf(navArgument("id") { type = NavType.StringType })
        ) { entry ->
            PhotoViewerScreen(
                itemId = entry.arguments?.getString("id").orEmpty(),
                onBack = { navController.popBackStack() }
            )
        }
    }
}
