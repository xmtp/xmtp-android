package org.xmtp.android.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.xmtp.android.example.ui.components.DeliveryStatus
import org.xmtp.android.example.ui.components.MessageBubble
import org.xmtp.android.example.ui.components.MessageComposer
import org.xmtp.android.example.ui.components.MessageSearchBar
import org.xmtp.android.example.ui.components.SearchResult
import org.xmtp.android.example.ui.components.SearchResultsList
import org.xmtp.android.example.ui.components.SystemMessage
import org.xmtp.android.example.ui.theme.XMTPTheme

data class MessageItem(
    val id: String,
    val content: String,
    val timestamp: String,
    val isMe: Boolean,
    val senderName: String? = null,
    val senderColor: Color? = null,
    val deliveryStatus: DeliveryStatus = DeliveryStatus.SENT,
    val reactions: List<Pair<String, Int>>? = null,
    val replyPreview: String? = null,
    val replyAuthor: String? = null,
    val isDeleted: Boolean = false,
    val isSystemMessage: Boolean = false,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    conversationName: String,
    messages: List<MessageItem>,
    isLoading: Boolean = false,
    isGroup: Boolean = false,
    memberCount: Int? = null,
    replyingTo: MessageItem? = null,
    modifier: Modifier = Modifier,
    onBackClick: () -> Unit = {},
    onInfoClick: () -> Unit = {},
    onSendMessage: (String) -> Unit = {},
    onAttachmentClick: () -> Unit = {},
    onEmojiClick: () -> Unit = {},
    onCancelReply: () -> Unit = {},
    onMessageLongClick: (String) -> Unit = {},
    onReplyClick: (String) -> Unit = {},
    onScrollToMessage: (String) -> Unit = {},
) {
    val listState = rememberLazyListState()
    var messageText by rememberSaveable { mutableStateOf("") }
    var isSearching by rememberSaveable { mutableStateOf(false) }
    var searchText by rememberSaveable { mutableStateOf("") }

    // Filter messages for search results (exclude deleted and system messages)
    val searchResults by remember(searchText, messages) {
        derivedStateOf {
            if (searchText.isEmpty()) {
                emptyList()
            } else {
                val lowercaseSearch = searchText.lowercase()
                messages
                    .filter { msg ->
                        !msg.isSystemMessage &&
                            !msg.isDeleted &&
                            msg.content.isNotEmpty() &&
                            msg.content.lowercase().contains(lowercaseSearch)
                    }.map { msg ->
                        SearchResult(
                            id = msg.id,
                            senderName = msg.senderName ?: "You",
                            content = msg.content,
                            timestamp = msg.timestamp,
                            isDeleted = msg.isDeleted,
                        )
                    }
            }
        }
    }

    // Scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty() && !isSearching) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = conversationName,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (isGroup && memberCount != null) {
                                Text(
                                    text = "$memberCount members",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { isSearching = !isSearching }) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                            )
                        }
                        if (isGroup) {
                            IconButton(onClick = onInfoClick) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "Group Info",
                                )
                            }
                        }
                    },
                    colors =
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                )

                // Search bar
                MessageSearchBar(
                    searchText = searchText,
                    onSearchTextChange = { searchText = it },
                    isSearching = isSearching,
                    onSearchToggle = { isSearching = it },
                )
            }
        },
        bottomBar = {
            AnimatedVisibility(
                visible = !isSearching,
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
            ) {
                MessageComposer(
                    value = messageText,
                    onValueChange = { messageText = it },
                    onSendClick = {
                        if (messageText.isNotBlank()) {
                            onSendMessage(messageText)
                            messageText = ""
                        }
                    },
                    replyTo = replyingTo?.content,
                    replyAuthor = replyingTo?.senderName,
                    onClearReply = onCancelReply,
                    onAttachmentClick = onAttachmentClick,
                    onEmojiClick = onEmojiClick,
                )
            }
        },
    ) { paddingValues ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                )
            } else if (isSearching && searchText.isNotEmpty()) {
                // Show search results
                SearchResultsList(
                    searchText = searchText,
                    results = searchResults,
                    onResultClick = { messageId ->
                        // Scroll to message and close search
                        onScrollToMessage(messageId)
                        isSearching = false
                        searchText = ""

                        // Find the index of the message and scroll to it
                        val index = messages.indexOfFirst { it.id == messageId }
                        if (index >= 0) {
                            // Using launched effect won't work here, so we handle it via callback
                        }
                    },
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(vertical = 8.dp),
                    reverseLayout = false,
                ) {
                    items(
                        items = messages,
                        key = { it.id },
                    ) { message ->
                        if (message.isSystemMessage) {
                            SystemMessage(text = message.content)
                        } else {
                            MessageBubble(
                                message = message.content,
                                timestamp = message.timestamp,
                                isMe = message.isMe,
                                senderName = message.senderName,
                                senderColor = message.senderColor,
                                deliveryStatus = message.deliveryStatus,
                                reactions = message.reactions,
                                replyPreview = message.replyPreview,
                                replyAuthor = message.replyAuthor,
                                isDeleted = message.isDeleted,
                                onLongClick = { onMessageLongClick(message.id) },
                                onReplyClick = { onReplyClick(message.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ChatScreenPreview() {
    XMTPTheme {
        ChatScreen(
            conversationName = "0x1234...5678",
            messages =
                listOf(
                    MessageItem(
                        id = "1",
                        content = "Hey! How are you?",
                        timestamp = "10:30 AM",
                        isMe = false,
                        senderName = "0x1234...5678",
                    ),
                    MessageItem(
                        id = "2",
                        content = "I'm doing great! Just shipped a new feature.",
                        timestamp = "10:31 AM",
                        isMe = true,
                    ),
                    MessageItem(
                        id = "3",
                        content = "That's awesome! Can you show me?",
                        timestamp = "10:32 AM",
                        isMe = false,
                        senderName = "0x1234...5678",
                    ),
                    MessageItem(
                        id = "4",
                        content = "Sure, let me send you a screenshot!",
                        timestamp = "10:33 AM",
                        isMe = true,
                        reactions = listOf("👍" to 1, "🔥" to 2),
                    ),
                ),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun GroupChatScreenPreview() {
    XMTPTheme {
        ChatScreen(
            conversationName = "XMTP Dev Team",
            isGroup = true,
            memberCount = 5,
            messages =
                listOf(
                    MessageItem(
                        id = "sys1",
                        content = "Alice joined the group",
                        timestamp = "",
                        isMe = false,
                        isSystemMessage = true,
                    ),
                    MessageItem(
                        id = "1",
                        content = "Welcome to the team!",
                        timestamp = "10:30 AM",
                        isMe = false,
                        senderName = "Bob",
                        senderColor = Color(0xFF5856D6),
                    ),
                    MessageItem(
                        id = "2",
                        content = "Thanks! Excited to be here.",
                        timestamp = "10:31 AM",
                        isMe = true,
                    ),
                    MessageItem(
                        id = "3",
                        content = "Let's ship some features!",
                        timestamp = "10:32 AM",
                        isMe = false,
                        senderName = "Charlie",
                        senderColor = Color(0xFFFF9500),
                    ),
                ),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ChatWithReplyPreview() {
    XMTPTheme {
        ChatScreen(
            conversationName = "Alice",
            messages =
                listOf(
                    MessageItem(
                        id = "1",
                        content = "Hey, did you see the new update?",
                        timestamp = "10:30 AM",
                        isMe = false,
                    ),
                    MessageItem(
                        id = "2",
                        content = "Yes! It looks amazing!",
                        timestamp = "10:31 AM",
                        isMe = true,
                        replyPreview = "Hey, did you see the new update?",
                        replyAuthor = "Alice",
                    ),
                ),
            replyingTo =
                MessageItem(
                    id = "1",
                    content = "Hey, did you see the new update?",
                    timestamp = "10:30 AM",
                    isMe = false,
                ),
        )
    }
}
