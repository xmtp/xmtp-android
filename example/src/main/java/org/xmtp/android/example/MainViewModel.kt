package org.xmtp.android.example

import androidx.annotation.UiThread
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.xmtp.android.example.pushnotifications.PushNotificationTokenManager
import org.xmtp.android.library.Conversation
import org.xmtp.android.library.Topic
import org.xmtp.android.library.libxmtp.DecodedMessage
import org.xmtp.android.library.push.Service
import timber.log.Timber

class MainViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<UiState>(UiState.Loading(null))
    val uiState: StateFlow<UiState> = _uiState

    @UiThread
    fun setupPush() {
        viewModelScope.launch(Dispatchers.IO) {
            PushNotificationTokenManager.ensurePushTokenIsConfigured()
        }
    }

    @UiThread
    fun fetchConversations() {
        when (val uiState = uiState.value) {
            is UiState.Success -> _uiState.value = UiState.Loading(uiState.listItems)
            else -> _uiState.value = UiState.Loading(null)
        }
        viewModelScope.launch(Dispatchers.IO) {
            val listItems = mutableListOf<MainListItem>()
            try {
                Timber.d("fetchConversations: starting, clientState=${ClientManager.clientState.value}")
                val conversations = ClientManager.client.conversations
                // Ensure we fetch the latest conversations from the network before listing
                Timber.d("fetchConversations: syncing conversations...")
                conversations.sync()
                Timber.d("fetchConversations: sync complete")
                val subscriptions =
                    conversations
                        .allPushTopics()
                        .map {
                            val hmacKeysResult = ClientManager.client.conversations.getHmacKeys()
                            val hmacKeys = hmacKeysResult.hmacKeysMap
                            val result =
                                hmacKeys[it]?.valuesList?.map { hmacKey ->
                                    Service.Subscription.HmacKey
                                        .newBuilder()
                                        .also { sub_key ->
                                            sub_key.key = hmacKey.hmacKey
                                            sub_key.thirtyDayPeriodsSinceEpoch = hmacKey.thirtyDayPeriodsSinceEpoch
                                        }.build()
                                }

                            Service.Subscription
                                .newBuilder()
                                .also { sub ->
                                    sub.addAllHmacKeys(result)
                                    sub.topic = it
                                    sub.isSilent = false
                                }.build()
                        }.toMutableList()

                val welcomeTopic =
                    Service.Subscription
                        .newBuilder()
                        .also { sub ->
                            sub.topic = Topic.userWelcome(ClientManager.client.installationId).description
                            sub.isSilent = false
                        }.build()
                subscriptions.add(welcomeTopic)

                PushNotificationTokenManager.xmtpPush.subscribeWithMetadata(subscriptions)
                val conversationList = conversations.list()
                Timber.d("fetchConversations: found ${conversationList.size} conversations")
                listItems.addAll(
                    conversationList.map { conversation ->
                        val lastMessage = fetchMostRecentMessage(conversation)
                        val (displayName, peerAddress) = getConversationDisplayInfo(conversation)
                        MainListItem.ConversationItem(
                            id = conversation.topic,
                            conversation = conversation,
                            mostRecentMessage = lastMessage,
                            displayName = displayName,
                            peerAddress = peerAddress,
                        )
                    },
                )
                Timber.d("fetchConversations: success, total items=${listItems.size}")
                _uiState.value = UiState.Success(listItems)
            } catch (e: Exception) {
                Timber.e(e, "fetchConversations: error")
                _uiState.value = UiState.Error(e.localizedMessage.orEmpty())
            }
        }
    }

    private suspend fun fetchMostRecentMessage(conversation: Conversation): DecodedMessage? = conversation.lastMessage()

    private suspend fun getConversationDisplayInfo(conversation: Conversation): Pair<String, String?> =
        when (conversation) {
            is Conversation.Group -> {
                val groupName = conversation.group.name()
                val displayName = if (groupName.isNotBlank()) groupName else conversation.id
                Pair(displayName, null)
            }

            is Conversation.Dm -> {
                val peerInboxId = conversation.dm.peerInboxId
                val members = conversation.dm.members()
                val peerMember = members.find { it.inboxId == peerInboxId }
                val peerAddress = peerMember?.identities?.firstOrNull()?.identifier
                val displayName = peerAddress ?: conversation.id
                Pair(displayName, peerAddress)
            }
        }

    // Stream for new conversations - reacts to client state changes
    @OptIn(ExperimentalCoroutinesApi::class)
    val stream: StateFlow<MainListItem?> =
        ClientManager.clientState
            .filterIsInstance<ClientManager.ClientState.Ready>()
            .flatMapLatest {
                ClientManager.client.conversations
                    .stream()
                    .distinctUntilChanged()
                    .mapLatest<Conversation, MainListItem?> { conversation ->
                        val lastMessage = fetchMostRecentMessage(conversation)
                        val (displayName, peerAddress) = getConversationDisplayInfo(conversation)
                        MainListItem.ConversationItem(
                            id = conversation.topic,
                            conversation = conversation,
                            mostRecentMessage = lastMessage,
                            displayName = displayName,
                            peerAddress = peerAddress,
                        )
                    }.catch { emptyFlow<MainListItem?>() }
            }.flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    // Stream for message updates - triggers when any conversation receives a new message
    // Uses flatMapLatest to react to client state changes
    @OptIn(ExperimentalCoroutinesApi::class)
    val messageStream: StateFlow<MessageUpdate?> =
        ClientManager.clientState
            .filterIsInstance<ClientManager.ClientState.Ready>()
            .flatMapLatest {
                ClientManager.client.conversations
                    .streamAllMessages()
                    .map<DecodedMessage, MessageUpdate?> { message ->
                        MessageUpdate(message.topic, message)
                    }.catch { emptyFlow<MessageUpdate?>() }
            }.flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    data class MessageUpdate(
        val topic: String,
        val message: DecodedMessage,
    )

    sealed class UiState {
        data class Loading(
            val listItems: List<MainListItem>?,
        ) : UiState()

        data class Success(
            val listItems: List<MainListItem>,
        ) : UiState()

        data class Error(
            val message: String,
        ) : UiState()
    }

    sealed class MainListItem(
        open val id: String,
        val itemType: Int,
    ) {
        companion object {
            const val ITEM_TYPE_CONVERSATION = 1
        }

        data class ConversationItem(
            override val id: String,
            val conversation: Conversation,
            val mostRecentMessage: DecodedMessage?,
            val displayName: String,
            val peerAddress: String? = null,
        ) : MainListItem(id, ITEM_TYPE_CONVERSATION)
    }
}
