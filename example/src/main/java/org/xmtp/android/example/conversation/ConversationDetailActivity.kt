package org.xmtp.android.example.conversation

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Gif
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.xmtp.android.example.ClientManager
import org.xmtp.android.example.extension.truncatedAddress
import org.xmtp.android.example.ui.components.EmojiPicker
import org.xmtp.android.example.ui.components.MessageBubble
import org.xmtp.android.example.ui.components.MessageComposer
import org.xmtp.android.example.ui.components.ReplyPreviewBar
import org.xmtp.android.example.ui.components.ReplyPreviewData
import org.xmtp.android.example.ui.components.SystemMessage
import org.xmtp.android.example.ui.dialogs.DeleteConfirmationDialog
import org.xmtp.android.example.ui.sheets.MessageOptionsSheet
import org.xmtp.android.example.ui.sheets.MessageOptionsState
import org.xmtp.android.example.ui.theme.XMTPTheme
import org.xmtp.android.library.Conversation
import org.xmtp.android.library.codecs.Attachment
import org.xmtp.android.library.codecs.DeletedMessage
import org.xmtp.android.library.codecs.Reaction
import org.xmtp.android.library.codecs.ReactionAction
import org.xmtp.android.library.libxmtp.DecodedMessageV2
import org.xmtp.android.library.libxmtp.Reply
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

class ConversationDetailActivity : ComponentActivity() {
    private val viewModel: ConversationDetailViewModel by viewModels()

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

    companion object {
        const val EXTRA_CONVERSATION_TOPIC = "EXTRA_CONVERSATION_TOPIC"
        private const val EXTRA_PEER_ADDRESS = "EXTRA_PEER_ADDRESS"

        fun intent(
            context: Context,
            topic: String,
            peerAddress: String,
        ): Intent =
            Intent(context, ConversationDetailActivity::class.java).apply {
                putExtra(EXTRA_CONVERSATION_TOPIC, topic)
                putExtra(EXTRA_PEER_ADDRESS, peerAddress)
            }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        viewModel.setConversationTopic(intent.extras?.getString(EXTRA_CONVERSATION_TOPIC))

        // Load and apply user preferences for hide deleted messages
        val keyUtil = org.xmtp.android.example.utils.KeyUtil(this)
        viewModel.setHideDeletedMessages(keyUtil.getHideDeletedMessages())

        viewModel.fetchMessages()

        // Observe streamed messages
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.streamMessages.collect { result ->
                    when (result) {
                        is ConversationDetailViewModel.StreamedMessageResult.RefreshNeeded -> {
                            viewModel.fetchMessages()
                        }
                        is ConversationDetailViewModel.StreamedMessageResult.NewMessage -> {
                            viewModel.fetchMessages()
                        }
                        null -> {}
                    }
                }
            }
        }

        setContent {
            XMTPTheme {
                ConversationDetailScreen(
                    viewModel = viewModel,
                    peerAddress = intent.extras?.getString(EXTRA_PEER_ADDRESS) ?: "",
                    conversationTopic = intent.extras?.getString(EXTRA_CONVERSATION_TOPIC) ?: "",
                    avatarColors = avatarColors,
                    onBackClick = { finish() },
                    onNavigateToGroupManagement = { topic ->
                        startActivity(GroupManagementActivity.intent(this, topic))
                    },
                    onNavigateToUserProfile = { address, inboxId ->
                        startActivity(UserProfileActivity.intent(this, address, inboxId))
                    },
                    onNavigateToAttachmentPreview = { uris ->
                        startActivity(AttachmentPreviewActivity.intent(this, uris))
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.fetchMessages()
    }
}

// Data class for message display
private data class MessageDisplayItem(
    val id: String,
    val message: String,
    val timestamp: String,
    val isMe: Boolean,
    val senderName: String?,
    val senderColor: Color?,
    val isSystemMessage: Boolean,
    val reactions: List<Pair<String, Int>>?,
    val replyPreview: String?,
    val replyAuthor: String?,
    val isDeleted: Boolean,
    val originalMessage: DecodedMessageV2?,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConversationDetailScreen(
    viewModel: ConversationDetailViewModel,
    peerAddress: String,
    conversationTopic: String,
    avatarColors: List<Color>,
    onBackClick: () -> Unit,
    onNavigateToGroupManagement: (String) -> Unit,
    onNavigateToUserProfile: (String, String?) -> Unit,
    onNavigateToAttachmentPreview: (List<Uri>) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsState()
    val replyToMessage by viewModel.replyToMessage.collectAsState()

    // Message composer state
    var messageText by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }

    // Conversation info state
    var conversationType by remember { mutableStateOf<Conversation.Type?>(null) }
    var headerTitle by remember { mutableStateOf(peerAddress.truncatedAddress()) }
    var headerSubtitle by remember { mutableStateOf("Loading...") }
    var peerWalletAddress by remember { mutableStateOf<String?>(null) }
    var peerInboxId by remember { mutableStateOf<String?>(null) }
    var isSuperAdmin by remember { mutableStateOf(false) }

    // Sheet states
    var showAttachmentPicker by remember { mutableStateOf(false) }
    var showMessageOptions by remember { mutableStateOf(false) }
    var showEmojiPicker by remember { mutableStateOf(false) }
    var selectedMessage by remember { mutableStateOf<DecodedMessageV2?>(null) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    // Camera image URI state
    var cameraImageUri by remember { mutableStateOf<Uri?>(null) }

    // Pending attachments to send after preview
    var pendingAttachmentUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var pendingCaptions by remember { mutableStateOf<List<String>>(emptyList()) }

    // Attachment preview launcher - handles result when user confirms sending
    val attachmentPreviewLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        timber.log.Timber.d("AttachmentPreviewLauncher: resultCode=${result.resultCode}, data=${result.data}")
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val data = result.data
            val uris = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                data?.getParcelableArrayListExtra(AttachmentPreviewActivity.RESULT_ATTACHMENTS, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                data?.getParcelableArrayListExtra(AttachmentPreviewActivity.RESULT_ATTACHMENTS)
            }
            val captions = data?.getStringArrayListExtra(AttachmentPreviewActivity.RESULT_CAPTIONS)
            timber.log.Timber.d("AttachmentPreviewLauncher: uris=$uris, captions=$captions")

            if (!uris.isNullOrEmpty()) {
                // Send each attachment
                isSending = true
                scope.launch {
                    try {
                        uris.forEachIndexed { index, uri ->
                            val caption = captions?.getOrNull(index) ?: ""

                            // Read file info from URI
                            val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
                            var filename = "attachment"
                            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                                if (cursor.moveToFirst()) {
                                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                                    if (nameIndex >= 0) {
                                        filename = cursor.getString(nameIndex) ?: "attachment"
                                    }
                                }
                            }

                            // Read file data
                            val fileData = withContext(Dispatchers.IO) {
                                try {
                                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                                } catch (e: Exception) {
                                    timber.log.Timber.e(e, "Failed to read file: $uri")
                                    null
                                }
                            }

                            if (fileData != null) {
                                timber.log.Timber.d("Sending attachment: $filename, type: $mimeType, size: ${fileData.size}")

                                // Send caption first if present
                                if (caption.isNotBlank()) {
                                    viewModel.sendMessage(caption).collect { /* ignore */ }
                                }

                                // Send attachment
                                val sendResult = viewModel.sendAttachment(filename, mimeType, fileData)
                                when (sendResult) {
                                    is ConversationDetailViewModel.SendAttachmentState.Success -> {
                                        timber.log.Timber.d("Attachment sent successfully: $filename")
                                    }
                                    is ConversationDetailViewModel.SendAttachmentState.Error -> {
                                        timber.log.Timber.e("Failed to send attachment: ${sendResult.message}")
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(context, "Failed: ${sendResult.message}", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                    else -> {}
                                }
                            } else {
                                timber.log.Timber.e("Failed to read file data for: $uri")
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Failed to read file", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        viewModel.fetchMessages()
                    } finally {
                        isSending = false
                    }
                }
            }
        }
    }

    // Activity result launchers
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            // Take persistable permission so URIs remain readable after preview returns
            uris.forEach { uri ->
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                } catch (_: SecurityException) {
                    // Some URIs may not support persistable permissions
                }
            }
            attachmentPreviewLauncher.launch(AttachmentPreviewActivity.intent(context, uris))
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && cameraImageUri != null) {
            attachmentPreviewLauncher.launch(AttachmentPreviewActivity.intent(context, listOf(cameraImageUri!!)))
        }
    }

    val fileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            // Take persistable permission so URIs remain readable after preview returns
            uris.forEach { uri ->
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                } catch (_: SecurityException) {
                    // Some URIs may not support persistable permissions
                }
            }
            attachmentPreviewLauncher.launch(AttachmentPreviewActivity.intent(context, uris))
        }
    }

    // Load conversation info
    LaunchedEffect(conversationTopic) {
        withContext(Dispatchers.IO) {
            try {
                val conversation = ClientManager.client.conversations.findConversationByTopic(conversationTopic)
                conversation?.let { conv ->
                    conversationType = conv.type

                    when (conv) {
                        is Conversation.Group -> {
                            val groupName = conv.group.name()
                            val members = conv.group.members()
                            val memberCount = members.size
                            isSuperAdmin = conv.group.isSuperAdmin(ClientManager.client.inboxId)

                            withContext(Dispatchers.Main) {
                                headerTitle = if (groupName.isNullOrBlank()) {
                                    conv.id.truncatedAddress()
                                } else {
                                    groupName
                                }
                                headerSubtitle = "$memberCount members"
                            }
                        }
                        is Conversation.Dm -> {
                            val members = conv.dm.members()
                            val peerMember = members.find { it.inboxId != ClientManager.client.inboxId }
                            peerMember?.let { member ->
                                peerInboxId = member.inboxId
                                peerWalletAddress = member.identities.firstOrNull()?.identifier

                                withContext(Dispatchers.Main) {
                                    val displayAddress = peerWalletAddress ?: conv.id
                                    headerTitle = displayAddress.truncatedAddress()
                                    headerSubtitle = "Direct message"
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    headerSubtitle = "Direct message"
                }
            }
        }
    }

    // Convert ViewModel state to UI messages
    val messages = remember(uiState) {
        val items = when (uiState) {
            is ConversationDetailViewModel.UiState.Success ->
                (uiState as ConversationDetailViewModel.UiState.Success).listItems
            is ConversationDetailViewModel.UiState.Loading ->
                (uiState as ConversationDetailViewModel.UiState.Loading).listItems ?: emptyList()
            is ConversationDetailViewModel.UiState.Error -> emptyList()
        }

        items.map { item ->
            when (item) {
                is ConversationDetailViewModel.MessageListItem.SentMessage -> {
                    convertToDisplayItem(item.message, isFromMe = true, avatarColors = avatarColors)
                }
                is ConversationDetailViewModel.MessageListItem.ReceivedMessage -> {
                    convertToDisplayItem(item.message, isFromMe = false, avatarColors = avatarColors)
                }
                is ConversationDetailViewModel.MessageListItem.SystemMessage -> {
                    MessageDisplayItem(
                        id = item.id,
                        message = item.text,
                        timestamp = formatMessageTime(item.message.sentAtNs / 1_000_000),
                        isMe = false,
                        senderName = null,
                        senderColor = null,
                        isSystemMessage = true,
                        reactions = null,
                        replyPreview = null,
                        replyAuthor = null,
                        isDeleted = false,
                        originalMessage = item.message,
                    )
                }
            }
        }
    }

    val isLoading = uiState is ConversationDetailViewModel.UiState.Loading &&
        (uiState as ConversationDetailViewModel.UiState.Loading).listItems.isNullOrEmpty()

    val listState = rememberLazyListState()

    // Scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
        topBar = {
            ConversationHeader(
                title = headerTitle,
                subtitle = headerSubtitle,
                avatarText = headerTitle.removePrefix("0x").take(2).uppercase(),
                avatarColor = avatarColors[abs(headerTitle.hashCode()) % avatarColors.size],
                onBackClick = onBackClick,
                onHeaderClick = {
                    when (conversationType) {
                        Conversation.Type.GROUP -> onNavigateToGroupManagement(conversationTopic)
                        Conversation.Type.DM -> peerWalletAddress?.let { address ->
                            onNavigateToUserProfile(address, peerInboxId)
                        }
                        null -> {}
                    }
                },
            )
        },
        bottomBar = {
            Column {
                // Reply preview
                replyToMessage?.let { message ->
                    val content = message.content<Any>()
                    val replyText = when (content) {
                        is String -> content
                        is Reply -> (content.content as? String) ?: "Message"
                        is Attachment -> if (content.mimeType.startsWith("image/")) "Photo" else "Attachment"
                        else -> "Message"
                    }
                    ReplyPreviewBar(
                        replyData = ReplyPreviewData(
                            authorName = message.senderInboxId.take(8) + "...",
                            messageText = replyText,
                        ),
                        onClose = { viewModel.clearReply() },
                    )
                }

                MessageComposer(
                    value = messageText,
                    onValueChange = { messageText = it },
                    onSendClick = {
                        if (messageText.isNotBlank()) {
                            isSending = true
                            val textToSend = messageText
                            messageText = ""
                            scope.launch {
                                viewModel.sendMessage(textToSend).collect { state ->
                                    when (state) {
                                        is ConversationDetailViewModel.SendMessageState.Success -> {
                                            isSending = false
                                            viewModel.fetchMessages()
                                        }
                                        is ConversationDetailViewModel.SendMessageState.Error -> {
                                            isSending = false
                                            Toast.makeText(context, state.message, Toast.LENGTH_SHORT).show()
                                        }
                                        else -> {}
                                    }
                                }
                            }
                        }
                    },
                    isSending = isSending,
                    onAttachmentClick = { showAttachmentPicker = true },
                    onEmojiClick = { showEmojiPicker = true },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
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
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    reverseLayout = true,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(messages, key = { it.id }) { displayItem ->
                        if (displayItem.isSystemMessage) {
                            SystemMessage(text = displayItem.message)
                        } else {
                            MessageBubble(
                                message = displayItem.message,
                                timestamp = displayItem.timestamp,
                                isMe = displayItem.isMe,
                                senderName = displayItem.senderName,
                                senderColor = displayItem.senderColor,
                                reactions = displayItem.reactions,
                                replyPreview = displayItem.replyPreview,
                                replyAuthor = displayItem.replyAuthor,
                                isDeleted = displayItem.isDeleted,
                                onLongClick = {
                                    displayItem.originalMessage?.let {
                                        selectedMessage = it
                                        showMessageOptions = true
                                    }
                                },
                                onReplyClick = displayItem.replyPreview?.let {
                                    { /* Could scroll to original message */ }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    // Attachment Picker Sheet
    if (showAttachmentPicker) {
        ModalBottomSheet(
            onDismissRequest = { showAttachmentPicker = false },
            sheetState = rememberModalBottomSheetState(),
        ) {
            AttachmentPickerContent(
                onCameraClick = {
                    showAttachmentPicker = false
                    val photoFile = File(context.cacheDir, "camera_${System.currentTimeMillis()}.jpg")
                    cameraImageUri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        photoFile,
                    )
                    cameraLauncher.launch(cameraImageUri)
                },
                onGalleryClick = {
                    showAttachmentPicker = false
                    galleryLauncher.launch("image/*")
                },
                onFileClick = {
                    showAttachmentPicker = false
                    fileLauncher.launch(arrayOf("*/*"))
                },
                onGifClick = {
                    showAttachmentPicker = false
                    galleryLauncher.launch("image/gif")
                },
            )
        }
    }

    // Emoji Picker Sheet
    if (showEmojiPicker) {
        ModalBottomSheet(
            onDismissRequest = { showEmojiPicker = false },
            sheetState = rememberModalBottomSheetState(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Text(
                    text = "Choose emoji",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
                EmojiPicker(
                    onEmojiSelected = { emoji ->
                        messageText += emoji
                        showEmojiPicker = false
                    },
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Message Options Sheet
    if (showMessageOptions && selectedMessage != null) {
        val message = selectedMessage!!
        val isFromMe = ClientManager.client.inboxId == message.senderInboxId
        val content = message.content<Any>()
        val canEdit = isFromMe && (content is String || content is Reply)
        val canDelete = isFromMe || (conversationType == Conversation.Type.GROUP && isSuperAdmin)

        // Find user's existing reaction
        var myExistingReaction: String? = null
        if (message.hasReactions) {
            val sortedReactions = message.reactions.sortedBy { it.sentAtNs }
            val myReactions = mutableMapOf<String, Boolean>()
            for (reactionMsg in sortedReactions) {
                val reaction = reactionMsg.content<Reaction>() ?: continue
                if (reactionMsg.senderInboxId == ClientManager.client.inboxId) {
                    when (reaction.action) {
                        ReactionAction.Added -> myReactions[reaction.content] = true
                        ReactionAction.Removed -> myReactions[reaction.content] = false
                        else -> {}
                    }
                }
            }
            myExistingReaction = myReactions.entries.firstOrNull { it.value }?.key
        }

        MessageOptionsSheet(
            state = MessageOptionsState(
                messageId = message.id,
                canEdit = canEdit,
                canDelete = canDelete,
                currentReaction = myExistingReaction,
            ),
            onDismiss = {
                showMessageOptions = false
                selectedMessage = null
            },
            onReply = {
                viewModel.setReplyToMessage(message)
                showMessageOptions = false
                selectedMessage = null
            },
            onEdit = {
                Toast.makeText(context, "Edit feature coming soon", Toast.LENGTH_SHORT).show()
                showMessageOptions = false
                selectedMessage = null
            },
            onDelete = {
                showMessageOptions = false
                showDeleteConfirmation = true
            },
            onReaction = { emoji ->
                scope.launch {
                    val result = when {
                        myExistingReaction == emoji -> {
                            viewModel.sendReaction(message.id, emoji, isRemoving = true)
                        }
                        myExistingReaction != null -> {
                            viewModel.sendReaction(message.id, myExistingReaction!!, isRemoving = true)
                            viewModel.sendReaction(message.id, emoji, isRemoving = false)
                        }
                        else -> {
                            viewModel.sendReaction(message.id, emoji, isRemoving = false)
                        }
                    }
                    when (result) {
                        is ConversationDetailViewModel.ReactionState.Success -> {
                            viewModel.fetchMessages()
                        }
                        is ConversationDetailViewModel.ReactionState.Error -> {
                            Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                        }
                        else -> {}
                    }
                }
                showMessageOptions = false
                selectedMessage = null
            },
        )
    }

    // Delete Confirmation Dialog
    if (showDeleteConfirmation && selectedMessage != null) {
        DeleteConfirmationDialog(
            onConfirm = {
                scope.launch {
                    viewModel.deleteMessage(selectedMessage!!.id).collect { state ->
                        when (state) {
                            is ConversationDetailViewModel.DeleteMessageState.Success -> {
                                Toast.makeText(context, "Message deleted", Toast.LENGTH_SHORT).show()
                                viewModel.fetchMessages()
                            }
                            is ConversationDetailViewModel.DeleteMessageState.Error -> {
                                Toast.makeText(context, state.message, Toast.LENGTH_SHORT).show()
                            }
                            else -> {}
                        }
                    }
                }
                showDeleteConfirmation = false
                selectedMessage = null
            },
            onDismiss = {
                showDeleteConfirmation = false
                selectedMessage = null
            },
        )
    }
}

@Composable
private fun ConversationHeader(
    title: String,
    subtitle: String,
    avatarText: String,
    avatarColor: Color,
    onBackClick: () -> Unit,
    onHeaderClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                )
            }

            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onHeaderClick)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(avatarColor),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = avatarText,
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun AttachmentPickerContent(
    onCameraClick: () -> Unit,
    onGalleryClick: () -> Unit,
    onFileClick: () -> Unit,
    onGifClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
    ) {
        Text(
            text = "Send attachment",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            AttachmentOption(
                icon = Icons.Default.CameraAlt,
                label = "Camera",
                onClick = onCameraClick,
            )
            AttachmentOption(
                icon = Icons.Default.Photo,
                label = "Gallery",
                onClick = onGalleryClick,
            )
            AttachmentOption(
                icon = Icons.Default.Description,
                label = "File",
                onClick = onFileClick,
            )
            AttachmentOption(
                icon = Icons.Default.Gif,
                label = "GIF",
                onClick = onGifClick,
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun AttachmentOption(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            modifier = Modifier.size(56.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Box(
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private fun convertToDisplayItem(
    message: DecodedMessageV2,
    isFromMe: Boolean,
    avatarColors: List<Color>,
): MessageDisplayItem {
    val content = message.content<Any>()

    val messageContent = when (content) {
        is String -> content
        is Reply -> (content.content as? String) ?: "Message"
        is Attachment -> if (content.mimeType.startsWith("image/")) {
            if (content.mimeType == "image/gif") "GIF" else "Photo"
        } else "Attachment: ${content.filename}"
        is DeletedMessage -> ""
        else -> message.fallbackText ?: "Message"
    }

    val isDeleted = content is DeletedMessage

    // Get reactions
    val reactions = if (message.hasReactions) {
        val sortedReactions = message.reactions.sortedBy { it.sentAtNs }
        val activeReactions = mutableMapOf<String, MutableSet<String>>()

        for (reactionMsg in sortedReactions) {
            val reaction = reactionMsg.content<Reaction>() ?: continue
            when (reaction.action) {
                ReactionAction.Added -> {
                    activeReactions.getOrPut(reaction.content) { mutableSetOf() }.add(reactionMsg.senderInboxId)
                }
                ReactionAction.Removed -> {
                    activeReactions[reaction.content]?.remove(reactionMsg.senderInboxId)
                }
                else -> {}
            }
        }

        activeReactions.filter { it.value.isNotEmpty() }
            .map { (emoji, senders) -> emoji to senders.size }
    } else {
        null
    }

    // Get reply info from content if it's a Reply type
    val (replyPreview, replyAuthor) = when (content) {
        is Reply -> {
            // Get the reply content text
            val replyText = when (val replyContent = content.content) {
                is String -> replyContent
                else -> "Message"
            }
            // Try to get author from inReplyTo if available
            val author = content.inReplyTo?.senderInboxId?.let { it.take(8) + "..." }
            replyText to author
        }
        else -> null to null
    }

    val senderName = if (!isFromMe) message.senderInboxId.take(8) + "..." else null
    val senderColor = if (!isFromMe) {
        avatarColors[abs(message.senderInboxId.hashCode()) % avatarColors.size]
    } else null

    return MessageDisplayItem(
        id = message.id,
        message = messageContent,
        timestamp = formatMessageTime(message.sentAtNs / 1_000_000),
        isMe = isFromMe,
        senderName = senderName,
        senderColor = senderColor,
        isSystemMessage = false,
        reactions = reactions,
        replyPreview = replyPreview,
        replyAuthor = replyAuthor,
        isDeleted = isDeleted,
        originalMessage = message,
    )
}

private fun formatMessageTime(timestampMs: Long): String {
    val messageDate = Date(timestampMs)
    val now = Calendar.getInstance()
    val messageCalendar = Calendar.getInstance().apply { time = messageDate }

    return when {
        now.get(Calendar.YEAR) == messageCalendar.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) == messageCalendar.get(Calendar.DAY_OF_YEAR) -> {
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(messageDate)
        }
        now.get(Calendar.YEAR) == messageCalendar.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) - messageCalendar.get(Calendar.DAY_OF_YEAR) == 1 -> {
            "Yesterday"
        }
        now.get(Calendar.YEAR) == messageCalendar.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) - messageCalendar.get(Calendar.DAY_OF_YEAR) < 7 -> {
            SimpleDateFormat("EEE", Locale.getDefault()).format(messageDate)
        }
        else -> {
            SimpleDateFormat("MMM d", Locale.getDefault()).format(messageDate)
        }
    }
}
