package com.example.movieapp.data.repository

import com.example.movieapp.data.local.MovieDao
import com.example.movieapp.data.local.MovieEntity
import com.example.movieapp.data.remote.TmdbClient
import com.example.movieapp.domain.model.MovieAccessException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collectLatest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class MovieRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var dao: FakeMovieDao
    private lateinit var repository: MovieRepository

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
        dao = FakeMovieDao()
        repository = MovieRepository(TmdbClient.create("test-only-token", server.url("/3/")), dao)
    }

    @After fun tearDown() { server.shutdown() }

    @Test fun homeEnrichmentUsesOneRequestAndPreservesOrderAndMissingFields() = runBlocking {
        val row = MovieEntity(42, "Cached", "Saved story", "/saved.jpg", null, "2026", 7.0,
            discoveryOrder = 19, genres = "Drama", usCertifications = "PG")
        dao.upsert(row)
        server.enqueue(json(details(title = "Updated")))
        repository.refreshHomeMovieDetails(42)
        assertEquals(row.copy(title = "Updated"), dao.findMovie(42))
        assertEquals(1, server.requestCount)
        assertEquals("/3/movie/42", server.takeRequest().path)
        server.enqueue(json("""{"id":42,"title":"Updated","genres":[]}"""))
        repository.refreshHomeMovieDetails(42)
        assertNull(dao.findMovie(42)?.genres)
        assertEquals(19L, dao.findMovie(42)?.discoveryOrder)
    }

    @Test fun homeErrorsAndIdentityMismatchPreserveCache() = runBlocking {
        val row = MovieEntity(42, "Cached", null, null, null, null, null)
        dao.upsert(row)
        for (code in listOf(404, 503)) {
            server.enqueue(MockResponse().setResponseCode(code))
            val failure = runCatching { repository.refreshHomeMovieDetails(42) }.exceptionOrNull()
            assertEquals(code, (failure as MovieAccessException.Http).statusCode)
            assertEquals(row, dao.findMovie(42))
        }
        server.enqueue(json(details(id = 43)))
        assertTrue(runCatching { repository.refreshHomeMovieDetails(42) }.exceptionOrNull() is MovieAccessException.InvalidIdentity)
        assertEquals(row, dao.findMovie(42))
        assertNull(dao.findMovie(43))
    }

    @Test fun absentOrInvalidHomeIdCannotInsertOrRequest() = runBlocking {
        for (id in listOf(0, 42)) {
            assertTrue(runCatching { repository.refreshHomeMovieDetails(id) }.exceptionOrNull() is MovieAccessException.InvalidIdentity)
        }
        assertEquals(0, server.requestCount)
        assertTrue(dao.writes.isEmpty())
    }

    @Test fun homeConnectionFailurePreservesCache() = runBlocking {
        val row = MovieEntity(42, "Cached", null, null, null, null, null)
        dao.upsert(row)
        server.shutdown()
        assertTrue(runCatching { repository.refreshHomeMovieDetails(42) }.exceptionOrNull() is MovieAccessException.Network)
        assertEquals(row, dao.findMovie(42))
    }
    @Test fun searchPagerLoadsWithoutTouchingLegacyHomeRows() = runBlocking {
        val legacy = MovieEntity(42, "Legacy", null, null, null, null, null)
        dao.saveCachedMovie(legacy)
        val writes = dao.writes.toList()
        dao.forbidAccess = true
        server.enqueue(json("""{"page":1,"total_pages":1,"total_results":1,"results":[{"id":99,"title":"Search result"}]}"""))
        val presenter = object : androidx.paging.PagingDataPresenter<com.example.movieapp.domain.model.MovieSummary>(kotlinx.coroutines.Dispatchers.Unconfined) {
            override suspend fun presentPagingDataEvent(event: androidx.paging.PagingDataEvent<com.example.movieapp.domain.model.MovieSummary>) = Unit
        }
        val collection = launch { repository.searchMovies("Search").collectLatest { presenter.collectFrom(it) } }
        try {
            kotlinx.coroutines.withTimeout(5_000) {
                while (presenter.snapshot().items.isEmpty()) kotlinx.coroutines.delay(10)
            }
            assertEquals(99, presenter.snapshot().items.single().id)
            assertEquals(1, server.requestCount)
            assertEquals("/3/search/movie", server.takeRequest().requestUrl!!.encodedPath)
        } finally { collection.cancelAndJoin(); dao.forbidAccess = false }
        assertEquals(writes, dao.writes)
        assertEquals(legacy, dao.findMovie(42))
        assertNull(dao.findMovie(99))
    }

    @Test fun searchDetailsNeverReadsOrWritesRoomAndDoesNotRequestReleases() = runBlocking {
        val legacy = MovieEntity(42, "Legacy verified", null, null, null, null, null)
        dao.saveCachedMovie(legacy)
        val writes = dao.writes.toList()
        dao.forbidAccess = true
        for (id in listOf(42, 99)) {
            server.enqueue(json(details(id, "Search metadata")))
            assertEquals("Search metadata", repository.fetchSearchDetails(id).title)
            assertEquals("/3/movie/$id", server.takeRequest().path)
        }
        assertEquals(2, server.requestCount)
        dao.forbidAccess = false
        assertEquals(writes, dao.writes)
        assertEquals(legacy, dao.findMovie(42))
        assertNull(dao.findMovie(99))
    }

    @Test fun searchDetailsValidatesIdentityAndPreservesNetworkErrorsWithoutRoomAccess() = runBlocking {
        dao.forbidAccess = true
        assertTrue(runCatching { repository.fetchSearchDetails(0) }.exceptionOrNull() is MovieAccessException.InvalidIdentity)
        assertEquals(0, server.requestCount)
        server.enqueue(json(details(43)))
        assertTrue(runCatching { repository.fetchSearchDetails(42) }.exceptionOrNull() is MovieAccessException.InvalidIdentity)
        server.enqueue(MockResponse().setResponseCode(503))
        assertTrue(runCatching { repository.fetchSearchDetails(42) }.exceptionOrNull() is MovieAccessException.Http)
        assertTrue(dao.writes.isEmpty())
    }

    private fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)
    private fun details(id: Int = 42, title: String = "Example", adult: String = "false") =
        """{"id":$id,"title":"$title","adult":$adult}"""
    /** Only persistence is fake; the real TmdbClient talks to local MockWebServer. */
    private class FakeMovieDao : MovieDao {
        override fun pagingSource(): androidx.paging.PagingSource<Int, MovieEntity> = error("Not used in repository tests")
        private val rows = MutableStateFlow<List<MovieEntity>>(emptyList())
        val writes = mutableListOf<String>()
        var forbidAccess = false
        override fun observeMovies(): Flow<List<MovieEntity>> { check(!forbidAccess); return rows }
        override fun observeMovie(id: Int): Flow<MovieEntity?> { check(!forbidAccess); return rows.map { list -> list.find { it.id == id } } }
        override suspend fun findMovie(id: Int): MovieEntity? { check(!forbidAccess); return rows.value.find { it.id == id } }
        override suspend fun nextDiscoveryOrder() = (rows.value.maxOfOrNull { it.discoveryOrder } ?: -1L) + 1
        override suspend fun upsert(movie: MovieEntity) {
            check(!forbidAccess)
            writes += "save"
            rows.value = rows.value.filterNot { it.id == movie.id } + movie
        }
        override suspend fun delete(id: Int) {
            check(!forbidAccess)
            writes += "delete"
            rows.value = rows.value.filterNot { it.id == id }
        }
    }
}
