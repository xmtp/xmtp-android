package org.xmtp.android.example.conversation

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.xmtp.android.example.ClientManager
import org.xmtp.android.example.R
import org.xmtp.android.example.databinding.ActivityConversationDetailBinding
import org.xmtp.android.example.extension.truncatedAddress
import org.xmtp.android.example.message.EmojiPickerAdapter
import org.xmtp.android.example.message.MessageAdapter
import org.xmtp.android.example.message.MessageClickListener
import org.xmtp.android.example.message.SearchResultAdapter
import org.xmtp.android.example.message.SearchResultItem
import org.xmtp.android.library.Conversation
import org.xmtp.android.library.codecs.Reaction
import org.xmtp.android.library.codecs.ReactionAction
import org.xmtp.android.library.libxmtp.DecodedMessageV2
import org.xmtp.android.library.libxmtp.Reply
import java.io.File
import kotlin.math.abs

class ConversationDetailActivity :
    AppCompatActivity(),
    MessageClickListener {
    private lateinit var binding: ActivityConversationDetailBinding
    private lateinit var adapter: MessageAdapter
    private lateinit var emojiAdapter: EmojiPickerAdapter
    private lateinit var searchResultAdapter: SearchResultAdapter
    private var isEmojiPickerVisible = false
    private var isSearchVisible = false
    private var shouldScrollAfterFetch = false

    private val viewModel: ConversationDetailViewModel by viewModels()

    // Attachment handling
    private var cameraImageUri: Uri? = null

    private val attachmentPreviewLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                val uris = result.data?.getParcelableArrayListExtra<Uri>(AttachmentPreviewActivity.RESULT_ATTACHMENTS)
                if (!uris.isNullOrEmpty()) {
                    sendAttachments(uris)
                }
            }
        }

    private val galleryLauncher =
        registerForActivityResult(
            ActivityResultContracts.GetMultipleContents(),
        ) { uris: List<Uri> ->
            if (uris.isNotEmpty()) {
                showAttachmentPreview(uris)
            }
        }

    private val cameraLauncher =
        registerForActivityResult(
            ActivityResultContracts.TakePicture(),
        ) { success: Boolean ->
            if (success) {
                cameraImageUri?.let { showAttachmentPreview(listOf(it)) }
            }
        }

    private val fileLauncher =
        registerForActivityResult(
            ActivityResultContracts.OpenMultipleDocuments(),
        ) { uris: List<Uri> ->
            if (uris.isNotEmpty()) {
                showAttachmentPreview(uris)
            }
        }

    private val peerAddress
        get() = intent.extras?.getString(EXTRA_PEER_ADDRESS)

    private val conversationTopic
        get() = intent.extras?.getString(EXTRA_CONVERSATION_TOPIC)

    private var conversationType: Conversation.Type? = null
    private var peerWalletAddress: String? = null
    private var peerInboxId: String? = null
    private var groupName: String? = null
    private var memberCount: Int = 0
    private var isSuperAdmin: Boolean = false

    private val avatarColors =
        listOf(
            Color.parseColor("#FC4F37"),
            Color.parseColor("#5856D6"),
            Color.parseColor("#34C759"),
            Color.parseColor("#FF9500"),
            Color.parseColor("#007AFF"),
            Color.parseColor("#AF52DE"),
            Color.parseColor("#00C7BE"),
            Color.parseColor("#FF2D55"),
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
        viewModel.setConversationTopic(intent.extras?.getString(EXTRA_CONVERSATION_TOPIC))

        // Load and apply user preferences for hide deleted messages
        val keyUtil = org.xmtp.android.example.utils.KeyUtil(this)
        viewModel.setHideDeletedMessages(keyUtil.getHideDeletedMessages())

        binding = ActivityConversationDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Setup header
        binding.backButton.setOnClickListener { finish() }
        binding.headerInfoArea.setOnClickListener { openConversationInfo() }

        // Set initial header values
        setupInitialHeader()

        adapter = MessageAdapter(this)
        binding.list.layoutManager =
            LinearLayoutManager(this, RecyclerView.VERTICAL, true)
        binding.list.adapter = adapter

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::ensureUiState)
            }
        }

        binding.messageEditText.requestFocus()
        binding.messageEditText.addTextChangedListener {
            val hasText = !binding.messageEditText.text.isNullOrBlank()
            binding.sendButton.isEnabled = hasText
        }

        // Initialize send button as disabled (no text)
        binding.sendButton.isEnabled = false

        binding.sendButton.setOnClickListener {
            val text = binding.messageEditText.text.toString()
            if (text.isNotBlank()) {
                // Hide emoji picker when sending
                if (isEmojiPickerVisible) {
                    hideEmojiPicker()
                }
                val flow = viewModel.sendMessage(text)
                lifecycleScope.launch {
                    repeatOnLifecycle(Lifecycle.State.STARTED) {
                        flow.collect(::ensureSendState)
                    }
                }
            }
        }

        // Setup emoji picker
        setupEmojiPicker()

        // Setup attachment button
        binding.attachButton.setOnClickListener {
            showAttachmentPicker()
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.streamMessages.collect(::handleStreamedResult)
            }
        }

        binding.refresh.setOnRefreshListener {
            viewModel.fetchMessages()
        }

        viewModel.fetchMessages()

        // Load conversation info for header
        loadConversationInfo()

        // Setup reply preview
        setupReplyPreview()

        // Setup edit preview
        setupEditPreview()

        // Setup search
        setupSearch()
    }

    private fun setupEmojiPicker() {
        // Setup emoji picker RecyclerView
        emojiAdapter =
            EmojiPickerAdapter { emoji ->
                // Insert emoji at cursor position
                val start = binding.messageEditText.selectionStart.coerceAtLeast(0)
                val end = binding.messageEditText.selectionEnd.coerceAtLeast(0)
                binding.messageEditText.text?.replace(
                    start.coerceAtMost(end),
                    start.coerceAtLeast(end),
                    emoji,
                )
            }

        binding.emojiPickerRecyclerView.layoutManager = GridLayoutManager(this, 8)
        binding.emojiPickerRecyclerView.adapter = emojiAdapter

        // Toggle emoji picker visibility
        binding.emojiButton.setOnClickListener {
            toggleEmojiPicker()
        }

        // Hide emoji picker when text input is focused and keyboard appears
        binding.messageEditText.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && isEmojiPickerVisible) {
                hideEmojiPicker()
            }
        }
    }

    private fun toggleEmojiPicker() {
        if (isEmojiPickerVisible) {
            hideEmojiPicker()
        } else {
            showEmojiPicker()
        }
    }

    private fun showEmojiPicker() {
        isEmojiPickerVisible = true
        binding.emojiPickerRecyclerView.visibility = View.VISIBLE
        binding.emojiButton.setImageResource(R.drawable.ic_keyboard_24)
        // Hide soft keyboard
        binding.messageEditText.clearFocus()
        val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.hideSoftInputFromWindow(binding.messageEditText.windowToken, 0)
    }

    private fun hideEmojiPicker() {
        isEmojiPickerVisible = false
        binding.emojiPickerRecyclerView.visibility = View.GONE
        binding.emojiButton.setImageResource(R.drawable.ic_emoji_24)
    }

    private fun setupSearch() {
        // Setup search result adapter
        searchResultAdapter = SearchResultAdapter { messageId ->
            // Close search and scroll to the message
            hideSearch()
            scrollToMessage(messageId)
        }
        binding.searchResultsList.layoutManager = LinearLayoutManager(this)
        binding.searchResultsList.adapter = searchResultAdapter

        // Search button click
        binding.searchButton.setOnClickListener {
            showSearch()
        }

        // Cancel button click
        binding.cancelSearchButton.setOnClickListener {
            hideSearch()
        }

        // Clear search text button
        binding.clearSearchButton.setOnClickListener {
            binding.searchEditText.text?.clear()
        }

        // Search text changes
        binding.searchEditText.addTextChangedListener { text ->
            val query = text?.toString() ?: ""
            binding.clearSearchButton.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE
            performSearch(query)
        }
    }

    private fun showSearch() {
        isSearchVisible = true
        binding.searchBarCard.visibility = View.VISIBLE
        binding.messageComposerCard.visibility = View.GONE
        binding.refresh.visibility = View.GONE
        binding.searchEditText.requestFocus()
        val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.showSoftInput(binding.searchEditText, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideSearch() {
        isSearchVisible = false
        binding.searchBarCard.visibility = View.GONE
        binding.searchResultsList.visibility = View.GONE
        binding.noResultsView.visibility = View.GONE
        binding.messageComposerCard.visibility = View.VISIBLE
        binding.refresh.visibility = View.VISIBLE
        binding.searchEditText.text?.clear()
        val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.hideSoftInputFromWindow(binding.searchEditText.windowToken, 0)
    }

    private fun performSearch(query: String) {
        if (query.isEmpty()) {
            binding.searchResultsList.visibility = View.GONE
            binding.noResultsView.visibility = View.GONE
            return
        }

        val currentState = viewModel.uiState.value
        val items = when (currentState) {
            is ConversationDetailViewModel.UiState.Success -> currentState.listItems
            is ConversationDetailViewModel.UiState.Loading -> currentState.listItems
            else -> null
        }

        items?.let { list ->
            val lowercaseQuery = query.lowercase()
            val results = list
                .mapNotNull { item ->
                    // Get the message from the sealed class
                    val message = when (item) {
                        is ConversationDetailViewModel.MessageListItem.SentMessage -> item.message
                        is ConversationDetailViewModel.MessageListItem.ReceivedMessage -> item.message
                        is ConversationDetailViewModel.MessageListItem.SystemMessage -> null // Skip system messages
                    } ?: return@mapNotNull null

                    // Get the content as text, handling Reply messages specially
                    val content = message.content<Any>()
                    val textContent = when (content) {
                        is String -> {
                            // Skip fallback text patterns like "Replied with '...' to an earlier message"
                            if (content.startsWith("Replied with")) return@mapNotNull null
                            content
                        }

                        is Reply -> {
                            // For replies, extract the actual reply text (not "Replied with...")
                            when (val replyContent = content.content) {
                                is String -> replyContent
                                else -> return@mapNotNull null
                            }
                        }

                        else -> return@mapNotNull null
                    }

                    // Skip empty or deleted messages
                    if (textContent.isEmpty()) return@mapNotNull null

                    // Check if it matches the search query
                    if (!textContent.lowercase().contains(lowercaseQuery)) return@mapNotNull null

                    SearchResultItem(
                        id = message.id,
                        senderInboxId = message.senderInboxId,
                        content = textContent,
                        sentAtNs = message.sentAtNs,
                        isDeleted = false
                    )
                }

            searchResultAdapter.setSearchQuery(query)
            searchResultAdapter.submitList(results)

            if (results.isEmpty()) {
                binding.searchResultsList.visibility = View.GONE
                binding.noResultsView.visibility = View.VISIBLE
            } else {
                binding.searchResultsList.visibility = View.VISIBLE
                binding.noResultsView.visibility = View.GONE
            }
        }
    }

    private fun setupReplyPreview() {
        // Close button clears the reply
        binding.replyPreviewClose.setOnClickListener {
            viewModel.clearReply()
        }

        // Observe reply state
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.replyToMessage.collect { message ->
                    if (message != null) {
                        binding.replyPreviewContainer.visibility = View.VISIBLE
                        binding.replyPreviewAuthor.text = message.senderInboxId.take(8) + "..."
                        val content = message.content<Any>()

                        // Reset image visibility
                        binding.replyPreviewImageContainer.visibility = View.GONE

                        // Extract the actual text content, handling Reply messages specially
                        val messageText =
                            when (content) {
                                is String -> content
                                is Reply -> {
                                    // For replies, show the reply content (not "Replied with...")
                                    when (val replyContent = content.content) {
                                        is String -> replyContent
                                        else -> "Message"
                                    }
                                }

                                is org.xmtp.android.library.codecs.Attachment -> {
                                    // Show image thumbnail for attachments
                                    if (content.mimeType.startsWith("image/")) {
                                        try {
                                            val bytes = content.data.toByteArray()
                                            val bitmap =
                                                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                            binding.replyPreviewImage.setImageBitmap(bitmap)
                                            binding.replyPreviewImageContainer.visibility = View.VISIBLE
                                        } catch (e: Exception) {
                                            // Ignore image decode errors
                                        }
                                        "Photo"
                                    } else {
                                        "Attachment"
                                    }
                                }

                                else -> message.fallbackText ?: "Message"
                            }
                        binding.replyPreviewText.text = messageText
                        binding.messageEditText.requestFocus()
                    } else {
                        binding.replyPreviewContainer.visibility = View.GONE
                    }
                }
            }
        }
    }

    private fun setupEditPreview() {
        // Close button clears the edit
        binding.editPreviewClose.setOnClickListener {
            viewModel.clearEdit()
            binding.messageEditText.text.clear()
        }

        // Observe edit state
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.editingMessage.collect { message ->
                    if (message != null) {
                        binding.editPreviewContainer.visibility = View.VISIBLE
                        val content = message.content<Any>()

                        // Reset image visibility
                        binding.editPreviewImageContainer.visibility = View.GONE

                        // Extract the actual text content, handling Reply messages specially
                        val messageText =
                            when (content) {
                                is String -> content
                                is Reply -> {
                                    // For replies, get the reply content (which is the actual text)
                                    when (val replyContent = content.content) {
                                        is String -> replyContent
                                        else -> message.fallbackText ?: "Message"
                                    }
                                }

                                is org.xmtp.android.library.codecs.Attachment -> {
                                    // Show image thumbnail for attachments being edited
                                    if (content.mimeType.startsWith("image/")) {
                                        try {
                                            val bytes = content.data.toByteArray()
                                            val bitmap =
                                                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                            binding.editPreviewImage.setImageBitmap(bitmap)
                                            binding.editPreviewImageContainer.visibility = View.VISIBLE
                                        } catch (e: Exception) {
                                            // Ignore image decode errors
                                        }
                                        "Photo"
                                    } else {
                                        "Attachment"
                                    }
                                }

                                else -> message.fallbackText ?: "Message"
                            }
                        binding.editPreviewText.text = messageText
                        // Pre-fill the text field with the message content
                        binding.messageEditText.setText(messageText)
                        binding.messageEditText.setSelection(messageText.length)
                        binding.messageEditText.requestFocus()
                    } else {
                        binding.editPreviewContainer.visibility = View.GONE
                    }
                }
            }
        }
    }

    private fun startEditingMessage(message: DecodedMessageV2) {
        // Clear any reply first
        viewModel.clearReply()
        // Set the message to edit
        viewModel.setEditingMessage(message)
    }

    private fun setupInitialHeader() {
        // Set initial values based on peerAddress
        val displayAddress = peerAddress ?: ""
        binding.headerTitle.text = displayAddress.truncatedAddress()
        binding.headerSubtitle.text = getString(R.string.loading)

        // Set avatar
        val avatarText =
            displayAddress
                .removePrefix("0x")
                .take(2)
                .uppercase()
        binding.headerAvatarText.text = avatarText

        val colorIndex = abs(displayAddress.hashCode()) % avatarColors.size
        binding.headerAvatarCard.setCardBackgroundColor(avatarColors[colorIndex])
    }

    private fun loadConversationInfo() {
        lifecycleScope.launch {
            try {
                val conversation =
                    withContext(Dispatchers.IO) {
                        conversationTopic?.let {
                            ClientManager.client.conversations.findConversationByTopic(it)
                        }
                    }

                conversation?.let { conv ->
                    conversationType = conv.type

                    when (conv) {
                        is Conversation.Group -> {
                            groupName = withContext(Dispatchers.IO) { conv.group.name() }
                            val members = withContext(Dispatchers.IO) { conv.group.members() }
                            memberCount = members.size

                            // Check if current user is a super admin
                            isSuperAdmin =
                                withContext(Dispatchers.IO) {
                                    conv.group.isSuperAdmin(ClientManager.client.inboxId)
                                }

                            // Update header for group
                            val displayName =
                                if (groupName.isNullOrBlank()) {
                                    conv.id.truncatedAddress()
                                } else {
                                    groupName!!
                                }
                            binding.headerTitle.text = displayName
                            binding.headerSubtitle.text = getString(R.string.members_count_value, memberCount)

                            // Update avatar for group
                            val avatarText =
                                displayName
                                    .removePrefix("0x")
                                    .take(2)
                                    .uppercase()
                            binding.headerAvatarText.text = avatarText
                        }

                        is Conversation.Dm -> {
                            // Get the peer's info for DMs
                            val members = withContext(Dispatchers.IO) { conv.dm.members() }
                            val peerMember = members.find { it.inboxId != ClientManager.client.inboxId }
                            peerMember?.let { member ->
                                peerInboxId = member.inboxId
                                peerWalletAddress = member.identities.firstOrNull()?.identifier

                                // Update header for DM
                                val displayAddress = peerWalletAddress ?: conv.id
                                binding.headerTitle.text = displayAddress.truncatedAddress()
                                binding.headerSubtitle.text = getString(R.string.direct_message)

                                // Update avatar
                                val avatarText =
                                    displayAddress
                                        .removePrefix("0x")
                                        .take(2)
                                        .uppercase()
                                binding.headerAvatarText.text = avatarText

                                val colorIndex = abs(displayAddress.hashCode()) % avatarColors.size
                                binding.headerAvatarCard.setCardBackgroundColor(avatarColors[colorIndex])
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Silently fail - header will just show initial values
                binding.headerSubtitle.text = getString(R.string.direct_message)
            }
        }
    }

    private fun openConversationInfo() {
        when (conversationType) {
            Conversation.Type.GROUP -> {
                conversationTopic?.let { topic ->
                    startActivity(GroupManagementActivity.intent(this, topic))
                }
            }

            Conversation.Type.DM -> {
                peerWalletAddress?.let { address ->
                    startActivity(UserProfileActivity.intent(this, address, peerInboxId))
                }
            }

            null -> {
                // Conversation info not loaded yet
                Toast.makeText(this, R.string.loading, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Reload conversation info in case something changed (e.g., group name updated)
        loadConversationInfo()
    }

    private fun ensureUiState(uiState: ConversationDetailViewModel.UiState) {
        binding.progress.visibility = View.GONE
        when (uiState) {
            is ConversationDetailViewModel.UiState.Loading -> {
                if (uiState.listItems.isNullOrEmpty()) {
                    binding.progress.visibility = View.VISIBLE
                } else {
                    adapter.setData(uiState.listItems)
                }
            }

            is ConversationDetailViewModel.UiState.Success -> {
                binding.refresh.isRefreshing = false
                adapter.setData(uiState.listItems)
                // Scroll to bottom after data is loaded if flag is set
                if (shouldScrollAfterFetch) {
                    shouldScrollAfterFetch = false
                    binding.list.post {
                        binding.list.scrollToPosition(0)
                    }
                }
            }

            is ConversationDetailViewModel.UiState.Error -> {
                binding.refresh.isRefreshing = false
                showError(uiState.message)
            }
        }
    }

    private fun ensureSendState(sendState: ConversationDetailViewModel.SendMessageState) {
        when (sendState) {
            is ConversationDetailViewModel.SendMessageState.Error -> {
                showError(sendState.message)
            }

            ConversationDetailViewModel.SendMessageState.Loading -> {
                binding.sendButton.isEnabled = false
                binding.messageEditText.isEnabled = false
            }

            ConversationDetailViewModel.SendMessageState.Success -> {
                binding.messageEditText.text.clear()
                binding.messageEditText.isEnabled = true
                binding.sendButton.isEnabled = true
                shouldScrollAfterFetch = true
                viewModel.fetchMessages()
            }
        }
    }

    private fun handleStreamedResult(result: ConversationDetailViewModel.StreamedMessageResult?) {
        when (result) {
            is ConversationDetailViewModel.StreamedMessageResult.NewMessage -> {
                adapter.addItem(result.item)
                // Auto-scroll to show new message (position 0 since layout is reversed)
                binding.list.post {
                    binding.list.smoothScrollToPosition(0)
                }
            }

            is ConversationDetailViewModel.StreamedMessageResult.RefreshNeeded -> {
                // A delete message was received, refresh the list to show updated state
                viewModel.fetchMessages()
            }

            null -> { /* ignore */
            }
        }
    }

    private fun showError(message: String) {
        val error = message.ifBlank { resources.getString(R.string.error) }
        Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
    }

    override fun onMessageLongClick(message: DecodedMessageV2) {
        showMessageOptionsDialog(message)
    }

    private fun showMessageOptionsDialog(message: DecodedMessageV2) {
        val isFromMe = ClientManager.client.inboxId == message.senderInboxId
        // Allow delete if it's my message OR if I'm a super admin in a group
        val canDelete = isFromMe || (conversationType == Conversation.Type.GROUP && isSuperAdmin)
        // Only allow edit for own text and reply messages (not attachments)
        val content = message.content<Any>()
        val canEdit = isFromMe && (content is String || content is Reply)

        // Find user's existing active reaction on this message
        // We need to aggregate Added/Removed to find the current state
        val myInboxId = ClientManager.client.inboxId
        var myExistingReaction: String? = null
        if (message.hasReactions) {
            // Sort reactions by timestamp to ensure correct chronological processing
            val sortedReactions = message.reactions.sortedBy { it.sentAtNs }
            // Track my reactions: emoji -> isActive
            val myReactions = mutableMapOf<String, Boolean>()
            for (reactionMsg in sortedReactions) {
                val reaction = reactionMsg.content<Reaction>() ?: continue
                if (reactionMsg.senderInboxId == myInboxId) {
                    when (reaction.action) {
                        ReactionAction.Added -> myReactions[reaction.content] = true
                        ReactionAction.Removed -> myReactions[reaction.content] = false
                        else -> {}
                    }
                }
            }
            // Find the first active reaction (should only be one per user typically)
            myExistingReaction = myReactions.entries.firstOrNull { it.value }?.key
        }

        // Create custom dialog with reaction picker
        val dialogView = layoutInflater.inflate(R.layout.dialog_message_options, null)
        val dialog =
            AlertDialog
                .Builder(this)
                .setView(dialogView)
                .create()

        // Setup quick reaction buttons
        val reactionEmojis =
            listOf("\uD83D\uDC4D", "\u2764\uFE0F", "\uD83D\uDE02", "\uD83D\uDE2E", "\uD83D\uDE22", "\uD83D\uDE21")
        val reactionButtons =
            listOf<View>(
                dialogView.findViewById(R.id.reaction1),
                dialogView.findViewById(R.id.reaction2),
                dialogView.findViewById(R.id.reaction3),
                dialogView.findViewById(R.id.reaction4),
                dialogView.findViewById(R.id.reaction5),
                dialogView.findViewById(R.id.reaction6),
            )

        reactionButtons.forEachIndexed { index, button ->
            val emoji = reactionEmojis[index]
            (button as? android.widget.TextView)?.text = emoji

            // Highlight if this is user's current reaction
            if (myExistingReaction == emoji) {
                button.setBackgroundResource(R.drawable.reaction_button_selected_background)
            }

            button.setOnClickListener {
                dialog.dismiss()
                when {
                    // Same emoji clicked - remove reaction
                    myExistingReaction == emoji -> {
                        removeReaction(message.id, emoji)
                    }
                    // Different emoji clicked when user has existing reaction - remove old, add new
                    myExistingReaction != null -> {
                        // Use sequential operation to avoid race condition
                        replaceReaction(message.id, myExistingReaction, emoji)
                    }
                    // No existing reaction - add new
                    else -> {
                        sendReaction(message.id, emoji)
                    }
                }
            }
        }

        // Setup action buttons
        dialogView.findViewById<View>(R.id.replyButton).setOnClickListener {
            dialog.dismiss()
            viewModel.setReplyToMessage(message)
        }

        // Setup edit button
        val editButton = dialogView.findViewById<View>(R.id.editButton)
        val dividerEdit = dialogView.findViewById<View>(R.id.dividerEdit)
        if (canEdit) {
            editButton.visibility = View.VISIBLE
            dividerEdit.visibility = View.VISIBLE
            editButton.setOnClickListener {
                dialog.dismiss()
                startEditingMessage(message)
            }
        } else {
            editButton.visibility = View.GONE
            dividerEdit.visibility = View.GONE
        }

        val deleteButton = dialogView.findViewById<View>(R.id.deleteButton)
        val divider = dialogView.findViewById<View>(R.id.divider)
        if (canDelete) {
            deleteButton.visibility = View.VISIBLE
            divider.visibility = View.VISIBLE
            deleteButton.setOnClickListener {
                dialog.dismiss()
                showDeleteConfirmationDialog(message)
            }
        } else {
            deleteButton.visibility = View.GONE
            divider.visibility = View.GONE
        }

        // Make dialog background transparent for better visual appearance
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    override fun onReplyClick(referenceMessageId: String) {
        scrollToMessage(referenceMessageId)
    }

    override fun onReactionClick(
        messageId: String,
        emoji: String,
    ) {
        sendReaction(messageId, emoji)
    }

    private fun sendReaction(
        messageId: String,
        emoji: String,
    ) {
        lifecycleScope.launch {
            when (val result = viewModel.sendReaction(messageId, emoji, isRemoving = false)) {
                is ConversationDetailViewModel.ReactionState.Error -> {
                    showError(result.message)
                }

                ConversationDetailViewModel.ReactionState.Success -> {
                    viewModel.fetchMessages()
                }

                else -> {}
            }
        }
    }

    private fun removeReaction(
        messageId: String,
        emoji: String,
    ) {
        lifecycleScope.launch {
            when (val result = viewModel.sendReaction(messageId, emoji, isRemoving = true)) {
                is ConversationDetailViewModel.ReactionState.Error -> {
                    showError(result.message)
                }

                ConversationDetailViewModel.ReactionState.Success -> {
                    viewModel.fetchMessages()
                }

                else -> {}
            }
        }
    }

    /**
     * Replace an existing reaction with a new one.
     * Removes the old reaction first and waits for completion before adding the new one.
     * This prevents race conditions when changing reactions.
     */
    private fun replaceReaction(
        messageId: String,
        oldEmoji: String,
        newEmoji: String,
    ) {
        lifecycleScope.launch {
            // First remove the old reaction and wait for completion
            val removeResult = viewModel.sendReaction(messageId, oldEmoji, isRemoving = true)
            when (removeResult) {
                is ConversationDetailViewModel.ReactionState.Error -> {
                    showError(removeResult.message)
                    return@launch
                }

                ConversationDetailViewModel.ReactionState.Success -> {
                    // Only after successful removal, add the new reaction
                    when (val addResult = viewModel.sendReaction(messageId, newEmoji, isRemoving = false)) {
                        is ConversationDetailViewModel.ReactionState.Error -> {
                            showError(addResult.message)
                        }

                        ConversationDetailViewModel.ReactionState.Success -> {
                            viewModel.fetchMessages()
                        }

                        else -> {}
                    }
                }

                else -> {}
            }
        }
    }

    private fun scrollToMessage(messageId: String) {
        // Find the position of the message with the given ID
        val currentState = viewModel.uiState.value
        val items =
            when (currentState) {
                is ConversationDetailViewModel.UiState.Success -> currentState.listItems
                is ConversationDetailViewModel.UiState.Loading -> currentState.listItems
                else -> null
            }

        items?.let { list ->
            val position = list.indexOfFirst { it.id == messageId }
            if (position != -1) {
                // Scroll to the position with smooth animation
                binding.list.smoothScrollToPosition(position)

                // Highlight the message briefly
                binding.list.postDelayed({
                    val viewHolder = binding.list.findViewHolderForAdapterPosition(position)
                    viewHolder?.itemView?.let { view ->
                        // Flash highlight effect
                        view.alpha = 0.5f
                        view
                            .animate()
                            .alpha(1f)
                            .setDuration(500)
                            .start()
                    }
                }, 300)
            } else {
                Toast.makeText(this, "Message not found", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showDeleteConfirmationDialog(message: DecodedMessageV2) {
        AlertDialog
            .Builder(this)
            .setTitle("Delete Message")
            .setMessage("Are you sure you want to delete this message?")
            .setPositiveButton("Delete") { _, _ ->
                deleteMessage(message.id)
            }.setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteMessage(messageId: String) {
        val flow = viewModel.deleteMessage(messageId)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                flow.collect(::ensureDeleteState)
            }
        }
    }

    private fun ensureDeleteState(deleteState: ConversationDetailViewModel.DeleteMessageState) {
        when (deleteState) {
            is ConversationDetailViewModel.DeleteMessageState.Error -> {
                showError(deleteState.message)
            }

            ConversationDetailViewModel.DeleteMessageState.Loading -> {
                // Could show a loading indicator
            }

            ConversationDetailViewModel.DeleteMessageState.Success -> {
                Toast.makeText(this, "Message deleted", Toast.LENGTH_SHORT).show()
                viewModel.fetchMessages()
            }
        }
    }

    private fun showAttachmentPicker() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_attachment_picker, null)
        val dialog =
            AlertDialog
                .Builder(this)
                .setView(dialogView)
                .create()

        dialogView.findViewById<View>(R.id.cameraOption).setOnClickListener {
            dialog.dismiss()
            openCamera()
        }

        dialogView.findViewById<View>(R.id.galleryOption).setOnClickListener {
            dialog.dismiss()
            openGallery()
        }

        dialogView.findViewById<View>(R.id.fileOption).setOnClickListener {
            dialog.dismiss()
            openFilePicker()
        }

        dialogView.findViewById<View>(R.id.gifOption).setOnClickListener {
            dialog.dismiss()
            openGifPicker()
        }

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun openCamera() {
        val photoFile = File(cacheDir, "camera_${System.currentTimeMillis()}.jpg")
        cameraImageUri =
            FileProvider.getUriForFile(
                this,
                "$packageName.fileprovider",
                photoFile,
            )
        cameraLauncher.launch(cameraImageUri)
    }

    private fun openGallery() {
        galleryLauncher.launch("image/*")
    }

    private fun openFilePicker() {
        fileLauncher.launch(arrayOf("*/*"))
    }

    private fun openGifPicker() {
        // Use the gallery picker with GIF mime type filter
        galleryLauncher.launch("image/gif")
    }

    private fun showAttachmentPreview(uris: List<Uri>) {
        val intent = AttachmentPreviewActivity.intent(this, uris)
        attachmentPreviewLauncher.launch(intent)
    }

    private fun sendAttachments(uris: List<Uri>) {
        lifecycleScope.launch {
            val attachmentCount = uris.size
            var successCount = 0
            var errorCount = 0

            Toast
                .makeText(
                    this@ConversationDetailActivity,
                    if (attachmentCount > 1) {
                        "Sending $attachmentCount attachments..."
                    } else {
                        getString(R.string.sending_attachment)
                    },
                    Toast.LENGTH_SHORT,
                ).show()

            for ((index, uri) in uris.withIndex()) {
                try {
                    val (filename, mimeType, data) =
                        withContext(Dispatchers.IO) {
                            readAttachmentFromUri(uri)
                        }

                    // Check file size (max 10MB for inline attachments)
                    val maxSize = 10 * 1024 * 1024 // 10MB
                    if (data.size > maxSize) {
                        errorCount++
                        Toast.makeText(
                            this@ConversationDetailActivity,
                            getString(R.string.attachment_too_large),
                            Toast.LENGTH_SHORT
                        ).show()
                        continue
                    }

                    when (val result = viewModel.sendAttachment(filename, mimeType, data)) {
                        is ConversationDetailViewModel.SendAttachmentState.Success -> {
                            successCount++
                        }

                        is ConversationDetailViewModel.SendAttachmentState.Error -> {
                            errorCount++
                        }

                        else -> {}
                    }
                } catch (e: Exception) {
                    errorCount++
                }
            }

            // Show result toast
            if (errorCount > 0) {
                Toast
                    .makeText(
                        this@ConversationDetailActivity,
                        if (successCount > 0) {
                            "Sent $successCount attachments, $errorCount failed"
                        } else {
                            getString(R.string.attachment_error)
                        },
                        Toast.LENGTH_SHORT,
                    ).show()
            }

            if (successCount > 0) {
                viewModel.fetchMessages()
            }
        }
    }

    private fun readAttachmentFromUri(uri: Uri): Triple<String, String, ByteArray> {
        val contentResolver: ContentResolver = contentResolver

        // Get filename and size
        var filename = "attachment"
        var fileSize: Long = 0
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) {
                    filename = cursor.getString(nameIndex) ?: "attachment"
                }
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex >= 0) {
                    fileSize = cursor.getLong(sizeIndex)
                }
            }
        }

        // Check file size limit (10MB max to prevent OOM)
        val maxSize = 10 * 1024 * 1024L // 10MB
        if (fileSize > maxSize) {
            throw IllegalArgumentException("File too large. Maximum size is 10MB.")
        }

        // Get MIME type
        val mimeType =
            contentResolver.getType(uri)
                ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                    filename.substringAfterLast('.', ""),
                )
                ?: "application/octet-stream"

        // Validate MIME type (basic security check)
        val allowedTypes = setOf(
            "image/", "video/", "audio/", "text/", "application/pdf",
            "application/msword", "application/vnd.", "application/json"
        )
        val isAllowed = allowedTypes.any { mimeType.startsWith(it) || mimeType == it }
        if (!isAllowed && !mimeType.startsWith("application/")) {
            throw IllegalArgumentException("File type not supported: $mimeType")
        }

        // Read data with size check
        val data = contentResolver.openInputStream(uri)?.use { inputStream ->
            val bytes = inputStream.readBytes()
            if (bytes.size > maxSize) {
                throw IllegalArgumentException("File too large. Maximum size is 10MB.")
            }
            bytes
        } ?: throw IllegalStateException("Could not read attachment")

        return Triple(filename, mimeType, data)
    }
}
