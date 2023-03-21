package org.xmtp.android.example.pushnotifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.UiThread
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.xmtp.android.example.ClientManager
import org.xmtp.android.library.Client
import org.xmtp.android.library.messages.PrivateKeyBundleV1Builder
import org.xmtp.android.library.push.XMTPPush

object PushNotificationTokenManager {

    private const val TAG = "PushTokenManager"
    private lateinit var applicationContext: Context

    private val _xmtpPushState = MutableStateFlow<XMTPPushState>(XMTPPushState.Unknown)
    val xmtpPushState: StateFlow<XMTPPushState> = _xmtpPushState

    private var _xmtpPush: XMTPPush? = null

    val xmtpPush: XMTPPush
        get() = if (xmtpPushState.value == XMTPPushState.Ready) {
            _xmtpPush!!
        } else {
            throw IllegalStateException("Client called before Ready state")
        }

    fun init(applicationContext: Context, pushServer: String) {
        this.applicationContext = applicationContext
        createXMTPPush(pushServer)
    }

    fun ensurePushTokenIsConfigured() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener(OnCompleteListener {
            if (!it.isSuccessful) {
                Log.e(TAG, "Firebase getInstanceId() failed", it.exception)
                return@OnCompleteListener
            }
            it.result?.let {
                runBlocking { xmtpPush.register(it) }
                configureNotificationChannels()
            }
        })
    }

    internal fun syncPushNotificationsToken(token: String) {
        runBlocking { xmtpPush.register(token) }
    }

    private fun configureNotificationChannels() {
        val channel = NotificationChannel(
            PushNotificationsService.CHANNEL_ID,
            "XMTP",
            NotificationManager.IMPORTANCE_DEFAULT
        )

        val notificationManager = applicationContext.getSystemService(
            FirebaseMessagingService.NOTIFICATION_SERVICE
        ) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    @UiThread
    fun createXMTPPush(pushServer: String) {
        if (xmtpPushState.value is XMTPPushState.Ready) return
        GlobalScope.launch(Dispatchers.IO) {
            try {
                _xmtpPush = XMTPPush(applicationContext, pushServer)
                _xmtpPushState.value = XMTPPushState.Ready
            } catch (e: Exception) {
                _xmtpPushState.value = XMTPPushState.Error(e.localizedMessage.orEmpty())
            }
        }
    }

    @UiThread
    fun clearXMTPPush() {
        _xmtpPushState.value = XMTPPushState.Unknown
        _xmtpPush = null
    }

    sealed class XMTPPushState {
        object Unknown : XMTPPushState()
        object Ready : XMTPPushState()
        data class Error(val message: String) : XMTPPushState()
    }
}
