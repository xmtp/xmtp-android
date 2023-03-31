# Adding specific content types to your application

You can create any content type you want to be used in your application you can read me about creating content types here: [LINK]

We have a few content types backed into the sdk directly for easy consumption (encryption and decryption) across applications.

TextCodec - (always registered by default) allows the passing of plain text.
AttachmentCodec - allows the passing of attachments
RemoteAttachmentCodec - allows the passing of remote attachments

## RemoteAttachment Example

- Register your client to accept the codecs that you want

```kotlin
        Client.register(codec = AttachmentCodec())
        Client.register(codec = RemoteAttachmentCodec())
```

- Create an Attachment 

```kotlin
val attachment = Attachment(
filename = "test.txt",
mimeType = "text/plain",
data = "hello world".toByteStringUtf8(),
)
```

- Encode the attachment for transport

```kotlin
val encodedEncryptedContent = RemoteAttachment.encodeEncrypted(
    content = attachment,
    codec = AttachmentCodec(),
)
```

- Create a RemoteAttachment from the encoded content

```kotlin
val remoteAttachment = RemoteAttachment.from(
    url = URL("https://abcdefg"),
    encryptedEncodedContent = encodedEncryptedContent
)
remoteAttachment.contentLength = attachment.data.size()
remoteAttachment.filename = attachment.filename
```

- Send a message with the attachment and set the contentType

```kotlin
val newConversation = client.conversations.newConversation(walletAddress)

newConversation.send(
    content = remoteAttachment,
    options = SendOptions(contentType = ContentTypeRemoteAttachment),
)
```

- Load a remote attachment on the receiving end

```kotlin
val message = newConversation.messages().first()

val loadedRemoteAttachment: RemoteAttachment = messages.content()
loadedRemoteAttachment.fetcher = Fetcher()
runBlocking {
    val attachment: Attachment = loadedRemoteAttachment.load() 
}
```