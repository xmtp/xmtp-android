package org.xmtp.android.example.conversation

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.launch
import org.xmtp.android.example.R
import org.xmtp.android.example.databinding.ActivityGroupManagementBinding
import org.xmtp.android.example.extension.truncatedAddress
import org.xmtp.android.library.libxmtp.PermissionLevel
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.regex.Pattern
import kotlin.math.abs

class GroupManagementActivity :
    AppCompatActivity(),
    MemberClickListener {
    private lateinit var binding: ActivityGroupManagementBinding
    private lateinit var memberAdapter: MemberAdapter

    private val viewModel: GroupManagementViewModel by viewModels()

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
        const val EXTRA_CONVERSATION_TOPIC = "EXTRA_CONVERSATION_TOPIC"
        private val ADDRESS_PATTERN = Pattern.compile("^0x[a-fA-F0-9]{40}$")

        fun intent(
            context: Context,
            topic: String,
        ): Intent =
            Intent(context, GroupManagementActivity::class.java).apply {
                putExtra(EXTRA_CONVERSATION_TOPIC, topic)
            }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.setConversationTopic(intent.extras?.getString(EXTRA_CONVERSATION_TOPIC))

        binding = ActivityGroupManagementBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        memberAdapter = MemberAdapter(this, canManageMembers = false)
        binding.membersList.layoutManager = LinearLayoutManager(this)
        binding.membersList.adapter = memberAdapter

        binding.addMemberButton.setOnClickListener {
            showAddMemberDialog()
        }

        binding.leaveGroupButton.setOnClickListener {
            showLeaveGroupConfirmation()
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::ensureUiState)
            }
        }

        viewModel.loadGroupInfo()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }

    private fun ensureUiState(uiState: GroupManagementViewModel.UiState) {
        when (uiState) {
            is GroupManagementViewModel.UiState.Loading -> {
                binding.progress.visibility = View.VISIBLE
            }
            is GroupManagementViewModel.UiState.Success -> {
                binding.progress.visibility = View.GONE

                // Set group avatar
                val colorIndex = abs(uiState.groupId.hashCode()) % avatarColors.size
                binding.groupAvatarCard.setCardBackgroundColor(avatarColors[colorIndex])
                binding.groupAvatarText.text = "G"

                // Set group ID
                binding.groupId.text = uiState.groupId.truncatedAddress()

                // Set created date
                uiState.createdAt?.let { date ->
                    val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                    binding.createdAt.text = dateFormat.format(date)
                }

                // Set member count
                binding.membersCount.text = getString(R.string.members_count_value, uiState.members.size)

                // Set current user role
                binding.yourRole.text =
                    when (uiState.currentUserRole) {
                        PermissionLevel.SUPER_ADMIN -> getString(R.string.role_super_admin)
                        PermissionLevel.ADMIN -> getString(R.string.role_admin)
                        PermissionLevel.MEMBER -> getString(R.string.role_member)
                    }

                // Show/hide add member button
                binding.addMemberButton.visibility =
                    if (uiState.canManageMembers) View.VISIBLE else View.GONE

                // Update adapter with new data and permissions
                memberAdapter = MemberAdapter(this, uiState.canManageMembers)
                binding.membersList.adapter = memberAdapter
                memberAdapter.setData(uiState.members)
            }
            is GroupManagementViewModel.UiState.Error -> {
                binding.progress.visibility = View.GONE
                showError(uiState.message)
            }
        }
    }

    private fun showAddMemberDialog() {
        val input =
            EditText(this).apply {
                hint = getString(R.string.enter_wallet_address)
                setPadding(48, 32, 48, 32)
            }

        AlertDialog
            .Builder(this)
            .setTitle(R.string.add_member)
            .setView(input)
            .setPositiveButton(R.string.add) { _, _ ->
                val address = input.text.toString().trim()
                if (ADDRESS_PATTERN.matcher(address).matches()) {
                    addMember(address)
                } else {
                    showError(getString(R.string.invalid_address))
                }
            }.setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun addMember(address: String) {
        lifecycleScope.launch {
            viewModel.addMember(address).collect { state ->
                when (state) {
                    is GroupManagementViewModel.ActionState.Loading -> {
                        binding.progress.visibility = View.VISIBLE
                    }
                    is GroupManagementViewModel.ActionState.Success -> {
                        binding.progress.visibility = View.GONE
                        Toast.makeText(this@GroupManagementActivity, state.message, Toast.LENGTH_SHORT).show()
                    }
                    is GroupManagementViewModel.ActionState.Error -> {
                        binding.progress.visibility = View.GONE
                        showError(state.message)
                    }
                }
            }
        }
    }

    private fun showLeaveGroupConfirmation() {
        AlertDialog
            .Builder(this)
            .setTitle(R.string.leave_group)
            .setMessage(R.string.leave_group_confirmation)
            .setPositiveButton(R.string.leave) { _, _ ->
                // TODO: Implement leave group when API supports it
                Toast.makeText(this, "Leave group not yet implemented", Toast.LENGTH_SHORT).show()
            }.setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onMemberClick(member: MemberItem) {
        // Could navigate to user profile
        member.displayAddress?.let { address ->
            startActivity(UserProfileActivity.intent(this, address, member.member.inboxId))
        }
    }

    override fun onPromoteToAdmin(member: MemberItem) {
        AlertDialog
            .Builder(this)
            .setTitle(R.string.promote_to_admin)
            .setMessage(
                getString(
                    R.string.promote_confirmation,
                    member.displayAddress?.truncatedAddress() ?: member.member.inboxId.truncatedAddress(),
                ),
            ).setPositiveButton(R.string.promote) { _, _ ->
                promoteToAdmin(member.member.inboxId)
            }.setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onDemoteFromAdmin(member: MemberItem) {
        AlertDialog
            .Builder(this)
            .setTitle(R.string.demote_from_admin)
            .setMessage(
                getString(
                    R.string.demote_confirmation,
                    member.displayAddress?.truncatedAddress() ?: member.member.inboxId.truncatedAddress(),
                ),
            ).setPositiveButton(R.string.demote) { _, _ ->
                demoteFromAdmin(member.member.inboxId)
            }.setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onRemoveMember(member: MemberItem) {
        AlertDialog
            .Builder(this)
            .setTitle(R.string.remove_member)
            .setMessage(
                getString(
                    R.string.remove_confirmation,
                    member.displayAddress?.truncatedAddress() ?: member.member.inboxId.truncatedAddress(),
                ),
            ).setPositiveButton(R.string.remove) { _, _ ->
                removeMember(member.member.inboxId)
            }.setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun promoteToAdmin(inboxId: String) {
        lifecycleScope.launch {
            viewModel.promoteToAdmin(inboxId).collect { state ->
                handleActionState(state)
            }
        }
    }

    private fun demoteFromAdmin(inboxId: String) {
        lifecycleScope.launch {
            viewModel.demoteFromAdmin(inboxId).collect { state ->
                handleActionState(state)
            }
        }
    }

    private fun removeMember(inboxId: String) {
        lifecycleScope.launch {
            viewModel.removeMember(inboxId).collect { state ->
                handleActionState(state)
            }
        }
    }

    private fun handleActionState(state: GroupManagementViewModel.ActionState) {
        when (state) {
            is GroupManagementViewModel.ActionState.Loading -> {
                binding.progress.visibility = View.VISIBLE
            }
            is GroupManagementViewModel.ActionState.Success -> {
                binding.progress.visibility = View.GONE
                Toast.makeText(this, state.message, Toast.LENGTH_SHORT).show()
            }
            is GroupManagementViewModel.ActionState.Error -> {
                binding.progress.visibility = View.GONE
                showError(state.message)
            }
        }
    }

    private fun showError(message: String) {
        val error = message.ifBlank { getString(R.string.error) }
        Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
    }
}
