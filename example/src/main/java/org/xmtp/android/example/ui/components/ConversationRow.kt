package org.xmtp.android.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.xmtp.android.example.ui.theme.XMTPTheme

@Composable
fun ConversationRow(
    name: String,
    lastMessage: String,
    timestamp: String,
    modifier: Modifier = Modifier,
    isGroup: Boolean = false,
    memberCount: Int? = null,
    unreadCount: Int = 0,
    onClick: () -> Unit = {}
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            Box(
                modifier = Modifier.size(52.dp),
                contentAlignment = Alignment.Center
            ) {
                Avatar(name = name, size = 52.dp)

                // Group/DM indicator badge
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .align(Alignment.BottomEnd)
                        .clip(CircleShape)
                        .background(if (isGroup) Color(0xFF34C759) else Color(0xFF007AFF)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isGroup) Icons.Default.Group else Icons.Default.Person,
                        contentDescription = if (isGroup) "Group" else "Direct Message",
                        modifier = Modifier.size(12.dp),
                        tint = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Content
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (unreadCount > 0) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = timestamp,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (unreadCount > 0)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        if (isGroup && memberCount != null) {
                            Text(
                                text = "$memberCount members",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Text(
                            text = lastMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = if (unreadCount > 0) FontWeight.Medium else FontWeight.Normal
                        )
                    }

                    if (unreadCount > 0) {
                        Spacer(modifier = Modifier.width(8.dp))

                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Text(
                                text = if (unreadCount > 99) "99+" else unreadCount.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun DMConversationPreview() {
    XMTPTheme {
        ConversationRow(
            name = "0x1234...5678",
            lastMessage = "Hey, how are you doing?",
            timestamp = "10:30 AM",
            isGroup = false
        )
    }
}

@Preview
@Composable
private fun GroupConversationPreview() {
    XMTPTheme {
        ConversationRow(
            name = "XMTP Dev Team",
            lastMessage = "Alice: Let's ship this feature!",
            timestamp = "Yesterday",
            isGroup = true,
            memberCount = 5
        )
    }
}

@Preview
@Composable
private fun UnreadConversationPreview() {
    XMTPTheme {
        ConversationRow(
            name = "Bob",
            lastMessage = "Check out this new update!",
            timestamp = "2:45 PM",
            isGroup = false,
            unreadCount = 3
        )
    }
}
