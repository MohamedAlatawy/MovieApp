package com.example.movieapp.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MovieMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        MovieUpdates.receive(applicationContext, message.data)
    }
}
