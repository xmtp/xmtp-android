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
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.xmtp.android.example.connect.ConnectWalletActivity
import org.xmtp.android.example.conversation.ConversationDetailActivity
import org.xmtp.android.example.conversation.ConversationsAdapter
import org.xmtp.android.example.conversation.ConversationsClickListener
import org.xmtp.android.example.conversation.NewConversationActivity
import org.xmtp.android.example.databinding.ActivityMainBinding
import org.xmtp.android.example.logs.LogViewerBottomSheet
import org.xmtp.android.example.pushnotifications.PushNotificationTokenManager
import org.xmtp.android.example.utils.KeyUtil
import org.xmtp.android.example.wallet.WalletInfoBottomSheet
import org.xmtp.android.library.Client
import org.xmtp.android.library.Conversation
import timber.log.Timber
import uniffi.xmtpv3.FfiLogLevel
import uniffi.xmtpv3.FfiLogRotation

class MainActivity :
    AppCompatActivity(),
    ConversationsClickListener,
    WalletInfoBottomSheet.WalletInfoListener,
    NavigationView.OnNavigationItemSelectedListener {
    private val viewModel: MainViewModel by viewModels()
    private lateinit var binding: ActivityMainBinding
    private lateinit var accountManager: AccountManager
    private lateinit var adapter: ConversationsAdapter
    private lateinit var drawerToggle: ActionBarDrawerToggle
    private var logsBottomSheet: LogViewerBottomSheet? = null
    private var walletInfoBottomSheet: WalletInfoBottomSheet? = null
    private val REQUEST_CODE_POST_NOTIFICATIONS = 101

    companion object {
        private const val PREFS_NAME = "XMTPPreferences"
        private const val KEY_LOGS_ACTIVATED = "logs_activated"

        // Push notification server - configured via build variants in production
        private const val DEFAULT_PUSH_SERVER = "10.0.2.2:8080"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        // Setup Navigation Drawer
        setupNavigationDrawer()

        adapter = ConversationsAdapter(clickListener = this)
        binding.list.layoutManager = LinearLayoutManager(this)
        binding.list.adapter = adapter
        binding.refresh.setOnRefreshListener {
            if (ClientManager.clientState.value is ClientManager.ClientState.Ready) {
                viewModel.fetchConversations()
            }
        }

        binding.fab.setOnClickListener {
            openNewConversation()
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                ClientManager.clientState.collect(::ensureClientState)
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::ensureUiState)
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.stream.collect(::addStreamedItem)
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.messageStream.collect(::handleMessageUpdate)
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

    private fun setupNavigationDrawer() {
        drawerToggle =
            ActionBarDrawerToggle(
                this,
                binding.drawerLayout,
                binding.toolbar,
                R.string.navigation_drawer_open,
                R.string.navigation_drawer_close,
            )
        binding.drawerLayout.addDrawerListener(drawerToggle)
        drawerToggle.syncState()

        binding.navigationView.setNavigationItemSelectedListener(this)
    }

    private fun updateDrawerHeader() {
        val headerView = binding.navigationView.getHeaderView(0)
        val client = ClientManager.client

        headerView.findViewById<TextView>(R.id.drawerWalletAddress).text =
            client.publicIdentity.identifier
        headerView.findViewById<TextView>(R.id.drawerEnvironment).text =
            client.environment.name

        // Update toggle states
        val menu = binding.navigationView.menu
        menu.findItem(R.id.nav_toggle_logs)?.isChecked = isLogsActivated()

        // Update hide deleted messages toggle state
        val keyUtil = KeyUtil(this)
        val hideDeleted = keyUtil.getHideDeletedMessages()
        menu.findItem(R.id.nav_hide_deleted_messages)?.isChecked = hideDeleted
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        val isClientReady = ClientManager.clientState.value is ClientManager.ClientState.Ready

        when (item.itemId) {
            R.id.nav_wallet_info -> {
                if (isClientReady) {
                    openWalletInfoBottomSheet()
                } else {
                    Toast.makeText(this, "Client not ready", Toast.LENGTH_SHORT).show()
                }
            }

            R.id.nav_new_conversation -> {
                if (isClientReady) {
                    openNewConversation()
                } else {
                    Toast.makeText(this, "Client not ready", Toast.LENGTH_SHORT).show()
                }
            }

            R.id.nav_new_group -> {
                if (isClientReady) {
                    openNewConversation()
                } else {
                    Toast.makeText(this, "Client not ready", Toast.LENGTH_SHORT).show()
                }
            }

            R.id.nav_view_logs -> {
                openLogsViewer()
            }

            R.id.nav_toggle_logs -> {
                val newState = !item.isChecked
                item.isChecked = newState
                onLogsToggled(newState)
                return true // Don't close drawer for toggle
            }

            R.id.nav_hide_deleted_messages -> {
                val newState = !item.isChecked
                item.isChecked = newState
                onHideDeletedMessagesToggled(newState)
                return true // Don't close drawer for toggle
            }

            R.id.nav_copy_address -> {
                if (isClientReady) {
                    copyWalletAddress()
                } else {
                    Toast.makeText(this, "Client not ready", Toast.LENGTH_SHORT).show()
                }
            }

            R.id.nav_disconnect -> {
                if (isClientReady) {
                    disconnectWallet()
                } else {
                    // Still allow disconnect to clear broken state
                    ClientManager.clearClient()
                    PushNotificationTokenManager.clearXMTPPush()
                    val accounts = accountManager.getAccountsByType(resources.getString(R.string.account_type))
                    accounts.forEach { account ->
                        accountManager.removeAccount(account, null, null, null)
                    }
                    showSignIn()
                }
            }
        }
        binding.drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (::binding.isInitialized && binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
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
        logsBottomSheet?.dismiss()
        walletInfoBottomSheet?.dismiss()
        super.onDestroy()
    }

    override fun onConversationClick(conversation: Conversation) {
        startActivity(
            ConversationDetailActivity.intent(
                this,
                topic = conversation.topic,
                peerAddress = conversation.id,
            ),
        )
    }

    private fun ensureClientState(clientState: ClientManager.ClientState) {
        Timber.d("ensureClientState: $clientState")
        when (clientState) {
            is ClientManager.ClientState.Ready -> {
                Timber.d("ensureClientState: Ready, fetching conversations...")
                viewModel.fetchConversations()
                binding.fab.visibility = View.VISIBLE
                updateDrawerHeader()
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
        }
    }

    private fun openWalletInfoBottomSheet() {
        walletInfoBottomSheet = WalletInfoBottomSheet.newInstance()
        walletInfoBottomSheet?.show(
            supportFragmentManager,
            WalletInfoBottomSheet.TAG,
        )
    }

    private fun addStreamedItem(item: MainViewModel.MainListItem?) {
        item?.let {
            adapter.addItem(item)
        }
    }

    private fun handleMessageUpdate(update: MainViewModel.MessageUpdate?) {
        update?.let {
            val contentType =
                it.message.encodedContent
                    ?.type
                    ?.typeId
            // For edit/delete messages, refresh the full conversation to get updated enriched content
            if (contentType == "editMessage" || contentType == "deleteMessage") {
                viewModel.fetchConversations()
            } else {
                adapter.updateConversationMessage(it.topic, it.message)
            }
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
        val address = ClientManager.client.publicIdentity.identifier
        keyUtil.clearPrivateKey(address)
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
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val walletAddress = ClientManager.client.publicIdentity.identifier
        val clip = ClipData.newPlainText("wallet_address", walletAddress)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "Wallet address copied", Toast.LENGTH_SHORT).show()
    }

    private fun openNewConversation() {
        startActivity(NewConversationActivity.intent(this))
    }

    private fun openLogsViewer() {
        logsBottomSheet = LogViewerBottomSheet.newInstance()
        logsBottomSheet?.show(
            supportFragmentManager,
            LogViewerBottomSheet.TAG,
        )
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

    // WalletInfoListener implementation
    override fun onLogsToggled(enabled: Boolean) {
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
        // Update drawer menu item state
        binding.navigationView.menu
            .findItem(R.id.nav_toggle_logs)
            ?.isChecked = enabled
    }

    override fun onDisconnectClicked() {
        disconnectWallet()
    }

    override fun isLogsEnabled(): Boolean = isLogsActivated()

    private fun onHideDeletedMessagesToggled(enabled: Boolean) {
        val keyUtil = KeyUtil(this)
        keyUtil.setHideDeletedMessages(enabled)
        val message = if (enabled) "Deleted messages will be hidden" else "Deleted messages will be shown"
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
