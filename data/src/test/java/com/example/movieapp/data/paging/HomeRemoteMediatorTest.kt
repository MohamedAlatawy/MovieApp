package com.example.movieapp.data.paging

import com.example.movieapp.domain.model.MovieAccessException
import androidx.paging.*
import com.example.movieapp.data.local.MovieDao
import com.example.movieapp.data.local.MovieEntity
import com.example.movieapp.data.remote.TmdbClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalPagingApi::class)
class HomeRemoteMediatorTest {
    private val server = MockWebServer()
    private val dao = FakeDao()
    private lateinit var mediator: HomeRemoteMediator
    private val state = PagingState<Int, MovieEntity>(emptyList(), null, PagingConfig(20), 0)

    @Before fun setUp() {
        server.start()
        val client = TmdbClient.create("synthetic-token", server.url("/3/"))
        mediator = HomeRemoteMediator(client, dao)
    }
    @After fun tearDown() { server.shutdown() }

    @Test fun oneRequestPerPagePreservesOrderAndDoesNotAuditCandidates() = runBlocking {
        page(1, 3, 30, 10, 30, 20)
        assertFalse(success(load(LoadType.REFRESH)).endOfPaginationReached)
        assertEquals(listOf(30, 10, 20), dao.rows.value.map { it.id })
        assertEquals(1, server.requestCount)
        page(2, 3, 20)
        assertFalse(success(load(LoadType.APPEND)).endOfPaginationReached)
        assertEquals(2, server.requestCount)
        page(3, 3, 5)
        assertTrue(success(load(LoadType.APPEND)).endOfPaginationReached)
        assertEquals(listOf(30, 10, 20, 5), dao.rows.value.map { it.id })
        assertEquals(listOf(0L, 1L, 2L, 3L), dao.rows.value.map { it.discoveryOrder })
        assertEquals(listOf(1, 2, 3), requestedPages())
        page(1, 1, 5, 30, 50)
        load(LoadType.REFRESH)
        assertEquals(listOf(30, 10, 20, 5, 50), dao.rows.value.map { it.id })
    }

    @Test fun emptyAndShortPagesAdvanceOnlyByMetadata() = runBlocking {
        page(1, 4)
        assertFalse(success(load(LoadType.REFRESH)).endOfPaginationReached)
        page(2, 4)
        assertFalse(success(load(LoadType.APPEND)).endOfPaginationReached)
        page(3, 4, 3)
        assertFalse(success(load(LoadType.APPEND)).endOfPaginationReached)
        page(4, 4)
        assertTrue(success(load(LoadType.APPEND)).endOfPaginationReached)
        assertEquals(listOf(1, 2, 3, 4), requestedPages())
    }

    @Test fun failedAppendAndRefreshRetryTheSameRawPage() = runBlocking {
        page(1, 2, 1)
        load(LoadType.REFRESH)
        server.enqueue(MockResponse().setResponseCode(503))
        assertTrue(load(LoadType.APPEND) is RemoteMediator.MediatorResult.Error)
        assertEquals(listOf(1), dao.rows.value.map { it.id })
        page(2, 2, 2)
        load(LoadType.APPEND)
        server.enqueue(MockResponse().setResponseCode(503))
        assertTrue(load(LoadType.REFRESH) is RemoteMediator.MediatorResult.Error)
        page(1, 1, 3)
        load(LoadType.REFRESH)
        assertEquals(listOf(1, 2, 2, 1, 1), requestedPages())
        assertEquals(listOf(1, 2, 3), dao.rows.value.map { it.id })
    }

    @Test fun validatesWholePageBeforeWritesAndDoesNotAdvanceOnPersistenceFailure() = runBlocking {
        page(1, 2, 1, 0)
        assertTrue(load(LoadType.REFRESH) is RemoteMediator.MediatorResult.Error)
        assertTrue(dao.rows.value.isEmpty())
        page(1, 2, 1)
        dao.failSave = true
        assertTrue(load(LoadType.REFRESH) is RemoteMediator.MediatorResult.Error)
        dao.failSave = false
        page(1, 2, 1)
        load(LoadType.REFRESH)
        assertEquals(listOf(1, 1, 1), requestedPages())
    }

    @Test fun rejectsInvalidPageMetadataAndPreservesCache() = runBlocking {
        dao.upsert(movie(7))
        for (body in listOf(
            """{"page":2,"total_pages":2,"total_results":2,"results":[]}""",
            """{"page":1,"total_pages":-1,"total_results":0,"results":[]}""",
            """{"page":1,"total_pages":0,"total_results":2,"results":[]}""",
            """{"page":1,"total_pages":1,"total_results":-1,"results":[]}"""
        )) {
            json(body)
            assertTrue(load(LoadType.REFRESH) is RemoteMediator.MediatorResult.Error)
            assertNotNull(dao.findMovie(7))
        }
    }

    @Test fun prependAndTerminalEmptyDoNotRequestMore() = runBlocking {
        assertTrue(success(load(LoadType.PREPEND)).endOfPaginationReached)
        assertEquals(0, server.requestCount)
        page(1, 0)
        assertTrue(success(load(LoadType.REFRESH)).endOfPaginationReached)
        assertTrue(success(load(LoadType.APPEND)).endOfPaginationReached)
        assertEquals(1, server.requestCount)
    }

    @Test fun appendPersistenceFailureRetriesWithoutAdvancingAndCapStopsAt500() = runBlocking {
        page(1, 2, 1)
        load(LoadType.REFRESH)
        page(2, 2, 2)
        dao.failSave = true
        assertTrue(load(LoadType.APPEND) is RemoteMediator.MediatorResult.Error)
        dao.failSave = false
        page(2, 2, 2)
        assertTrue(success(load(LoadType.APPEND)).endOfPaginationReached)
        assertEquals(listOf(1, 2, 2), requestedPages())
        var requests = 0
        val capped = HomeRemoteMediator({ page ->
            requests++
            com.example.movieapp.data.remote.MoviePageDto(page, 900, 900, emptyList())
        }, dao)
        assertFalse(success(capped.load(LoadType.REFRESH, state)).endOfPaginationReached)
        repeat(498) { assertFalse(success(capped.load(LoadType.APPEND, state)).endOfPaginationReached) }
        assertTrue(success(capped.load(LoadType.APPEND, state)).endOfPaginationReached)
        assertTrue(success(capped.load(LoadType.APPEND, state)).endOfPaginationReached)
        assertEquals(500, requests)
    }

    @Test fun cancellationPropagates() = runBlocking {
        val cancelled = kotlinx.coroutines.CancellationException("cancelled")
        val subject = HomeRemoteMediator({ throw cancelled }, dao)
        assertSame(cancelled, runCatching { subject.load(LoadType.REFRESH, state) }.exceptionOrNull())
    }
    private suspend fun load(type: LoadType) = mediator.load(type, state)
    private fun success(result: RemoteMediator.MediatorResult) = result as RemoteMediator.MediatorResult.Success
    private fun page(page: Int, total: Int, vararg ids: Int) {
        val count = if (total == 0) 0 else maxOf(total, ids.size)
        val results = ids.joinToString(",") { """{"id":$it,"title":"Movie $it"}""" }
        json("""{"page":$page,"total_pages":$total,"total_results":${count},"results":[$results]}""")
    }
    private fun json(body: String) {
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(body))
    }
    private fun requestedPages() = (1..server.requestCount).mapNotNull {
        val url = server.takeRequest().requestUrl!!
        if (url.encodedPath.endsWith("discover/movie")) url.queryParameter("page")!!.toInt() else null
    }
    private fun movie(id: Int) = MovieEntity(id, "Movie $id", null, null, null, null, null)

    private class FakeDao : MovieDao {
        var failSave = false
        override suspend fun saveDiscoverPage(movies: List<MovieEntity>) {
            if (failSave) throw MovieAccessException.InvalidResponse()
            super.saveDiscoverPage(movies)
        }
        val rows = MutableStateFlow<List<MovieEntity>>(emptyList())
        override fun pagingSource(): PagingSource<Int, MovieEntity> = error("Room tested on device")
        override fun observeMovies() = rows
        override fun observeMovie(id: Int) = rows.map { it.find { movie -> movie.id == id } }
        override suspend fun findMovie(id: Int) = rows.value.find { it.id == id }
        override suspend fun nextDiscoveryOrder() = (rows.value.maxOfOrNull { it.discoveryOrder } ?: -1L) + 1
        override suspend fun upsert(movie: MovieEntity) {
            rows.value = (rows.value.filterNot { it.id == movie.id } + movie).sortedBy { it.discoveryOrder }
        }
        override suspend fun delete(id: Int) { rows.value = rows.value.filterNot { it.id == id } }
    }
}
