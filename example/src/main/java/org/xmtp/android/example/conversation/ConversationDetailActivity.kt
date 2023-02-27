package org.xmtp.android.example.conversation

import android.R.id.home
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import org.xmtp.android.example.databinding.ActivityConversationDetailBinding
import org.xmtp.android.library.Conversation

class ConversationDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityConversationDetailBinding

    private val viewModel: ConversationDetailViewModel by viewModels()

    companion object {
        const val EXTRA_CONVERSATION = "EXTRA_CONVERSATION"
        const val PEER_ADDRESS = "[Your ETH address]"

        fun intent(context: Context, conversation: Conversation): Intent {
            return Intent(context, ConversationDetailActivity::class.java).apply {
                putExtra(EXTRA_CONVERSATION, conversation)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.setConversation(intent.extras?.getParcelable(EXTRA_CONVERSATION))

        binding = ActivityConversationDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.subtitle = PEER_ADDRESS

        viewModel.fetchMessages()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            home -> {
                finish()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }
}
