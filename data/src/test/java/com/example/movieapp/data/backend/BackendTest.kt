package com.example.movieapp.data.backend

import androidx.paging.*
import com.example.movieapp.domain.model.MovieAccessException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalPagingApi::class)
class BackendTest {
    private val json = """{"id":1001,"title":"Toy Harbor","overview":"Demo fiction","releaseDate":"2020-01-01","posterUrl":null,"voteAverage":6.0,"genres":["Family"],"adult":false,"usCertification":null,"lastModified":"2026-01-01T00:00:00Z","dataSource":"synthetic-demo","eligibilityEvidence":"unverified","isDemo":true}"""
    private fun page(items: String = json, page: Int = 1, total: Int = 31) =
        """{"items":[$items],"page":$page,"pageSize":20,"totalItems":$total,"totalPages":${(total+19)/20},"hasNextPage":${page < (total+19)/20}}"""
    private val state = PagingState<Int, DemoMovie>(emptyList(), null, PagingConfig(20), 0)

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

    @Test fun homeRetryKeepsCacheAndSameRawPage() = runBlocking {
        val server = MockWebServer()
        try {
            val dao = MemoryDao()
            val mediator = BackendRemoteMediator(BackendApi.create(server.url("/").toString()), dao)
            server.enqueue(MockResponse().setBody(page()))
            assertTrue(mediator.load(LoadType.REFRESH, state) is RemoteMediator.MediatorResult.Success)
            server.enqueue(MockResponse().setResponseCode(503))
            val error = mediator.load(LoadType.APPEND, state) as RemoteMediator.MediatorResult.Error
            assertTrue(error.throwable is MovieAccessException.Http)
            assertEquals("Toy Harbor", dao.movie(1001)?.title)
            server.enqueue(MockResponse().setBody(page("", 2)))
            assertTrue((mediator.load(LoadType.APPEND, state) as RemoteMediator.MediatorResult.Success).endOfPaginationReached)
            assertEquals(listOf("/api/movies?page=1&pageSize=20","/api/movies?page=2&pageSize=20","/api/movies?page=2&pageSize=20"),
                List(3) { server.takeRequest().path })
            assertEquals(3, server.requestCount)
        } finally { server.shutdown() }
    }

    @Test fun searchOrderingKeysEmptyAndLastPagesWithoutDetailRequests() = runBlocking {
        val server = MockWebServer()
        try {
            val source = BackendSearchPagingSource(BackendApi.create(server.url("/").toString()), "Toy")
            val second = json.replace("1001", "1002")
            server.enqueue(MockResponse().setBody(page("$second,$json,$second", total=61)))
            val first = source.load(PagingSource.LoadParams.Refresh(null,20,false)) as PagingSource.LoadResult.Page
            assertEquals(listOf(1002,1001), first.data.map { it.id })
            assertNull(first.prevKey); assertEquals(2, first.nextKey)
            server.enqueue(MockResponse().setBody(page("",2,61)))
            val empty = source.load(PagingSource.LoadParams.Append(2,20,false)) as PagingSource.LoadResult.Page
            assertTrue(empty.data.isEmpty()); assertEquals(3,empty.nextKey)
            server.enqueue(MockResponse().setBody(page(json,4,61)))
            val last = source.load(PagingSource.LoadParams.Append(4,20,false)) as PagingSource.LoadResult.Page
            assertEquals(3,last.prevKey); assertNull(last.nextKey)
            assertEquals(listOf(1,2,4), List(3) { server.takeRequest().requestUrl!!.queryParameter("page")!!.toInt() })
            assertEquals(3,server.requestCount)
        } finally { server.shutdown() }
    }

    @Test fun searchDetailsNeverAccessRoomEvenForCachedIdentity() = runBlocking {
        val server = MockWebServer()
        try {
            val dao = object : MemoryDao() {
                override suspend fun movie(id: Int): DemoMovie? = error("No Room reads")
                override fun observeMovie(id: Int): Flow<DemoMovie?> = error("No Room observation")
                override suspend fun upsert(movie: DemoMovie) = error("No Room writes")
                override fun pagingSource(): PagingSource<Int, DemoMovie> = error("No Room paging")
                override suspend fun delete(id: Int) = error("No Room delete")
            }
            val repo = BackendRepository(BackendApi.create(server.url("/").toString()),dao)
            repo.searchMovies("Toy").first()
            server.enqueue(MockResponse().setBody(json))
            assertEquals("Toy Harbor",repo.fetchSearchDetails(1001).title)
            assertEquals("/api/movies/1001",server.takeRequest().path)
            assertEquals(1,server.requestCount)
        } finally { server.shutdown() }
    }

    @Test fun existingOnlyRefreshDuplicateAndFailurePreserveRows() = runBlocking {
        val server = MockWebServer()
        try {
            val dao = MemoryDao()
            val repo = BackendRepository(BackendApi.create(server.url("/").toString()),dao)
            repo.refreshHomeMovieDetails(1001)
            assertEquals(0,server.requestCount); assertTrue(dao.ids().isEmpty())
            dao.save(listOf(DemoMovie(1001,"Cached")))
            repeat(2) { server.enqueue(MockResponse().setBody(json)); repo.refreshHomeMovieDetails(1001) }
            assertEquals(1,dao.ids().size)
            assertEquals("Toy Harbor",repo.observeCachedMovie(1001).first()?.title)
            for (code in listOf(404,503)) {
                server.enqueue(MockResponse().setResponseCode(code))
                assertTrue(runCatching { repo.refreshHomeMovieDetails(1001) }.exceptionOrNull() is MovieAccessException.Http)
                assertEquals("Toy Harbor",dao.movie(1001)?.title)
            }
        } finally { server.shutdown() }
    }

    @Test fun invalidIdentityAndTrustAndPageFailBeforeWrites() = runBlocking {
        val server = MockWebServer()
        try {
            val dao = MemoryDao()
            val api = BackendApi.create(server.url("/").toString())
            val repo = BackendRepository(api,dao)
            assertTrue(runCatching { repo.fetchSearchDetails(0) }.exceptionOrNull() is MovieAccessException.InvalidIdentity)
            assertEquals(0,server.requestCount)
            server.enqueue(MockResponse().setBody(json))
            assertTrue(runCatching { repo.fetchSearchDetails(1002) }.exceptionOrNull() is MovieAccessException.InvalidIdentity)
            server.enqueue(MockResponse().setBody(page("$json,"+json.replace("synthetic-demo","unknown"))))
            assertTrue(BackendRemoteMediator(api,dao).load(LoadType.REFRESH,state) is RemoteMediator.MediatorResult.Error)
            assertTrue(dao.ids().isEmpty())
            server.enqueue(MockResponse().setBody(page(page=2)))
            assertTrue(BackendRemoteMediator(api,dao).load(LoadType.REFRESH,state) is RemoteMediator.MediatorResult.Error)
        } finally { server.shutdown() }
    }

    @Test fun blankSearchAndPrependNeverCallNetworkAndCancellationPropagates() = runBlocking {
        val api = object : BackendApi {
            override suspend fun movie(id: Int): BackendMovieDto = throw CancellationException("cancel")
            override suspend fun movies(page: Int,size: Int): BackendPage = throw CancellationException("cancel")
            override suspend fun search(query: String,page: Int,size: Int): BackendPage = throw CancellationException("cancel")
        }
        assertTrue(BackendSearchPagingSource(api," ").load(PagingSource.LoadParams.Refresh(null,20,false)) is PagingSource.LoadResult.Page)
        val mediator = BackendRemoteMediator(api,MemoryDao())
        assertTrue((mediator.load(LoadType.PREPEND,state) as RemoteMediator.MediatorResult.Success).endOfPaginationReached)
        assertTrue(runCatching { mediator.load(LoadType.REFRESH,state) }.exceptionOrNull() is CancellationException)
        assertTrue(runCatching { BackendSearchPagingSource(api,"x").load(PagingSource.LoadParams.Refresh(null,20,false)) }.exceptionOrNull() is CancellationException)
        assertTrue(runCatching { BackendRepository(api,MemoryDao()).fetchSearchDetails(1) }.exceptionOrNull() is CancellationException)
    }

    @Test fun searchErrorsRetrySameKeyAndMalformedMapping() = runBlocking {
        val server = MockWebServer()
        try {
            val api = BackendApi.create(server.url("/").toString())
            val source = BackendSearchPagingSource(api,"Toy")
            server.enqueue(MockResponse().setResponseCode(503))
            assertTrue(source.load(PagingSource.LoadParams.Append(2,20,false)) is PagingSource.LoadResult.Error)
            server.enqueue(MockResponse().setBody(page(json,2)))
            assertTrue(source.load(PagingSource.LoadParams.Append(2,20,false)) is PagingSource.LoadResult.Page)
            assertEquals(server.takeRequest().path,server.takeRequest().path)
            server.enqueue(MockResponse().setBody("{}"))
            assertTrue(runCatching { BackendRepository(api,MemoryDao()).fetchSearchDetails(1) }.exceptionOrNull() is MovieAccessException.InvalidResponse)
        } finally { server.shutdown() }
    }

    @Test fun invalidMessagesAreIgnored() {
        val valid = mapOf("eventType" to "movie_updated","movieId" to "9001","updatedAt" to "2026-01-01T00:00:00.0000000Z")
        assertEquals(9001,MovieUpdateEvent.movieId(valid))
        for (id in listOf("0","-1","+1","x","2147483648")) assertNull(MovieUpdateEvent.movieId(valid+("movieId" to id)))
        assertNull(MovieUpdateEvent.movieId(valid+("eventType" to "other")))
        assertNull(MovieUpdateEvent.movieId(valid+("updatedAt" to "bad")))
        assertNull(MovieUpdateEvent.movieId(emptyMap()))
    }

    @Test fun failedPersistenceDoesNotAdvanceRawPage() = runBlocking {
        val server = MockWebServer()
        try {
            var fail = false
            val persistenceFailure = MovieAccessException.InvalidResponse()
            var saveAttempts = 0
            val dao = object : MemoryDao() {
                override suspend fun save(movies: List<DemoMovie>): Boolean {
                    saveAttempts++
                    // Fail before writes; real SQLite rollback is covered by DemoDatabaseTest.
                    if (fail) throw persistenceFailure
                    return super.save(movies)
                }
            }
            val mediator = BackendRemoteMediator(BackendApi.create(server.url("/").toString()),dao)
            server.enqueue(MockResponse().setBody(page()))
            val refreshed = mediator.load(LoadType.REFRESH,state) as RemoteMediator.MediatorResult.Success
            assertFalse(refreshed.endOfPaginationReached)
            assertEquals(listOf(1001),dao.ids())
            val beforeAppend = dao.rows.value.toMap()

            val pageTwo = page("${json.replace("1001","1002")},${json.replace("1001","1003")}",2)
            repeat(2) { server.enqueue(MockResponse().setBody(pageTwo)) }
            fail = true
            val failed = mediator.load(LoadType.APPEND,state) as RemoteMediator.MediatorResult.Error
            assertSame(persistenceFailure,failed.throwable)
            assertEquals(2,saveAttempts)
            assertEquals(beforeAppend,dao.rows.value)
            assertNull(dao.movie(1002))
            assertNull(dao.movie(1003))

            fail = false
            val retried = mediator.load(LoadType.APPEND,state) as RemoteMediator.MediatorResult.Success
            assertTrue(retried.endOfPaginationReached)
            assertEquals(3,saveAttempts)
            assertEquals(listOf(1001,1002,1003),dao.ids())
            assertEquals(beforeAppend[1001],dao.movie(1001))
            assertEquals(beforeAppend[1001]?.copy(id=1002),dao.movie(1002))
            assertEquals(beforeAppend[1001]?.copy(id=1003),dao.movie(1003))
            assertEquals(3,dao.rows.value.size)

            val exhausted = withTimeout(2_000) { mediator.load(LoadType.APPEND,state) } as RemoteMediator.MediatorResult.Success
            assertTrue(exhausted.endOfPaginationReached)
            assertEquals(3,saveAttempts)
            assertEquals(3,server.requestCount)
            assertEquals(listOf(1,2,2),List(3) {
                server.takeRequest().requestUrl!!.queryParameter("page")!!.toInt()
            })
        } finally { server.shutdown() }
    }

    private open class MemoryDao : DemoMovieDao() {
        val rows = MutableStateFlow<Map<Int,DemoMovie>>(emptyMap())
        override fun pagingSource(): PagingSource<Int,DemoMovie> = error("Room tested on device")
        override fun observeMovie(id: Int) = rows.map { it[id] }
        override suspend fun movie(id: Int) = rows.value[id]
        override suspend fun ids() = rows.value.keys.sorted()
        override suspend fun delete(id: Int) { rows.value = rows.value-id }
        override suspend fun upsert(movie: DemoMovie) { rows.value = rows.value+(movie.id to movie) }
    }
}
