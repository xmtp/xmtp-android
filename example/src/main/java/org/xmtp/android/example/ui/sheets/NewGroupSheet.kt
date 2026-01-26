package org.xmtp.android.example.ui.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.xmtp.android.example.ui.components.ContactRow
import org.xmtp.android.example.ui.components.RecentContactData
import org.xmtp.android.example.ui.theme.XMTPTheme

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NewGroupSheet(
    groupName: String,
    addressInput: String,
    selectedMembers: List<String> = emptyList(),
    recentContacts: List<RecentContactData> = emptyList(),
    isLoading: Boolean = false,
    canCreate: Boolean = false,
    onDismiss: () -> Unit,
    onGroupNameChange: (String) -> Unit,
    onAddressChange: (String) -> Unit,
    onAddMember: () -> Unit,
    onRemoveMember: (String) -> Unit,
    onRecentContactClick: (String) -> Unit,
    onCreate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                text = "New Group",
                style = MaterialTheme.typography.titleLarge,
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Group name input
            Card(
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

            Spacer(modifier = Modifier.height(16.dp))

            // Selected members chips
            if (selectedMembers.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "${selectedMembers.size} member(s) added",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Address input
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = addressInput,
                        onValueChange = onAddressChange,
                        modifier = Modifier
                            .weight(1f)
                            .padding(4.dp),
                        placeholder = { Text("Add member address") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "Address",
                            )
                        },
                        singleLine = true,
                    )

                    if (addressInput.isNotEmpty()) {
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

            Text(
                text = "Enter a valid Ethereum address (0x...)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )

            // Recent contacts
            if (recentContacts.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "RECENT CONTACTS",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))

                recentContacts.take(5).forEach { contact ->
                    ContactRow(
                        contact = contact,
                        isGroupMode = true,
                        isSelected = selectedMembers.contains(contact.address),
                        onClick = { onRecentContactClick(contact.address) },
                        onAddClick = { onRecentContactClick(contact.address) },
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Create button
            Button(
                onClick = {
                    onCreate()
                    onDismiss()
                },
                enabled = canCreate && !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text("Create Group")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Preview(showBackground = true)
@Composable
private fun NewGroupSheetContentPreview() {
    XMTPTheme {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Text(
                text = "New Group",
                style = MaterialTheme.typography.titleLarge,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                OutlinedTextField(
                    value = "XMTP Dev Team",
                    onValueChange = {},
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

            Spacer(modifier = Modifier.height(16.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                InputChip(
                    selected = false,
                    onClick = {},
                    label = { Text("0x1234...5678") },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Remove",
                            modifier = Modifier.size(18.dp),
                        )
                    },
                )
                InputChip(
                    selected = false,
                    onClick = {},
                    label = { Text("0xabcd...efgh") },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Remove",
                            modifier = Modifier.size(18.dp),
                        )
                    },
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "2 member(s) added",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {},
                enabled = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Text("Create Group")
            }
        }
    }
}
