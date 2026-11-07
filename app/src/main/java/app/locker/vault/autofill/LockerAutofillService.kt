package app.locker.vault.autofill

import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.Dataset
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.SaveCallback
import android.service.autofill.SaveRequest
import android.text.InputType
import android.view.View
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import app.locker.vault.LockerApp
import app.locker.vault.R
import app.locker.vault.core.SessionState
import app.locker.vault.data.VaultItem
import app.locker.vault.data.VaultType
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Offline autofill. Datasets are built only while the vault is unlocked; the service
 * never has a network permission and never stores credentials anywhere except the vault.
 */
class LockerAutofillService : AutofillService() {
    override fun onFillRequest(
        request: FillRequest,
        cancellationSignal: CancellationSignal,
        callback: FillCallback
    ) {
        val container = (application as LockerApp).container
        if (container.session.state.value != SessionState.UNLOCKED) {
            callback.onSuccess(null)
            return
        }
        val structure = request.fillContexts.lastOrNull()?.structure
        val fields = structure?.let(::parseFields)
        if (fields == null || (fields.usernameId == null && fields.passwordId == null)) {
            callback.onSuccess(null)
            return
        }
        val packageName = structure.activityComponent?.packageName.orEmpty()
        val root = if (structure.windowNodeCount > 0) {
            structure.getWindowNodeAt(0).rootViewNode
        } else null
        val domain = root?.webDomain.orEmpty()
        val all = container.repository.items.value.filter {
            it.type == VaultType.SECRET &&
                it.deletedAt == null &&
                it.archivedAt == null &&
                !it.requireBiometric
        }
        val matching = all.filter { item ->
            matches(item, packageName, domain)
        }.ifEmpty { all.take(5) }

        val datasets = matching.mapNotNull { item ->
            val presentation = RemoteViews(applicationContext.packageName, R.layout.autofill_dataset).apply {
                setTextViewText(R.id.autofill_title, item.title)
                setTextViewText(
                    R.id.autofill_subtitle,
                    item.username.ifBlank { item.website.ifBlank { "Locker password" } }
                )
            }
            val builder = Dataset.Builder(presentation)
            var hasValue = false
            fields.usernameId?.let { id ->
                if (item.username.isNotBlank()) {
                    builder.setValue(id, AutofillValue.forText(item.username))
                    hasValue = true
                }
            }
            fields.passwordId?.let { id ->
                if (item.password.isNotBlank()) {
                    builder.setValue(id, AutofillValue.forText(item.password))
                    hasValue = true
                }
            }
            if (hasValue) builder.build() else null
        }
        callback.onSuccess(
            if (datasets.isEmpty()) null
            else FillResponse.Builder().apply {
                datasets.forEach { addDataset(it) }
            }.build()
        )
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        val container = (application as LockerApp).container
        if (container.session.state.value != SessionState.UNLOCKED) {
            callback.onSuccess()
            return
        }
        val structure = request.fillContexts.lastOrNull()?.structure ?: run {
            callback.onSuccess()
            return
        }
        val fields = parseFields(structure)
        val username = (fields.usernameNode?.text?.toString()
            ?: fields.usernameNode?.autofillValue?.textValue?.toString()).orEmpty()
        val password = (fields.passwordNode?.text?.toString()
            ?: fields.passwordNode?.autofillValue?.textValue?.toString()).orEmpty()
        if (password.isBlank()) {
            callback.onSuccess()
            return
        }
        val root = if (structure.windowNodeCount > 0) {
            structure.getWindowNodeAt(0).rootViewNode
        } else null
        val domain = root?.webDomain.orEmpty()
        val packageName = structure.activityComponent?.packageName.orEmpty()
        val title = domain.ifBlank { packageName }.ifBlank { "Autofilled login" }
        container.appScope.launch {
            val existing = container.repository.items.value.firstOrNull { item ->
                item.type == VaultType.SECRET &&
                    item.deletedAt == null &&
                    !item.requireBiometric &&
                    item.username.equals(username, ignoreCase = true) &&
                    matches(item, packageName, domain)
            }
            if (existing != null) {
                container.repository.updateItem(
                    existing.copy(password = password, updatedAt = System.currentTimeMillis())
                )
            } else {
                container.repository.createTextItem(
                    type = VaultType.SECRET,
                    title = title,
                    username = username,
                    password = password,
                    website = domain.ifBlank { packageName },
                    collectionIds = emptyList()
                )
            }
        }
        callback.onSuccess()
    }

    private data class AutofillFields(
        val usernameId: AutofillId?,
        val passwordId: AutofillId?,
        val usernameNode: android.app.assist.AssistStructure.ViewNode?,
        val passwordNode: android.app.assist.AssistStructure.ViewNode?
    )

    private fun parseFields(structure: android.app.assist.AssistStructure): AutofillFields {
        var usernameId: AutofillId? = null
        var passwordId: AutofillId? = null
        var usernameNode: android.app.assist.AssistStructure.ViewNode? = null
        var passwordNode: android.app.assist.AssistStructure.ViewNode? = null

        fun walk(node: android.app.assist.AssistStructure.ViewNode) {
            val hints = node.autofillHints?.map { it.lowercase(Locale.US) }.orEmpty()
            val inputType = node.inputType
            val hintText = node.hint?.lowercase(Locale.US).orEmpty()
            val idText = node.idEntry?.lowercase(Locale.US).orEmpty()
            val passwordLike = hints.any { it.contains("password") } ||
                hintText.contains("password") || hintText.contains("passcode") ||
                idText.contains("password") || idText.contains("passwd") ||
                (inputType and InputType.TYPE_TEXT_VARIATION_PASSWORD) != 0 ||
                (inputType and InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD) != 0
            val usernameLike = hints.any {
                it.contains("username") || it.contains("email") || it == View.AUTOFILL_HINT_EMAIL_ADDRESS
            } || idText.contains("user") || idText.contains("email") ||
                idText.contains("login") || hintText.contains("user") ||
                hintText.contains("email") || hintText.contains("login")
            if (node.autofillId != null) {
                if (passwordLike && passwordId == null) {
                    passwordId = node.autofillId
                    passwordNode = node
                } else if (usernameLike && usernameId == null) {
                    usernameId = node.autofillId
                    usernameNode = node
                }
            }
            for (index in 0 until node.childCount) {
                walk(node.getChildAt(index))
            }
        }
        walk(
            if (structure.windowNodeCount > 0) {
                structure.getWindowNodeAt(0).rootViewNode
            } else {
                return AutofillFields(null, null, null, null)
            }
        )
        return AutofillFields(usernameId, passwordId, usernameNode, passwordNode)
    }

    private fun matches(item: VaultItem, packageName: String, domain: String): Boolean {
        val website = normalizeHost(item.website)
        if (website.isBlank()) return false
        val packageLower = packageName.lowercase(Locale.US)
        val domainLower = normalizeHost(domain)
        if (website == packageLower) return true
        if (domainLower.isNotBlank()) {
            if (website == domainLower) return true
            if (website.endsWith(".$domainLower")) return true
            // A login saved for a subdomain should still match its parent site.
            if (domainLower.endsWith(".$website")) return true
        }
        // App package names often end with the service name (com.example.spotify).
        val packageTail = packageLower.substringAfterLast('.')
        return packageTail.length >= 4 && website == packageTail
    }

    private fun normalizeHost(raw: String): String =
        raw.trim().lowercase(Locale.US)
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("www.")
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')
            .substringBefore(':')
}
