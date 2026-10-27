package com.example.movieapp.data.paging

import androidx.paging.PagingSource
import com.example.movieapp.data.local.MovieDao
import com.example.movieapp.data.local.MovieEntity
import com.example.movieapp.data.remote.TmdbClient
import com.example.movieapp.data.repository.MovieRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class SearchPagingSourceTest {
    private val server = MockWebServer()
    private val dao = FakeDao()
    private lateinit var source: SearchPagingSource
    @Before fun setUp() {
        server.start()
        val client = TmdbClient.create("synthetic-token", server.url("/3/"))
        source = SearchPagingSource("  toy story  ", client, MovieRepository(client, dao), dao)
    }
    @After fun tearDown() { server.shutdown() }

    @Test fun verifiesEligibilityKeepsSearchRankAndAppendsWithoutDuplicates() = runBlocking {
        dao.saveVerified(movie(2, "Existing"))
        page(1, 2, 9, 3, 2, 4, 5)
        evidence(9, "PG", "Zulu")
        evidence(3, "R", "Restricted")
        evidence(2, "G", "Alpha")
        evidence(4, "", "Unknown")
        evidence(5, "PG", "Adult", adult = "true")
        val first = load() as PagingSource.LoadResult.Page
        assertEquals(listOf(9, 2), first.data.map { it.id })
        assertEquals(0L, dao.findMovie(2)!!.discoveryOrder)
        assertNull(dao.findMovie(3))
        assertNull(dao.findMovie(4))
        assertNull(dao.findMovie(5))
        assertEquals(2, first.nextKey)
        val request = server.takeRequest().requestUrl!!
        assertEquals("/3/search/movie", request.encodedPath)
        assertEquals("toy story", request.queryParameter("query"))
        assertEquals("false", request.queryParameter("include_adult"))
        assertEquals("1", request.queryParameter("page"))
        assertFalse(request.queryParameterNames.any { it.startsWith("certification") })
        page(2, 2, 9, 8)
        evidence(8, "PG-13", "New")
        val next = load(2) as PagingSource.LoadResult.Page
        assertEquals(listOf(8), next.data.map { it.id })
        assertNull(next.nextKey)
    }

    @Test fun failedEvidenceDoesNotEmitPartialResultsAndRetryKeepsPage() = runBlocking {
        dao.saveVerified(movie(7, "Cached"))
        page(1, 1, 7, 8)
        evidence(7, "PG", "Cached")
        json("""{"id":8,"title":"New","adult":false}""")
        server.enqueue(MockResponse().setResponseCode(503))
        assertTrue(load() is PagingSource.LoadResult.Error)
        assertNotNull(dao.findMovie(7))
        assertNull(dao.findMovie(8))
        page(1, 1, 7, 8)
        evidence(7, "PG", "Cached")
        evidence(8, "G", "New")
        assertEquals(listOf(7, 8), (load() as PagingSource.LoadResult.Page).data.map { it.id })
        val pages = (1..server.requestCount).mapNotNull {
            val url = server.takeRequest().requestUrl!!
            if (url.encodedPath.endsWith("search/movie")) url.queryParameter("page") else null
        }
        assertEquals(listOf("1", "1"), pages)
    }

    @Test fun emptyBatchesPauseAndContinueWithoutFalseExhaustion() = runBlocking {
        for (number in 1..3) page(number, 4)
        val paused = load() as PagingSource.LoadResult.Error
        assertTrue(paused.throwable is SearchPagingSource.ContinueSearching)
        assertEquals(3, server.requestCount)
        page(4, 4, 1)
        evidence(1, "G", "Found")
        val resumed = load() as PagingSource.LoadResult.Page
        assertEquals(1, resumed.data.single().id)
        assertNull(resumed.nextKey)
    }

    @Test fun mismatchedIdentityIsNeverDisplayed() = runBlocking {
        page(1, 1, 1)
        evidence(2, "PG", "Wrong identity")
        assertTrue(load() is PagingSource.LoadResult.Error)
        assertTrue(dao.rows.value.isEmpty())
    }

    @Test fun blankQueryMakesNoRequests() = runBlocking {
        val client = TmdbClient.create("", server.url("/3/"))
        val blank = SearchPagingSource("  ", client, MovieRepository(client, dao), dao)
        val result = blank.load(PagingSource.LoadParams.Refresh(null, 20, false)) as PagingSource.LoadResult.Page
        assertTrue(result.data.isEmpty())
        assertEquals(0, server.requestCount)
    }

    @Test fun cancellationIsNotConvertedIntoPagingError() = runBlocking {
        server.enqueue(MockResponse().setBody("{}").setBodyDelay(5, TimeUnit.SECONDS))
        val pending = async(Dispatchers.IO) { load() }
        assertNotNull(withContext(Dispatchers.IO) { server.takeRequest(2, TimeUnit.SECONDS) })
        pending.cancelAndJoin()
        assertTrue(pending.isCancelled)
        assertTrue(dao.rows.value.isEmpty())
    }

    private suspend fun load(page: Int? = null) = source.load(
        if (page == null) PagingSource.LoadParams.Refresh(null, 20, false)
        else PagingSource.LoadParams.Append(page, 20, false))
    private fun page(number: Int, total: Int, vararg ids: Int) = json(
        """{"page":$number,"total_pages":$total,"total_results":${ids.size},"results":[${ids.joinToString(",") { """{"id":$it,"title":"Candidate"}""" }}]}""")
    private fun evidence(id: Int, rating: String, title: String, adult: String = "false") {
        json("""{"id":$id,"title":"$title","adult":$adult}""")
        json("""{"id":$id,"results":[{"iso_3166_1":"US","release_dates":[{"certification":"$rating"}]}]}""")
    }
    private fun json(body: String) { server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(body)) }
    private fun movie(id: Int, title: String) = MovieEntity(id, title, null, null, null, null, null)

    private class FakeDao : MovieDao {
        val rows = MutableStateFlow<List<MovieEntity>>(emptyList())
        override fun pagingSource(): PagingSource<Int, MovieEntity> = error("Not used by Search")
        override fun observeMovies() = rows
        override fun observeMovie(id: Int) = rows.map { list -> list.find { it.id == id } }
        override suspend fun findMovie(id: Int) = rows.value.find { it.id == id }
        override suspend fun nextDiscoveryOrder() = (rows.value.maxOfOrNull { it.discoveryOrder } ?: -1L) + 1
        override suspend fun upsert(movie: MovieEntity) { rows.value = rows.value.filterNot { it.id == movie.id } + movie }
        override suspend fun delete(id: Int) { rows.value = rows.value.filterNot { it.id == id } }
    }
}
