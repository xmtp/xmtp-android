package org.xmtp.android.example.conversation

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.xmtp.android.example.ClientManager
import org.xmtp.android.example.R
import org.xmtp.android.example.databinding.ActivityUserProfileBinding
import org.xmtp.android.example.extension.truncatedAddress
import org.xmtp.android.library.libxmtp.IdentityKind
import org.xmtp.android.library.libxmtp.InboxState
import org.xmtp.android.library.libxmtp.PublicIdentity
import kotlin.math.abs

class UserProfileActivity : AppCompatActivity() {
    private lateinit var binding: ActivityUserProfileBinding

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityUserProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val walletAddress =
            intent.getStringExtra(EXTRA_WALLET_ADDRESS) ?: run {
                finish()
                return
            }
        val inboxId = intent.getStringExtra(EXTRA_INBOX_ID)

        setupUi(walletAddress, inboxId)
        setupClickListeners(walletAddress, inboxId)

        if (inboxId != null) {
            loadInboxState(inboxId)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }

    private fun setupUi(
        walletAddress: String,
        inboxId: String?,
    ) {
        // Set avatar
        val avatarText =
            walletAddress
                .removePrefix("0x")
                .take(2)
                .uppercase()
        binding.userAvatarText.text = avatarText

        val colorIndex = abs((inboxId ?: walletAddress).hashCode()) % avatarColors.size
        binding.userAvatarCard.setCardBackgroundColor(avatarColors[colorIndex])

        // Set wallet address
        binding.walletAddress.text = walletAddress.truncatedAddress()
        binding.fullWalletAddress.text = walletAddress

        // Set inbox ID
        binding.inboxId.text = inboxId ?: getString(R.string.unknown)
    }

    private fun setupClickListeners(
        walletAddress: String,
        inboxId: String?,
    ) {
        binding.copyAddressButton.setOnClickListener {
            copyToClipboard("Wallet Address", walletAddress)
        }

        binding.copyInboxIdButton.setOnClickListener {
            inboxId?.let { id ->
                copyToClipboard("Inbox ID", id)
            }
        }

        binding.sendMessageButton.setOnClickListener {
            startConversation(walletAddress)
        }
    }

    private fun loadInboxState(inboxId: String) {
        binding.progress.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val inboxStates =
                    withContext(Dispatchers.IO) {
                        ClientManager.client.inboxStatesForInboxIds(
                            refreshFromNetwork = true,
                            inboxIds = listOf(inboxId),
                        )
                    }

                val inboxState = inboxStates.firstOrNull()
                if (inboxState != null) {
                    displayInboxState(inboxState)
                }

                binding.progress.visibility = View.GONE
            } catch (e: Exception) {
                binding.progress.visibility = View.GONE
                // Silently fail - we still have basic info
            }
        }
    }

    private fun displayInboxState(inboxState: InboxState) {
        val identities = inboxState.identities
        if (identities.size > 1) {
            binding.identitiesCard.isVisible = true
            binding.identitiesList.layoutManager = LinearLayoutManager(this)
            binding.identitiesList.adapter = IdentityAdapter(identities)
        }
    }

    private fun copyToClipboard(
        label: String,
        text: String,
    ) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, getString(R.string.copied_to_clipboard, label), Toast.LENGTH_SHORT).show()
    }

    private fun startConversation(walletAddress: String) {
        binding.progress.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val conversation =
                    withContext(Dispatchers.IO) {
                        val publicIdentity = PublicIdentity(IdentityKind.ETHEREUM, walletAddress)
                        ClientManager.client.conversations.findOrCreateDmWithIdentity(publicIdentity)
                    }

                binding.progress.visibility = View.GONE

                startActivity(
                    ConversationDetailActivity.intent(
                        this@UserProfileActivity,
                        topic = conversation.topic,
                        peerAddress = conversation.id,
                    ),
                )
                finish()
            } catch (e: Exception) {
                binding.progress.visibility = View.GONE
                Toast
                    .makeText(
                        this@UserProfileActivity,
                        e.localizedMessage ?: getString(R.string.error),
                        Toast.LENGTH_SHORT,
                    ).show()
            }
        }
    }
}

// Simple adapter for linked identities
class IdentityAdapter(
    private val identities: List<org.xmtp.android.library.libxmtp.PublicIdentity>,
) : androidx.recyclerview.widget.RecyclerView.Adapter<IdentityAdapter.IdentityViewHolder>() {
    override fun onCreateViewHolder(
        parent: android.view.ViewGroup,
        viewType: Int,
    ): IdentityViewHolder {
        val textView =
            android.widget.TextView(parent.context).apply {
                layoutParams =
                    android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
                setPadding(0, 8, 0, 8)
                setTextColor(parent.context.getColor(R.color.text_secondary))
                textSize = 14f
            }
        return IdentityViewHolder(textView)
    }

    override fun onBindViewHolder(
        holder: IdentityViewHolder,
        position: Int,
    ) {
        holder.bind(identities[position])
    }

    override fun getItemCount(): Int = identities.size

    class IdentityViewHolder(
        private val textView: android.widget.TextView,
    ) : androidx.recyclerview.widget.RecyclerView.ViewHolder(textView) {
        fun bind(identity: org.xmtp.android.library.libxmtp.PublicIdentity) {
            textView.text = identity.identifier
        }
    }
}
