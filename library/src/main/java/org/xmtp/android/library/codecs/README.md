# Use content types in your app

When you build an app with XMTP, all messages are encoded with a [content type](https://xmtp.org/docs/dev-concepts/content-types) to ensure that an XMTP message API client knows how to encode and decode messages, ensuring interoperability and consistent display of messages across apps.

`xmtp-android` supports the following content types:

- `TextCodec`: This is the default content type and enables sending plain text messages.
- `AttachmentCodec`: Enables sending attachments.
- `RemoteAttachmentCodec`: Enables sending remote attachments.

## Use `AttachmentCodec` and `RemoteAttachmentCodec` to handle remote attachments

### Register your client to accept the codecs

```kotlin
        Client.register(codec = AttachmentCodec())
        Client.register(codec = RemoteAttachmentCodec())
```

### Create an attachment 

```kotlin
val attachment = Attachment(
filename = "test.txt",
mimeType = "text/plain",
data = "hello world".toByteStringUtf8(),
)
```

### Encode the attachment for transport

```kotlin
val encodedEncryptedContent = RemoteAttachment.encodeEncrypted(
    content = attachment,
    codec = AttachmentCodec(),
)
```

### Create a `RemoteAttachment` from the encoded content

```kotlin
val remoteAttachment = RemoteAttachment.from(
    url = URL("https://abcdefg"),
    encryptedEncodedContent = encodedEncryptedContent
)
remoteAttachment.contentLength = attachment.data.size()
remoteAttachment.filename = attachment.filename
```

### Send a message with the attachment and set the contentType

```kotlin
val newConversation = client.conversations.newConversation(walletAddress)

newConversation.send(
    content = remoteAttachment,
    options = SendOptions(contentType = ContentTypeRemoteAttachment),
)
```

### Load a remote attachment on the receiving end

```kotlin
val message = newConversation.messages().first()

val loadedRemoteAttachment: RemoteAttachment = messages.content()
loadedRemoteAttachment.fetcher = Fetcher()
runBlocking {
    val attachment: Attachment = loadedRemoteAttachment.load() 
}
```


You can create any content type you want to be used in your application you can read me about creating content types here: [LINK]
