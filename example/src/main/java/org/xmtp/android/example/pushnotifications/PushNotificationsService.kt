package org.xmtp.android.example.pushnotifications

import android.accounts.AccountManager
import android.app.PendingIntent
import android.content.Intent
import android.util.Base64
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.xmtp.android.example.ClientManager
import org.xmtp.android.example.R
import org.xmtp.android.example.conversation.ConversationDetailActivity
import org.xmtp.android.example.extension.truncatedAddress
import org.xmtp.android.library.messages.EnvelopeBuilder
import java.util.Date

class PushNotificationsService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "PushNotificationService"

        internal const val CHANNEL_ID = "xmtp_message"
    }

    /**
     * Note: This is only being called when the app is foregrounded because we are using FCM's
     * `notification` payload. Backgrounded notifications are sent directly to the system tray.
     * We receive the url to open on clicks in MainActivity's intent.
     *
     * https://firebase.google.com/docs/cloud-messaging/android/receive#handling_messages
     */
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "On message received.")

        val keysData = loadKeys()
        if (keysData == null) {
            Log.e(TAG, "Attempting to send push to a logged out user.")
            return
        }

        val encryptedMessage = remoteMessage.data["encryptedMessage"]
        val topic = remoteMessage.data["topic"]
        val encryptedMessageData = Base64.decode(encryptedMessage, Base64.NO_WRAP)
        if (encryptedMessage == null || topic == null || encryptedMessageData == null) {
            Log.e(TAG, "Did not get correct message data from push")
            return
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, ConversationDetailActivity::class.java).apply { putExtra("topic", topic) },
            (PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        )

        GlobalScope.launch(Dispatchers.Main) {
            ClientManager.createClient(keysData)
        }
        val conversation = ClientManager.client.fetchConversation(topic)
        if (conversation == null) {
            Log.e(TAG, "No keys or conversation persisted")
            return
        }
        val envelope = EnvelopeBuilder.buildFromString(topic, Date(), encryptedMessageData)

        val decodedMessage = conversation.decode(envelope)
        val body = decodedMessage.body
        val title = conversation.peerAddress.truncatedAddress()

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_xmtp_white)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setColor(ContextCompat.getColor(this, R.color.black))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pendingIntent)

        // Use the URL as the ID for now until one is passed back from the server.
        NotificationManagerCompat.from(this).apply {
            notify(topic.hashCode(), builder.build())
        }
    }

    private fun loadKeys(): String? {
        val accountManager = AccountManager.get(this)
        val accounts = accountManager.getAccountsByType(resources.getString(R.string.account_type))
        val account = accounts.firstOrNull() ?: return null
        return accountManager.getPassword(account)
    }
}
