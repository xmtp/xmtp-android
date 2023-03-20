package org.xmtp.android.library.push

import io.grpc.Grpc
import io.grpc.ManagedChannel
import io.grpc.TlsChannelCredentials
import org.xmtp.android.library.XMTPException
import org.xmtp.android.library.push.Service.DeliveryMechanism
import org.xmtp.android.library.push.Service.RegisterInstallationRequest
import org.xmtp.android.library.push.Service.SubscribeRequest


data class XMTPPush(
    var installationId: String,
    var installationIdKey: String = "installationId",
    var pushServer: String = "",
) {
    companion object {
        public var shared = XMTPPush()
    }

    init {
//        val id = UserDefaults.standard.string(forKey = installationIdKey)
//        if (id != null) {
//            installationId = id
//        } else {
//            installationId = UUID.randomUUID().toString()
//            UserDefaults.standard.set(installationId, forKey = installationIdKey)
//        }
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
