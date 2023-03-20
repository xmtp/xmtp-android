package org.xmtp.android.library.push

import io.grpc.Context
import io.grpc.Grpc
import io.grpc.ManagedChannel
import io.grpc.TlsChannelCredentials
import org.xmtp.android.library.ApiClient
import org.xmtp.android.library.Contacts
import org.xmtp.android.library.Conversations
import org.xmtp.android.library.XMTPException
import org.xmtp.android.library.push.Service.DeliveryMechanism
import org.xmtp.android.library.push.Service.RegisterInstallationRequest
import org.xmtp.android.library.push.Service.SubscribeRequest
import java.util.UUID

class XMTPPush() {
    lateinit var installationId: String
    lateinit var context: android.content.Context
    var installationIdKey: String = "installationId"
    var pushServer: String = ""

    companion object {
        var shared = XMTPPush()
    }

    constructor(
        context: android.content.Context,
        installationIdKey: String = "installationId",
        pushServer: String = "",
    ) : this() {
        this.context = context
        val id = PushPreferences.getInstallationId(context)
        if (id != null) {
            this.installationId = id
        } else {
            installationId = UUID.randomUUID().toString()
            PushPreferences.setInstallationId(context, installationId)
        }
        this.installationIdKey = installationIdKey
        this.pushServer = pushServer
    }

    fun request(): Boolean {
        if (pushServer == "") {
            throw XMTPException("No push server")
        }
//        if (UNUserNotificationCenter.current()
//                .requestAuthorization(options = listOf(. alert, . badge
//                ))) {
//            UIApplication.shared.registerForRemoteNotifications()
//            return true
//        }
        return false
    }

    suspend fun register(token: String) {
        if (pushServer == "") {
            throw XMTPException("No push server")
        }

        val request = RegisterInstallationRequest.newBuilder().also { request ->
            request.installationId = installationId
            request.deliveryMechanism = DeliveryMechanism.newBuilder().also { delivery ->
                delivery.firebaseDeviceToken = token
            }.build()
        }.build()
        client.registerInstallation(request = request)
    }

    suspend fun subscribe(topics: List<String>) {
        if (pushServer == "") {
            throw XMTPException("No push server")
        }
        val request = SubscribeRequest.newBuilder().also { request ->
            request.installationId = installationId
            request.clearTopics()
            request.addAllTopics(topics)
        }.build()
        client.subscribe(request = request)
    }

    val client: NotificationsGrpcKt.NotificationsCoroutineStub
        get() {
            val protocolClient: ManagedChannel =
                Grpc.newChannelBuilder(
                    pushServer, TlsChannelCredentials.create(),
                ).build()
            return NotificationsGrpcKt.NotificationsCoroutineStub(channel = protocolClient)
        }
}
