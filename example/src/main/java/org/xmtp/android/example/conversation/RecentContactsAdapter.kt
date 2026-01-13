package org.xmtp.android.example.conversation

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import org.xmtp.android.example.R
import org.xmtp.android.example.databinding.ListItemRecentContactBinding
import org.xmtp.android.example.extension.truncatedAddress
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

class RecentContactsAdapter(
    private val clickListener: RecentContactClickListener,
    private val avatarColors: List<Int>,
) : RecyclerView.Adapter<RecentContactsAdapter.ViewHolder>() {
    private var contacts: List<RecentContact> = emptyList()
    private var isGroupMode: Boolean = false
    private var selectedAddresses: Set<String> = emptySet()

    fun setData(newContacts: List<RecentContact>) {
        val diffCallback = RecentContactDiffCallback(contacts, newContacts, selectedAddresses, selectedAddresses)
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        contacts = newContacts
        diffResult.dispatchUpdatesTo(this)
    }

    fun setGroupMode(
        groupMode: Boolean,
        selectedList: List<String>,
    ) {
        val oldSelectedAddresses = selectedAddresses
        isGroupMode = groupMode
        selectedAddresses = selectedList.map { it.lowercase() }.toSet()

        // Only update items that changed selection state
        if (oldSelectedAddresses != selectedAddresses || isGroupMode != groupMode) {
            val diffCallback = RecentContactDiffCallback(contacts, contacts, oldSelectedAddresses, selectedAddresses)
            val diffResult = DiffUtil.calculateDiff(diffCallback)
            diffResult.dispatchUpdatesTo(this)
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ViewHolder {
        val binding =
            ListItemRecentContactBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false,
            )
        return ViewHolder(binding, clickListener, avatarColors)
    }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int,
    ) {
        holder.bind(contacts[position], isGroupMode, selectedAddresses.contains(contacts[position].address.lowercase()))
    }

    override fun getItemCount(): Int = contacts.size

    class ViewHolder(
        private val binding: ListItemRecentContactBinding,
        private val clickListener: RecentContactClickListener,
        private val avatarColors: List<Int>,
    ) : RecyclerView.ViewHolder(binding.root) {
        private var currentContact: RecentContact? = null

        init {
            binding.root.setOnClickListener {
                currentContact?.let { clickListener.onContactClick(it) }
            }
            binding.addButton.setOnClickListener {
                currentContact?.let { clickListener.onAddClick(it) }
            }
        }

        fun bind(
            contact: RecentContact,
            isGroupMode: Boolean,
            isSelected: Boolean,
        ) {
            currentContact = contact

            // Set avatar
            val avatarText =
                contact.address
                    .removePrefix("0x")
                    .take(2)
                    .uppercase()
            binding.avatarText.text = avatarText

            val colorIndex = abs(contact.address.hashCode()) % avatarColors.size
            binding.avatarCard.setCardBackgroundColor(avatarColors[colorIndex])

            // Set address
            binding.contactAddress.text = contact.address.truncatedAddress()

            // Set last activity time
            binding.contactSubtitle.text = formatLastActivity(contact.lastActivityTime)

            // Show/hide elements based on mode and selection
            if (isGroupMode) {
                binding.addButton.isVisible = !isSelected
                binding.selectedCheck.isVisible = isSelected
            } else {
                binding.addButton.isVisible = false
                binding.selectedCheck.isVisible = false
            }
        }

        private fun formatLastActivity(timestampNs: Long): String {
            val timestampMs = timestampNs / 1_000_000
            val messageDate = Date(timestampMs)
            val now = Calendar.getInstance()
            val messageCalendar = Calendar.getInstance().apply { time = messageDate }

            val timeAgo =
                when {
                    now.get(Calendar.YEAR) == messageCalendar.get(Calendar.YEAR) &&
                        now.get(Calendar.DAY_OF_YEAR) == messageCalendar.get(Calendar.DAY_OF_YEAR) -> {
                        binding.root.context.getString(R.string.last_messaged, "today")
                    }
                    now.get(Calendar.YEAR) == messageCalendar.get(Calendar.YEAR) &&
                        now.get(Calendar.DAY_OF_YEAR) - messageCalendar.get(Calendar.DAY_OF_YEAR) == 1 -> {
                        binding.root.context.getString(R.string.last_messaged, "yesterday")
                    }
                    now.get(Calendar.YEAR) == messageCalendar.get(Calendar.YEAR) &&
                        now.get(Calendar.DAY_OF_YEAR) - messageCalendar.get(Calendar.DAY_OF_YEAR) < 7 -> {
                        val daysDiff = now.get(Calendar.DAY_OF_YEAR) - messageCalendar.get(Calendar.DAY_OF_YEAR)
                        binding.root.context.getString(R.string.last_messaged, "$daysDiff days ago")
                    }
                    else -> {
                        val formattedDate = SimpleDateFormat("MMM d", Locale.getDefault()).format(messageDate)
                        binding.root.context.getString(R.string.last_messaged, formattedDate)
                    }
                }
            return timeAgo
        }
    }

    private class RecentContactDiffCallback(
        private val oldList: List<RecentContact>,
        private val newList: List<RecentContact>,
        private val oldSelected: Set<String>,
        private val newSelected: Set<String>,
    ) : DiffUtil.Callback() {
        override fun getOldListSize(): Int = oldList.size

        override fun getNewListSize(): Int = newList.size

        override fun areItemsTheSame(
            oldItemPosition: Int,
            newItemPosition: Int,
        ): Boolean = oldList[oldItemPosition].address == newList[newItemPosition].address

        override fun areContentsTheSame(
            oldItemPosition: Int,
            newItemPosition: Int,
        ): Boolean {
            val oldItem = oldList[oldItemPosition]
            val newItem = newList[newItemPosition]
            val oldAddress = oldItem.address.lowercase()
            val newAddress = newItem.address.lowercase()
            return oldItem == newItem &&
                oldSelected.contains(oldAddress) == newSelected.contains(newAddress)
        }
    }
}
