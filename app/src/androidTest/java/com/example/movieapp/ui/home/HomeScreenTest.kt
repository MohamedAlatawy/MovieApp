package com.example.movieapp.ui.home

import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.movieapp.data.local.MovieDatabase
import com.example.movieapp.data.paging.HomeRemoteMediator
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.LazyPagingItems
import com.example.movieapp.data.local.MovieEntity
import com.example.movieapp.data.remote.TmdbException
import com.example.movieapp.ui.theme.MovieAppTheme
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test

class HomeScreenTest {
    @get:Rule val compose = createComposeRule()
    private val idle = LoadState.NotLoading(true)

    @Test fun localRefreshDoesNotChangeNetworkFeedbackOrMoviePosition() {
        val row = MovieEntity(1, "Cached movie", null, null, null, "2026", null)
        fun page(source: LoadState, remote: LoadState) = PagingData.from(
            listOf(row),
            sourceLoadStates = LoadStates(source, idle, idle),
            mediatorLoadStates = LoadStates(remote, idle, idle),
        )
        val flow = MutableStateFlow(page(idle, idle))
        lateinit var movies: LazyPagingItems<MovieEntity>
        compose.setContent {
            movies = flow.collectAsLazyPagingItems()
            MovieAppTheme { HomeScreen(movies) }
        }
        compose.onNodeWithText("Cached movie").assertIsDisplayed()
        val top = compose.onNodeWithText("Cached movie").fetchSemanticsNode().boundsInRoot.top
        repeat(3) {
            flow.value = page(LoadState.Loading, idle)
            compose.waitUntil(5_000) { movies.loadState.source.refresh is LoadState.Loading }
            compose.onNodeWithText("Refresh").assertIsEnabled()
            compose.onAllNodes(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertCountEquals(0)
            assertEquals(top, compose.onNodeWithText("Cached movie").fetchSemanticsNode().boundsInRoot.top, 2f)
            flow.value = page(idle, idle)
            compose.waitUntil(5_000) { movies.loadState.source.refresh is LoadState.NotLoading }
        }
        flow.value = page(idle, LoadState.Loading)
        compose.waitUntil(5_000) { movies.loadState.mediator?.refresh is LoadState.Loading }
        compose.onNodeWithText("Refresh").assertIsNotEnabled()
        compose.onAllNodes(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertCountEquals(1)
        assertEquals(top, compose.onNodeWithText("Cached movie").fetchSemanticsNode().boundsInRoot.top, 2f)
        flow.value = page(idle, idle)
        compose.waitUntil(5_000) { movies.loadState.mediator?.refresh is LoadState.NotLoading }
        compose.onNodeWithText("Refresh").assertIsEnabled()
        assertEquals(top, compose.onNodeWithText("Cached movie").fetchSemanticsNode().boundsInRoot.top, 2f)
    }

    @OptIn(ExperimentalPagingApi::class)
    @Test fun continueKeepsViewportDuringRoomUpdate() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, MovieDatabase::class.java).build()
        val allowWrite = CompletableDeferred<Unit>()
        val finishLoad = CompletableDeferred<Unit>()
        val retryStarted = CompletableDeferred<Unit>()
        val written = CompletableDeferred<Unit>()
        try {
            val dao = database.movieDao()
            val rows = (1..80).map { MovieEntity(it, "Movie %03d".format(it), null, null, null, "2026", null) }
            rows.forEach { dao.saveVerified(it) }
            var paused = false
            val mediator = object : RemoteMediator<Int, MovieEntity>() {
                override suspend fun load(loadType: LoadType, state: PagingState<Int, MovieEntity>): MediatorResult {
                    if (loadType == LoadType.REFRESH) return MediatorResult.Success(false)
                    if (loadType == LoadType.PREPEND) return MediatorResult.Success(true)
                    if (!paused) {
                        paused = true
                        return MediatorResult.Error(HomeRemoteMediator.ContinueBrowsing())
                    }
                    retryStarted.complete(Unit)
                    allowWrite.await()
                    dao.saveVerified(rows.first())
                    dao.saveVerified(MovieEntity(81, "New movie", null, null, null, "2026", null))
                    written.complete(Unit)
                    finishLoad.await()
                    return MediatorResult.Success(true)
                }
            }
            val generations = java.util.concurrent.atomic.AtomicInteger()
            val flow = Pager(PagingConfig(20, enablePlaceholders = true),
                remoteMediator = mediator, pagingSourceFactory = {
                    generations.incrementAndGet()
                    dao.pagingSource()
                }).flow
            lateinit var movies: LazyPagingItems<MovieEntity>
            compose.setContent {
                movies = flow.collectAsLazyPagingItems()
                MovieAppTheme { HomeScreen(movies) }
            }
            compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Continue browsing"))
            val before = compose.onNodeWithText("Movie 080").fetchSemanticsNode().boundsInRoot.top
            compose.onNodeWithText("Continue browsing").performClick()
            compose.waitUntil(5_000) { retryStarted.isCompleted }
            compose.waitForIdle()
            val loading = compose.onNodeWithText("Movie 080").fetchSemanticsNode().boundsInRoot.top
            assertEquals("Footer transition moved the last movie", before, loading, 2f)
            val snapshot = movies.itemSnapshotList
            allowWrite.complete(Unit)
            compose.waitUntil(5_000) { written.isCompleted }
            compose.waitUntil(5_000) { generations.get() > 1 && movies.itemSnapshotList !== snapshot &&
                movies.loadState.source.refresh is LoadState.NotLoading }
            compose.waitForIdle()
            val after = compose.onNodeWithText("Movie 080").fetchSemanticsNode().boundsInRoot.top
            assertEquals("Room insertion moved the last movie", before, after, 2f)
            finishLoad.complete(Unit)
            compose.waitUntil(5_000) { movies.loadState.mediator?.append is LoadState.NotLoading }
            compose.waitForIdle()
            val finished = compose.onNodeWithText("Movie 080").fetchSemanticsNode().boundsInRoot.top
            assertEquals("Finished load moved the last movie", before, finished, 2f)
        } finally {
            allowWrite.complete(Unit)
            finishLoad.complete(Unit)
            compose.waitForIdle()
            database.close()
        }
    }

    private fun show(rows: List<MovieEntity> = emptyList(), refresh: LoadState = idle,
                     append: LoadState = idle) {
        val flow = flowOf(PagingData.from(rows,
            sourceLoadStates = LoadStates(idle, idle, idle),
            mediatorLoadStates = LoadStates(refresh, idle, append)))
        compose.setContent { MovieAppTheme { HomeScreen(flow.collectAsLazyPagingItems()) } }
    }

    @Test fun emptyShowsRefreshAction() {
        show()
        compose.onNodeWithText("No movies yet").assertIsDisplayed()
        compose.onNodeWithText("Refresh movies").assertHasClickAction()
    }

    @Test fun initialLoadingShowsProgress() {
        show(refresh = LoadState.Loading)
        compose.onNodeWithText("Finding movies for you").assertIsDisplayed()
    }

    @Test fun missingTokenShowsConfigurationErrorAndRetry() {
        show(refresh = LoadState.Error(TmdbException.MissingToken()))
        compose.onNodeWithText("Movie service is not configured.", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Retry").assertHasClickAction()
    }

    @Test fun cachedMovieStaysVisibleWithAppendError() {
        show(listOf(MovieEntity(1, "Cached movie", null, null, null, "2026-01-01", null)),
            append = LoadState.Error(TmdbException.Network()))
        compose.onNodeWithText("Cached movie").assertIsDisplayed()
        compose.onNodeWithText("2026").assertIsDisplayed()
        compose.onNodeWithText("Retry").assertExists()
    }
}
