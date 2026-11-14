package app.locker.vault.core

/**
 * Quick-start trial vault. The password is intentionally public and must be changed before
 * the vault is used for anything real; the UI keeps reminding the user until it is.
 */
object TrialVault {
    const val DEFAULT_PASSWORD = "Qwerty1234567"

    fun isDefaultPassword(value: String): Boolean = value == DEFAULT_PASSWORD
}
