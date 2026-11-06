package app.locker.vault.ui.screens.lock

import androidx.compose.animation.AnimatedVisibility
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.password
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.locker.vault.BiometricAuthActivity
import app.locker.vault.R
import app.locker.vault.core.Formatting
import app.locker.vault.core.UnlockResult
import app.locker.vault.ui.LocalAppContainer
import app.locker.vault.ui.components.LockerTextField
import app.locker.vault.ui.components.PrimaryButton
import app.locker.vault.ui.components.SecureWindow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sin

@Composable
fun LockScreen() {
    val app = LocalAppContainer.current
    val context = LocalContext.current
    SecureWindow()
    val settings by app.preferences.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var password by remember { mutableStateOf("") }
    var reveal by rememberSaveable { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var recoveryOpen by remember { mutableStateOf(false) }
    var recoveryCode by remember { mutableStateOf("") }
    var recoveryLoading by remember { mutableStateOf(false) }
    var recoveryError by remember { mutableStateOf<String?>(null) }
    var biometricPromptShown by remember { mutableStateOf(false) }
    val shake = remember { Animatable(0f) }

    val canUseBiometrics = remember {
        settings.biometricEnabled && app.biometricKeyStore.canAuthenticate()
    }

    fun shakeError(message: String) {
        error = message
        scope.launch {
            shake.snapTo(1f)
            shake.animateTo(0f, tween(420))
        }
    }

    val biometricLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val message = result.data?.getStringExtra(BiometricAuthActivity.EXTRA_MESSAGE)
        if (result.resultCode != android.app.Activity.RESULT_OK &&
            message != null && message != "Cancelled"
        ) {
            shakeError(message)
        }
        biometricPromptShown = false
    }

    fun unlockWithPassword() {
        if (password.isBlank() || loading) return
        loading = true
        error = null
        scope.launch {
            when (val result = app.session.unlockWithPassword(password.toCharArray())) {
                is UnlockResult.Success -> password = ""
                is UnlockResult.WrongCredentials -> {
                    loading = false
                    password = ""
                    shakeError("Incorrect password. Try again.")
                }
                is UnlockResult.InvalidRecoveryKey -> {
                    loading = false
                    shakeError("That recovery key doesn't match this vault.")
                }
                is UnlockResult.Failure -> {
                    loading = false
                    shakeError(result.message)
                }
            }
        }
    }

    fun showBiometricPrompt() {
        if (!canUseBiometrics) return
        biometricPromptShown = true
        runCatching {
            biometricLauncher.launch(
                android.content.Intent(context, BiometricAuthActivity::class.java).putExtra(
                    BiometricAuthActivity.EXTRA_MODE,
                    BiometricAuthActivity.MODE_UNLOCK
                )
            )
        }.onFailure {
            biometricPromptShown = false
            shakeError("Could not start biometric unlock. Use your password.")
        }
    }

    LaunchedEffect(Unit) {
        if (canUseBiometrics && !biometricPromptShown) {
            delay(650)
            showBiometricPrompt()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LockBackdrop()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(32.dp))
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.8f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "VAULT LOCKED",
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 1.1.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(42.dp))
            Surface(
                modifier = Modifier.size(98.dp),
                shape = RoundedCornerShape(31.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(R.drawable.ic_shield_heart),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            Text(
                "Welcome back",
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Unlock your private vault",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Text(
                buildString {
                    append("Auto-locks ")
                    append(
                        when (settings.autoLockSeconds) {
                            0 -> "immediately"
                            30 -> "after 30 seconds"
                            60 -> "after 1 minute"
                            300 -> "after 5 minutes"
                            900 -> "after 15 minutes"
                            else -> "after ${settings.autoLockSeconds}s"
                        }
                    )
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
            )

            Spacer(Modifier.height(38.dp))
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        translationX = sin(
                            shake.value * 6.28318f * 3f
                        ) * 11.dp.toPx() * shake.value
                    }
                    .fillMaxWidth()
            ) {
                LockerTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        error = null
                    },
                    placeholder = "Enter your vault password",
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    visualTransformation = if (reveal) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    isSensitive = true,
                    trailingContent = {
                        IconButton(onClick = { reveal = !reveal }) {
                            Icon(
                                if (reveal) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = if (reveal) "Hide password" else "Show password",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp)
                )
            }

            AnimatedVisibility(
                visible = error != null,
                enter = fadeIn(tween(160)),
                exit = fadeOut(tween(120))
            ) {
                Text(
                    text = error ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, start = 4.dp),
                    textAlign = TextAlign.Start
                )
            }

            Spacer(Modifier.height(18.dp))
            PrimaryButton(
                text = "Unlock vault",
                onClick = { unlockWithPassword() },
                enabled = password.isNotBlank(),
                loading = loading
            )

            if (canUseBiometrics) {
                Spacer(Modifier.height(16.dp))
                Surface(
                    onClick = { if (!loading) showBiometricPrompt() },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.Fingerprint,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(9.dp))
                        Text(
                            "Use biometrics",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(Modifier.height(22.dp))
            TextButton(onClick = {
                recoveryCode = ""
                recoveryError = null
                recoveryOpen = true
            }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.Key,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        "Use recovery key",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(26.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Rounded.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.size(12.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "AES-256-GCM · Nothing leaves this device",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                )
            }
            Spacer(Modifier.height(28.dp))
        }
    }

    if (recoveryOpen) {
        AlertDialog(
            onDismissRequest = { if (!recoveryLoading) recoveryOpen = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(28.dp),
            icon = {
                Icon(
                    Icons.Rounded.Key,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text(
                    "Use recovery key",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column {
                    Text(
                        "Enter the recovery key you saved when you created the vault.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                        androidx.compose.foundation.text.BasicTextField(
                            value = recoveryCode,
                            onValueChange = {
                                recoveryCode = it
                                recoveryError = null
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .semantics { password() }
                                .padding(16.dp),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 0.8.sp
                            ),
                            minLines = 2,
                            maxLines = 4,
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(
                                MaterialTheme.colorScheme.primary
                            ),
                            decorationBox = { inner ->
                                Box {
                                    if (recoveryCode.isEmpty()) {
                                        Text(
                                            "XXXX XXXX XXXX …",
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontFamily = FontFamily.Monospace
                                            ),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                        )
                                    }
                                    inner()
                                }
                            }
                        )
                    }
                    AnimatedVisibility(visible = recoveryError != null) {
                        Text(
                            recoveryError ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 10.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = recoveryCode.isNotBlank() && !recoveryLoading,
                    onClick = {
                        recoveryLoading = true
                        recoveryError = null
                        scope.launch {
                            when (val result = app.session.unlockWithRecovery(recoveryCode)) {
                                is UnlockResult.Success -> {
                                    recoveryLoading = false
                                    recoveryOpen = false
                                }
                                is UnlockResult.InvalidRecoveryKey -> {
                                    recoveryLoading = false
                                    recoveryError = "That key doesn't match this vault."
                                }
                                is UnlockResult.Failure -> {
                                    recoveryLoading = false
                                    recoveryError = result.message
                                }
                                is UnlockResult.WrongCredentials -> {
                                    recoveryLoading = false
                                    recoveryError = "That key doesn't match this vault."
                                }
                            }
                        }
                    }
                ) {
                    if (recoveryLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Unlock")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { recoveryOpen = false }, enabled = !recoveryLoading) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }
}

@Composable
private fun LockBackdrop() {
    val transition = rememberInfiniteTransition(label = "lockBackdrop")
    val pulse by transition.animateFloat(
        initialValue = 0.75f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4200), RepeatMode.Reverse),
        label = "pulse"
    )
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(560.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f * pulse),
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.04f),
                            Color.Transparent
                        ),
                        radius = 900f
                    )
                )
        )
        Box(
            Modifier
                .size(300.dp)
                .align(Alignment.BottomStart)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.05f),
                            Color.Transparent
                        )
                    ),
                    CircleShape
                )
        )
    }
}
