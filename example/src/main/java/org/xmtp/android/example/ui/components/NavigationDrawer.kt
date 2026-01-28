package org.xmtp.android.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.xmtp.android.example.ui.theme.XMTPTheme

data class DrawerState(
    val walletAddress: String,
    val environment: String,
    val isLogsEnabled: Boolean,
    val hideDeletedMessages: Boolean,
)

@Composable
fun NavigationDrawerContent(
    drawerState: DrawerState,
    onWalletInfoClick: () -> Unit,
    onNewConversationClick: () -> Unit,
    onNewGroupClick: () -> Unit,
    onHideDeletedMessagesToggle: (Boolean) -> Unit,
    onViewLogsClick: () -> Unit,
    onToggleLogsClick: (Boolean) -> Unit,
    onCopyAddressClick: () -> Unit,
    onDisconnectClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalDrawerSheet(modifier = modifier) {
        // Header
        DrawerHeader(
            walletAddress = drawerState.walletAddress,
            environment = drawerState.environment,
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Main actions
        DrawerItem(
            label = "Wallet Info",
            icon = Icons.Default.Info,
            onClick = onWalletInfoClick,
        )

        DrawerItem(
            label = "New Conversation",
            icon = Icons.Default.Add,
            onClick = onNewConversationClick,
        )

        DrawerItem(
            label = "New Group",
            icon = Icons.Default.GroupAdd,
            onClick = onNewGroupClick,
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // Settings section
        Text(
            text = "Settings",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp),
        )

        DrawerToggleItem(
            label = "Hide Deleted Messages",
            icon = Icons.Default.VisibilityOff,
            checked = drawerState.hideDeletedMessages,
            onCheckedChange = onHideDeletedMessagesToggle,
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // Logs section
        Text(
            text = "Logs",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp),
        )

        DrawerItem(
            label = "View Logs",
            icon = Icons.Default.Description,
            onClick = onViewLogsClick,
        )

        DrawerToggleItem(
            label = "Persistent Logs",
            icon = Icons.Default.BugReport,
            checked = drawerState.isLogsEnabled,
            onCheckedChange = onToggleLogsClick,
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // Account section
        Text(
            text = "Account",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp),
        )

        DrawerItem(
            label = "Copy Address",
            icon = Icons.Default.ContentCopy,
            onClick = onCopyAddressClick,
        )

        DrawerItem(
            label = "Disconnect Wallet",
            icon = Icons.AutoMirrored.Filled.Logout,
            onClick = onDisconnectClick,
            tint = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun DrawerHeader(
    walletAddress: String,
    environment: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primary)
            .statusBarsPadding()
            .padding(16.dp),
    ) {
        Column {
            // Avatar
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "Profile",
                    modifier = Modifier.size(36.dp),
                    tint = Color.White,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Wallet address
            Text(
                text = walletAddress,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Environment
            Text(
                text = environment,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.8f),
            )
        }
    }
}

@Composable
private fun DrawerItem(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    NavigationDrawerItem(
        icon = {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
            )
        },
        label = {
            Text(
                text = label,
                color = if (tint == MaterialTheme.colorScheme.error) tint else MaterialTheme.colorScheme.onSurface,
            )
        },
        selected = false,
        onClick = onClick,
        modifier = modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
    )
}

@Composable
private fun DrawerToggleItem(
    label: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationDrawerItem(
        icon = {
            Icon(
                imageVector = icon,
                contentDescription = label,
            )
        },
        label = { Text(label) },
        badge = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
            )
        },
        selected = false,
        onClick = { onCheckedChange(!checked) },
        modifier = modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
    )
}

@Preview(showBackground = true)
@Composable
private fun NavigationDrawerContentPreview() {
    XMTPTheme {
        NavigationDrawerContent(
            drawerState = DrawerState(
                walletAddress = "0x1234567890abcdef1234567890abcdef12345678",
                environment = "DEV",
                isLogsEnabled = false,
                hideDeletedMessages = false,
            ),
            onWalletInfoClick = {},
            onNewConversationClick = {},
            onNewGroupClick = {},
            onHideDeletedMessagesToggle = {},
            onViewLogsClick = {},
            onToggleLogsClick = {},
            onCopyAddressClick = {},
            onDisconnectClick = {},
        )
    }
}
