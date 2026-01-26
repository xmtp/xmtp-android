package org.xmtp.android.example.ui.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.xmtp.android.example.ui.theme.XMTPTheme

data class WalletInfo(
    val walletAddress: String,
    val inboxId: String,
    val installationId: String,
    val environment: String,
    val libXmtpVersion: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletInfoSheet(
    walletInfo: WalletInfo,
    isLogsEnabled: Boolean,
    onDismiss: () -> Unit,
    onCopyValue: (label: String, value: String) -> Unit,
    onLogsToggled: (Boolean) -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            // Title
            Text(
                text = "Wallet Info",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Wallet Address
            InfoRow(
                label = "Wallet Address",
                value = walletInfo.walletAddress,
                onCopy = { onCopyValue("Wallet Address", walletInfo.walletAddress) },
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // Inbox ID
            InfoRow(
                label = "Inbox ID",
                value = walletInfo.inboxId,
                onCopy = { onCopyValue("Inbox ID", walletInfo.inboxId) },
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // Installation ID
            InfoRow(
                label = "Installation ID",
                value = walletInfo.installationId,
                onCopy = { onCopyValue("Installation ID", walletInfo.installationId) },
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // Environment
            InfoRow(
                label = "Environment",
                value = walletInfo.environment,
                onCopy = { onCopyValue("Environment", walletInfo.environment) },
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // LibXMTP Version
            InfoRow(
                label = "LibXMTP Version",
                value = walletInfo.libXmtpVersion,
                onCopy = { onCopyValue("LibXMTP Version", walletInfo.libXmtpVersion) },
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // Logs Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "Enable Logs",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Persist debug logs to file",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = isLogsEnabled,
                    onCheckedChange = onLogsToggled,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Disconnect Button
            Button(
                onClick = onDisconnect,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Text("Disconnect Wallet")
            }
        }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        IconButton(onClick = onCopy) {
            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = "Copy $label",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun WalletInfoSheetPreview() {
    XMTPTheme {
        WalletInfoSheet(
            walletInfo = WalletInfo(
                walletAddress = "0x1234567890abcdef1234567890abcdef12345678",
                inboxId = "inbox-id-12345",
                installationId = "installation-id-67890",
                environment = "DEV",
                libXmtpVersion = "1.0.0",
            ),
            isLogsEnabled = false,
            onDismiss = {},
            onCopyValue = { _, _ -> },
            onLogsToggled = {},
            onDisconnect = {},
        )
    }
}
