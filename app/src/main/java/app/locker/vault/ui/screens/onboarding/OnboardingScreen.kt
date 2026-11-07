package app.locker.vault.ui.screens.onboarding

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import android.content.Intent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.locker.vault.BiometricAuthActivity
import app.locker.vault.R
import app.locker.vault.core.SessionManager
import app.locker.vault.core.SessionState
import app.locker.vault.core.UnlockResult
import app.locker.vault.ui.LocalAppContainer
import app.locker.vault.ui.components.BiometricPromptHelper
import app.locker.vault.ui.components.IconTile
import app.locker.vault.ui.components.LockerTextField
import app.locker.vault.ui.components.PrimaryButton
import app.locker.vault.ui.components.SecureWindow
import app.locker.vault.ui.components.findFragmentActivity
import app.locker.vault.ui.theme.BrandAmber
import app.locker.vault.ui.theme.BrandMint
import app.locker.vault.ui.theme.BrandViolet
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen() {
    var page by rememberSaveable { mutableIntStateOf(0) }
    Box(Modifier.fillMaxSize()) {
        OnboardingBackdrop()
        AnimatedContent(
            targetState = page,
            transitionSpec = {
                if (targetState > initialState) {
                    (slideInHorizontally(tween(320)) { it / 4 } + fadeIn(tween(260)))
                        .togetherWith(slideOutHorizontally(tween(260)) { -it / 6 } + fadeOut(tween(180)))
                } else {
                    (slideInHorizontally(tween(320)) { -it / 4 } + fadeIn(tween(260)))
                        .togetherWith(slideOutHorizontally(tween(260)) { it / 6 } + fadeOut(tween(180)))
                }
            },
            label = "onboarding"
        ) { index ->
            if (index == 0) {
                WelcomePage(onContinue = { page = 1 })
            } else {
                CreatePasswordPage(onBack = { page = 0 })
            }
        }
    }
}

@Composable
private fun OnboardingBackdrop() {
    val transition = rememberInfiniteTransition(label = "backdrop")
    val glow by transition.animateFloat(
        initialValue = 0.72f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3600), RepeatMode.Reverse),
        label = "glow"
    )
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(620.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.20f * glow),
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.04f),
                            Color.Transparent
                        ),
                        radius = 1000f
                    )
                )
        )
        Box(
            Modifier
                .size(260.dp)
                .align(Alignment.BottomEnd)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            BrandViolet.copy(alpha = 0.10f),
                            Color.Transparent
                        )
                    ),
                    CircleShape
                )
        )
    }
}

@Composable
private fun WelcomePage(onContinue: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(52.dp))
        GlowLogo()
        Spacer(Modifier.height(28.dp))
        Text(
            "Locker",
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onSurface,
            letterSpacing = (-1.5).sp
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Your private space for the things\nthat matter most.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 24.sp
        )
        Spacer(Modifier.height(34.dp))
        FeatureRow(
            icon = Icons.Rounded.Shield,
            tint = BrandMint,
            title = "Encrypted from the ground up",
            subtitle = "AES-256-GCM and Argon2id protect every item."
        )
        Spacer(Modifier.height(12.dp))
        FeatureRow(
            icon = Icons.Rounded.CloudOff,
            tint = BrandAmber,
            title = "No account. No cloud.",
            subtitle = "Your vault lives on this device. Nothing syncs."
        )
        Spacer(Modifier.height(12.dp))
        FeatureRow(
            icon = Icons.Rounded.Fingerprint,
            tint = BrandViolet,
            title = "Unlock in a heartbeat",
            subtitle = "Biometrics backed by hardware secure keys."
        )
        Spacer(Modifier.height(34.dp))
        PrimaryButton(text = "Create your vault", onClick = onContinue)
        Spacer(Modifier.height(14.dp))
        Text(
            "No internet permission · No tracking · No accounts",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun GlowLogo() {
    val transition = rememberInfiniteTransition(label = "logoGlow")
    val scale by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(tween(2600), RepeatMode.Reverse),
        label = "logoScale"
    )
    Box(contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(164.dp)
                .background(
                    Brush.radialGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.28f),
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
                            Color.Transparent
                        )
                    ),
                    CircleShape
                )
        )
        Surface(
            modifier = Modifier.size(104.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(R.drawable.ic_shield_heart),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(54.dp)
                )
            }
        }
    }
}

@Composable
private fun FeatureRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    title: String,
    subtitle: String
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.82f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconTile(icon = icon, tint = tint, size = 46.dp)
            Spacer(Modifier.size(13.dp))
            Column {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 17.sp
                )
            }
        }
    }
}

@Composable
private fun CreatePasswordPage(onBack: () -> Unit) {
    val app = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var reveal by rememberSaveable { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val valid = password.length >= 10 && password == confirm

    fun submit() {
        if (!valid || loading) return
        loading = true
        error = null
        scope.launch {
            val result = app.session.createVault(password.toCharArray())
            loading = false
            if (result is UnlockResult.Failure) {
                error = result.message
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
    ) {
        Spacer(Modifier.height(10.dp))
        IconButton(onClick = onBack) {
            Icon(
                Icons.Rounded.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Create your\nvault password",
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.onSurface,
            lineHeight = 44.sp
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "This password encrypts everything. It never leaves your phone and can't be reset by anyone.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 24.sp
        )
        Spacer(Modifier.height(30.dp))

        LockerTextField(
            value = password,
            onValueChange = { password = it; error = null },
            placeholder = "At least 10 characters",
            label = "Password",
            required = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
            isSensitive = true,
            trailingContent = {
                IconButton(onClick = { reveal = !reveal }) {
                    Icon(
                        if (reveal) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = if (reveal) "Hide password" else "Show password",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        )
        Spacer(Modifier.height(14.dp))
        LockerTextField(
            value = confirm,
            onValueChange = { confirm = it; error = null },
            placeholder = "Repeat your password",
            label = "Confirm password",
            required = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
            isSensitive = true
        )
        Spacer(Modifier.height(16.dp))
        PasswordStrength(password)
        AnimatedVisibility(visible = confirm.isNotEmpty() && password != confirm) {
            Text(
                "Passwords don't match yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 10.dp, start = 4.dp)
            )
        }
        Spacer(Modifier.height(24.dp))

        Surface(
            shape = RoundedCornerShape(22.dp),
            color = BrandAmber.copy(alpha = 0.10f),
            border = androidx.compose.foundation.BorderStroke(
                1.dp, BrandAmber.copy(alpha = 0.28f)
            )
        ) {
            Row(Modifier.padding(16.dp)) {
                Icon(
                    Icons.Rounded.Key,
                    contentDescription = null,
                    tint = BrandAmber,
                    modifier = Modifier.size(21.dp)
                )
                Spacer(Modifier.size(11.dp))
                Text(
                    "We'll generate a recovery key next. Keep it somewhere safe — it's the only backup for a forgotten password.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }
        }

        if (error != null) {
            Spacer(Modifier.height(14.dp))
            Text(
                error ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }

        Spacer(Modifier.height(26.dp))
        PrimaryButton(
            text = "Create vault",
            onClick = { submit() },
            enabled = valid,
            loading = loading
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Rounded.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.size(6.dp))
            Text(
                "Argon2id · 96 MB · AES-256-GCM",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun PasswordStrength(password: String) {
    if (password.isEmpty()) return
    val score = scorePassword(password)
    val labels = listOf("Very weak", "Weak", "Fair", "Strong", "Excellent")
    val colors = listOf(
        MaterialTheme.colorScheme.error,
        BrandAmber,
        BrandAmber,
        BrandMint,
        BrandMint
    )
    Column(Modifier.padding(horizontal = 2.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(4) { index ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            if (index < score) colors[score - 1]
                            else MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                )
            }
        }
        Spacer(Modifier.height(7.dp))
        Text(
            labels[(score - 1).coerceIn(0, 4)],
            style = MaterialTheme.typography.labelSmall,
            color = colors[(score - 1).coerceIn(0, 4)]
        )
    }
}

fun scorePassword(password: String): Int {
    var score = 0
    if (password.length >= 8) score++
    if (password.length >= 12) score++
    if (password.any { it.isDigit() } && password.any { it.isLetter() }) score++
    if (password.any { !it.isLetterOrDigit() }) score++
    if (password.any { it.isUpperCase() } && password.any { it.isLowerCase() }) score++
    return score.coerceIn(1, 4)
}

@Composable
fun RecoveryKeyScreen() {
    BackHandler(enabled = true) {
        // The recovery key is shown exactly once; leaving would lose it.
    }
    SecureWindow()
    val app = LocalAppContainer.current
    val context = LocalContext.current
    val code by app.session.pendingRecoveryCode.collectAsStateWithLifecycle()
    var saved by rememberSaveable { mutableStateOf(false) }
    var verifying by rememberSaveable { mutableStateOf(false) }
    var answer1 by remember { mutableStateOf("") }
    var answer2 by remember { mutableStateOf("") }
    var verifyError by remember { mutableStateOf<String?>(null) }
    val rawCode = code ?: ""
    val groups = remember(rawCode) {
        // Numeric recovery codes are 25 digits, displayed and grouped in fives.
        rawCode.replace(" ", "").chunked(5)
    }
    val expected = remember(groups) {
        listOf(
            groups.getOrNull(1).orEmpty(),
            groups.getOrNull(3).orEmpty()
        )
    }
    val display = remember(rawCode) {
        if (rawCode.isBlank()) "" else rawCode.replace(" ", "").chunked(5)
            .joinToString(" ")
    }

    fun normalize(value: String) = value.replace(" ", "").replace("\n", "").uppercase()

    fun verify(): Boolean {
        val answers = listOf(answer1, answer2)
        val ok = expected.indices.all { index ->
            val answer = normalize(answers[index])
            answer.isNotEmpty() && answer == normalize(expected[index])
        }
        if (!ok) {
            verifyError = "Those groups don't match the recovery key. Check your written copy."
            return false
        }
        app.session.acknowledgeRecoverySaved()
        return true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(42.dp))
        Surface(
            modifier = Modifier.size(82.dp),
            shape = CircleShape,
            color = BrandMint.copy(alpha = 0.14f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = BrandMint,
                    modifier = Modifier.size(42.dp)
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(
            "Save your\nrecovery key",
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            lineHeight = 44.sp
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "This is the only way back into your vault if you forget your password. Write it down and keep it somewhere private.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 24.sp
        )
        Spacer(Modifier.height(26.dp))

        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "RECOVERY KEY",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.4.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                                    if (display.isNotBlank()) {
                                        app.secureClipboard.copy(
                                            label = "Locker recovery key",
                                            value = display.replace(" ", "").replace("\n", ""),
                                            clearAfterSeconds = 120
                                        )
                                android.widget.Toast.makeText(
                                    context,
                                    "Recovery key copied",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Rounded.ContentCopy,
                            contentDescription = "Copy recovery key",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    text = display.ifBlank { "Generating…" },
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 15.sp,
                        letterSpacing = 0.8.sp,
                        lineHeight = 27.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        Icons.Rounded.Key,
                        contentDescription = null,
                        tint = BrandAmber,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        "If someone else gets this key, they can read your vault. Don't store it in screenshots or cloud notes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 17.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        if (!verifying) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = saved, onCheckedChange = { saved = it })
                Text(
                    "I've saved my recovery key somewhere safe",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(18.dp))
            PrimaryButton(
                text = "Continue",
                onClick = {
                    if (saved) {
                        verifying = true
                        verifyError = null
                    }
                },
                enabled = saved && display.isNotBlank()
            )
        } else {
            Text(
                "Confirm your copy",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Type these groups from your written copy. This proves the key is recoverable before we finish setup.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))
            LockerTextField(
                value = answer1,
                onValueChange = { answer1 = it; verifyError = null },
                placeholder = "Group 2",
                label = "Group 2",
                required = true
            )
            Spacer(Modifier.height(10.dp))
            LockerTextField(
                value = answer2,
                onValueChange = { answer2 = it; verifyError = null },
                placeholder = "Group 4",
                label = "Group 4",
                required = true
            )
            if (verifyError != null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    verifyError ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Spacer(Modifier.height(18.dp))
            PrimaryButton(
                text = "Verify and continue",
                onClick = { verify() },
                enabled = answer1.isNotBlank() && answer2.isNotBlank()
            )
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = {
                verifying = false
                verifyError = null
            }) {
                Text("Show the key again", color = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
fun BiometricSetupScreen() {
    BackHandler(enabled = true) {
        // Keep the setup flow on screen; the user can choose Skip for now.
    }
    val app = LocalAppContainer.current
    val context = LocalContext.current
    val canUse = remember { app.biometricKeyStore.canAuthenticate() }
    var error by remember { mutableStateOf<String?>(null) }
    var enabling by remember { mutableStateOf(false) }
    val enrollLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        enabling = false
        if (app.preferences.settings.value.biometricEnabled) {
            app.session.completeOnboarding()
        } else {
            val message = result.data?.getStringExtra(BiometricAuthActivity.EXTRA_MESSAGE)
            if (message != null && message != "Cancelled") error = message
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(64.dp))
        Surface(
            modifier = Modifier.size(112.dp),
            shape = CircleShape,
            color = BrandViolet.copy(alpha = 0.14f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Rounded.Fingerprint,
                    contentDescription = null,
                    tint = BrandViolet,
                    modifier = Modifier.size(56.dp)
                )
            }
        }
        Spacer(Modifier.height(30.dp))
        Text(
            "Unlock in\na heartbeat",
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            lineHeight = 44.sp
        )
        Spacer(Modifier.height(14.dp))
        Text(
            if (canUse) {
                "Your fingerprint is verified by Android's hardware-backed Keystore. The vault key is never stored in plain text."
            } else {
                "No fingerprint or face unlock is set up on this device yet. You can add it later in Security settings."
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 24.sp
        )
        Spacer(Modifier.height(44.dp))
        if (canUse) {
            PrimaryButton(
                text = if (enabling) "Waiting for fingerprint…" else "Enable biometric unlock",
                onClick = {
                    enabling = true
                    error = null
                    runCatching {
                        enrollLauncher.launch(
                            Intent(context, BiometricAuthActivity::class.java).putExtra(
                                BiometricAuthActivity.EXTRA_MODE,
                                BiometricAuthActivity.MODE_ENROLL
                            )
                        )
                    }.onFailure {
                        enabling = false
                        error = "Biometric unlock is unavailable on this device."
                    }
                },
                enabled = !enabling
            )
            Spacer(Modifier.height(18.dp))
            TextButton(onClick = { app.session.completeOnboarding() }) {
                Text(
                    "Skip for now",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        } else {
            PrimaryButton(
                text = "Continue with password",
                onClick = { app.session.completeOnboarding() }
            )
        }
        AnimatedVisibility(visible = error != null) {
            Text(
                error ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
        Spacer(Modifier.height(40.dp))
        Text(
            "You can change this anytime in Security settings.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(28.dp))
    }
}
