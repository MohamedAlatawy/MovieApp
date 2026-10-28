package com.example.movieapp.data.paging

import com.example.movieapp.domain.model.MovieAccessException
import androidx.paging.PagingSource
import com.example.movieapp.data.remote.MovieSummaryDto
import com.example.movieapp.data.remote.TmdbClient
import kotlinx.coroutines.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class SearchPagingSourceTest {
    private val server = MockWebServer()
    private lateinit var source: SearchPagingSource
    @Before fun setUp() {
        server.start()
        source = SearchPagingSource("  toy story  ", TmdbClient.create("synthetic-token", server.url("/3/")))
    }
    @After fun tearDown() { server.shutdown() }

    @Test fun oneRequestReturnsSummariesInSearchOrderWithoutCertificationRequests() = runBlocking {
        json("""{"page":1,"total_pages":2,"total_results":4,"results":[
            {"id":9,"title":"R film","certification":"R"},
            {"id":3,"title":"NC-17 film","certification":"NC-17"},
            {"id":2,"title":"Unknown"},{"id":9,"title":"Duplicate"}]}""")
        val first = load() as PagingSource.LoadResult.Page
        assertEquals(listOf(9,3,2), first.data.map { it.id })
        assertEquals(listOf("R film","NC-17 film","Unknown"), first.data.map { it.title })
        assertNull(first.prevKey)
        assertEquals(2, first.nextKey)
        assertEquals(1, server.requestCount)
        val url = server.takeRequest().requestUrl!!
        assertEquals("/3/search/movie", url.encodedPath)
        assertEquals("toy story", url.queryParameter("query"))
        assertEquals("false", url.queryParameter("include_adult"))
        assertFalse(url.queryParameterNames.any { it.startsWith("certification") })
        page(2,2,9,8)
        val second = load(2) as PagingSource.LoadResult.Page
        assertEquals(listOf(8), second.data.map { it.id })
        assertEquals(1, second.prevKey)
        assertNull(second.nextKey)
        assertEquals(2, server.requestCount)
        assertEquals("/3/search/movie", server.takeRequest().requestUrl!!.encodedPath)
        // Reloading a page must not suppress its own previously emitted rows.
        page(2,2,9,8)
        assertEquals(listOf(8), (load(2) as PagingSource.LoadResult.Page).data.map { it.id })
    }

    @Test fun emptyAndShortPagesUseMetadataWithoutScanning() = runBlocking {
        page(1,3)
        val empty = load() as PagingSource.LoadResult.Page
        assertTrue(empty.data.isEmpty())
        assertEquals(2, empty.nextKey)
        assertEquals(1, server.requestCount)
        page(2,3,7)
        assertEquals(3, (load(2) as PagingSource.LoadResult.Page).nextKey)
        page(3,3)
        assertNull((load(3) as PagingSource.LoadResult.Page).nextKey)
        assertEquals(3, server.requestCount)
    }

    @Test fun errorsRetryTheSamePageAndInvalidIdsOrMetadataFail() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503))
        val failure = load() as PagingSource.LoadResult.Error
        assertEquals(503, (failure.throwable as MovieAccessException.Http).statusCode)
        page(1,1,7)
        assertEquals(7, (load() as PagingSource.LoadResult.Page).data.single().id)
        assertEquals("1", server.takeRequest().requestUrl!!.queryParameter("page"))
        assertEquals("1", server.takeRequest().requestUrl!!.queryParameter("page"))
        page(2,2,0)
        assertTrue(load(2) is PagingSource.LoadResult.Error)
        page(3,3,8)
        assertTrue(load(2) is PagingSource.LoadResult.Error)
        page(2,-1)
        assertTrue(load(2) is PagingSource.LoadResult.Error)
    }

    @Test fun blankQueryMakesNoRequestsAndZeroPagesEnds() = runBlocking {
        val blank = SearchPagingSource("  ", TmdbClient.create("", server.url("/3/")))
        val result = blank.load(PagingSource.LoadParams.Refresh(null,20,false)) as PagingSource.LoadResult.Page
        assertTrue(result.data.isEmpty())
        assertEquals(0, server.requestCount)
        page(1,0)
        assertNull((load() as PagingSource.LoadResult.Page).nextKey)
    }

    @Test fun appendErrorUsesDomainFailureAndRetriesWithoutChangingKeys() = runBlocking {
        page(1,2,7)
        val first = load() as PagingSource.LoadResult.Page
        server.enqueue(MockResponse().setResponseCode(503))
        val failure = load(first.nextKey) as PagingSource.LoadResult.Error
        assertEquals(503, (failure.throwable as MovieAccessException.Http).statusCode)
        page(2,2,8)
        val recovered = load(first.nextKey) as PagingSource.LoadResult.Page
        assertEquals(listOf(8), recovered.data.map { it.id })
        assertNull(recovered.nextKey)
        assertEquals(listOf("1", "2", "2"), (1..3).map { server.takeRequest().requestUrl!!.queryParameter("page") })
    }
    @Test fun cancellationIsNotConvertedIntoPagingError() = runBlocking {
        server.enqueue(MockResponse().setBody("{}").setBodyDelay(5, TimeUnit.SECONDS))
        val pending = async(Dispatchers.IO) { load() }
        assertNotNull(withContext(Dispatchers.IO) { server.takeRequest(2, TimeUnit.SECONDS) })
        pending.cancelAndJoin()
        assertTrue(pending.isCancelled)
    }

    private suspend fun load(page: Int? = null): PagingSource.LoadResult<Int, MovieSummaryDto> = source.load(
        if (page == null) PagingSource.LoadParams.Refresh(null,20,false)
        else PagingSource.LoadParams.Append(page,20,false))
    private fun page(number: Int, total: Int, vararg ids: Int) = json(
        """{"page":$number,"total_pages":$total,"total_results":${ids.size},"results":[${ids.joinToString(",") { """{"id":$it,"title":"Candidate"}""" }}]}""")
    private fun json(body: String) { server.enqueue(MockResponse().setHeader("Content-Type","application/json").setBody(body)) }
}