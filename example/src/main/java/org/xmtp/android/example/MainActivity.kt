package org.xmtp.android.example

import android.accounts.AccountManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.launch
import org.xmtp.android.example.connect.ConnectWalletActivity
import org.xmtp.android.example.conversation.ConversationDetailActivity
import org.xmtp.android.example.conversation.ConversationsAdapter
import org.xmtp.android.example.conversation.ConversationsClickListener
import org.xmtp.android.example.databinding.ActivityMainBinding
import org.xmtp.android.library.Conversation

class MainActivity : AppCompatActivity(),
    ConversationsClickListener {

    private val viewModel: MainViewModel by viewModels()
    private lateinit var binding: ActivityMainBinding
    private lateinit var accountManager: AccountManager
    private lateinit var adapter: ConversationsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        accountManager = AccountManager.get(this)

        val keys = loadKeys()
        if (keys == null) {
            showSignIn()
            return
        }

        ClientService.createClient(keys)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        adapter = ConversationsAdapter(clickListener = this)
        binding.list.layoutManager = LinearLayoutManager(this)
        binding.list.adapter = adapter
        binding.refresh.setOnRefreshListener {
            if (ClientService.clientState.value is ClientService.ClientState.Ready) {
                viewModel.fetchConversations()
            }
        }

        binding.fab.setOnClickListener {
            openConversationDetail()
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                ClientService.clientState.collect(::ensureClientState)
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::ensureUiState)
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.disconnect -> {
                disconnectWallet()
                true
            }
            R.id.copy_address -> {
                copyWalletAddress()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onConversationClick(conversation: Conversation) {
        val intent = Intent(this, ConversationDetailActivity::class.java).apply {
            // TODO: We have to figure out how to pass a conversation around!
            // We can either have conversation implement parcelable, but if we do that -- it can't
            // hold a `Client` instance. Or we can create a way to fetch a conversation by peer address
            // from a client so we don't have to serialize it here.
            // putExtra(ConversationDetailActivity.EXTRA_CONVERSATION, conversation)
        }
        startActivity(intent)
    }

    override fun onFooterClick(address: String) {
        copyWalletAddress()
    }

    private fun ensureClientState(clientState: ClientService.ClientState) {
        when (clientState) {
            is ClientService.ClientState.Ready -> {
                viewModel.fetchConversations()
                binding.fab.visibility = View.VISIBLE
            }
            is ClientService.ClientState.Error -> showError(clientState.message)
            is ClientService.ClientState.Unknown -> Unit
        }
    }

    private fun ensureUiState(uiState: MainViewModel.UiState) {
        binding.progress.visibility = View.GONE
        when (uiState) {
            is MainViewModel.UiState.Loading -> {
                if (uiState.listItems.isNullOrEmpty()) {
                    binding.progress.visibility = View.VISIBLE
                } else {
                    adapter.setData(uiState.listItems)
                }
            }
            is MainViewModel.UiState.Success -> {
                binding.refresh.isRefreshing = false
                adapter.setData(uiState.listItems)
            }
            is MainViewModel.UiState.Error -> {
                binding.refresh.isRefreshing = false
                showError(uiState.message)
            }
        }
    }

    private fun loadKeys(): String? {
        val accounts = accountManager.getAccountsByType(resources.getString(R.string.account_type))
        val account = accounts.firstOrNull() ?: return null
        return accountManager.getPassword(account)
    }

    private fun showSignIn() {
        startActivity(Intent(this, ConnectWalletActivity::class.java))
        finish()
    }

    private fun showError(message: String) {
        val error = message.ifBlank { resources.getString(R.string.error) }
        Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
    }

    private fun disconnectWallet() {
        ClientService.clearClient()
        val accounts = accountManager.getAccountsByType(resources.getString(R.string.account_type))
        accounts.forEach { account ->
            accountManager.removeAccount(account, null, null, null)
        }
        showSignIn()
    }

    private fun copyWalletAddress() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("address", ClientService.client.address)
        clipboard.setPrimaryClip(clip)
    }

    private fun openConversationDetail() {
        startActivity(Intent(this, ConversationDetailActivity::class.java))
    }
}
