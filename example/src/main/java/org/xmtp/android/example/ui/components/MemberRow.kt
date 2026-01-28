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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.xmtp.android.example.ui.theme.XMTPTheme
import kotlin.math.abs

enum class MemberPermissionLevel {
    SUPER_ADMIN,
    ADMIN,
    MEMBER,
}

data class MemberRowData(
    val inboxId: String,
    val displayAddress: String?,
    val permissionLevel: MemberPermissionLevel,
    val isCurrentUser: Boolean,
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
fun MemberRow(
    member: MemberRowData,
    canManageMembers: Boolean,
    modifier: Modifier = Modifier,
    onMemberClick: () -> Unit = {},
    onPromoteToAdmin: () -> Unit = {},
    onDemoteFromAdmin: () -> Unit = {},
    onRemoveMember: () -> Unit = {},
) {
    var showMenu by remember { mutableStateOf(false) }

    val avatarText = member.displayAddress
        ?.removePrefix("0x")
        ?.take(2)
        ?.uppercase()
        ?: member.inboxId.take(2).uppercase()

    val colorIndex = abs(member.inboxId.hashCode()) % avatarColors.size
    val avatarColor = avatarColors[colorIndex]

    val displayName = if (member.isCurrentUser) {
        "${member.displayAddress?.truncatedAddress() ?: member.inboxId.truncatedAddress()} (You)"
    } else {
        member.displayAddress?.truncatedAddress() ?: member.inboxId.truncatedAddress()
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onMemberClick),
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

            // Member info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )

                    // Role badge
                    when (member.permissionLevel) {
                        MemberPermissionLevel.SUPER_ADMIN -> {
                            RoleBadge(
                                text = "Super Admin",
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        MemberPermissionLevel.ADMIN -> {
                            RoleBadge(
                                text = "Admin",
                                color = Color(0xFF5856D6),
                            )
                        }
                        MemberPermissionLevel.MEMBER -> {
                            // No badge for regular members
                        }
                    }
                }

                Text(
                    text = "Inbox: ${member.inboxId.take(8)}...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Menu button
            if (canManageMembers && !member.isCurrentUser) {
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Member options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                    ) {
                        when (member.permissionLevel) {
                            MemberPermissionLevel.MEMBER -> {
                                DropdownMenuItem(
                                    text = { Text("Promote to Admin") },
                                    onClick = {
                                        showMenu = false
                                        onPromoteToAdmin()
                                    },
                                )
                            }
                            MemberPermissionLevel.ADMIN -> {
                                DropdownMenuItem(
                                    text = { Text("Remove Admin") },
                                    onClick = {
                                        showMenu = false
                                        onDemoteFromAdmin()
                                    },
                                )
                            }
                            MemberPermissionLevel.SUPER_ADMIN -> {
                                // Can't modify super admin
                            }
                        }
                        if (member.permissionLevel != MemberPermissionLevel.SUPER_ADMIN) {
                            DropdownMenuItem(
                                text = { Text("Remove from Group") },
                                onClick = {
                                    showMenu = false
                                    onRemoveMember()
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoleBadge(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.15f),
        modifier = modifier,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
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

@Preview(showBackground = true)
@Composable
private fun MemberRowPreview() {
    XMTPTheme {
        MemberRow(
            member = MemberRowData(
                inboxId = "abc123def456",
                displayAddress = "0x1234567890abcdef1234567890abcdef12345678",
                permissionLevel = MemberPermissionLevel.MEMBER,
                isCurrentUser = false,
            ),
            canManageMembers = true,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun MemberRowAdminPreview() {
    XMTPTheme {
        MemberRow(
            member = MemberRowData(
                inboxId = "admin123456",
                displayAddress = "0xabcdef1234567890abcdef1234567890abcdef12",
                permissionLevel = MemberPermissionLevel.ADMIN,
                isCurrentUser = false,
            ),
            canManageMembers = true,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun MemberRowCurrentUserPreview() {
    XMTPTheme {
        MemberRow(
            member = MemberRowData(
                inboxId = "myinbox12345",
                displayAddress = "0x9876543210fedcba9876543210fedcba98765432",
                permissionLevel = MemberPermissionLevel.SUPER_ADMIN,
                isCurrentUser = true,
            ),
            canManageMembers = true,
        )
    }
}
