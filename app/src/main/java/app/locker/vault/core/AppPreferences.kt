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
    val accentId: String = Accent.ID_DEFAULT,
    val trueBlack: Boolean = false,
    val blockScreenshots: Boolean = true,
    val autoLockSeconds: Int = 60,
    val clipboardClearSeconds: Int = 30,
    val biometricEnabled: Boolean = false,
    val sortMode: SortMode = SortMode.NEWEST,
    val haptics: Boolean = true,
    val autoPurgeTrash: Boolean = true,
    val wipeAfterAttempts: Int = 0,
    val recoveryUsed: Boolean = false,
    val disguiseMode: String = DisguiseManager.ID_NONE,
    val generatorLength: Int = 22,
    val generatorDigits: Boolean = true,
    val generatorSymbols: Boolean = true,
    val generatorNoAmbiguous: Boolean = true,
    val calculatorCodeHash: String = "",
    val savedSearchesJson: String = "[]",
    val autoBackupEnabled: Boolean = false,
    val autoBackupTreeUri: String = "",
    val autoBackupKeep: Int = 5,
    val autoBackupLastAt: Long = 0L,
    val duressSalt: String = "",
    val duressVerifier: String = "",
    val duressFastVerifier: Boolean = false,
    val legacyVideosMigrated: Boolean = false
)

/** Launcher disguise chosen by the user; see [DisguiseManager]. */
object Disguise {
    const val ID_NONE = "none"
    const val ID_CALCULATOR = "calculator"
    const val ID_CALENDAR = "calendar"
    const val ID_NOTES = "notes"
    const val ID_DEFAULT = ID_NONE
}

object Accent {
    const val ID_DEFAULT = "blue"
}

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
        accentId = prefs.getString("accent_id", null) ?: Accent.ID_DEFAULT,
        trueBlack = prefs.getBoolean("true_black", false),
        blockScreenshots = prefs.getBoolean("block_screenshots", true),
        autoLockSeconds = prefs.getInt("auto_lock_seconds", 60),
        clipboardClearSeconds = prefs.getInt("clipboard_clear_seconds", 30),
        biometricEnabled = prefs.getBoolean("biometric_enabled", false),
        sortMode = SortMode.from(prefs.getString("sort_mode", null)),
        haptics = prefs.getBoolean("haptics", true),
        autoPurgeTrash = prefs.getBoolean("auto_purge_trash", true),
        wipeAfterAttempts = prefs.getInt("wipe_after_attempts", 0),
        recoveryUsed = prefs.getBoolean("recovery_used", false),
        disguiseMode = prefs.getString("disguise_mode", null) ?: Disguise.ID_DEFAULT,
        generatorLength = prefs.getInt("generator_length", 22),
        generatorDigits = prefs.getBoolean("generator_digits", true),
        generatorSymbols = prefs.getBoolean("generator_symbols", true),
        generatorNoAmbiguous = prefs.getBoolean("generator_no_ambiguous", true),
        calculatorCodeHash = prefs.getString("calculator_code_hash", null).orEmpty(),
        savedSearchesJson = prefs.getString("saved_searches", null) ?: "[]",
        autoBackupEnabled = prefs.getBoolean("auto_backup_enabled", false),
        autoBackupTreeUri = prefs.getString("auto_backup_tree", null).orEmpty(),
        autoBackupKeep = prefs.getInt("auto_backup_keep", 5),
        autoBackupLastAt = prefs.getLong("auto_backup_last_at", 0L),
        duressSalt = prefs.getString("duress_salt", null).orEmpty(),
        duressVerifier = prefs.getString("duress_verifier", null).orEmpty(),
        duressFastVerifier = prefs.getBoolean("duress_fast_verifier", false),
        legacyVideosMigrated = prefs.getBoolean("legacy_videos_migrated", false)
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
    fun setAccent(value: String) = update { putString("accent_id", value) }
    fun setTrueBlack(value: Boolean) = update { putBoolean("true_black", value) }
    fun setDisguiseMode(value: String) = update { putString("disguise_mode", value) }
    fun setGeneratorLength(value: Int) = update { putInt("generator_length", value.coerceIn(10, 64)) }
    fun setGeneratorDigits(value: Boolean) = update { putBoolean("generator_digits", value) }
    fun setGeneratorSymbols(value: Boolean) = update { putBoolean("generator_symbols", value) }
    fun setGeneratorNoAmbiguous(value: Boolean) = update { putBoolean("generator_no_ambiguous", value) }
    fun setWipeAfterAttempts(value: Int) = update { putInt("wipe_after_attempts", value) }
    fun setRecoveryUsed(value: Boolean) = update { putBoolean("recovery_used", value) }

    fun setCalculatorCode(code: String) =
        update { putString("calculator_code_hash", hashCalculatorCode(code)) }

    fun hasCustomCalculatorCode(): Boolean = settings.value.calculatorCodeHash.isNotBlank()

    fun calculatorCodeMatches(candidate: String): Boolean {
        val stored = settings.value.calculatorCodeHash
            .ifBlank { hashCalculatorCode(DEFAULT_CALCULATOR_CODE) }
        return stored == hashCalculatorCode(candidate)
    }

    fun setSavedSearchesJson(json: String) = update { putString("saved_searches", json) }

    fun setAutoBackupEnabled(value: Boolean) = update { putBoolean("auto_backup_enabled", value) }
    fun setAutoBackupTreeUri(value: String) = update { putString("auto_backup_tree", value) }
    fun setAutoBackupKeep(value: Int) = update { putInt("auto_backup_keep", value.coerceIn(2, 20)) }
    fun setAutoBackupLastAt(value: Long) = update { putLong("auto_backup_last_at", value) }

    fun setDuressCredentials(salt: String, verifier: String) = update {
        putString("duress_salt", salt)
        putString("duress_verifier", verifier)
    }

    fun setDuressFastVerifier(value: Boolean) =
        update { putBoolean("duress_fast_verifier", value) }

    fun setLegacyVideosMigrated(value: Boolean) =
        update { putBoolean("legacy_videos_migrated", value) }

    fun clearDuressCredentials() = update {
        remove("duress_salt")
        remove("duress_verifier")
        remove("duress_fast_verifier")
    }

    private fun hashCalculatorCode(value: String): String =
        Crypto.sha256(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    fun failedAttempts(scope: String = "main"): Int {
        val key = if (scope == "main") "failed_attempts" else "failed_attempts_$scope"
        return prefs.getInt(key, 0)
    }

    /**
     * commit() is deliberate: a wrong-password counter must survive an immediate process kill,
     * otherwise force-stopping the app could bypass the erase-after-N-attempts protection.
     */
    fun setFailedAttempts(value: Int, scope: String = "main") {
        val key = if (scope == "main") "failed_attempts" else "failed_attempts_$scope"
        prefs.edit().putInt(key, value).commit()
        _settings.value = read()
    }

    fun reset() {
        prefs.edit().clear().apply()
        _settings.value = read()
    }

    companion object {
        const val DEFAULT_CALCULATOR_CODE = "1234"
    }
}
