package com.example.movieapp.push

import android.content.Context
import androidx.work.*
import com.example.movieapp.data.backend.BackendRepository
import com.example.movieapp.data.backend.MovieUpdateEvent
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

@EntryPoint
@InstallIn(SingletonComponent::class)
interface BackendWorkerEntryPoint {
    fun repository(): BackendRepository
    fun signal(): MovieUpdateSignal
}

object MovieUpdates {
    fun receive(context: Context, data: Map<String, String>) {
        val signal = EntryPointAccessors.fromApplication(context.applicationContext, BackendWorkerEntryPoint::class.java).signal()
        try {
            dispatch(data, signal, { id -> MovieNotifications.post(context, id) }) { id -> schedule(context, id) }
        } catch (_: RuntimeException) {
            // Scheduling failure must not crash the process or undo the independent UI hint.
            android.util.Log.w("MovieUpdates", "Unable to schedule cached movie refresh")
        }
    }

    internal fun dispatch(data: Map<String, String>, signal: MovieUpdateSignal,
        notify: (Int) -> Unit = {}, schedule: (Int) -> Unit) {
        val id = MovieUpdateEvent.movieId(data) ?: return
        // Notify active Details independently, before scheduling can fail or background work is delayed.
        signal.publish(id)
        try {
            schedule(id)
        } finally {
            // Notification permission/settings or posting failures must never gate either data path.
            try { notify(id) } catch (_: RuntimeException) { }
        }
    }

    private fun schedule(context: Context, id: Int) {
        val request = OneTimeWorkRequestBuilder<MovieRefreshWorker>()
            .setInputData(workDataOf("movieId" to id))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()
        WorkManager.getInstance(context).enqueueUniqueWork("backend-movie-$id", ExistingWorkPolicy.REPLACE, request)
    }
    fun reconcile(context: Context) {
        val request = OneTimeWorkRequestBuilder<MovieRefreshWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
        WorkManager.getInstance(context).enqueueUniqueWork("backend-foreground", ExistingWorkPolicy.KEEP, request)
    }
}

class MovieRefreshWorker(context: Context, parameters: WorkerParameters,
    private val repository: BackendRepository) : CoroutineWorker(context, parameters) {
    constructor(context: Context, parameters: WorkerParameters) : this(context, parameters,
        EntryPointAccessors.fromApplication(context.applicationContext, BackendWorkerEntryPoint::class.java).repository())
    override suspend fun doWork(): Result {
        return try {
            val id = inputData.getInt("movieId", 0)
            if (id > 0) repository.refreshHomeMovieDetails(id) else repository.reconcile()
            Result.success()
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (_: Exception) { if (runAttemptCount < 3) Result.retry() else Result.failure() }
    }
}
