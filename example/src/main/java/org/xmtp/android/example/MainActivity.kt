package org.xmtp.android.example

import android.Manifest
import android.accounts.AccountManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.xmtp.android.example.connect.ConnectWalletActivity
import org.xmtp.android.example.conversation.ConversationDetailActivity
import org.xmtp.android.example.conversation.NewConversationActivity
import org.xmtp.android.example.pushnotifications.PushNotificationTokenManager
import org.xmtp.android.example.ui.components.DrawerState
import org.xmtp.android.example.ui.screens.ConversationItem
import org.xmtp.android.example.ui.screens.MainScreen
import org.xmtp.android.example.ui.sheets.LogFileInfo
import org.xmtp.android.example.ui.sheets.LogViewerSheet
import org.xmtp.android.example.ui.sheets.WalletInfo
import org.xmtp.android.example.ui.sheets.WalletInfoSheet
import org.xmtp.android.example.ui.sheets.formatFileSize
import org.xmtp.android.example.ui.theme.XMTPTheme
import org.xmtp.android.example.utils.KeyUtil
import org.xmtp.android.library.Client
import timber.log.Timber
import uniffi.xmtpv3.FfiLogLevel
import uniffi.xmtpv3.FfiLogRotation
import org.xmtp.android.example.extension.truncatedAddress
import org.xmtp.android.library.Conversation
import org.xmtp.android.library.codecs.Attachment
import org.xmtp.android.library.codecs.DeletedMessage
import org.xmtp.android.library.libxmtp.Reply
import org.xmtp.proto.mls.message.contents.TranscriptMessages.GroupUpdated
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private lateinit var accountManager: AccountManager
    private val REQUEST_CODE_POST_NOTIFICATIONS = 101

    companion object {
        private const val PREFS_NAME = "XMTPPreferences"
        private const val KEY_LOGS_ACTIVATED = "logs_activated"

        // Push notification server - configured via build variants in production
        private const val DEFAULT_PUSH_SERVER = "10.0.2.2:8080"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        accountManager = AccountManager.get(this)
        checkAndRequestPermissions()
        PushNotificationTokenManager.init(this, DEFAULT_PUSH_SERVER)
        viewModel.setupPush()

        val keys = KeyUtil(this).loadKeys()
        if (keys == null) {
            showSignIn()
            return
        }

        ClientManager.createClient(keys, this)

        setContent {
            XMTPTheme {
                MainActivityContent(
                    viewModel = viewModel,
                    onConversationClick = { conversationId, topic ->
                        openConversation(topic, conversationId)
                    },
                    onNewConversationClick = { openNewConversation() },
                    onNewGroupClick = { openNewConversation() },
                    onHideDeletedMessagesToggle = { enabled -> onHideDeletedMessagesToggled(enabled) },
                    onToggleLogsClick = { enabled -> onLogsToggled(enabled) },
                    onCopyAddressClick = { copyWalletAddress() },
                    onDisconnectClick = { disconnectWallet() },
                    isLogsEnabled = isLogsActivated(),
                    hideDeletedMessages = KeyUtil(this).getHideDeletedMessages(),
                )
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                ClientManager.clientState.collect(::ensureClientState)
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                ClientManager.clientState.collect { state ->
                    when (state) {
                        is ClientManager.ClientState.Error -> {
                            retryCreateClientWithBackoff()
                        }

                        is ClientManager.ClientState.Ready -> {
                            retryJob?.cancel()
                            retryJob = null
                        }

                        else -> Unit
                    }
                }
            }
        }
    }

    private var retryJob: Job? = null

    private fun retryCreateClientWithBackoff() {
        if (retryJob?.isActive == true) return // Prevent duplicate retries

        val keys =
            KeyUtil(this).loadKeys() ?: run {
                showSignIn()
                return
            }

        retryJob =
            lifecycleScope.launch(Dispatchers.IO) {
                var delayTime = 1000L // start at 1 second
                val maxDelay = 30000L // cap at 30 seconds

                while (ClientManager.clientState.value is ClientManager.ClientState.Error && isActive) {
                    Log.d("RETRY", "Retrying client creation after ${delayTime}ms")
                    ClientManager.createClient(keys, this@MainActivity)

                    delay(delayTime)
                    delayTime = (delayTime * 2).coerceAtMost(maxDelay) // exponential backoff
                }

                Log.d("RETRY", "Retry stopped: current state=${ClientManager.clientState.value}")
            }
    }

    override fun onResume() {
        super.onResume()
        // Check if logs were previously activated and reactivate if needed
        if (isLogsActivated()) {
            Client.activatePersistentLibXMTPLogWriter(applicationContext, FfiLogLevel.DEBUG, FfiLogRotation.MINUTELY, 3)
        }

        // If we're still in error state, make sure retry is running
        if (ClientManager.clientState.value is ClientManager.ClientState.Error) {
            val keys = KeyUtil(this).loadKeys()
            if (keys == null) {
                showSignIn()
                return
            }
            retryCreateClientWithBackoff()
        }
    }

    override fun onDestroy() {
        // Cancel the retry job to prevent memory leaks
        retryJob?.cancel()
        retryJob = null
        super.onDestroy()
    }

    private fun openConversation(topic: String, peerAddress: String) {
        startActivity(
            ConversationDetailActivity.intent(
                this,
                topic = topic,
                peerAddress = peerAddress,
            ),
        )
    }

    private fun ensureClientState(clientState: ClientManager.ClientState) {
        Timber.d("ensureClientState: $clientState")
        when (clientState) {
            is ClientManager.ClientState.Ready -> {
                Timber.d("ensureClientState: Ready, fetching conversations...")
                viewModel.fetchConversations()
            }

            is ClientManager.ClientState.Error -> {
                Timber.e("ensureClientState: Error - ${clientState.message}")
                // If there's no wallet key, clear the account and redirect to sign-in
                if (clientState.message.contains("No wallet key found")) {
                    val accounts = accountManager.getAccountsByType(resources.getString(R.string.account_type))
                    accounts.forEach { account ->
                        accountManager.removeAccount(account, null, null, null)
                    }
                    showSignIn()
                } else {
                    showError(clientState.message)
                }
            }

            is ClientManager.ClientState.Unknown -> {
                Timber.d("ensureClientState: Unknown")
            }

            is ClientManager.ClientState.Creating -> {
                Timber.d("ensureClientState: Creating client...")
            }
        }
    }

    private fun showSignIn() {
        startActivity(Intent(this, ConnectWalletActivity::class.java))
        finish()
    }

    private fun showError(message: String) {
        val error = message.ifBlank { resources.getString(R.string.error) }
        Log.e("MainActivity", message)
        Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
    }

    private fun disconnectWallet() {
        // Clear the stored private key and environment before clearing the client
        val keyUtil = KeyUtil(this)
        // Safely get address only if client is ready
        val address = if (ClientManager.clientState.value is ClientManager.ClientState.Ready) {
            ClientManager.client.publicIdentity.identifier
        } else {
            null
        }
        address?.let { keyUtil.clearPrivateKey(it) }
        keyUtil.clearEnvironment()

        ClientManager.clearClient()
        PushNotificationTokenManager.clearXMTPPush()
        val accounts = accountManager.getAccountsByType(resources.getString(R.string.account_type))
        accounts.forEach { account ->
            accountManager.removeAccount(account, null, null, null)
        }
        showSignIn()
    }

    private fun copyWalletAddress() {
        if (ClientManager.clientState.value !is ClientManager.ClientState.Ready) {
            Toast.makeText(this, "Client not ready", Toast.LENGTH_SHORT).show()
            return
        }
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val walletAddress = ClientManager.client.publicIdentity.identifier
        val clip = ClipData.newPlainText("wallet_address", walletAddress)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "Wallet address copied", Toast.LENGTH_SHORT).show()
    }

    private fun openNewConversation() {
        startActivity(NewConversationActivity.intent(this))
    }

    private fun checkAndRequestPermissions() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQUEST_CODE_POST_NOTIFICATIONS,
            )
        }
    }

    // Add helper methods to manage log activation state
    private fun isLogsActivated(): Boolean {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_LOGS_ACTIVATED, false)
    }

    private fun setLogsActivated(activated: Boolean) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_LOGS_ACTIVATED, activated).apply()
    }

    private fun onLogsToggled(enabled: Boolean) {
        if (enabled) {
            Client.activatePersistentLibXMTPLogWriter(
                applicationContext,
                FfiLogLevel.DEBUG,
                FfiLogRotation.MINUTELY,
                3,
            )
            setLogsActivated(true)
            Toast.makeText(this, "Persistent logs activated", Toast.LENGTH_SHORT).show()
        } else {
            Client.deactivatePersistentLibXMTPLogWriter()
            setLogsActivated(false)
            Toast.makeText(this, "Persistent logs deactivated", Toast.LENGTH_SHORT).show()
        }
    }

    private fun onHideDeletedMessagesToggled(enabled: Boolean) {
        val keyUtil = KeyUtil(this)
        keyUtil.setHideDeletedMessages(enabled)
        val message = if (enabled) "Deleted messages will be hidden" else "Deleted messages will be shown"
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun MainActivityContent(
    viewModel: MainViewModel,
    onConversationClick: (String, String) -> Unit,
    onNewConversationClick: () -> Unit,
    onNewGroupClick: () -> Unit,
    onHideDeletedMessagesToggle: (Boolean) -> Unit,
    onToggleLogsClick: (Boolean) -> Unit,
    onCopyAddressClick: () -> Unit,
    onDisconnectClick: () -> Unit,
    isLogsEnabled: Boolean,
    hideDeletedMessages: Boolean,
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val clientState by ClientManager.clientState.collectAsState()

    // Sheet states
    var showWalletInfoSheet by remember { mutableStateOf(false) }
    var showLogViewerSheet by remember { mutableStateOf(false) }

    // Track log and hide deleted states for UI updates
    var logsEnabled by remember { mutableStateOf(isLogsEnabled) }
    var hideDeleted by remember { mutableStateOf(hideDeletedMessages) }

    // Convert ViewModel state to UI state
    val conversations = remember(uiState, clientState) {
        val items = when (uiState) {
            is MainViewModel.UiState.Success -> (uiState as MainViewModel.UiState.Success).listItems
            is MainViewModel.UiState.Loading -> (uiState as MainViewModel.UiState.Loading).listItems ?: emptyList()
            is MainViewModel.UiState.Error -> emptyList()
        }

        items.filterIsInstance<MainViewModel.MainListItem.ConversationItem>().map { item ->
            val isGroup = item.conversation.type == Conversation.Type.GROUP
            val displayName = when (item.conversation.type) {
                Conversation.Type.GROUP -> item.displayName
                Conversation.Type.DM -> item.displayName.truncatedAddress()
            }

            // Format message time
            val formattedTime = item.mostRecentMessage?.let { message ->
                formatMessageTime(message.sentAtNs / 1_000_000)
            } ?: ""

            // Get message preview
            val myInboxId = if (clientState is ClientManager.ClientState.Ready) {
                ClientManager.client.inboxId
            } else ""

            val messageBody = when (val content = item.mostRecentMessage?.content<Any>()) {
                is String -> content
                is Reply -> when (val replyContent = content.content) {
                    is String -> replyContent
                    else -> "Message"
                }
                is Attachment -> if (content.mimeType.startsWith("image/")) {
                    if (content.mimeType == "image/gif") "GIF" else "Photo"
                } else "Attachment"
                is GroupUpdated -> {
                    val added = content.addedInboxesList?.size ?: 0
                    val removed = content.removedInboxesList?.size ?: 0
                    when {
                        added > 0 && removed > 0 -> "$added added, $removed removed"
                        added > 0 -> "$added member${if (added > 1) "s" else ""} added"
                        removed > 0 -> "$removed member${if (removed > 1) "s" else ""} removed"
                        else -> "Group updated"
                    }
                }
                is DeletedMessage -> "Message deleted"
                else -> "Message"
            }

            val isMe = item.mostRecentMessage?.senderInboxId == myInboxId
            val lastMessage = if (messageBody.isNotBlank()) {
                if (isMe) "You: $messageBody" else messageBody
            } else ""

            ConversationItem(
                id = item.conversation.topic,
                name = displayName,
                lastMessage = lastMessage,
                timestamp = formattedTime,
                isGroup = isGroup,
                memberCount = null, // Would require async call to get member count
                unreadCount = 0,
            )
        }
    }

    val isLoading = uiState is MainViewModel.UiState.Loading &&
        (uiState as MainViewModel.UiState.Loading).listItems.isNullOrEmpty()

    // Get wallet info from client state
    val walletAddress = remember(clientState) {
        if (clientState is ClientManager.ClientState.Ready) {
            ClientManager.client.publicIdentity.identifier
        } else {
            ""
        }
    }

    val environment = remember(clientState) {
        if (clientState is ClientManager.ClientState.Ready) {
            ClientManager.client.environment.name
        } else {
            ""
        }
    }

    val inboxId = remember(clientState) {
        if (clientState is ClientManager.ClientState.Ready) {
            ClientManager.client.inboxId
        } else {
            ""
        }
    }

    val installationId = remember(clientState) {
        if (clientState is ClientManager.ClientState.Ready) {
            ClientManager.client.installationId
        } else {
            ""
        }
    }

    val drawerState = DrawerState(
        walletAddress = walletAddress,
        environment = environment,
        isLogsEnabled = logsEnabled,
        hideDeletedMessages = hideDeleted,
    )

    // Find topic for conversation navigation (topic is the conversation id)
    val conversationTopics = remember(uiState) {
        val items = when (uiState) {
            is MainViewModel.UiState.Success -> (uiState as MainViewModel.UiState.Success).listItems
            is MainViewModel.UiState.Loading -> (uiState as MainViewModel.UiState.Loading).listItems ?: emptyList()
            else -> emptyList()
        }
        items.filterIsInstance<MainViewModel.MainListItem.ConversationItem>()
            .associate { it.conversation.topic to it.conversation.topic }
    }

    MainScreen(
        conversations = conversations,
        drawerState = drawerState,
        isLoading = isLoading,
        onConversationClick = { conversationId ->
            val topic = conversationTopics[conversationId] ?: ""
            onConversationClick(conversationId, topic)
        },
        onNewConversationClick = onNewConversationClick,
        onSearchClick = { /* TODO: Implement search */ },
        onWalletInfoClick = { showWalletInfoSheet = true },
        onNewGroupClick = onNewGroupClick,
        onHideDeletedMessagesToggle = { enabled ->
            hideDeleted = enabled
            onHideDeletedMessagesToggle(enabled)
        },
        onViewLogsClick = { showLogViewerSheet = true },
        onToggleLogsClick = { enabled ->
            logsEnabled = enabled
            onToggleLogsClick(enabled)
        },
        onCopyAddressClick = onCopyAddressClick,
        onDisconnectClick = onDisconnectClick,
    )

    // Wallet Info Sheet
    if (showWalletInfoSheet) {
        val walletInfo = WalletInfo(
            walletAddress = walletAddress,
            inboxId = inboxId,
            installationId = installationId,
            environment = environment,
            libXmtpVersion = if (clientState is ClientManager.ClientState.Ready) ClientManager.client.libXMTPVersion else "",
        )

        WalletInfoSheet(
            walletInfo = walletInfo,
            isLogsEnabled = logsEnabled,
            onDismiss = { showWalletInfoSheet = false },
            onCopyValue = { label, value ->
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText(label, value)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, "$label copied", Toast.LENGTH_SHORT).show()
            },
            onLogsToggled = { enabled ->
                logsEnabled = enabled
                onToggleLogsClick(enabled)
            },
            onDisconnect = {
                showWalletInfoSheet = false
                onDisconnectClick()
            },
        )
    }

    // Log Viewer Sheet
    if (showLogViewerSheet) {
        val logDir = File(context.filesDir, "xmtp_logs")
        val logFiles = if (logDir.exists()) {
            logDir.listFiles()
                ?.filter { it.isFile && it.name.endsWith(".log") }
                ?.sortedByDescending { it.lastModified() }
                ?.map { file ->
                    LogFileInfo(
                        file = file,
                        name = file.name,
                        size = formatFileSize(file.length()),
                    )
                } ?: emptyList()
        } else {
            emptyList()
        }

        LogViewerSheet(
            logFiles = logFiles,
            onDismiss = { showLogViewerSheet = false },
            onShareFile = { file ->
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file,
                )
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Share Log File"))
            },
        )
    }
}

private fun formatMessageTime(timestampMs: Long): String {
    val messageDate = Date(timestampMs)
    val now = Calendar.getInstance()
    val messageCalendar = Calendar.getInstance().apply { time = messageDate }

    return when {
        // Today - show time
        now.get(Calendar.YEAR) == messageCalendar.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) == messageCalendar.get(Calendar.DAY_OF_YEAR) -> {
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(messageDate)
        }
        // Yesterday
        now.get(Calendar.YEAR) == messageCalendar.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) - messageCalendar.get(Calendar.DAY_OF_YEAR) == 1 -> {
            "Yesterday"
        }
        // Within the last week - show day name
        now.get(Calendar.YEAR) == messageCalendar.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) - messageCalendar.get(Calendar.DAY_OF_YEAR) < 7 -> {
            SimpleDateFormat("EEE", Locale.getDefault()).format(messageDate)
        }
        // Older - show date
        else -> {
            SimpleDateFormat("MMM d", Locale.getDefault()).format(messageDate)
        }
    }
}
