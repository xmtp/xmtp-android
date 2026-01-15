package org.xmtp.android.example.conversation

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import org.xmtp.android.example.R
import org.xmtp.android.example.databinding.ListItemMemberBinding
import org.xmtp.android.example.extension.truncatedAddress
import org.xmtp.android.library.libxmtp.Member
import org.xmtp.android.library.libxmtp.PermissionLevel
import kotlin.math.abs

data class MemberItem(
    val member: Member,
    val isCurrentUser: Boolean,
    val displayAddress: String?,
)

interface MemberClickListener {
    fun onMemberClick(member: MemberItem)

    fun onPromoteToAdmin(member: MemberItem)

    fun onDemoteFromAdmin(member: MemberItem)

    fun onRemoveMember(member: MemberItem)
}

class MemberAdapter(
    private val listener: MemberClickListener,
    private val canManageMembers: Boolean,
) : RecyclerView.Adapter<MemberAdapter.MemberViewHolder>() {
    private val members = mutableListOf<MemberItem>()

    private val avatarColors =
        listOf(
            Color.parseColor("#FC4F37"), // XMTP Red
            Color.parseColor("#5856D6"), // Purple
            Color.parseColor("#34C759"), // Green
            Color.parseColor("#FF9500"), // Orange
            Color.parseColor("#007AFF"), // Blue
            Color.parseColor("#AF52DE"), // Magenta
            Color.parseColor("#00C7BE"), // Teal
            Color.parseColor("#FF2D55"), // Pink
        )

    fun setData(newMembers: List<MemberItem>) {
        val diffCallback = MemberDiffCallback(members, newMembers)
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        members.clear()
        members.addAll(newMembers)
        diffResult.dispatchUpdatesTo(this)
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): MemberViewHolder {
        val binding =
            ListItemMemberBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false,
            )
        return MemberViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: MemberViewHolder,
        position: Int,
    ) {
        holder.bind(members[position])
    }

    override fun getItemCount(): Int = members.size

    inner class MemberViewHolder(
        private val binding: ListItemMemberBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: MemberItem) {
            val context = binding.root.context

            // Set avatar
            val displayText =
                item.displayAddress
                    ?.removePrefix("0x")
                    ?.take(2)
                    ?.uppercase()
                    ?: item.member.inboxId
                        .take(2)
                        .uppercase()
            binding.memberAvatarText.text = displayText

            val colorIndex = abs(item.member.inboxId.hashCode()) % avatarColors.size
            binding.memberAvatarCard.setCardBackgroundColor(avatarColors[colorIndex])

            // Set address
            val addressText =
                if (item.isCurrentUser) {
                    "${item.displayAddress?.truncatedAddress() ?: item.member.inboxId.truncatedAddress()} (You)"
                } else {
                    item.displayAddress?.truncatedAddress() ?: item.member.inboxId.truncatedAddress()
                }
            binding.memberAddress.text = addressText

            // Set inbox ID
            binding.memberInboxId.text =
                context.getString(
                    R.string.inbox_id_display,
                    item.member.inboxId.take(8) + "...",
                )

            // Set role badge
            when (item.member.permissionLevel) {
                PermissionLevel.SUPER_ADMIN -> {
                    binding.memberRole.isVisible = true
                    binding.memberRole.text = context.getString(R.string.role_super_admin)
                    binding.memberRole.setChipBackgroundColorResource(R.color.xmtp_primary)
                }
                PermissionLevel.ADMIN -> {
                    binding.memberRole.isVisible = true
                    binding.memberRole.text = context.getString(R.string.role_admin)
                    binding.memberRole.setChipBackgroundColorResource(R.color.admin_badge)
                }
                PermissionLevel.MEMBER -> {
                    binding.memberRole.isVisible = false
                }
            }

            // Setup menu button
            binding.menuButton.isVisible = canManageMembers && !item.isCurrentUser
            binding.menuButton.setOnClickListener { view ->
                showMemberMenu(view, item)
            }

            // Click listener
            binding.root.setOnClickListener {
                listener.onMemberClick(item)
            }
        }

        private fun showMemberMenu(
            anchor: View,
            item: MemberItem,
        ) {
            val popup = PopupMenu(anchor.context, anchor)
            popup.menuInflater.inflate(R.menu.menu_member, popup.menu)

            // Show/hide menu items based on current role
            val promoteItem = popup.menu.findItem(R.id.action_promote)
            val demoteItem = popup.menu.findItem(R.id.action_demote)
            val removeItem = popup.menu.findItem(R.id.action_remove)

            when (item.member.permissionLevel) {
                PermissionLevel.SUPER_ADMIN -> {
                    promoteItem?.isVisible = false
                    demoteItem?.isVisible = false
                    removeItem?.isVisible = false
                }
                PermissionLevel.ADMIN -> {
                    promoteItem?.isVisible = false
                    demoteItem?.isVisible = true
                    removeItem?.isVisible = true
                }
                PermissionLevel.MEMBER -> {
                    promoteItem?.isVisible = true
                    demoteItem?.isVisible = false
                    removeItem?.isVisible = true
                }
            }

            popup.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.action_promote -> {
                        listener.onPromoteToAdmin(item)
                        true
                    }
                    R.id.action_demote -> {
                        listener.onDemoteFromAdmin(item)
                        true
                    }
                    R.id.action_remove -> {
                        listener.onRemoveMember(item)
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }
    }

    private class MemberDiffCallback(
        private val oldList: List<MemberItem>,
        private val newList: List<MemberItem>,
    ) : DiffUtil.Callback() {
        override fun getOldListSize(): Int = oldList.size

        override fun getNewListSize(): Int = newList.size

        override fun areItemsTheSame(
            oldPos: Int,
            newPos: Int,
        ): Boolean = oldList[oldPos].member.inboxId == newList[newPos].member.inboxId

        override fun areContentsTheSame(
            oldPos: Int,
            newPos: Int,
        ): Boolean = oldList[oldPos] == newList[newPos]
    }
}
