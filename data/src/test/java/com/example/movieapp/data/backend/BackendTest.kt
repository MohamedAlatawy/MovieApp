package com.example.movieapp.data.backend

import androidx.paging.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalPagingApi::class)
class BackendTest {
    private val json = """{"id":1001,"title":"Toy Harbor","overview":"Demo fiction","releaseDate":"2020-01-01","posterUrl":null,"voteAverage":6.0,"genres":["Family"],"adult":false,"usCertification":null,"lastModified":"2026-01-01T00:00:00Z","dataSource":"synthetic-demo","eligibilityEvidence":"unverified","isDemo":true}"""
    private fun page(items: String = json, page: Int = 1, next: Boolean = false) =
        """{"items":[$items],"page":$page,"pageSize":20,"totalItems":31,"totalPages":${if(next) 2 else page},"hasNextPage":$next}"""

    @Test fun mapsContractAndDoesNotSendAuthentication() = runBlocking {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setBody(json))
            val movie = BackendApi.create(server.url("/").toString()).movie(1001).toMovie()
            assertEquals("Family", movie.genres)
            assertNull(movie.posterPath)
            assertEquals("2020-01-01", movie.releaseDate)
            val request = server.takeRequest()
            assertEquals("/api/movies/1001", request.path)
            assertNull(request.getHeader("Authorization"))
            assertNull(request.getHeader("X-Admin-Key"))
        } finally { server.shutdown() }
    }

    @Test fun homePagesRetryWithoutSkippingAndKeepCache() = runBlocking {
        val server = MockWebServer()
        try {
            val dao = MemoryDao()
            val mediator = BackendRemoteMediator(BackendApi.create(server.url("/").toString()), dao)
            val state = PagingState<Int, DemoMovie>(emptyList(), null, PagingConfig(20), 0)
            server.enqueue(MockResponse().setBody(page(next = true)))
            assertTrue(mediator.load(LoadType.REFRESH, state) is RemoteMediator.MediatorResult.Success)
            server.enqueue(MockResponse().setResponseCode(503))
            assertTrue(mediator.load(LoadType.APPEND, state) is RemoteMediator.MediatorResult.Error)
            assertEquals("Toy Harbor", dao.movie(1001)?.title)
            server.enqueue(MockResponse().setBody(page(items = "", page = 2)))
            assertTrue((mediator.load(LoadType.APPEND, state) as RemoteMediator.MediatorResult.Success).endOfPaginationReached)
            assertEquals("/api/movies?page=1&pageSize=20", server.takeRequest().path)
            assertEquals("/api/movies?page=2&pageSize=20", server.takeRequest().path)
            assertEquals("/api/movies?page=2&pageSize=20", server.takeRequest().path)
        } finally { server.shutdown() }
    }

    @Test fun searchUsesItsEndpointAndServerIds() = runBlocking {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setBody(page(next = true)))
            server.enqueue(MockResponse().setBody(page(json.replace("1001", "1002").replace("Toy Harbor", "Toy Bay"), 2)))
            val dao = MemoryDao()
            val mediator = BackendRemoteMediator(BackendApi.create(server.url("/").toString()), dao, "Toy")
            val state = PagingState<Int, DemoMovie>(emptyList(), null, PagingConfig(20), 0)
            mediator.load(LoadType.REFRESH, state)
            mediator.load(LoadType.APPEND, state)
            assertEquals(listOf(1001,1002), dao.ids())
            assertEquals("/api/movies/search?query=Toy&page=1&pageSize=20", server.takeRequest().path)
            assertEquals("/api/movies/search?query=Toy&page=2&pageSize=20", server.takeRequest().path)
        } finally { server.shutdown() }
    }

    @Test fun eventRefetchUpdatesObservationAndDuplicatesDoNotDuplicateRows() = runBlocking {
        val server = MockWebServer()
        try {
            val dao = MemoryDao()
            val repository = BackendRepository(BackendApi.create(server.url("/").toString()), dao)
            val id = MovieUpdateEvent.movieId(mapOf("eventType" to "movie_updated", "movieId" to "1001", "updatedAt" to "2026-01-01T00:00:00Z"))!!
            repeat(2) { server.enqueue(MockResponse().setBody(json)); repository.refreshMovie(id) }
            assertEquals(1, dao.ids().size)
            assertEquals("Toy Harbor", repository.observeMovie(id).first()?.title)
            server.enqueue(MockResponse().setBody(json.replace("Toy Harbor", "Renamed").replace("2026-01-01T", "2026-02-01T")))
            repository.refreshMovie(id)
            assertEquals("Renamed", repository.observeMovie(id).first()?.title)
            server.enqueue(MockResponse().setResponseCode(503))
            assertTrue(runCatching { repository.refreshMovie(id) }.isFailure)
            assertEquals("Renamed", repository.observeMovie(id).first()?.title)
        } finally { server.shutdown() }
    }

    @Test fun rejectsWrongIdentityAndTrustMarker() = runBlocking {
        val server = MockWebServer()
        try {
            val dao = MemoryDao()
            val repo = BackendRepository(BackendApi.create(server.url("/").toString()), dao)
            server.enqueue(MockResponse().setBody(json))
            assertTrue(runCatching { repo.refreshMovie(1002) }.isFailure)
            server.enqueue(MockResponse().setBody(json.replace("synthetic-demo", "unknown")))
            assertTrue(runCatching { repo.refreshMovie(1001) }.isFailure)
            assertTrue(dao.ids().isEmpty())
        } finally { server.shutdown() }
    }

    @Test fun invalidMessagesAreIgnored() {
        val valid = mapOf("eventType" to "movie_updated", "movieId" to "9001", "updatedAt" to "2026-01-01T00:00:00.0000000Z")
        assertEquals(9001, MovieUpdateEvent.movieId(valid))
        for (id in listOf("0","-1","+1","x","2147483648")) assertNull(MovieUpdateEvent.movieId(valid + ("movieId" to id)))
        assertNull(MovieUpdateEvent.movieId(valid + ("eventType" to "other")))
        assertNull(MovieUpdateEvent.movieId(valid + ("updatedAt" to "bad")))
        assertNull(MovieUpdateEvent.movieId(emptyMap()))
    }

    private class MemoryDao : DemoMovieDao() {
        val rows = MutableStateFlow<Map<Int, DemoMovie>>(emptyMap())
        override fun pagingSource(query: String): PagingSource<Int, DemoMovie> = error("Room tested on device")
        override fun observeMovie(id: Int) = rows.map { it[id] }
        override suspend fun movie(id: Int) = rows.value[id]
        override suspend fun ids() = rows.value.keys.sorted()
        override suspend fun delete(id: Int) { rows.value = rows.value - id }
        override suspend fun upsert(movie: DemoMovie) { rows.value = rows.value + (movie.id to movie) }
    }
}
