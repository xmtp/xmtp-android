package org.xmtp.android.library

import org.xmtp.proto.message.contents.PrivateKeyOuterClass


data class ClientOptions(val api: Api = Api()) {
    data class Api(val env: XMTPEnvironment = XMTPEnvironment.DEV, val isSecure: Boolean = true)
}

class Client {
    lateinit var address: String
    lateinit var privateKeyBundleV1: PrivateKeyOuterClass.PrivateKeyBundleV1
    lateinit var apiClient: ApiClient

    val conversations: Conversations by lazy.init(client = this)
        val contacts : Contacts = Contacts(client = this)
    val environment: XMTPEnvironment = apiClient.environment

    fun create(account: SigningKey, options: ClientOptions? = null): Client {
        val clientOptions = options ?: ClientOptions()
        val apiClient = GRPCApiClient(environment = clientOptions.api.env, secure = clientOptions.api.isSecure)
        return create(account = account, apiClient = apiClient)
    }

    fun create(account: SigningKey, apiClient: ApiClient): Client {
        val privateKeyBundleV1 = await
        loadOrCreateKeys(for = account, apiClient = apiClient)
        val client = Client(
            address = account.address,
            privateKeyBundleV1 = privateKeyBundleV1,
            apiClient = apiClient
        )
        client.ensureUserContactPublished()
        return client
    }


    fun publishUserContact(legacy: Boolean = false) {
        var envelopes: List<Envelope> = listOf()
        if (legacy) {
            var contactBundle = ContactBundle()
            contactBundle.v1.keyBundle = privateKeyBundleV1.toPublicKeyBundle()
            var envelope = Envelope()
            envelope.contentTopic = Topic.contact(address).description
            envelope.timestampNs = UInt64(Date().millisecondsSinceEpoch * 1_000_000)
            envelope.message = contactBundle.serializedData()
            envelopes.append(envelope)
        }
        var contactBundle = ContactBundle()
        contactBundle.v2.keyBundle = keys.getPublicKeyBundle()
        contactBundle.v2.keyBundle.identityKey.signature.ensureWalletSignature()
        var envelope = Envelope()
        envelope.contentTopic = Topic.contact(address).description
        envelope.timestampNs = UInt64(Date().millisecondsSinceEpoch * 1_000_000)
        envelope.message = contactBundle.serializedData()
        envelopes.append(envelope)
        await
        publish(envelopes = envelopes)
    }

    fun getUserContact(peerAddress: String): ContactBundle? {
        return await
        contacts.find(peerAddress)
    }

}