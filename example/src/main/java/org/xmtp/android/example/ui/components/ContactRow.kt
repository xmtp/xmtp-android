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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

data class RecentContactData(
    val address: String,
    val lastActivityTimeNs: Long,
)

private val avatarColors = listOf(
    Color(0xFFFC4F37), // XMTP Red
    Color(0xFF5856D6), // Purple
    Color(0xFF34C759), // Green
    Color(0xFFFF9500), // Orange
    Color(0xFF007AFF), // Blue
    Color(0xFFAF52DE), // Magenta
    Color(0xFF00C7BE), // Teal
    Color(0xFFFF2D55), // Pink
)

@Composable
fun ContactRow(
    contact: RecentContactData,
    modifier: Modifier = Modifier,
    isGroupMode: Boolean = false,
    isSelected: Boolean = false,
    onClick: () -> Unit = {},
    onAddClick: () -> Unit = {},
) {
    val avatarText = contact.address
        .removePrefix("0x")
        .take(2)
        .uppercase()

    val colorIndex = abs(contact.address.hashCode()) % avatarColors.size
    val avatarColor = avatarColors[colorIndex]

    val displayAddress = contact.address.truncatedAddress()
    val lastActivity = formatLastActivity(contact.lastActivityTimeNs)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(avatarColor),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = avatarText,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Contact info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = displayAddress,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = lastActivity,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Group mode controls
            if (isGroupMode) {
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                } else {
                    IconButton(onClick = onAddClick) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add to group",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

private fun String.truncatedAddress(): String {
    if (length >= 10) {
        val start = 6
        val end = length - 4
        return replaceRange(start, end, "...")
    }
    return this
}

private fun formatLastActivity(timestampNs: Long): String {
    val timestampMs = timestampNs / 1_000_000
    val messageDate = Date(timestampMs)
    val now = Calendar.getInstance()
    val messageCalendar = Calendar.getInstance().apply { time = messageDate }

    return when {
        now.get(Calendar.YEAR) == messageCalendar.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) == messageCalendar.get(Calendar.DAY_OF_YEAR) -> {
            "Last messaged today"
        }
        now.get(Calendar.YEAR) == messageCalendar.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) - messageCalendar.get(Calendar.DAY_OF_YEAR) == 1 -> {
            "Last messaged yesterday"
        }
        now.get(Calendar.YEAR) == messageCalendar.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) - messageCalendar.get(Calendar.DAY_OF_YEAR) < 7 -> {
            val daysDiff = now.get(Calendar.DAY_OF_YEAR) - messageCalendar.get(Calendar.DAY_OF_YEAR)
            "Last messaged $daysDiff days ago"
        }
        else -> {
            val formattedDate = SimpleDateFormat("MMM d", Locale.getDefault()).format(messageDate)
            "Last messaged $formattedDate"
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ContactRowPreview() {
    XMTPTheme {
        ContactRow(
            contact = RecentContactData(
                address = "0x1234567890abcdef1234567890abcdef12345678",
                lastActivityTimeNs = System.currentTimeMillis() * 1_000_000,
            ),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ContactRowGroupModePreview() {
    XMTPTheme {
        ContactRow(
            contact = RecentContactData(
                address = "0xabcdef1234567890abcdef1234567890abcdef12",
                lastActivityTimeNs = (System.currentTimeMillis() - 86400000) * 1_000_000, // yesterday
            ),
            isGroupMode = true,
            isSelected = false,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ContactRowSelectedPreview() {
    XMTPTheme {
        ContactRow(
            contact = RecentContactData(
                address = "0x9876543210fedcba9876543210fedcba98765432",
                lastActivityTimeNs = (System.currentTimeMillis() - 172800000) * 1_000_000, // 2 days ago
            ),
            isGroupMode = true,
            isSelected = true,
        )
    }
}
