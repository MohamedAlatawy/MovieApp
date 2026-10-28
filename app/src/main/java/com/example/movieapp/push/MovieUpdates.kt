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
interface BackendWorkerEntryPoint { fun repository(): BackendRepository }

object MovieUpdates {
    fun receive(context: Context, data: Map<String, String>) {
        val id = MovieUpdateEvent.movieId(data) ?: return
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
            if (id > 0) repository.refreshMovie(id) else repository.reconcile()
            Result.success()
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (_: Exception) { if (runAttemptCount < 3) Result.retry() else Result.failure() }
    }
}
