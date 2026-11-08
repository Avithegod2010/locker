package app.locker.vault.ui.screens.editor

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.locker.vault.core.TransferCodec
import app.locker.vault.ui.LocalAppContainer
import app.locker.vault.ui.components.LockerTextField
import app.locker.vault.ui.components.SecureWindow
import app.locker.vault.ui.components.encodeQrCode
import app.locker.vault.ui.theme.BrandMint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Shows a text item as an encrypted QR payload. The QR is generated locally; the passphrase
 * is never stored. Saving the image writes only the encrypted payload.
 */
@Composable
fun TransferQrScreen(itemId: String, onBack: () -> Unit) {
    val app = LocalAppContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    SecureWindow()
    val items by app.repository.items.collectAsStateWithLifecycle()
    val item = remember(itemId, items) { items.firstOrNull { it.id == itemId } }

    var passphrase by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var qr by remember { mutableStateOf<Bitmap?>(null) }
    var payload by rememberSaveable { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(payload) {
        if (qr == null) {
            payload?.let { qr = encodeQrCode(it) }
        }
    }

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("image/png")
    ) { uri: Uri? ->
        app.session.endExternalActivity()
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val bitmap = qr ?: payload?.let { encodeQrCode(it) }
            if (bitmap == null) {
                android.widget.Toast.makeText(
                    context,
                    "Could not prepare the QR image",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                return@launch
            }
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
                    } != null
                }.getOrDefault(false)
            }
            android.widget.Toast.makeText(
                context,
                if (ok) "Encrypted QR saved" else "Could not save the QR image",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun generate() {
        val current = item ?: return
        when {
            passphrase.length < 8 -> error = "Use at least 8 characters for the transfer passphrase."
            passphrase != confirm -> error = "The passphrases don't match."
            else -> {
                busy = true
                error = null
                scope.launch {
                    val result = withContext(Dispatchers.Default) {
                        runCatching {
                            val encoded = TransferCodec.encode(current, passphrase.toCharArray())
                            encoded to (encodeQrCode(encoded)
                                ?: error("Could not render the QR code"))
                        }
                    }
                    busy = false
                    result.onSuccess { (encoded, bitmap) ->
                        payload = encoded
                        qr = bitmap
                    }.onFailure { error = it.message ?: "Could not create the QR code" }
                }
            }
        }
    }

    EditorScaffold(
        title = "Encrypted QR",
        onBack = onBack,
        saveEnabled = qr != null,
        saving = false,
        saveLabel = "Save QR image",
        onSave = {
            if (qr != null) {
                app.session.beginExternalActivity()
                saveLauncher.launch("Locker-transfer-${item?.title ?: "item"}.png")
            }
        }
    ) {
        Spacer(Modifier.height(14.dp))
        Text(
            "This QR carries the item encrypted with a one-time passphrase. Anyone who has both the image and the passphrase can read it. The passphrase is not stored by Locker.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        LockerTextField(
            value = passphrase,
            onValueChange = { passphrase = it; error = null },
            placeholder = "At least 8 characters",
            label = "Transfer passphrase",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = PasswordVisualTransformation(),
            isSensitive = true
        )
        Spacer(Modifier.height(12.dp))
        LockerTextField(
            value = confirm,
            onValueChange = { confirm = it; error = null },
            placeholder = "Repeat passphrase",
            label = "Confirm",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = PasswordVisualTransformation(),
            isSensitive = true
        )
        Spacer(Modifier.height(14.dp))
        TextButton(onClick = { generate() }, enabled = !busy && item != null) {
            Icon(
                Icons.Rounded.QrCode2,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(6.dp))
            Text(
                if (busy) "Encrypting…" else "Generate QR",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
        }
        if (error != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                error ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        val bitmap = qr
        if (bitmap != null) {
            Spacer(Modifier.height(16.dp))
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Encrypted transfer QR code",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
            )
            Text(
                "Keep this image private. It is protected by the passphrase, not by your vault password.",
                style = MaterialTheme.typography.bodySmall,
                color = BrandMint,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.height(30.dp))
    }
}
