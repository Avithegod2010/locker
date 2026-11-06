package app.locker.vault

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import app.locker.vault.core.UnlockResult
import app.locker.vault.ui.components.BiometricPromptHelper

/**
 * A tiny transparent FragmentActivity used only because BiometricPrompt requires a
 * FragmentActivity. The main UI is a ComponentActivity so the Activity Result API works
 * correctly on Android versions that reserve the upper 16 bits of request codes.
 */
class BiometricAuthActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        if (savedInstanceState != null) return
        val container = (application as LockerApp).container
        if (intent.getStringExtra(EXTRA_MODE) == MODE_ENROLL) {
            container.session.beginExternalActivity()
        }
        when (intent.getStringExtra(EXTRA_MODE)) {
            MODE_ENROLL -> {
                val cipher = runCatching { container.session.prepareBiometricEnrollmentCipher() }
                    .getOrNull()
                if (cipher == null) {
                    finishWith(false, "Biometric unlock is unavailable on this device")
                    return
                }
                BiometricPromptHelper.authenticate(
                    activity = this,
                    cipher = cipher,
                    title = "Enable biometric unlock",
                    subtitle = "Confirm your fingerprint to protect this vault",
                    onSuccess = { authenticated ->
                        val ok = container.session.completeBiometricEnrollment(authenticated)
                        finishWith(ok, if (ok) "Biometric unlock enabled" else "Could not save the biometric key")
                    },
                    onCancelled = { finishWith(false, "Cancelled") },
                    onFailure = { finishWith(false, it) }
                )
            }
            MODE_UNLOCK -> {
                val cipher = container.session.prepareBiometricUnlockCipher()
                if (cipher == null) {
                    finishWith(false, "Biometric unlock is not set up")
                    return
                }
                BiometricPromptHelper.authenticate(
                    activity = this,
                    cipher = cipher,
                    title = "Unlock Locker",
                    subtitle = "Confirm your identity to open the vault",
                    onSuccess = { authenticated ->
                        when (val result = container.session.unlockWithBiometricCipher(authenticated)) {
                            UnlockResult.Success -> finishWith(true, null)
                            is UnlockResult.Failure -> finishWith(false, result.message)
                            else -> finishWith(false, "Biometric unlock failed")
                        }
                    },
                    onCancelled = { finishWith(false, "Cancelled") },
                    onFailure = { finishWith(false, it) }
                )
            }
            else -> finishWith(false, "Unknown biometric request")
        }
    }

    private fun finishWith(success: Boolean, message: String?) {
        if (intent.getStringExtra(EXTRA_MODE) == MODE_ENROLL) {
            (application as LockerApp).container.session.endExternalActivity()
        }
        setResult(
            if (success) RESULT_OK else RESULT_CANCELED,
            android.content.Intent().putExtra(EXTRA_MESSAGE, message)
        )
        finish()
    }

    companion object {
        const val EXTRA_MODE = "mode"
        const val EXTRA_MESSAGE = "message"
        const val MODE_ENROLL = "enroll"
        const val MODE_UNLOCK = "unlock"
    }
}
