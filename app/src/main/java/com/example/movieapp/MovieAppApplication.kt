package com.example.movieapp

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging

@HiltAndroidApp
class MovieAppApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (FirebaseApp.initializeApp(this) == null) {
            Log.i("MovieFirebase", "Client configuration missing; messaging disabled")
            return
        }
        FirebaseMessaging.getInstance().subscribeToTopic("movies")
            .addOnCompleteListener { result ->
                Log.i("MovieFirebase", if (result.isSuccessful) "Topic subscription succeeded" else "Topic subscription failed")
            }
    }
}
