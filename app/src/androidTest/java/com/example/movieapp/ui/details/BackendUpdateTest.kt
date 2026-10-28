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
import com.example.movieapp.ui.theme.MovieAppTheme
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class BackendUpdateTest {
    @get:Rule val compose = createComposeRule()

    @Test fun simulatedEventRefetchesBackendAndUpdatesRoomAndVisibleDetails() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, DemoDatabase::class.java).build()
        val server = MockWebServer()
        try {
            db.movies().save(listOf(DemoMovie(9001,"Before update")))
            val repo = BackendRepository(BackendApi.create(server.url("/").toString()), db.movies())
            compose.setContent {
                val movie by repo.observeMovie(9001).collectAsState(initial = null)
                MovieAppTheme { MovieDetailsScreen(MovieDetailsState(movie, loading = false), {}, {}) }
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
