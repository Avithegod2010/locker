package app.locker.vault.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.locker.vault.AppContainer
import app.locker.vault.core.DisguiseManager
import app.locker.vault.core.SessionState
import app.locker.vault.ui.navigation.LockerNavHost
import app.locker.vault.ui.screens.disguise.CalculatorScreen
import app.locker.vault.ui.screens.lock.LockScreen
import app.locker.vault.ui.screens.onboarding.BiometricSetupScreen
import app.locker.vault.ui.screens.onboarding.OnboardingScreen
import app.locker.vault.ui.screens.onboarding.RecoveryKeyScreen
import app.locker.vault.ui.theme.LockerTheme

@Composable
fun LockerRoot(container: AppContainer) {
    val settings by container.preferences.settings.collectAsStateWithLifecycle()
    val sessionState by container.session.state.collectAsStateWithLifecycle()
    var calculatorPassed by remember { mutableStateOf(false) }

    LaunchedEffect(sessionState) {
        if (sessionState != SessionState.LOCKED) calculatorPassed = false
        if (sessionState == SessionState.UNLOCKED) {
            container.autoBackup.maybeRun()
        }
    }

    LockerTheme(settings = settings) {
        CompositionLocalProvider(LocalAppContainer provides container) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                val calculatorGate = sessionState == SessionState.LOCKED &&
                    settings.disguiseMode == DisguiseManager.ID_CALCULATOR &&
                    !calculatorPassed

                if (calculatorGate) {
                    CalculatorScreen(onUnlock = { calculatorPassed = true })
                } else {
                    AnimatedContent(
                        targetState = sessionState,
                        transitionSpec = {
                            (fadeIn(tween(260)) + scaleIn(tween(300), initialScale = 0.985f))
                                .togetherWith(fadeOut(tween(190)) + scaleOut(tween(220), targetScale = 1.01f))
                        },
                        label = "session"
                    ) { state ->
                        when (state) {
                            SessionState.SETUP -> OnboardingScreen()
                            SessionState.RECOVERY -> RecoveryKeyScreen()
                            SessionState.BIOMETRIC_SETUP -> BiometricSetupScreen()
                            SessionState.LOCKED -> LockScreen()
                            SessionState.UNLOCKED -> LockerNavHost()
                        }
                    }
                }
            }
        }
    }
}
