package org.xmtp.android.example

import android.app.Application
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.xmtp.android.example.extension.flowWhileShared
import org.xmtp.android.example.extension.stateFlow
import org.xmtp.android.example.pushnotifications.PushNotificationTokenManager
import org.xmtp.android.library.Conversation
import org.xmtp.android.library.libxmtp.DecodedMessage
import org.xmtp.android.library.Topic
import org.xmtp.android.library.push.Service

class MainViewModel : ViewModel(), DefaultLifecycleObserver {

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading(null))
    val uiState: StateFlow<UiState> = _uiState
    
    private var streamingJob: kotlinx.coroutines.Job? = null
    private var isStreamingActive = false
    private var lastMessageTime = 0L

    init {
        // Register for app lifecycle events
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onCleared() {
        super.onCleared()
        // Cancel streaming job and unregister lifecycle observer
        streamingJob?.cancel()
        isStreamingActive = false
        ProcessLifecycleOwner.get().lifecycle.removeObserver(this)
        Log.d("CAMERONVOELL", "ViewModel cleared, stopped streaming")
    }

    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        // App is going to background
        Log.d("CAMERONVOELL", "App is going to background - starting streamAllMessages...")
        // startStreamAllMessages()
    }

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        // App is coming to foreground
        Log.d("CAMERONVOELL", "App is coming to foreground")
        startStreamAllMessages()
    }

    private fun startStreamAllMessages() {
        // Cancel any existing streaming job
        streamingJob?.cancel()
        
        try {
            Log.d("CAMERONVOELL", "Starting robust streamAllMessages with restart logic...")
            
            streamingJob = kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                startStreamingWithRestart()
            }
            
            // Also start a watchdog to monitor and restart streaming if it stops
            startStreamingWatchdog()
            
        } catch (e: Exception) {
            Log.e("CAMERONVOELL", "error caught starting streamAllMessages: ${e.message}", e)
        }
    }
    
    private suspend fun startStreamingWithRestart() {
        var restartCount = 0
        val maxRestarts = 10
        
        while (restartCount < maxRestarts) {
            try {
                // Wait for client to be ready with retry logic
                var attempts = 0
                val maxAttempts = 3
                
                while (attempts < maxAttempts) {
                    attempts++
                    
                    if (ClientManager.clientState.value is ClientManager.ClientState.Ready) {
                        Log.d("CAMERONVOELL", "Client ready on attempt $attempts (restart #$restartCount), starting stream collection...")
                        isStreamingActive = true
                        
                        // Start streaming and collect messages with onClose callback
                        ClientManager.client.conversations.streamAllMessages(
                            onClose = {
                                Log.w("CAMERONVOELL", "Stream onClose callback triggered - will restart stream")
                                isStreamingActive = false
                                // The stream will be restarted by the outer restart loop
                            }
                        ).collect { message ->
                            lastMessageTime = System.currentTimeMillis()
                            Log.d("CAMERONVOELL", "Message streamed: ${message.body} from ${message.senderInboxId}")
                        }
                        
                        // If we reach here, the stream ended (shouldn't happen normally)
                        Log.w("CAMERONVOELL", "Stream collection ended unexpectedly")
                        break
                        
                    } else {
                        Log.w("CAMERONVOELL", "Client not ready on attempt $attempts of $maxAttempts (restart #$restartCount)")
                        if (attempts < maxAttempts) {
                            Log.d("CAMERONVOELL", "Waiting 1 second before retry...")
                            delay(1000)
                        }
                    }
                }
                
                if (attempts >= maxAttempts) {
                    Log.e("CAMERONVOELL", "Client not ready after $maxAttempts attempts (restart #$restartCount)")
                }
                
            } catch (e: Exception) {
                Log.e("CAMERONVOELL", "Streaming error (restart #$restartCount): ${e.message}", e)
            }
            
            isStreamingActive = false
            restartCount++
            
            if (restartCount < maxRestarts) {
                val delayMs = minOf(5000L * restartCount, 30000L) // Exponential backoff, max 30s
                Log.d("CAMERONVOELL", "Restarting stream in ${delayMs/1000}s (restart #$restartCount)")
                delay(delayMs)
            }
        }
        
        Log.e("CAMERONVOELL", "Max restarts ($maxRestarts) reached, giving up on streaming")
    }
    
    private fun startStreamingWatchdog() {
        kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
            while (true) {
                delay(30000) // Check every 30 seconds
                
                if (isStreamingActive) {
                    val timeSinceLastMessage = System.currentTimeMillis() - lastMessageTime
                    if (timeSinceLastMessage > 60000) { // No messages for 1 minute
                        Log.w("CAMERONVOELL", "Streaming watchdog: No messages for ${timeSinceLastMessage/1000}s, restarting...")
                        startStreamAllMessages() // Restart
                        break // Exit this watchdog, new one will be started
                    } else {
                        Log.d("CAMERONVOELL", "Streaming watchdog: OK (last message ${timeSinceLastMessage/1000}s ago)")
                    }
                }
            }
        }
    }

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
                val conversations = ClientManager.client.conversations
                val subscriptions = conversations.allPushTopics().map {
                    val hmacKeysResult = ClientManager.client.conversations.getHmacKeys()
                    val hmacKeys = hmacKeysResult.hmacKeysMap
                    val result = hmacKeys[it]?.valuesList?.map { hmacKey ->
                        Service.Subscription.HmacKey.newBuilder().also { sub_key ->
                            sub_key.key = hmacKey.hmacKey
                            sub_key.thirtyDayPeriodsSinceEpoch = hmacKey.thirtyDayPeriodsSinceEpoch
                        }.build()
                    }

                    Service.Subscription.newBuilder().also { sub ->
                        sub.addAllHmacKeys(result)
                        sub.topic = it
                        sub.isSilent = false
                    }.build()
                }.toMutableList()

                val welcomeTopic = Service.Subscription.newBuilder().also { sub ->
                    sub.topic = Topic.userWelcome(ClientManager.client.installationId).description
                    sub.isSilent = false
                }.build()
                subscriptions.add(welcomeTopic)

                PushNotificationTokenManager.xmtpPush.subscribeWithMetadata(subscriptions)
                listItems.addAll(
                    conversations.list().map { conversation ->
                        val lastMessage = fetchMostRecentMessage(conversation)
                        MainListItem.ConversationItem(
                            id = conversation.topic,
                            conversation,
                            lastMessage
                        )
                    }
                )
                
                listItems.add(
                    MainListItem.Footer(
                        id = "footer",
                        ClientManager.client.inboxId,
                        ClientManager.client.environment.name
                    )
                )
                _uiState.value = UiState.Success(listItems)
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.localizedMessage.orEmpty())
            }
        }
    }

    @WorkerThread
    private fun fetchMostRecentMessage(conversation: Conversation): DecodedMessage? {
        return runBlocking { conversation.lastMessage() }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val stream: StateFlow<MainListItem?> =
        stateFlow(viewModelScope, null) { subscriptionCount ->
            if (ClientManager.clientState.value is ClientManager.ClientState.Ready) {
                ClientManager.client.conversations.stream()
                    .flowWhileShared(
                        subscriptionCount,
                        SharingStarted.WhileSubscribed(1000L)
                    )
                    .flowOn(Dispatchers.IO)
                    .distinctUntilChanged()
                    .mapLatest { conversation ->
                        val lastMessage = fetchMostRecentMessage(conversation)
                        MainListItem.ConversationItem(conversation.topic, conversation, lastMessage)
                    }
                    .catch { emptyFlow<MainListItem>() }
            } else {
                emptyFlow()
            }
        }

    sealed class UiState {
        data class Loading(val listItems: List<MainListItem>?) : UiState()
        data class Success(val listItems: List<MainListItem>) : UiState()
        data class Error(val message: String) : UiState()
    }

    sealed class MainListItem(open val id: String, val itemType: Int) {
        companion object {
            const val ITEM_TYPE_CONVERSATION = 1
            const val ITEM_TYPE_FOOTER = 2
        }

        data class ConversationItem(
            override val id: String,
            val conversation: Conversation,
            val mostRecentMessage: DecodedMessage?,
        ) : MainListItem(id, ITEM_TYPE_CONVERSATION)

        data class Footer(
            override val id: String,
            val address: String,
            val environment: String,
        ) : MainListItem(id, ITEM_TYPE_FOOTER)
    }
}
