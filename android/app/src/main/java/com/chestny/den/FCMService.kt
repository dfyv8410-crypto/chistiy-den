package com.chestny.den

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class FCMService : FirebaseMessagingService() {
    companion object {
        private const val TAG = "FCMService"
    }

    override fun onNewToken(token: String) {
        Log.d(TAG, "New FCM token: $token")
    }

    override fun onMessageReceived(message: RemoteMessage) {
        Log.d(TAG, "Message received: ${message.from}")
        val title = message.notification?.title ?: message.data["title"] ?: "Чистый день"
        val body = message.notification?.body ?: message.data["body"] ?: ""
        NotificationHelper.showNotification(this, title, body)
    }
}
