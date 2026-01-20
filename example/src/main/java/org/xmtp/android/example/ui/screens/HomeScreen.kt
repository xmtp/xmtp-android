package org.xmtp.android.example.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.xmtp.android.example.ui.components.ConversationRow
import org.xmtp.android.example.ui.theme.XMTPTheme

data class ConversationItem(
    val id: String,
    val name: String,
    val lastMessage: String,
    val timestamp: String,
    val isGroup: Boolean = false,
    val memberCount: Int? = null,
    val unreadCount: Int = 0
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    conversations: List<ConversationItem>,
    isLoading: Boolean = false,
    modifier: Modifier = Modifier,
    onConversationClick: (String) -> Unit = {},
    onNewConversationClick: () -> Unit = {},
    onSearchClick: () -> Unit = {}
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Messages",
                        style = MaterialTheme.typography.headlineMedium
                    )
                },
                actions = {
                    IconButton(onClick = onSearchClick) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNewConversationClick,
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "New Conversation"
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                conversations.isEmpty() -> {
                    EmptyConversationsView(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                else -> {
                    ConversationsList(
                        conversations = conversations,
                        onConversationClick = onConversationClick
                    )
                }
            }
        }
    }
}

@Composable
private fun ConversationsList(
    conversations: List<ConversationItem>,
    onConversationClick: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        items(
            items = conversations,
            key = { it.id }
        ) { conversation ->
            ConversationRow(
                name = conversation.name,
                lastMessage = conversation.lastMessage,
                timestamp = conversation.timestamp,
                isGroup = conversation.isGroup,
                memberCount = conversation.memberCount,
                unreadCount = conversation.unreadCount,
                onClick = { onConversationClick(conversation.id) }
            )
            Divider(
                modifier = Modifier.padding(start = 80.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun EmptyConversationsView(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "No Conversations Yet",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Start a new conversation using the + button",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    XMTPTheme {
        HomeScreen(
            conversations = listOf(
                ConversationItem(
                    id = "1",
                    name = "0x1234...5678",
                    lastMessage = "Hey, how are you?",
                    timestamp = "10:30 AM",
                    isGroup = false
                ),
                ConversationItem(
                    id = "2",
                    name = "XMTP Dev Team",
                    lastMessage = "Alice: Let's ship this!",
                    timestamp = "Yesterday",
                    isGroup = true,
                    memberCount = 5,
                    unreadCount = 3
                ),
                ConversationItem(
                    id = "3",
                    name = "Bob",
                    lastMessage = "Check out this update!",
                    timestamp = "2:45 PM",
                    isGroup = false,
                    unreadCount = 1
                )
            )
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun EmptyHomeScreenPreview() {
    XMTPTheme {
        HomeScreen(conversations = emptyList())
    }
}

@Preview(showBackground = true)
@Composable
private fun LoadingHomeScreenPreview() {
    XMTPTheme {
        HomeScreen(
            conversations = emptyList(),
            isLoading = true
        )
    }
}
