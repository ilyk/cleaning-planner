package com.ilyk.cleaningplanner.fcm

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class CleaningPlannerMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        // TODO: Handle incoming messages
        // Create notifications for task assignments, due soon, etc.
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // TODO: Send token to backend
    }
}

