package com.example.movieapp.data.paging

import androidx.paging.*
import com.example.movieapp.data.local.MovieDao
import com.example.movieapp.data.local.MovieEntity
import com.example.movieapp.data.remote.TmdbClient
import com.example.movieapp.data.repository.MovieRepository
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
        mediator = HomeRemoteMediator(client, MovieRepository(client, dao), dao)
    }
    @After fun tearDown() { server.shutdown() }

    @Test fun discoverOrderSurvivesAppendDuplicatesAndRefresh() = runBlocking {
        page(1, 2, 30, 10, 20)
        evidence(30, "PG", "Zulu")
        evidence(10, "R", "Denied")
        evidence(20, "G", "Middle")
        load(LoadType.REFRESH)
        assertEquals(listOf(30, 20), dao.rows.value.map { it.id })
        page(2, 2, 20, 5, 40)
        evidence(20, "PG", "Renamed")
        evidence(5, "PG", "Alpha")
        evidence(40, "G", "Beta")
        load(LoadType.APPEND)
        assertEquals(listOf(30, 20, 5, 40), dao.rows.value.map { it.id })
        assertEquals(listOf(0L, 1L, 2L, 3L), dao.rows.value.map { it.discoveryOrder })
        page(1, 1, 40, 30, 50)
        evidence(40, "G")
        evidence(30, "PG")
        evidence(50, "G", "A new discovery")
        load(LoadType.REFRESH)
        assertEquals(listOf(30, 20, 5, 40, 50), dao.rows.value.map { it.id })
        assertNull(dao.findMovie(10))
    }

    @Test fun skipsRestrictedPageThenAppendsRawPageAndRevokesRestrictedCache() = runBlocking {
        dao.upsert(movie(1))
        page(1, 3, 1)
        evidence(1, "R")
        page(2, 3, 2)
        evidence(2, "PG")
        assertEquals(false, success(load(LoadType.REFRESH)).endOfPaginationReached)
        assertNull(dao.findMovie(1))
        assertNotNull(dao.findMovie(2))
        page(3, 3, 3)
        evidence(3, "G")
        assertTrue(success(load(LoadType.APPEND)).endOfPaginationReached)
        assertEquals(listOf(1, 2, 3), requestedPages())
        assertEquals(listOf(2, 3), dao.rows.value.map { it.id })
    }

    @Test fun failedAppendPreservesCacheAndRetriesSamePageWithoutDuplicates() = runBlocking {
        page(1, 2, 1)
        evidence(1, "PG-13")
        load(LoadType.REFRESH)
        page(2, 2, 2)
        json("""{"id":2,"title":"Movie 2","adult":false}""")
        server.enqueue(MockResponse().setResponseCode(503))
        assertTrue(load(LoadType.APPEND) is RemoteMediator.MediatorResult.Error)
        assertEquals(listOf(1), dao.rows.value.map { it.id })
        page(2, 2, 2)
        evidence(2, "G")
        assertTrue(success(load(LoadType.APPEND)).endOfPaginationReached)
        assertEquals(listOf(1, 2, 2), requestedPages())
        assertEquals(2, dao.rows.value.size)
    }

    @Test fun failedRefreshResumesFailedPageAndSuccessfulNewRefreshStartsAtOne() = runBlocking {
        page(1, 2, 1)
        evidence(1, "R")
        server.enqueue(MockResponse().setResponseCode(503))
        assertTrue(load(LoadType.REFRESH) is RemoteMediator.MediatorResult.Error)
        page(2, 2, 2)
        evidence(2, "PG")
        load(LoadType.REFRESH)
        page(1, 1, 2)
        evidence(2, "PG")
        load(LoadType.REFRESH)
        assertEquals(listOf(1, 2, 2, 1), requestedPages())
        assertEquals(1, dao.rows.value.size)
    }

    @Test fun emptyBatchPausesWithoutExhaustionAndContinuesAtNextRawPage() = runBlocking {
        for (page in 1..3) page(page, 4)
        val result = load(LoadType.REFRESH) as RemoteMediator.MediatorResult.Error
        assertTrue(result.throwable is HomeRemoteMediator.ContinueBrowsing)
        assertEquals(3, server.requestCount)
        page(4, 4, 4)
        evidence(4, "PG")
        assertTrue(success(load(LoadType.REFRESH)).endOfPaginationReached)
        assertEquals(listOf(1, 2, 3, 4), requestedPages())
    }

    @Test fun duplicateOnlyAppendAdvancesAndMissingTokenPreservesCache() = runBlocking {
        page(1, 3, 1)
        evidence(1, "G")
        load(LoadType.REFRESH)
        page(2, 3, 1)
        evidence(1, "G")
        page(3, 3, 2)
        evidence(2, "G")
        load(LoadType.APPEND)
        assertEquals(2, dao.rows.value.size)
        val client = TmdbClient.create("", server.url("/3/"))
        val missing = HomeRemoteMediator(client, MovieRepository(client, dao), dao)
        val count = server.requestCount
        assertTrue(missing.load(LoadType.REFRESH, state) is RemoteMediator.MediatorResult.Error)
        assertEquals(count, server.requestCount)
        assertEquals(2, dao.rows.value.size)
    }

    @Test fun prependDoesNotRequestNetworkAndEmptyRemoteEnds() = runBlocking {
        assertTrue(success(load(LoadType.PREPEND)).endOfPaginationReached)
        assertEquals(0, server.requestCount)
        page(1, 0)
        assertTrue(success(load(LoadType.REFRESH)).endOfPaginationReached)
    }

    private suspend fun load(type: LoadType) = mediator.load(type, state)
    private fun success(result: RemoteMediator.MediatorResult) = result as RemoteMediator.MediatorResult.Success
    private fun page(page: Int, total: Int, vararg ids: Int) {
        val results = ids.joinToString(",") { """{"id":$it,"title":"Movie $it"}""" }
        json("""{"page":$page,"total_pages":$total,"total_results":$total,"results":[$results]}""")
    }
    private fun evidence(id: Int, rating: String, title: String = "Movie $id") {
        json("""{"id":$id,"title":"$title","adult":false}""")
        json("""{"id":$id,"results":[{"iso_3166_1":"US","release_dates":[{"certification":"$rating"}]}]}""")
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
