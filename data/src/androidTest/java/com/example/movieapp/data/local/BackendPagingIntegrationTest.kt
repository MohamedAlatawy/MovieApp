package com.example.movieapp.data.local

import androidx.paging.*
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.movieapp.data.backend.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalPagingApi::class)
class BackendPagingIntegrationTest {
    @Test fun realRoomEmptyDuplicatePagesAppendFailureAndRetry() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,
            DemoDatabase::class.java).build()
        val calls = java.util.Collections.synchronizedList(mutableListOf<Int>())
        val releaseSecond = CompletableDeferred<Unit>()
        var failThird = true
        val api = object : BackendApi {
            override suspend fun movie(id: Int): BackendMovieDto = error("No per-movie requests")
            override suspend fun search(query: String,page: Int,size: Int): BackendPage = error("No search")
            override suspend fun movies(page: Int,size: Int): BackendPage {
                calls += page
                if (page == 2) releaseSecond.await()
                if (page == 3 && failThird) throw java.io.IOException()
                val rows = when (page) {
                    3,4 -> listOf(seedMovie(7))
                    5 -> listOf(seedMovie(8))
                    else -> emptyList()
                }
                return BackendPage(rows,page,20,81,5,page < 5)
            }
        }
        val sources = InvalidatingPagingSourceFactory { db.movies().pagingSource() }
        val mediator = BackendRemoteMediator(api,db.movies(),sources::invalidate)
        val presenter = object : PagingDataPresenter<DemoMovie>(Dispatchers.Main) {
            override suspend fun presentPagingDataEvent(event: PagingDataEvent<DemoMovie>) = Unit
        }
        val job = launch(Dispatchers.Main) {
            Pager(PagingConfig(20,enablePlaceholders=true),remoteMediator=mediator,pagingSourceFactory=sources)
                .flow.collectLatest { presenter.collectFrom(it) }
        }
        suspend fun waitFor(condition: () -> Boolean) {
            try { withTimeout(6_000) { while (!withContext(Dispatchers.Main) { condition() }) delay(20) } }
            catch (error: TimeoutCancellationException) { throw AssertionError("calls=$calls states=${presenter.loadStateFlow.value}",error) }
        }
        try {
            waitFor { calls.contains(2) && presenter.loadStateFlow.value?.mediator?.append is LoadState.Loading }
            assertTrue(presenter.snapshot().items.isEmpty())
            releaseSecond.complete(Unit)
            waitFor { presenter.loadStateFlow.value?.mediator?.append is LoadState.Error }
            assertEquals(listOf(1,2,3),calls.toList())
            failThird = false
            withContext(Dispatchers.Main) { presenter.retry() }
            waitFor {
                if (presenter.size > 0) presenter[presenter.size-1]
                presenter.snapshot().items.map { it.id } == listOf(7,8) &&
                    presenter.loadStateFlow.value?.mediator?.append == LoadState.NotLoading(true)
            }
            assertEquals(listOf(1,2,3,3,4,5),calls.toList())
        } finally { releaseSecond.complete(Unit); job.cancelAndJoin(); db.close() }
    }

    private fun seedMovie(id: Int) = BackendMovieDto(id,"Movie $id","Demo","2020-01-01",null,7.0,
        listOf("Family"),false,null,"2026-01-01T00:00:00Z","synthetic-demo","unverified",true)
}
