package app.locker.vault.ui

import androidx.compose.runtime.staticCompositionLocalOf
import app.locker.vault.AppContainer

val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("AppContainer was not provided")
}
