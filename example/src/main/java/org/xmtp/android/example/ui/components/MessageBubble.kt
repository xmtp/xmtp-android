package org.xmtp.android.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.xmtp.android.example.ui.theme.SentMessageBackground
import org.xmtp.android.example.ui.theme.XMTPTheme

enum class DeliveryStatus {
    SENDING,
    SENT,
    FAILED
}

@Composable
fun MessageBubble(
    message: String,
    timestamp: String,
    isMe: Boolean,
    modifier: Modifier = Modifier,
    senderName: String? = null,
    senderColor: Color? = null,
    deliveryStatus: DeliveryStatus = DeliveryStatus.SENT,
    reactions: List<Pair<String, Int>>? = null,
    replyPreview: String? = null,
    replyAuthor: String? = null,
    isDeleted: Boolean = false,
    onLongClick: () -> Unit = {},
    onReplyClick: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
    ) {
        if (isMe) {
            Spacer(modifier = Modifier.weight(1f, fill = false).widthIn(min = 60.dp))
        }

        Column(
            horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
        ) {
            // Sender name for group messages
            if (senderName != null && !isMe) {
                Text(
                    text = senderName,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = senderColor ?: MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 12.dp, bottom = 2.dp)
                )
            }

            // Message bubble
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = if (isMe) SentMessageBackground else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .clickable(onClick = onLongClick)
            ) {
                // Use IntrinsicSize.Max to ensure all children share the maximum width
                // This makes the reply bubble at least as wide as the preview
                Column(
                    modifier = Modifier
                        .width(IntrinsicSize.Max)
                        .padding(
                            horizontal = 12.dp,
                            vertical = 8.dp
                        )
                ) {
                    // Reply preview
                    if (replyPreview != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isMe) Color.White.copy(alpha = 0.15f)
                                   else MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 4.dp)
                                .clickable { onReplyClick?.invoke() }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(2.dp)
                                        .size(2.dp, 24.dp)
                                        .background(
                                            if (isMe) Color.White.copy(alpha = 0.5f)
                                            else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                        )
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    if (replyAuthor != null) {
                                        Text(
                                            text = replyAuthor,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (isMe) Color.White.copy(alpha = 0.8f)
                                                   else MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Text(
                                        text = replyPreview,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isMe) Color.White.copy(alpha = 0.7f)
                                               else MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2
                                    )
                                }
                            }
                        }
                    }

                    // Message content
                    Text(
                        text = if (isDeleted) "This message was deleted" else message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isMe) Color.White else MaterialTheme.colorScheme.onSurface,
                        fontStyle = if (isDeleted) FontStyle.Italic else FontStyle.Normal,
                        modifier = if (replyPreview != null) Modifier.fillMaxWidth() else Modifier
                    )

                    // Timestamp and delivery status
                    Row(
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(top = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = timestamp,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isMe) Color.White.copy(alpha = 0.7f)
                                   else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )

                        if (isMe) {
                            Spacer(modifier = Modifier.width(4.dp))
                            when (deliveryStatus) {
                                DeliveryStatus.SENDING -> Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = "Sending",
                                    modifier = Modifier.size(12.dp),
                                    tint = Color.White.copy(alpha = 0.7f)
                                )
                                DeliveryStatus.SENT -> Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Sent",
                                    modifier = Modifier.size(12.dp),
                                    tint = Color.White.copy(alpha = 0.7f)
                                )
                                DeliveryStatus.FAILED -> Icon(
                                    imageVector = Icons.Default.Error,
                                    contentDescription = "Failed",
                                    modifier = Modifier.size(12.dp),
                                    tint = Color.Red
                                )
                            }
                        }
                    }
                }
            }

            // Reactions
            if (!reactions.isNullOrEmpty()) {
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    reactions.take(5).forEach { (emoji, count) ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = emoji, fontSize = 14.sp)
                                if (count > 1) {
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        text = count.toString(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (!isMe) {
            Spacer(modifier = Modifier.weight(1f, fill = false).widthIn(min = 60.dp))
        }
    }
}

@Composable
fun SystemMessage(
    text: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

@Preview
@Composable
private fun SentMessagePreview() {
    XMTPTheme {
        MessageBubble(
            message = "Hello! How are you doing today?",
            timestamp = "10:30 AM",
            isMe = true
        )
    }
}

@Preview
@Composable
private fun ReceivedMessagePreview() {
    XMTPTheme {
        MessageBubble(
            message = "I'm doing great, thanks for asking!",
            timestamp = "10:31 AM",
            isMe = false,
            senderName = "0x1234...5678",
            senderColor = Color(0xFF5856D6)
        )
    }
}

@Preview
@Composable
private fun MessageWithReactionsPreview() {
    XMTPTheme {
        MessageBubble(
            message = "This is awesome!",
            timestamp = "10:32 AM",
            isMe = true,
            reactions = listOf("👍" to 3, "❤️" to 2)
        )
    }
}

@Preview
@Composable
private fun ReplyMessagePreview() {
    XMTPTheme {
        MessageBubble(
            message = "Yes, I agree with you!",
            timestamp = "10:33 AM",
            isMe = false,
            replyPreview = "This is the original message that was very long...",
            replyAuthor = "0x1234...5678"
        )
    }
}

@Preview
@Composable
private fun DeletedMessagePreview() {
    XMTPTheme {
        MessageBubble(
            message = "",
            timestamp = "10:34 AM",
            isMe = true,
            isDeleted = true
        )
    }
}

@Preview
@Composable
private fun SystemMessagePreview() {
    XMTPTheme {
        SystemMessage(text = "Alice joined the group")
    }
}
