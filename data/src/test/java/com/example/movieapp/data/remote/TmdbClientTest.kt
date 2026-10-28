package com.example.movieapp.data.remote

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class TmdbClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: TmdbClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = TmdbClient.create(token = TEST_TOKEN, baseUrl = server.url("/3/"))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun discoverUsesExactFiltersAndRetainsPagination() = runBlocking {
        respond("""{"page":2,"total_pages":8,"total_results":151,"results":[{"id":42,"title":"Example","adult":false}],"unknown":true}""")
        val result = client.discoverMovies(page = 2)
        val request = server.takeRequest(2, TimeUnit.SECONDS)!!
        val url = request.requestUrl!!
        assertEquals("GET", request.method)
        assertEquals("/3/discover/movie", url.encodedPath)
        assertEquals(setOf("page", "include_adult", "certification_country", "certification.lte", "sort_by"), url.queryParameterNames)
        assertEquals("2", url.queryParameter("page"))
        assertEquals("false", url.queryParameter("include_adult"))
        assertEquals("US", url.queryParameter("certification_country"))
        assertEquals("PG-13", url.queryParameter("certification.lte"))
        assertEquals("popularity.desc", url.queryParameter("sort_by"))
        assertEquals(2, result.page)
        assertEquals(8, result.totalPages)
        assertEquals(151, result.totalResults)
        assertEquals(42, result.results.single().id)
        // Boolean assertion avoids including header values in failure output.
        assertTrue(request.getHeader("Authorization") == "Bearer $TEST_TOKEN")
    }

    @Test
    fun searchEncodesQueryAndDoesNotSendCertificationFilters() = runBlocking {
        respond("""{"page":3,"total_pages":4,"total_results":62,"results":[]}""")
        val result = client.searchMovies("space & time", page = 3)
        val url = server.takeRequest(2, TimeUnit.SECONDS)!!.requestUrl!!
        assertEquals("/3/search/movie", url.encodedPath)
        assertEquals(setOf("query", "page", "include_adult"), url.queryParameterNames)
        assertEquals("space & time", url.queryParameter("query"))
        assertEquals("3", url.queryParameter("page"))
        assertEquals("false", url.queryParameter("include_adult"))
        assertEquals(3, result.page)
        assertEquals(4, result.totalPages)
        assertEquals(62, result.totalResults)
        assertTrue(result.results.isEmpty())
    }

    @Test
    fun detailsParseUsefulFields() = runBlocking {
        respond("""{"id":42,"title":"Example","overview":"Story","poster_path":"/poster.jpg","backdrop_path":"/backdrop.jpg","release_date":"2025-04-01","vote_average":7.5,"genres":[{"id":18,"name":"Drama"}],"adult":false}""")
        val details = client.movieDetails(42)
        assertEquals("/3/movie/42", server.takeRequest(2, TimeUnit.SECONDS)!!.path)
        assertEquals(MovieDetailsDto(42, "Example", "Story", "/poster.jpg", "/backdrop.jpg", "2025-04-01", 7.5, listOf(GenreDto(18, "Drama")), false), details)
    }

    @Test
    fun optionalMissingAndNullFieldsStayNull() = runBlocking {
        respond("""{"id":42,"title":"Example","poster_path":null,"genres":null,"adult":null}""")
        assertEquals(MovieDetailsDto(42, "Example"), client.movieDetails(42))
        respond("""{"page":1,"total_pages":1,"total_results":1,"results":[{"id":42,"title":"Example","overview":null}]}""")
        assertEquals(MovieSummaryDto(42, "Example"), client.discoverMovies().results.single())
    }

    @Test
    fun missingRequiredIdIsInvalidInsteadOfFabricated() = runBlocking {
        respond("""{"title":"Example"}""")
        expect<TmdbException.InvalidResponse> { client.movieDetails(42) }
        Unit
    }

    @Test
    fun httpErrorsPreserveStatusWithoutResponseBody() = runBlocking {
        for (code in listOf(401, 404, 429, 500, 503)) {
            server.enqueue(MockResponse().setResponseCode(code).setBody("private diagnostic body"))
            val error = expect<TmdbException.Http> { client.movieDetails(42) }
            assertEquals(code, error.statusCode)
            assertFalse(error.message.orEmpty().contains("private diagnostic body"))
        }
    }

    @Test
    fun malformedJsonIsInvalidResponse() = runBlocking {
        respond("""{"id":42,"title": }""")
        expect<TmdbException.InvalidResponse> { client.movieDetails(42) }
        Unit
    }

    @Test
    fun emptySuccessfulResponseIsInvalid() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))
        expect<TmdbException.InvalidResponse> { client.movieDetails(42) }
        Unit
    }

    @Test
    fun connectionFailureMapsToNetwork() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        expect<TmdbException.Network> { client.movieDetails(42) }
        Unit
    }

    @Test
    fun missingTokenFailsBeforeSendingRequest() = runBlocking {
        val unauthenticated = TmdbClient.create(token = "", baseUrl = server.url("/3/"))
        expect<TmdbException.MissingToken> { unauthenticated.movieDetails(42) }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun nonAsciiTokenIsRejectedBeforeClientCreation() = runBlocking {
        val error = expect<IllegalArgumentException> {
            TmdbClient.create(token = "test-only-\u00e9", baseUrl = server.url("/3/"))
        }
        assertEquals("TMDb token contains unsupported characters.", error.message)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun cancellationIsNotTranslatedToNetworkError() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val pending = async(start = CoroutineStart.UNDISPATCHED) { client.movieDetails(42) }
        assertNotNull(server.takeRequest(2, TimeUnit.SECONDS))
        pending.cancel()
        withTimeout(2_000) {
            expect<CancellationException> { pending.await() }
            pending.join()
        }
        assertTrue(pending.isCancelled)
    }

    private fun respond(body: String) {
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(body))
    }

    private suspend inline fun <reified T : Throwable> expect(block: () -> Unit): T {
        try {
            block()
        } catch (error: Throwable) {
            if (error is T) return error
            throw error
        }
        throw AssertionError("Expected ${T::class.simpleName}")
    }

    private companion object {
        const val TEST_TOKEN = "test-only-token"
    }
}
