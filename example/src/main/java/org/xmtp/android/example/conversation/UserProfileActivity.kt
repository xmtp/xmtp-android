package org.xmtp.android.example.conversation

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.xmtp.android.example.ClientManager
import org.xmtp.android.example.ui.theme.XMTPTheme
import org.xmtp.android.library.libxmtp.IdentityKind
import org.xmtp.android.library.libxmtp.InboxState
import org.xmtp.android.library.libxmtp.PublicIdentity
import kotlin.math.abs

class UserProfileActivity : ComponentActivity() {
    companion object {
        private const val EXTRA_WALLET_ADDRESS = "EXTRA_WALLET_ADDRESS"
        private const val EXTRA_INBOX_ID = "EXTRA_INBOX_ID"

        fun intent(
            context: Context,
            walletAddress: String,
            inboxId: String? = null,
        ): Intent =
            Intent(context, UserProfileActivity::class.java).apply {
                putExtra(EXTRA_WALLET_ADDRESS, walletAddress)
                putExtra(EXTRA_INBOX_ID, inboxId)
            }
    }

    private val avatarColors = listOf(
        Color(0xFFFC4F37),
        Color(0xFF5856D6),
        Color(0xFF34C759),
        Color(0xFFFF9500),
        Color(0xFF007AFF),
        Color(0xFFAF52DE),
        Color(0xFF00C7BE),
        Color(0xFFFF2D55),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val walletAddress = intent.getStringExtra(EXTRA_WALLET_ADDRESS) ?: run {
            finish()
            return
        }
        val inboxId = intent.getStringExtra(EXTRA_INBOX_ID)

        setContent {
            XMTPTheme {
                var isLoading by remember { mutableStateOf(false) }
                var inboxState by remember { mutableStateOf<InboxState?>(null) }
                var isStartingConversation by remember { mutableStateOf(false) }

                // Load inbox state if we have an inbox ID
                LaunchedEffect(inboxId) {
                    if (inboxId != null) {
                        isLoading = true
                        try {
                            val inboxStates = withContext(Dispatchers.IO) {
                                ClientManager.client.inboxStatesForInboxIds(
                                    refreshFromNetwork = true,
                                    inboxIds = listOf(inboxId),
                                )
                            }
                            inboxState = inboxStates.firstOrNull()
                        } catch (e: Exception) {
                            // Silently fail - we still have basic info
                        } finally {
                            isLoading = false
                        }
                    }
                }

                UserProfileScreen(
                    walletAddress = walletAddress,
                    inboxId = inboxId,
                    inboxState = inboxState,
                    isLoading = isLoading || isStartingConversation,
                    avatarColors = avatarColors,
                    onBackClick = { finish() },
                    onCopyAddress = { copyToClipboard("Wallet Address", walletAddress) },
                    onCopyInboxId = {
                        inboxId?.let { id -> copyToClipboard("Inbox ID", id) }
                    },
                    onSendMessage = {
                        isStartingConversation = true
                        startConversation(walletAddress)
                    },
                )
            }
        }
    }

    private fun copyToClipboard(label: String, text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "$label copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    private fun startConversation(walletAddress: String) {
        lifecycleScope.launch {
            try {
                val conversation = withContext(Dispatchers.IO) {
                    val publicIdentity = PublicIdentity(IdentityKind.ETHEREUM, walletAddress)
                    ClientManager.client.conversations.findOrCreateDmWithIdentity(publicIdentity)
                }

                startActivity(
                    ConversationDetailActivity.intent(
                        this@UserProfileActivity,
                        topic = conversation.topic,
                        peerAddress = conversation.id,
                    ),
                )
                finish()
            } catch (e: Exception) {
                Toast.makeText(
                    this@UserProfileActivity,
                    e.localizedMessage ?: "Error",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UserProfileScreen(
    walletAddress: String,
    inboxId: String?,
    inboxState: InboxState?,
    isLoading: Boolean,
    avatarColors: List<Color>,
    onBackClick: () -> Unit,
    onCopyAddress: () -> Unit,
    onCopyInboxId: () -> Unit,
    onSendMessage: () -> Unit,
) {
    val avatarText = walletAddress
        .removePrefix("0x")
        .take(2)
        .uppercase()

    val colorIndex = abs((inboxId ?: walletAddress).hashCode()) % avatarColors.size
    val avatarColor = avatarColors[colorIndex]

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile") },
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(modifier = Modifier.height(24.dp))

                // Avatar
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(avatarColor),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = avatarText,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Abbreviated address
                Text(
                    text = walletAddress.truncatedAddress(),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Wallet Address Card
                InfoCard(
                    title = "Wallet Address",
                    value = walletAddress,
                    onCopy = onCopyAddress,
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Inbox ID Card
                InfoCard(
                    title = "Inbox ID",
                    value = inboxId ?: "Unknown",
                    onCopy = if (inboxId != null) onCopyInboxId else null,
                )

                // Linked Identities
                inboxState?.let { state ->
                    if (state.identities.size > 1) {
                        Spacer(modifier = Modifier.height(24.dp))

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            ),
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                            ) {
                                Text(
                                    text = "Linked Identities",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                state.identities.forEach { identity ->
                                    Text(
                                        text = identity.identifier,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(vertical = 4.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Send Message Button
                Button(
                    onClick = onSendMessage,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    enabled = !isLoading,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Message,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = "Send Message",
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }

            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
    }
}

@Composable
private fun InfoCard(
    title: String,
    value: String,
    onCopy: (() -> Unit)?,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (onCopy != null) {
                IconButton(onClick = onCopy) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy",
                        tint = MaterialTheme.colorScheme.primary,
                    )
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
