package org.xmtp.android.example.pushnotifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService

object PushNotificationTokenManager {

    private const val TAG = "PushTokenManager"
    private lateinit var applicationContext: Context

    fun init(applicationContext: Context) {
        this.applicationContext = applicationContext
    }

    fun ensurePushTokenIsConfigured() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener(OnCompleteListener {
            if (!it.isSuccessful) {
                Log.e(TAG, "Firebase getInstanceId() failed", it.exception)
                return@OnCompleteListener
            }
            it.result?.let {
                // put a breakpoint here to get the token so you can trigger notifications from curl
                val token = it
                configureNotificationChannels()
            }
        })
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
}
