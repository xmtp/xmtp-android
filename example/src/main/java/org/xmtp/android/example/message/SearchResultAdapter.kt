package org.xmtp.android.example.message

import android.graphics.Color
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import org.xmtp.android.example.R
import org.xmtp.android.example.extension.truncatedAddress
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

data class SearchResultItem(
    val id: String,
    val senderInboxId: String,
    val content: String,
    val sentAtNs: Long,
    val isDeleted: Boolean = false,
)

class SearchResultAdapter(
    private val onResultClick: (String) -> Unit,
) : ListAdapter<SearchResultItem, SearchResultAdapter.ViewHolder>(DiffCallback()) {
    private var searchQuery: String = ""

    fun setSearchQuery(query: String) {
        searchQuery = query
    }

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

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ViewHolder {
        val view =
            LayoutInflater
                .from(parent.context)
                .inflate(R.layout.item_search_result, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int,
    ) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        itemView: View,
    ) : RecyclerView.ViewHolder(itemView) {
        private val avatarCard: MaterialCardView = itemView.findViewById(R.id.avatarCard)
        private val avatarText: TextView = itemView.findViewById(R.id.avatarText)
        private val senderName: TextView = itemView.findViewById(R.id.senderName)
        private val timestamp: TextView = itemView.findViewById(R.id.timestamp)
        private val messageContent: TextView = itemView.findViewById(R.id.messageContent)

        fun bind(item: SearchResultItem) {
            // Set avatar
            val displayAddress = item.senderInboxId
            avatarText.text = displayAddress.removePrefix("0x").take(2).uppercase()
            val colorIndex = abs(displayAddress.hashCode()) % avatarColors.size
            avatarCard.setCardBackgroundColor(avatarColors[colorIndex])

            // Set sender name
            senderName.text = displayAddress.truncatedAddress()

            // Set timestamp
            timestamp.text = formatTimestamp(item.sentAtNs)

            // Set content with highlighted search term
            messageContent.text = highlightSearchTerm(item.content, searchQuery)

            // Click handler
            itemView.setOnClickListener {
                onResultClick(item.id)
            }
        }

        private fun formatTimestamp(sentAtNs: Long): String {
            val date = Date(sentAtNs / 1_000_000) // Convert nanoseconds to milliseconds
            val now = System.currentTimeMillis()
            val diff = now - date.time

            return when {
                diff < 60_000 -> "Just now"
                diff < 3600_000 -> "${diff / 60_000}m ago"
                diff < 86400_000 -> SimpleDateFormat("h:mm a", Locale.getDefault()).format(date)
                diff < 604800_000 -> SimpleDateFormat("EEE", Locale.getDefault()).format(date)
                else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(date)
            }
        }

        private fun highlightSearchTerm(
            text: String,
            query: String,
        ): SpannableString {
            val spannableString = SpannableString(text)
            if (query.isEmpty()) return spannableString

            val lowerText = text.lowercase()
            val lowerQuery = query.lowercase()
            var startIndex = 0

            while (true) {
                val index = lowerText.indexOf(lowerQuery, startIndex)
                if (index == -1) break

                // Bold the matched text
                spannableString.setSpan(
                    StyleSpan(android.graphics.Typeface.BOLD),
                    index,
                    index + query.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )

                // Color the matched text
                spannableString.setSpan(
                    ForegroundColorSpan(Color.parseColor("#007AFF")),
                    index,
                    index + query.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )

                startIndex = index + query.length
            }

            return spannableString
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<SearchResultItem>() {
        override fun areItemsTheSame(
            oldItem: SearchResultItem,
            newItem: SearchResultItem,
        ): Boolean = oldItem.id == newItem.id

        override fun areContentsTheSame(
            oldItem: SearchResultItem,
            newItem: SearchResultItem,
        ): Boolean = oldItem == newItem
    }
}
