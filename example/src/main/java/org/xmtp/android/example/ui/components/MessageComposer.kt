package org.xmtp.android.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.xmtp.android.example.ui.theme.XMTPTheme

@Composable
fun MessageComposer(
    value: String,
    onValueChange: (String) -> Unit,
    onSendClick: () -> Unit,
    modifier: Modifier = Modifier,
    isSending: Boolean = false,
    replyTo: String? = null,
    replyAuthor: String? = null,
    onClearReply: () -> Unit = {},
    onAttachmentClick: () -> Unit = {},
    onEmojiClick: () -> Unit = {},
) {
    val canSend = value.isNotBlank() && !isSending

    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .imePadding(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
    ) {
        Column {
            // Reply preview
            AnimatedVisibility(
                visible = replyTo != null,
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
            ) {
                if (replyTo != null) {
                    ReplyPreview(
                        message = replyTo,
                        author = replyAuthor,
                        onDismiss = onClearReply,
                    )
                }
            }

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                // Attachment button
                IconButton(
                    onClick = onAttachmentClick,
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add attachment",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }

                // Text input field
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        BasicTextField(
                            value = value,
                            onValueChange = onValueChange,
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .heightIn(min = 40.dp, max = 120.dp)
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                            textStyle =
                                TextStyle(
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 16.sp,
                                ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            decorationBox = { innerTextField ->
                                Box {
                                    if (value.isEmpty()) {
                                        Text(
                                            text = "Message",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 16.sp,
                                        )
                                    }
                                    innerTextField()
                                }
                            },
                            enabled = !isSending,
                        )

                        // Emoji button
                        IconButton(
                            onClick = onEmojiClick,
                            modifier =
                                Modifier
                                    .size(36.dp)
                                    .padding(bottom = 2.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.EmojiEmotions,
                                contentDescription = "Emoji",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Send button
                Box(
                    modifier =
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(
                                if (canSend) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                            ).clickable(enabled = canSend) { onSendClick() },
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReplyPreview(
    message: String,
    author: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Left accent bar
        Box(
            modifier =
                Modifier
                    .width(2.dp)
                    .height(32.dp)
                    .background(MaterialTheme.colorScheme.primary),
        )

        Spacer(modifier = Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Replying to ${author ?: "message"}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }

        IconButton(
            onClick = onDismiss,
            modifier = Modifier.size(24.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Cancel reply",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Preview
@Composable
private fun MessageComposerPreview() {
    XMTPTheme {
        var text by remember { mutableStateOf("") }
        MessageComposer(
            value = text,
            onValueChange = { text = it },
            onSendClick = {},
        )
    }
}

@Preview
@Composable
private fun MessageComposerWithTextPreview() {
    XMTPTheme {
        MessageComposer(
            value = "Hello, this is a test message!",
            onValueChange = {},
            onSendClick = {},
        )
    }
}

@Preview
@Composable
private fun MessageComposerSendingPreview() {
    XMTPTheme {
        MessageComposer(
            value = "Sending message...",
            onValueChange = {},
            onSendClick = {},
            isSending = true,
        )
    }
}

@Preview
@Composable
private fun MessageComposerWithReplyPreview() {
    XMTPTheme {
        MessageComposer(
            value = "",
            onValueChange = {},
            onSendClick = {},
            replyTo = "This is the original message content",
            replyAuthor = "0x1234...5678",
        )
    }
}
