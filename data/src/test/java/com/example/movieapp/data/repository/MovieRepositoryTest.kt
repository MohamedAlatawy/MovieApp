package com.example.movieapp.data.repository

import com.example.movieapp.data.local.MovieDao
import com.example.movieapp.data.local.MovieEntity
import com.example.movieapp.data.remote.TmdbClient
import com.example.movieapp.data.remote.TmdbException
import com.example.movieapp.domain.eligibility.MovieEligibilityPolicy.Decision
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
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

    @Test fun eligibleMovieIsPersistedAndUpdated() = runBlocking {
        enqueueVerification("PG")
        assertEquals(Decision.Allow, repository.refreshMovie(42))
        assertEquals("Example", dao.findMovie(42)?.title)
        server.enqueue(json(details(title = "Updated")))
        server.enqueue(json(releases("PG-13")))
        repository.refreshMovie(42)
        assertEquals("Updated", dao.findMovie(42)?.title)
        assertEquals(listOf("save", "save"), dao.writes)
    }

    @Test fun verifiedRestrictionRevokesCachedMovie() = runBlocking {
        enqueueVerification("PG")
        repository.refreshMovie(42)
        enqueueVerification("R")
        assertEquals(Decision.Deny, repository.refreshMovie(42))
        assertNull(dao.findMovie(42))
    }

    @Test fun restrictedOrUnknownEvidenceNeverAuthorizes() = runBlocking {
        for (rating in listOf("R", "NC-17", "UNKNOWN", "", " ")) {
            enqueueVerification(rating)
            assertEquals(Decision.Deny, repository.refreshMovie(42))
            assertNull(dao.findMovie(42))
        }
    }

    @Test fun missingRecordsAndConflictingCountriesAreMappedWithoutDiscardingEvidence() = runBlocking {
        server.enqueue(json(details()))
        server.enqueue(json("""{"id":42,"results":[]}"""))
        assertEquals(Decision.Deny, repository.refreshMovie(42))
        server.enqueue(json(details()))
        server.enqueue(json("""{"id":42,"results":[{"iso_3166_1":"US","release_dates":[{"certification":"PG"},{"certification":"R"}]}]}"""))
        assertEquals(Decision.Deny, repository.refreshMovie(42))
        server.enqueue(json(details()))
        server.enqueue(json("""{"id":42,"results":[{"iso_3166_1":"GB","release_dates":[{"certification":"PG"}]}]}"""))
        assertEquals(Decision.Deny, repository.refreshMovie(42))
        assertNull(dao.findMovie(42))
    }

    @Test fun adultTrueAndUnknownDeny() = runBlocking {
        for (adult in listOf("true", "null")) {
            server.enqueue(json(details(adult = adult)))
            server.enqueue(json(releases("G")))
            assertEquals(Decision.Deny, repository.refreshMovie(42))
        }
        assertNull(dao.findMovie(42))
    }

    @Test fun mismatchedIdentityPreservesCacheAndDoesNotAuthorizeAnotherMovie() = runBlocking {
        enqueueVerification("G")
        repository.refreshMovie(42)
        for ((detailId, releaseId) in listOf(43 to 42, 42 to 43)) {
            server.enqueue(json(details(id = detailId)))
            server.enqueue(json(releases("G", releaseId)))
            expectInvalidResponse()
            assertNotNull(dao.findMovie(42))
            assertNull(dao.findMovie(43))
        }
    }

    @Test fun secondRequestFailurePreservesVerifiedContent() = runBlocking {
        enqueueVerification("G")
        repository.refreshMovie(42)
        val cached = dao.findMovie(42)
        server.enqueue(json(details(title = "Unverified update")))
        server.enqueue(MockResponse().setResponseCode(503))
        try {
            repository.refreshMovie(42)
            fail("Expected HTTP failure")
        } catch (error: TmdbException.Http) {
            assertEquals(503, error.statusCode)
        }
        assertEquals(cached, dao.findMovie(42))
    }

    @Test fun ioFailurePreservesVerifiedContent() = runBlocking {
        enqueueVerification("G")
        repository.refreshMovie(42)
        // Close existing pooled sockets and the listener to guarantee a connection failure.
        server.shutdown()
        try {
            repository.refreshMovie(42)
            fail("Expected network failure")
        } catch (_: TmdbException.Network) {
            assertNotNull(dao.findMovie(42))
        }
    }

    @Test fun malformedIncompleteResponseCannotNewlyAuthorize() = runBlocking {
        server.enqueue(json(details()))
        server.enqueue(json("""{"id":42}"""))
        expectInvalidResponse()
        assertNull(dao.findMovie(42))
        assertTrue(dao.writes.isEmpty())
    }

    @Test fun verifiedDetailsMetadataPreservesAllAllowedUsRatings() = runBlocking {
        server.enqueue(json("""{"id":42,"title":"Example","adult":false,"overview":"A story","vote_average":7.4,"genres":[{"id":1,"name":"Adventure"},{"id":2,"name":"Animation"}]}"""))
        server.enqueue(json("""{"id":42,"results":[{"iso_3166_1":"US","release_dates":[{"certification":" pg-13 "},{"certification":"PG"},{"certification":""},{"certification":"PG"}]},{"iso_3166_1":"GB","release_dates":[{"certification":"12"}]}]}"""))
        assertEquals(Decision.Allow, repository.refreshMovie(42))
        val movie = dao.findMovie(42)!!
        assertEquals("A story", movie.overview)
        assertEquals(7.4, movie.voteAverage!!, 0.0)
        assertEquals("Adventure · Animation", movie.genres)
        assertEquals("PG / PG-13", movie.usCertifications)
        enqueueVerification("G")
        repository.refreshMovie(42)
        assertEquals(movie.discoveryOrder, dao.findMovie(42)!!.discoveryOrder)
        assertNull(dao.findMovie(42)!!.genres)
    }
    private suspend fun expectInvalidResponse() {
        try {
            repository.refreshMovie(42)
            fail("Expected invalid response")
        } catch (_: TmdbException.InvalidResponse) { }
    }

    private fun enqueueVerification(rating: String) {
        server.enqueue(json(details()))
        server.enqueue(json(releases(rating)))
    }

    private fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)
    private fun details(id: Int = 42, title: String = "Example", adult: String = "false") =
        """{"id":$id,"title":"$title","adult":$adult}"""
    private fun releases(rating: String, id: Int = 42) =
        """{"id":$id,"results":[{"iso_3166_1":"US","release_dates":[{"certification":"$rating"}]}]}"""

    /** Only persistence is fake; the real TmdbClient talks to local MockWebServer. */
    private class FakeMovieDao : MovieDao {
        override fun pagingSource(): androidx.paging.PagingSource<Int, MovieEntity> = error("Not used in repository tests")
        private val rows = MutableStateFlow<List<MovieEntity>>(emptyList())
        val writes = mutableListOf<String>()
        override fun observeMovies(): Flow<List<MovieEntity>> = rows
        override fun observeMovie(id: Int) = rows.map { list -> list.find { it.id == id } }
        override suspend fun findMovie(id: Int) = rows.value.find { it.id == id }
        override suspend fun nextDiscoveryOrder() = (rows.value.maxOfOrNull { it.discoveryOrder } ?: -1L) + 1
        override suspend fun upsert(movie: MovieEntity) {
            writes += "save"
            rows.value = rows.value.filterNot { it.id == movie.id } + movie
        }
        override suspend fun delete(id: Int) {
            writes += "delete"
            rows.value = rows.value.filterNot { it.id == id }
        }
    }
}
