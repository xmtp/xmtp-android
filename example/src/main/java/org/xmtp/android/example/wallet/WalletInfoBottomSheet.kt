package org.xmtp.android.example.wallet

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import org.xmtp.android.example.ClientManager
import org.xmtp.android.example.R

class WalletInfoBottomSheet : BottomSheetDialogFragment() {
    interface WalletInfoListener {
        fun onLogsToggled(enabled: Boolean)

        fun onDisconnectClicked()

        fun isLogsEnabled(): Boolean
    }

    private var listener: WalletInfoListener? = null

    companion object {
        const val TAG = "WalletInfoBottomSheet"

        fun newInstance(): WalletInfoBottomSheet = WalletInfoBottomSheet()
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        listener = context as? WalletInfoListener
    }

    override fun onDetach() {
        super.onDetach()
        listener = null
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? = inflater.inflate(R.layout.bottom_sheet_wallet_info, container, false)

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        val client = ClientManager.client

        // Populate wallet info
        view.findViewById<TextView>(R.id.walletAddressValue).text =
            client.publicIdentity.identifier
        view.findViewById<TextView>(R.id.inboxIdValue).text =
            client.inboxId
        view.findViewById<TextView>(R.id.installationIdValue).text =
            client.installationId
        view.findViewById<TextView>(R.id.environmentValue).text =
            client.environment.name
        view.findViewById<TextView>(R.id.libxmtpVersionValue).text =
            client.libXMTPVersion

        // Setup copy button click listeners
        view.findViewById<ImageButton>(R.id.copyWalletAddress).setOnClickListener {
            copyToClipboard(
                getString(R.string.wallet_address_label),
                client.publicIdentity.identifier,
            )
        }

        view.findViewById<ImageButton>(R.id.copyInboxId).setOnClickListener {
            copyToClipboard(getString(R.string.inbox_id_label), client.inboxId)
        }

        view.findViewById<ImageButton>(R.id.copyInstallationId).setOnClickListener {
            copyToClipboard(getString(R.string.installation_id_label), client.installationId)
        }

        view.findViewById<ImageButton>(R.id.copyEnvironment).setOnClickListener {
            copyToClipboard(getString(R.string.environment_label), client.environment.name)
        }

        view.findViewById<ImageButton>(R.id.copyLibxmtpVersion).setOnClickListener {
            copyToClipboard(getString(R.string.libxmtp_version_label), client.libXMTPVersion)
        }

        // Setup logs toggle
        val logsSwitch = view.findViewById<SwitchMaterial>(R.id.logsSwitch)
        logsSwitch.isChecked = listener?.isLogsEnabled() ?: false
        logsSwitch.setOnCheckedChangeListener { _, isChecked ->
            listener?.onLogsToggled(isChecked)
        }

        // Setup disconnect button
        view.findViewById<MaterialButton>(R.id.disconnectButton).setOnClickListener {
            dismiss()
            listener?.onDisconnectClicked()
        }
    }

    private fun copyToClipboard(
        label: String,
        value: String,
    ) {
        val clipboard =
            requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, value)
        clipboard.setPrimaryClip(clip)
        Toast
            .makeText(requireContext(), getString(R.string.copied_to_clipboard, label), Toast.LENGTH_SHORT)
            .show()
    }
}
