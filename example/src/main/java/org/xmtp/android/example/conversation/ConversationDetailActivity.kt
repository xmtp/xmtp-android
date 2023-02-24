package org.xmtp.android.example.conversation

import android.R.id.home
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import org.xmtp.android.example.databinding.ActivityConversationDetailBinding

class ConversationDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityConversationDetailBinding

    companion object {
        const val EXTRA_CONVERSATION = "EXTRA_CONVERSATION"
        private const val PEER_ADDRESS = "[Set to your eth address]"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityConversationDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.subtitle = PEER_ADDRESS
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
