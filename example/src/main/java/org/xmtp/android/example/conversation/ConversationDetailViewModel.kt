package org.xmtp.android.example.conversation

import androidx.annotation.UiThread
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.xmtp.android.library.Conversation

class ConversationDetailViewModel : ViewModel() {
    var conversation: Conversation? = null

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading(null))
    val uiState: StateFlow<UiState> = _uiState

    @UiThread
    fun fetchMessages(peerAddress: String) {
        when (val uiState = uiState.value) {
            is UiState.Success -> _uiState.value = UiState.Loading(uiState.listItems)
            else -> _uiState.value = UiState.Loading(null)
        }
        viewModelScope.launch(Dispatchers.IO) {
            val listItems = mutableListOf<MessageListItem>()
            try {
                conversation?.let {
                    it.messages().map { message ->
                        MessageListItem.Message(message.id, message.body)
                    }
                }
                _uiState.value = UiState.Success(listItems)
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.localizedMessage.orEmpty())
            }
        }
    }

    sealed class UiState {
        data class Loading(val listItems: List<MessageListItem>?) : UiState()
        data class Success(val listItems: List<MessageListItem>) : UiState()
        data class Error(val message: String) : UiState()
    }

    sealed class MessageListItem(open val id: String, val itemType: Int) {
        companion object {
            const val ITEM_TYPE_MESSAGE = 1
        }
        data class Message(override val id: String, val body: String) :
            MessageListItem(id, ITEM_TYPE_MESSAGE)
    }
}
