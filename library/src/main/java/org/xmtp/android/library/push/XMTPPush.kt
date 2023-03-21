package org.xmtp.android.library.push

import io.grpc.Grpc
import io.grpc.ManagedChannel
import io.grpc.TlsChannelCredentials
import org.xmtp.android.library.XMTPException
import org.xmtp.android.library.push.Service.DeliveryMechanism
import org.xmtp.android.library.push.Service.RegisterInstallationRequest
import org.xmtp.android.library.push.Service.SubscribeRequest
import java.util.UUID

class XMTPPush() {
    lateinit var installationId: String
    lateinit var context: android.content.Context
    var pushServer: String = ""

    constructor(
        context: android.content.Context,
        pushServer: String = "",
    ) : this() {
        this.context = context
        val id = PushPreferences.getInstallationId(context)
        if (id.isNullOrBlank()) {
            installationId = UUID.randomUUID().toString()
            PushPreferences.setInstallationId(context, installationId)
        } else {
            this.installationId = id
        }
        this.pushServer = pushServer
    }

    suspend fun register(token: String) {
        if (pushServer == "") {
            throw XMTPException("No push server")
        }

        val request = RegisterInstallationRequest.newBuilder().also { request ->
            request.installationId = installationId
            request.deliveryMechanismBuilder.apply {
                clearDeliveryMechanismType()
                firebaseDeviceToken = token
            }
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
