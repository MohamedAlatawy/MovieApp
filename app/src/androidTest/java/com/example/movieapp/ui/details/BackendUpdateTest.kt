package com.example.movieapp.ui.details

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.*
import androidx.work.testing.TestListenableWorkerBuilder
import android.content.Context
import com.example.movieapp.data.backend.*
import com.example.movieapp.push.MovieRefreshWorker
import com.example.movieapp.push.MovieUpdateSignal
import com.example.movieapp.push.MovieUpdates
import com.example.movieapp.domain.usecase.*
import com.example.movieapp.ui.theme.MovieAppTheme
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class BackendUpdateTest {
    @get:Rule val compose = createComposeRule()

    @Test fun searchEventRefreshesVisibleDetailsWithoutWorkerOrRoomWrites() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, DemoDatabase::class.java).build()
        val server = MockWebServer()
        val signal = MovieUpdateSignal()
        val repo = BackendRepository(BackendApi.create(server.url("/").toString()), db.movies())
        lateinit var vm: MovieDetailsViewModel
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            vm = MovieDetailsViewModel(ObserveCachedMovieUseCase(repo),GetSearchMovieDetailsUseCase(repo),
                RefreshHomeMovieDetailsUseCase(repo),signal)
        }
        fun response(id: Int,title: String) = MockResponse().setBody("""{"id":$id,"title":"$title","overview":"Server synopsis","releaseDate":"2020-01-01","posterUrl":null,"voteAverage":8.2,"genres":["Family"],"adult":false,"usCertification":null,"lastModified":"2026-10-29T00:00:00Z","dataSource":"synthetic-demo","eligibilityEvidence":"unverified","isDemo":true}""")
        try {
            db.movies().save(listOf(DemoMovie(9001,"Home remains unchanged")))
            compose.setContent {
                val state by vm.state.collectAsState()
                MovieAppTheme { MovieDetailsScreen(state, vm::close, vm::retry) }
            }
            for (id in listOf(9001,9002)) {
                server.enqueue(response(id,"Before $id"))
                compose.runOnIdle { vm.open(id,MovieOrigin.SEARCH) }
                compose.waitUntil(5_000) { compose.onAllNodesWithText("Before $id").fetchSemanticsNodes().isNotEmpty() }
                server.enqueue(response(id,"After $id"))
                // Dispatches the same valid event boundary; deliberately do not run a Home worker.
                compose.runOnIdle {
                    MovieUpdates.dispatch(mapOf("eventType" to "movie_updated","movieId" to "$id",
                        "updatedAt" to "2026-10-29T00:00:00Z"),signal) {}
                }
                compose.waitUntil(5_000) { compose.onAllNodesWithText("After $id").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithText("After $id").assertIsDisplayed()
                assertEquals("/api/movies/$id",server.takeRequest().path)
                assertEquals("/api/movies/$id",server.takeRequest().path)
            }
            assertEquals(listOf(9001),db.movies().ids())
            assertEquals("Home remains unchanged",db.movies().movie(9001)?.title)
            assertEquals(4,server.requestCount)
        } finally {
            compose.runOnIdle { androidx.lifecycle.ViewModelStore().apply { put("details",vm); clear() } }
            server.shutdown(); db.close()
        }
    }

    @Test fun workerMissingRowIsNoOpAndNetworkFailureRetries() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, DemoDatabase::class.java).build()
        var requests = 0
        val api = object : BackendApi {
            override suspend fun movies(page: Int,size: Int): BackendPage = error("No paging")
            override suspend fun search(query: String,page: Int,size: Int): BackendPage = error("No search")
            override suspend fun movie(id: Int): BackendMovieDto { requests++; throw java.io.IOException() }
        }
        val repo = BackendRepository(api,db.movies())
        val factory = object : WorkerFactory() {
            override fun createWorker(appContext: Context,workerClassName: String,workerParameters: WorkerParameters) =
                MovieRefreshWorker(appContext,workerParameters,repo)
        }
        try {
            fun worker(attempt: Int = 0) = TestListenableWorkerBuilder<MovieRefreshWorker>(context)
                .setWorkerFactory(factory).setInputData(workDataOf("movieId" to 9001)).setRunAttemptCount(attempt).build()
            assertEquals(ListenableWorker.Result.success(),worker().doWork())
            assertEquals(0,requests)
            db.movies().save(listOf(DemoMovie(9001,"Cached")))
            assertEquals(ListenableWorker.Result.retry(),worker().doWork())
            assertEquals(ListenableWorker.Result.failure(),worker(3).doWork())
            assertEquals("Cached",db.movies().movie(9001)?.title)
        } finally { db.close() }
    }

    @Test fun simulatedEventRefetchesBackendAndUpdatesRoomAndVisibleDetails() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, DemoDatabase::class.java).build()
        val server = MockWebServer()
        try {
            db.movies().save(listOf(DemoMovie(9001,"Before update")))
            val repo = BackendRepository(BackendApi.create(server.url("/").toString()), db.movies())
            compose.setContent {
                val movie by repo.observeCachedMovie(9001).collectAsState(initial = null)
                MovieAppTheme { MovieDetailsScreen(MovieDetailsState(movie?.toDetailsContent(), loading = false), {}, {}) }
            }
            compose.waitUntil(5_000) { compose.onAllNodesWithText("Before update").fetchSemanticsNodes().isNotEmpty() }
            val id = MovieUpdateEvent.movieId(mapOf("eventType" to "movie_updated", "movieId" to "9001",
                "updatedAt" to "2026-10-28T00:00:00Z", "title" to "Untrusted payload title"))!!
            server.enqueue(MockResponse().setBody("""{"id":9001,"title":"Backend updated title","overview":"Server synopsis","releaseDate":"2020-01-01","posterUrl":null,"voteAverage":8.2,"genres":["Family"],"adult":false,"usCertification":null,"lastModified":"2026-10-28T00:00:00Z","dataSource":"synthetic-demo","eligibilityEvidence":"unverified","isDemo":true}"""))
            val factory = object : WorkerFactory() {
                override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters) =
                    MovieRefreshWorker(appContext, workerParameters, repo)
            }
            val worker = TestListenableWorkerBuilder<MovieRefreshWorker>(context)
                .setWorkerFactory(factory).setInputData(workDataOf("movieId" to id)).build()
            assertEquals(ListenableWorker.Result.success(), worker.doWork())
            assertEquals("/api/movies/9001", server.takeRequest().path)
            assertEquals("Backend updated title", db.movies().movie(9001)?.title)
            compose.waitUntil(5_000) { compose.onAllNodesWithText("Backend updated title").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Backend updated title").assertIsDisplayed()
            compose.onNodeWithText("Untrusted payload title").assertDoesNotExist()
            compose.onNodeWithText("Demo Data · US certification unverified").performScrollTo().assertIsDisplayed()
            Unit
        } finally { compose.waitForIdle(); server.shutdown(); db.close() }
    }
}
