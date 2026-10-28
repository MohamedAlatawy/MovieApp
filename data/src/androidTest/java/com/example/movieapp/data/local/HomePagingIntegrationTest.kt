package com.example.movieapp.data.local

import androidx.paging.*
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.movieapp.data.paging.HomeRemoteMediator
import com.example.movieapp.data.remote.MoviePageDto
import com.example.movieapp.data.remote.MovieSummaryDto
import com.example.movieapp.data.remote.TmdbException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalPagingApi::class)
class HomePagingIntegrationTest {
    @Test fun emptyPagesAppendErrorRetryAndDuplicateBoundaryProgressWithRealRoom() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext, MovieDatabase::class.java,
        ).build()
        val calls = java.util.Collections.synchronizedList(mutableListOf<Int>())
        val releaseSecond = CompletableDeferred<Unit>()
        var failThird = true
        val sources = InvalidatingPagingSourceFactory(db.movieDao()::pagingSource)
        val mediator = HomeRemoteMediator({ page ->
            calls += page
            if (page == 2) releaseSecond.await()
            if (page == 3 && failThird) throw TmdbException.Http(503)
            val rows = when (page) {
                3, 4 -> listOf(MovieSummaryDto(7, "Cached seven"))
                5 -> listOf(MovieSummaryDto(8, "Final eight"))
                else -> emptyList()
            }
            MoviePageDto(page, 5, 2, rows)
        }, db.movieDao(), sources::invalidate)
        val presenter = object : PagingDataPresenter<MovieEntity>(Dispatchers.Main) {
            override suspend fun presentPagingDataEvent(event: PagingDataEvent<MovieEntity>) = Unit
        }
        val job = launch(Dispatchers.Main) {
            Pager(PagingConfig(20, enablePlaceholders = true), remoteMediator = mediator,
                pagingSourceFactory = sources).flow.collectLatest { presenter.collectFrom(it) }
        }
        suspend fun waitFor(condition: () -> Boolean) = withTimeout(8_000) {
            try { while (!withContext(Dispatchers.Main) { condition() }) delay(20) } catch (error: TimeoutCancellationException) { throw AssertionError("calls=$calls states=${presenter.loadStateFlow.value}", error) }
        }
        try {
            waitFor { calls.contains(2) && presenter.loadStateFlow.value?.mediator?.append is LoadState.Loading }
            assertTrue(presenter.snapshot().items.isEmpty())
            releaseSecond.complete(Unit)
            waitFor { presenter.loadStateFlow.value?.mediator?.append is LoadState.Error }
            assertEquals(listOf(1, 2, 3), calls.toList())
            assertTrue(presenter.snapshot().items.isEmpty())
            failThird = false
            withContext(Dispatchers.Main) { presenter.retry() }
            waitFor {
                if (presenter.size > 0) presenter[presenter.size - 1]
                presenter.snapshot().items.map { it.id } == listOf(7, 8) &&
                    presenter.loadStateFlow.value?.mediator?.append == LoadState.NotLoading(true)
            }
            assertEquals(listOf(1, 2, 3, 3, 4, 5), calls.toList())
            assertEquals(listOf(0L, 1L), presenter.snapshot().items.map { it.discoveryOrder })
        } finally {
            releaseSecond.complete(Unit)
            job.cancelAndJoin()
            db.close()
        }
    }
}