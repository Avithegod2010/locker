package app.locker.vault.core

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode(val storageValue: String) {
    SYSTEM("system"), LIGHT("light"), DARK("dark");

    companion object {
        fun from(value: String?): ThemeMode =
            entries.firstOrNull { it.storageValue == value } ?: DARK
    }
}

enum class SortMode(val storageValue: String) {
    NEWEST("newest"), OLDEST("oldest"), NAME("name");

    companion object {
        fun from(value: String?): SortMode =
            entries.firstOrNull { it.storageValue == value } ?: NEWEST
    }
}

data class AppSettings(
    val onboardingComplete: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val dynamicColor: Boolean = false,
    val blockScreenshots: Boolean = true,
    val autoLockSeconds: Int = 60,
    val clipboardClearSeconds: Int = 30,
    val biometricEnabled: Boolean = false,
    val sortMode: SortMode = SortMode.NEWEST,
    val haptics: Boolean = true,
    val autoPurgeTrash: Boolean = true,
    val wipeAfterAttempts: Int = 0,
    val recoveryUsed: Boolean = false
)

/**
 * Non-secret preferences. Secret material lives in [KeyMaterialStore] and is always wrapped.
 */
class AppPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("locker_settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private fun read(): AppSettings = AppSettings(
        onboardingComplete = prefs.getBoolean("onboarding_complete", false),
        themeMode = ThemeMode.from(prefs.getString("theme_mode", null)),
        dynamicColor = prefs.getBoolean("dynamic_color", false),
        blockScreenshots = prefs.getBoolean("block_screenshots", true),
        autoLockSeconds = prefs.getInt("auto_lock_seconds", 60),
        clipboardClearSeconds = prefs.getInt("clipboard_clear_seconds", 30),
        biometricEnabled = prefs.getBoolean("biometric_enabled", false),
        sortMode = SortMode.from(prefs.getString("sort_mode", null)),
        haptics = prefs.getBoolean("haptics", true),
        autoPurgeTrash = prefs.getBoolean("auto_purge_trash", true),
        wipeAfterAttempts = prefs.getInt("wipe_after_attempts", 0),
        recoveryUsed = prefs.getBoolean("recovery_used", false)
    )

    private fun update(block: android.content.SharedPreferences.Editor.() -> Unit) {
        prefs.edit().apply(block).apply()
        _settings.value = read()
    }

    fun setOnboardingComplete(value: Boolean) = update { putBoolean("onboarding_complete", value) }
    fun setThemeMode(mode: ThemeMode) = update { putString("theme_mode", mode.storageValue) }
    fun setDynamicColor(value: Boolean) = update { putBoolean("dynamic_color", value) }
    fun setBlockScreenshots(value: Boolean) = update { putBoolean("block_screenshots", value) }
    fun setAutoLockSeconds(value: Int) = update { putInt("auto_lock_seconds", value) }
    fun setClipboardClearSeconds(value: Int) = update { putInt("clipboard_clear_seconds", value) }
    fun setBiometricEnabled(value: Boolean) = update { putBoolean("biometric_enabled", value) }
    fun setSortMode(mode: SortMode) = update { putString("sort_mode", mode.storageValue) }
    fun setHaptics(value: Boolean) = update { putBoolean("haptics", value) }
    fun setAutoPurgeTrash(value: Boolean) = update { putBoolean("auto_purge_trash", value) }
    fun setWipeAfterAttempts(value: Int) = update { putInt("wipe_after_attempts", value) }
    fun setRecoveryUsed(value: Boolean) = update { putBoolean("recovery_used", value) }

    fun failedAttempts(): Int = prefs.getInt("failed_attempts", 0)

    /**
     * commit() is deliberate: a wrong-password counter must survive an immediate process kill,
     * otherwise force-stopping the app could bypass the erase-after-N-attempts protection.
     */
    fun setFailedAttempts(value: Int) {
        prefs.edit().putInt("failed_attempts", value).commit()
        _settings.value = read()
    }

    fun reset() {
        prefs.edit().clear().apply()
        _settings.value = read()
    }
}
