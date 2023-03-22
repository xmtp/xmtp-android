# Enable your app to send push notifications

You can use the `xmtp-android` SDK and a notification server to enable your app to send push notifications. This tutorial describes the process using Firebase Cloud Messaging and an example notification server written in Golang.

## Set up a Firebase Cloud Messaging project

For this tutorial, we'll use [Firebase Cloud Messaging](https://console.firebase.google.com/) (FCM) as a convenient way to set up a messaging server.

1. Create an FCM project.

2. Add an app to your FCM project. This generates a `google-services.json` file that you need in subsequent steps.

3. Add the `google-services.json` file to your Android project as described in the FCM project creation process.

4. Generate your FCM credentials, which you need to run your notification server. To do this, from your FCM dashboard, click the gear icon next to **Project Overview** and select **Project settings**. Select **Service accounts**. Select **Go** and click **Generate new private key**. 

## Run an example notification server

Now that you have your FCM server set up, take a look at the [export-kotlin-proto-code](https://github.com/xmtp/example-notification-server-go/tree/np/export-kotlin-proto-code) branch in the `example-notifications-server-go` repo. 

This example branch can serve as the basis for what you might want to provide for your own notification server. The branch also demonstrates how to generate the proto code.

Here are the steps to run a notification server based on the example branch:

1. Clone the [example-notification-server-go](https://github.com/xmtp/example-notification-server-go) repo.

2. Complete the steps in [Local Setup](https://github.com/xmtp/example-notification-server-go/blob/np/export-kotlin-proto-code/README.md#local-setup).

3. Get the FCM project ID and `google-services.json` file you created earlier and run:

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

4. You should now be able to see push notifications coming across your local network.

5. Copy the contents of `google-services.json` into the file in the example folder.

6. Uncomment `id 'com.google.gms.google-services'` in the example `build.gradle`.

7. Uncomment the following code in the top level of `build.gradle`:

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

8. Sync the gradle project.

9. Add your notification server address to `MainActivity`. In this case, it should be `PushNotificationTokenManager.init(this, "10.0.2.2:8080")`.

10. Change the environment to `XMTPEnvironment.PRODUCTION`.

11. The proto code from the example notification server has already been generated in the SDK.

12. Set up your app to register the FCM token with the network and then subscribe each conversation to push notifications. For example:

    ```kotlin
    XMTPPush(context, "10.0.2.2:8080").register(token)
    ```

    ```kotlin
    XMTPPush(context, "10.0.2.2:8080").subscribe(conversations.map { it.topic })
    ```
