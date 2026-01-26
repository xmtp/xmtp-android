package org.xmtp.android.example.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.xmtp.android.example.ui.components.ConversationRow
import org.xmtp.android.example.ui.components.DrawerState
import org.xmtp.android.example.ui.components.NavigationDrawerContent
import org.xmtp.android.example.ui.theme.XMTPTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    conversations: List<ConversationItem>,
    drawerState: DrawerState,
    isLoading: Boolean = false,
    modifier: Modifier = Modifier,
    onConversationClick: (String) -> Unit = {},
    onNewConversationClick: () -> Unit = {},
    onSearchClick: () -> Unit = {},
    onWalletInfoClick: () -> Unit = {},
    onNewGroupClick: () -> Unit = {},
    onHideDeletedMessagesToggle: (Boolean) -> Unit = {},
    onViewLogsClick: () -> Unit = {},
    onToggleLogsClick: (Boolean) -> Unit = {},
    onCopyAddressClick: () -> Unit = {},
    onDisconnectClick: () -> Unit = {},
) {
    val drawerStateCompose = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerStateCompose,
        drawerContent = {
            NavigationDrawerContent(
                drawerState = drawerState,
                onWalletInfoClick = {
                    scope.launch { drawerStateCompose.close() }
                    onWalletInfoClick()
                },
                onNewConversationClick = {
                    scope.launch { drawerStateCompose.close() }
                    onNewConversationClick()
                },
                onNewGroupClick = {
                    scope.launch { drawerStateCompose.close() }
                    onNewGroupClick()
                },
                onHideDeletedMessagesToggle = onHideDeletedMessagesToggle,
                onViewLogsClick = {
                    scope.launch { drawerStateCompose.close() }
                    onViewLogsClick()
                },
                onToggleLogsClick = onToggleLogsClick,
                onCopyAddressClick = {
                    scope.launch { drawerStateCompose.close() }
                    onCopyAddressClick()
                },
                onDisconnectClick = {
                    scope.launch { drawerStateCompose.close() }
                    onDisconnectClick()
                },
            )
        },
        modifier = modifier,
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "Messages",
                            style = MaterialTheme.typography.headlineMedium,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerStateCompose.open() } }) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Open menu",
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = onSearchClick) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = onNewConversationClick,
                    containerColor = MaterialTheme.colorScheme.primary,
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "New Conversation",
                    )
                }
            },
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
            ) {
                when {
                    isLoading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }

                    conversations.isEmpty() -> {
                        EmptyStateView(
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }

                    else -> {
                        ConversationsList(
                            conversations = conversations,
                            onConversationClick = onConversationClick,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversationsList(
    conversations: List<ConversationItem>,
    onConversationClick: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        items(
            items = conversations,
            key = { it.id },
        ) { conversation ->
            ConversationRow(
                name = conversation.name,
                lastMessage = conversation.lastMessage,
                timestamp = conversation.timestamp,
                isGroup = conversation.isGroup,
                memberCount = conversation.memberCount,
                unreadCount = conversation.unreadCount,
                onClick = { onConversationClick(conversation.id) },
            )
            HorizontalDivider(
                modifier = Modifier.padding(start = 80.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            )
        }
    }
}

@Composable
private fun EmptyStateView(modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "No Conversations Yet",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "Start a new conversation using the + button or from the menu",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun MainScreenPreview() {
    XMTPTheme {
        MainScreen(
            conversations = listOf(
                ConversationItem(
                    id = "1",
                    name = "0x1234...5678",
                    lastMessage = "Hey, how are you?",
                    timestamp = "10:30 AM",
                    isGroup = false,
                ),
                ConversationItem(
                    id = "2",
                    name = "XMTP Dev Team",
                    lastMessage = "Alice: Let's ship this!",
                    timestamp = "Yesterday",
                    isGroup = true,
                    memberCount = 5,
                    unreadCount = 3,
                ),
            ),
            drawerState = DrawerState(
                walletAddress = "0x1234567890abcdef1234567890abcdef12345678",
                environment = "DEV",
                isLogsEnabled = false,
                hideDeletedMessages = false,
            ),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun MainScreenEmptyPreview() {
    XMTPTheme {
        MainScreen(
            conversations = emptyList(),
            drawerState = DrawerState(
                walletAddress = "0x1234567890abcdef1234567890abcdef12345678",
                environment = "DEV",
                isLogsEnabled = false,
                hideDeletedMessages = false,
            ),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun MainScreenLoadingPreview() {
    XMTPTheme {
        MainScreen(
            conversations = emptyList(),
            drawerState = DrawerState(
                walletAddress = "0x1234567890abcdef1234567890abcdef12345678",
                environment = "DEV",
                isLogsEnabled = false,
                hideDeletedMessages = false,
            ),
            isLoading = true,
        )
    }
}
