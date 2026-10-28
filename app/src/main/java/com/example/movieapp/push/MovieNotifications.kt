package com.example.movieapp.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.movieapp.MainActivity
import com.example.movieapp.R

/** Local presentation of a validated hint, never a claim that cache synchronization succeeded. */
internal object MovieNotifications {
    const val CHANNEL = "movie_updates"
    private const val ACTION = "com.example.movieapp.OPEN_UPDATED_MOVIE"
    private const val MOVIE_ID = "updated_movie_id"

    fun intent(context: Context, id: Int): Intent {
        require(id > 0)
        return Intent(context, MainActivity::class.java).setAction(ACTION)
            .putExtra(MOVIE_ID, id)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }

    fun movieId(intent: Intent?): Int? = if (intent?.action == ACTION)
        intent.getIntExtra(MOVIE_ID, 0).takeIf { it > 0 } else null

    fun build(context: Context, id: Int) = NotificationCompat.Builder(context, CHANNEL)
        .setSmallIcon(R.drawable.ic_movie_notification)
        .setContentTitle("Movie updated")
        .setContentText("Movie details have been updated")
        .setAutoCancel(true)
        .setOnlyAlertOnce(true)
        .setContentIntent(PendingIntent.getActivity(context, id, intent(context, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        .build()

    fun post(context: Context, id: Int) {
        require(id > 0)
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "Movie updates", NotificationManager.IMPORTANCE_DEFAULT))
            if (manager.getNotificationChannel(CHANNEL)?.importance == NotificationManager.IMPORTANCE_NONE) return
        }
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        if (!manager.areNotificationsEnabled()) return
        // Same movie replaces its existing notification; different IDs retain independent tap targets.
        manager.notify(id, build(context, id))
    }
}
