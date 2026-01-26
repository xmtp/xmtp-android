package org.xmtp.android.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.xmtp.android.example.ui.components.MemberPermissionLevel
import org.xmtp.android.example.ui.components.MemberRow
import org.xmtp.android.example.ui.components.MemberRowData
import org.xmtp.android.example.ui.theme.XMTPTheme
import kotlin.math.abs

data class GroupDetails(
    val groupId: String,
    val groupName: String,
    val createdAt: String,
    val memberCount: Int,
    val currentUserRole: MemberPermissionLevel,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupManagementScreen(
    groupDetails: GroupDetails,
    members: List<MemberRowData>,
    isLoading: Boolean = false,
    modifier: Modifier = Modifier,
    onBackClick: () -> Unit = {},
    onAddMemberClick: () -> Unit = {},
    onMemberClick: (String) -> Unit = {},
    onPromoteToAdmin: (String) -> Unit = {},
    onDemoteFromAdmin: (String) -> Unit = {},
    onRemoveMember: (String) -> Unit = {},
    onLeaveGroup: () -> Unit = {},
) {
    val canManageMembers = groupDetails.currentUserRole == MemberPermissionLevel.SUPER_ADMIN ||
        groupDetails.currentUserRole == MemberPermissionLevel.ADMIN

    val avatarColors = listOf(
        Color(0xFFFC4F37),
        Color(0xFF5856D6),
        Color(0xFF34C759),
        Color(0xFFFF9500),
        Color(0xFF007AFF),
        Color(0xFFAF52DE),
        Color(0xFF00C7BE),
        Color(0xFFFF2D55),
    )
    val avatarColor = avatarColors[abs(groupDetails.groupId.hashCode()) % avatarColors.size]

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Group Info") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                ),
            )
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(modifier = Modifier.height(24.dp))

                    // Group Avatar
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(avatarColor),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = groupDetails.groupName
                                .removePrefix("0x")
                                .take(2)
                                .uppercase(),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Group ID
                    Text(
                        text = groupDetails.groupId,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Group Details Card
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                        ) {
                            Text(
                                text = "Group Details",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            InfoRow(label = "Created", value = groupDetails.createdAt)
                            Spacer(modifier = Modifier.height(12.dp))
                            InfoRow(label = "Members", value = "${groupDetails.memberCount} members")
                            Spacer(modifier = Modifier.height(12.dp))
                            InfoRow(
                                label = "Your Role",
                                value = when (groupDetails.currentUserRole) {
                                    MemberPermissionLevel.SUPER_ADMIN -> "Super Admin"
                                    MemberPermissionLevel.ADMIN -> "Admin"
                                    MemberPermissionLevel.MEMBER -> "Member"
                                },
                                valueColor = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Members Card
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "Members",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f),
                                )

                                if (canManageMembers) {
                                    TextButton(onClick = onAddMemberClick) {
                                        Icon(
                                            imageVector = Icons.Default.PersonAdd,
                                            contentDescription = "Add member",
                                            modifier = Modifier.size(18.dp),
                                        )
                                        Text(
                                            text = "Add",
                                            modifier = Modifier.padding(start = 4.dp),
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            members.forEach { member ->
                                MemberRow(
                                    member = member,
                                    canManageMembers = canManageMembers && !member.isCurrentUser,
                                    onMemberClick = { onMemberClick(member.inboxId) },
                                    onPromoteToAdmin = { onPromoteToAdmin(member.inboxId) },
                                    onDemoteFromAdmin = { onDemoteFromAdmin(member.inboxId) },
                                    onRemoveMember = { onRemoveMember(member.inboxId) },
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Leave Group Button
                    OutlinedButton(
                        onClick = onLeaveGroup,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .height(52.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = "Leave group",
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = "Leave Group",
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = valueColor,
            fontWeight = if (valueColor != MaterialTheme.colorScheme.onSurface) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun GroupManagementScreenPreview() {
    XMTPTheme {
        GroupManagementScreen(
            groupDetails = GroupDetails(
                groupId = "0x1234...abcd",
                groupName = "XMTP Dev Team",
                createdAt = "Jan 15, 2024",
                memberCount = 3,
                currentUserRole = MemberPermissionLevel.SUPER_ADMIN,
            ),
            members = listOf(
                MemberRowData(
                    inboxId = "inbox1",
                    displayAddress = "0x1234...5678",
                    permissionLevel = MemberPermissionLevel.SUPER_ADMIN,
                    isCurrentUser = true,
                ),
                MemberRowData(
                    inboxId = "inbox2",
                    displayAddress = "0xabcd...efgh",
                    permissionLevel = MemberPermissionLevel.ADMIN,
                    isCurrentUser = false,
                ),
                MemberRowData(
                    inboxId = "inbox3",
                    displayAddress = "0x9876...5432",
                    permissionLevel = MemberPermissionLevel.MEMBER,
                    isCurrentUser = false,
                ),
            ),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun GroupManagementScreenLoadingPreview() {
    XMTPTheme {
        GroupManagementScreen(
            groupDetails = GroupDetails(
                groupId = "",
                groupName = "",
                createdAt = "",
                memberCount = 0,
                currentUserRole = MemberPermissionLevel.MEMBER,
            ),
            members = emptyList(),
            isLoading = true,
        )
    }
}
