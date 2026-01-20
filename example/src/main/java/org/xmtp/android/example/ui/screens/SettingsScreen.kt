package org.xmtp.android.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.xmtp.android.example.ui.theme.XMTPTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    hideDeletedMessages: Boolean = false,
    notificationsEnabled: Boolean = true,
    darkModeEnabled: Boolean = false,
    modifier: Modifier = Modifier,
    onHideDeletedMessagesChange: (Boolean) -> Unit = {},
    onNotificationsChange: (Boolean) -> Unit = {},
    onDarkModeChange: (Boolean) -> Unit = {},
    onPrivacyClick: () -> Unit = {},
    onStorageClick: () -> Unit = {},
    onViewLogsClick: () -> Unit = {},
    onAboutClick: () -> Unit = {},
    onClearDataClick: () -> Unit = {}
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.headlineMedium
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Appearance Section
            SectionTitle(text = "Appearance")

            SettingsCard {
                SettingsToggleRow(
                    icon = Icons.Default.DarkMode,
                    title = "Dark Mode",
                    subtitle = "Use dark theme",
                    isChecked = darkModeEnabled,
                    onCheckedChange = onDarkModeChange
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Messages Section
            SectionTitle(text = "Messages")

            SettingsCard {
                SettingsToggleRow(
                    icon = Icons.Default.DeleteForever,
                    title = "Hide Deleted Messages",
                    subtitle = "Don't show deleted messages in conversations",
                    isChecked = hideDeletedMessages,
                    onCheckedChange = onHideDeletedMessagesChange
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Notifications Section
            SectionTitle(text = "Notifications")

            SettingsCard {
                SettingsToggleRow(
                    icon = Icons.Default.Notifications,
                    title = "Push Notifications",
                    subtitle = "Receive notifications for new messages",
                    isChecked = notificationsEnabled,
                    onCheckedChange = onNotificationsChange
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // General Section
            SectionTitle(text = "General")

            SettingsCard {
                SettingsNavigationRow(
                    icon = Icons.Default.Security,
                    title = "Privacy & Security",
                    onClick = onPrivacyClick
                )
                SettingsNavigationRow(
                    icon = Icons.Default.Storage,
                    title = "Storage",
                    onClick = onStorageClick
                )
                SettingsNavigationRow(
                    icon = Icons.Default.BugReport,
                    title = "View Logs",
                    onClick = onViewLogsClick
                )
                SettingsNavigationRow(
                    icon = Icons.Default.Info,
                    title = "About",
                    onClick = onAboutClick
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Danger Zone
            SectionTitle(text = "Danger Zone", color = MaterialTheme.colorScheme.error)

            SettingsCard(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            ) {
                SettingsNavigationRow(
                    icon = Icons.Default.DeleteForever,
                    title = "Clear All Data",
                    iconTint = MaterialTheme.colorScheme.error,
                    onClick = onClearDataClick
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Version info
            Text(
                text = "XMTP Example App v1.0.0",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

@Composable
private fun SectionTitle(
    text: String,
    color: Color = MaterialTheme.colorScheme.primary
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = color,
        modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
    )
}

@Composable
private fun SettingsCard(
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column {
            content()
        }
    }
}

@Composable
private fun SettingsToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!isChecked) }
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(
            checked = isChecked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun SettingsNavigationRow(
    icon: ImageVector,
    title: String,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    XMTPTheme {
        SettingsScreen()
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenDarkPreview() {
    XMTPTheme(darkTheme = true) {
        SettingsScreen(darkModeEnabled = true)
    }
}
