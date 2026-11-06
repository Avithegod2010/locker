package app.locker.vault.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.locker.vault.data.PasswordAudit
import app.locker.vault.data.PasswordIssue
import app.locker.vault.data.VaultItem
import app.locker.vault.ui.LocalAppContainer
import app.locker.vault.ui.components.EmptyState
import app.locker.vault.ui.theme.BrandAmber
import app.locker.vault.ui.theme.BrandCoral
import app.locker.vault.ui.theme.BrandMint
import app.locker.vault.ui.theme.BrandViolet

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PasswordHealthScreen(
    onBack: () -> Unit,
    onOpenItem: (VaultItem) -> Unit
) {
    val app = LocalAppContainer.current
    val items by app.repository.items.collectAsStateWithLifecycle()
    val active = remember(items) { items.filter { it.deletedAt == null } }
    val issues = remember(active) { PasswordAudit.audit(active) }
    val weak = remember(active) { PasswordAudit.weakCount(active) }
    val reused = remember(active) { PasswordAudit.reusedCount(active) }
    val old = remember(active) { PasswordAudit.oldCount(active) }

    SettingsScaffold(title = "Password health", onBack = onBack) {
        Spacer(Modifier.height(6.dp))
        InfoBanner(
            icon = Icons.Rounded.Lock,
            tint = BrandMint,
            title = "All checks happen on this device",
            text = "Locker compares your passwords locally. No password or hash is ever sent anywhere."
        )

        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            HealthStat("Weak", weak, BrandCoral, Modifier.weight(1f))
            HealthStat("Reused", reused, BrandAmber, Modifier.weight(1f))
            HealthStat("Old", old, BrandViolet, Modifier.weight(1f))
        }

        Spacer(Modifier.height(20.dp))
        if (issues.isEmpty()) {
            EmptyState(
                icon = Icons.Rounded.CheckCircle,
                title = "Nothing to fix",
                subtitle = "No weak, reused, common, or year-old passwords found in this vault."
            )
        } else {
            Text(
                "NEEDS ATTENTION",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 6.dp, bottom = 9.dp)
            )
            issues.forEach { issue ->
                IssueCard(issue = issue, onClick = { onOpenItem(issue.item) })
                Spacer(Modifier.height(8.dp))
            }
        }
        Spacer(Modifier.height(34.dp))
    }
}

@Composable
private fun HealthStat(
    label: String,
    value: Int,
    tint: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = tint.copy(alpha = 0.10f),
        modifier = modifier
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                "$value",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = tint
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IssueCard(issue: PasswordIssue, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = BrandCoral.copy(alpha = 0.14f),
                modifier = Modifier.size(42.dp)
            ) {
                androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.Key,
                        contentDescription = null,
                        tint = BrandCoral,
                        modifier = Modifier.size(21.dp)
                    )
                }
            }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    issue.item.title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(5.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    issue.reasons.forEach { reason ->
                        Surface(
                            shape = CircleShape,
                            color = BrandAmber.copy(alpha = 0.13f)
                        ) {
                            Text(
                                reason,
                                style = MaterialTheme.typography.labelSmall,
                                color = BrandAmber,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }
            Icon(
                Icons.Rounded.Warning,
                contentDescription = null,
                tint = BrandAmber.copy(alpha = 0.7f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
