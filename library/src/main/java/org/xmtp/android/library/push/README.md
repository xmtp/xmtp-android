# Adding Push notifications to your application

First you'll want to setup a server to get a FCM project id and credentials. The easiest way to get setup quickly is [Firebase](https://console.firebase.google.com/). This will prompt you to add a `google-services.json` file to your project which will include the FCM project ID and from the firebase dashboard under the `Service accounts` you will be able to generate the FCM credentials.

After you have Firebase setup you'll want to look at the code written in the [example-notifications-server-go](https://github.com/xmtp/example-notification-server-go/tree/np/export-kotlin-proto-code) this will serve as the basis for what you might want to add in your own backend. This branch also shows how to generate the proto code. If you'd like to see how it works in the example keep reading below.

### Running Example Notification Server

- Clone the repository https://github.com/xmtp/example-notification-server-go
- Follow the steps for local setup https://github.com/xmtp/example-notification-server-go/blob/np/export-kotlin-proto-code/README.md#local-setup
- Get your FCM project id and json file from step 1 above and run
```bash
dev/run \                                                                                                                                                                                                                              ✘ 1
  --xmtp-listener-tls \
  --xmtp-listener \
  --api \
  -x "production.xmtp.network:5556" \
  -d "postgres://postgres:xmtp@localhost:25432/postgres?sslmode=disable" \
  --fcm-enabled \
  --fcm-credentials-json=YOURFCMJSON \
  --fcm-project-id="YOURFCMPROJECTID"
  ```
***Now you should be able to see pushes coming across the network locally***
- Get the `google-services.json` file from Firebase and copy the contents into the file in the example folder
- Uncomment `id 'com.google.gms.google-services'` in the example `build.gradle`
- Uncomment in the top level `build.gradle`
```
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath 'com.google.gms:google-services:4.3.15'
    }
}
```
- Sync the gradle project
- You'll need to add your push server address in `MainActivity` in this case it should be `PushNotificationTokenManager.init(this, "10.0.2.2:8080")`
- You'll also need to change the environment `XMTPEnvironment.PRODUCTION`
- The proto code from the example notification server has already been generated in the SDK
- The final piece is setting up your app to register the FCM token with the network and then subscribing each conversation to push notifications.

```kotlin
XMTPPush(context, "10.0.2.2:8080").register(token)
```

```kotlin
XMTPPush(context, "10.0.2.2:8080").subscribe(conversations.map { it.topic })
```



