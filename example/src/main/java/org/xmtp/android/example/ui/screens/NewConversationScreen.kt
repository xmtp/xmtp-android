package org.xmtp.android.example.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.xmtp.android.example.ui.components.ContactRow
import org.xmtp.android.example.ui.components.RecentContactData
import org.xmtp.android.example.ui.theme.XMTPTheme

enum class ConversationType {
    DM,
    GROUP,
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NewConversationScreen(
    addressInput: String,
    groupName: String = "",
    selectedMembers: List<String> = emptyList(),
    recentContacts: List<RecentContactData> = emptyList(),
    conversationType: ConversationType = ConversationType.DM,
    isLoading: Boolean = false,
    canCreate: Boolean = false,
    modifier: Modifier = Modifier,
    onAddressChange: (String) -> Unit = {},
    onGroupNameChange: (String) -> Unit = {},
    onConversationTypeChange: (ConversationType) -> Unit = {},
    onAddMember: () -> Unit = {},
    onRemoveMember: (String) -> Unit = {},
    onRecentContactClick: (String) -> Unit = {},
    onClose: () -> Unit = {},
    onCreate: () -> Unit = {},
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("New Message") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
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
        bottomBar = {
            Button(
                onClick = onCreate,
                enabled = canCreate && !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .height(52.dp),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(
                        text = when (conversationType) {
                            ConversationType.DM -> "Start Conversation"
                            ConversationType.GROUP -> "Create Group"
                        },
                    )
                }
            }
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Conversation type selector
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                SegmentedButton(
                    selected = conversationType == ConversationType.DM,
                    onClick = { onConversationTypeChange(ConversationType.DM) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "DM",
                            modifier = Modifier.size(18.dp),
                        )
                    },
                ) {
                    Text("Direct Message")
                }
                SegmentedButton(
                    selected = conversationType == ConversationType.GROUP,
                    onClick = { onConversationTypeChange(ConversationType.GROUP) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Groups,
                            contentDescription = "Group",
                            modifier = Modifier.size(18.dp),
                        )
                    },
                ) {
                    Text("Group Chat")
                }
            }

            // Group name input (only for groups)
            if (conversationType == ConversationType.GROUP) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    OutlinedTextField(
                        value = groupName,
                        onValueChange = onGroupNameChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp),
                        placeholder = { Text("Group name (optional)") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Groups,
                                contentDescription = "Group",
                            )
                        },
                        singleLine = true,
                    )
                }

                // Selected members chips
                if (selectedMembers.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                    ) {
                        selectedMembers.forEach { member ->
                            InputChip(
                                selected = false,
                                onClick = { onRemoveMember(member) },
                                label = {
                                    Text(
                                        text = if (member.length > 12) {
                                            member.take(6) + "..." + member.takeLast(4)
                                        } else {
                                            member
                                        },
                                    )
                                },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove",
                                        modifier = Modifier.size(18.dp),
                                    )
                                },
                                modifier = Modifier.padding(end = 8.dp, bottom = 4.dp),
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Address input
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = addressInput,
                        onValueChange = onAddressChange,
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Enter wallet address") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "Address",
                            )
                        },
                        trailingIcon = {
                            if (addressInput.isNotEmpty()) {
                                IconButton(onClick = { onAddressChange("") }) {
                                    Icon(
                                        imageVector = if (conversationType == ConversationType.GROUP) {
                                            Icons.Default.Add
                                        } else {
                                            Icons.Default.Clear
                                        },
                                        contentDescription = if (conversationType == ConversationType.GROUP) {
                                            "Add member"
                                        } else {
                                            "Clear"
                                        },
                                    )
                                }
                            }
                        },
                        singleLine = true,
                    )

                    if (conversationType == ConversationType.GROUP && addressInput.isNotEmpty()) {
                        IconButton(onClick = onAddMember) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add member",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }

            // Helper text
            Text(
                text = "Enter a valid Ethereum address (0x...)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )

            if (conversationType == ConversationType.GROUP && selectedMembers.isNotEmpty()) {
                Text(
                    text = "${selectedMembers.size} member(s) added",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }

            // Recent contacts section
            if (recentContacts.isNotEmpty()) {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "RECENT CONTACTS",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Spacer(modifier = Modifier.height(8.dp))

                recentContacts.forEach { contact ->
                    ContactRow(
                        contact = contact,
                        isGroupMode = conversationType == ConversationType.GROUP,
                        isSelected = selectedMembers.contains(contact.address),
                        onClick = { onRecentContactClick(contact.address) },
                        onAddClick = { onRecentContactClick(contact.address) },
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun NewConversationScreenDMPreview() {
    XMTPTheme {
        NewConversationScreen(
            addressInput = "0x1234567890abcdef",
            conversationType = ConversationType.DM,
            recentContacts = listOf(
                RecentContactData(
                    address = "0xabcd1234efgh5678",
                    lastActivityTimeNs = System.currentTimeMillis() * 1_000_000,
                ),
                RecentContactData(
                    address = "0x9876543210fedcba",
                    lastActivityTimeNs = (System.currentTimeMillis() - 3600000) * 1_000_000,
                ),
            ),
            canCreate = true,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun NewConversationScreenGroupPreview() {
    XMTPTheme {
        NewConversationScreen(
            addressInput = "",
            groupName = "XMTP Dev Team",
            conversationType = ConversationType.GROUP,
            selectedMembers = listOf(
                "0x1234567890abcdef1234567890abcdef12345678",
                "0xabcdef1234567890abcdef1234567890abcdef12",
            ),
            recentContacts = listOf(
                RecentContactData(
                    address = "0xabcd1234efgh5678",
                    lastActivityTimeNs = System.currentTimeMillis() * 1_000_000,
                ),
            ),
            canCreate = true,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun NewConversationScreenLoadingPreview() {
    XMTPTheme {
        NewConversationScreen(
            addressInput = "0x1234567890abcdef",
            conversationType = ConversationType.DM,
            isLoading = true,
            canCreate = true,
        )
    }
}
