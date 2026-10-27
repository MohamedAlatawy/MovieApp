package com.example.movieapp.ui.details

import com.example.movieapp.data.local.MovieEntity
import com.example.movieapp.data.remote.TmdbException
import com.example.movieapp.domain.eligibility.MovieEligibilityPolicy.Decision
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MovieDetailsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val movie = MovieEntity(42, "Verified", "Synopsis", null, null, "2026-01-01", 8.0)

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun networkFailurePreservesCachedContentAndRetryUpdatesIt() = runTest(dispatcher) {
        val cache = MutableStateFlow<MovieEntity?>(movie)
        var fail = true
        val model = MovieDetailsViewModel({ cache }) {
            if (fail) throw TmdbException.Network()
            cache.value = movie.copy(title = "Fresh verified")
            Decision.Allow
        }
        model.open(42)
        advanceUntilIdle()
        assertEquals(movie, model.state.value.movie)
        assertTrue(model.state.value.error)
        fail = false
        model.retry()
        advanceUntilIdle()
        assertEquals("Fresh verified", model.state.value.movie?.title)
        assertFalse(model.state.value.error)
        model.close()
    }

    @Test fun deniedAndExternallyRevokedMoviesDisappear() = runTest(dispatcher) {
        val cache = MutableStateFlow<MovieEntity?>(movie)
        var deny = false
        val model = MovieDetailsViewModel({ cache }) {
            if (deny) { cache.value = null; Decision.Deny } else Decision.Allow
        }
        model.open(42)
        advanceUntilIdle()
        cache.value = null
        runCurrent()
        assertNull(model.state.value.movie)
        cache.value = movie
        runCurrent()
        deny = true
        model.retry()
        advanceUntilIdle()
        assertNull(model.state.value.movie)
        assertTrue(model.state.value.denied)
        model.close()
    }

    @Test fun noCacheFailureDoesNotAuthorizeAndInvalidIdDoesNotRequest() = runTest(dispatcher) {
        var calls = 0
        val model = MovieDetailsViewModel({ MutableStateFlow(null) }) {
            calls++
            throw TmdbException.Network()
        }
        model.open(-1)
        runCurrent()
        assertEquals(0, calls)
        assertTrue(model.state.value.denied)
        model.open(42)
        advanceUntilIdle()
        assertEquals(1, calls)
        assertNull(model.state.value.movie)
        assertTrue(model.state.value.error)
        model.close()
    }

    @Test fun changingMovieCancelsPreviousRefreshAndClearsPreviousContent() = runTest(dispatcher) {
        var cancelled = false
        var calls = 0
        val model = MovieDetailsViewModel({ id -> MutableStateFlow(if (id == 42) movie else null) }) { id ->
            calls++
            if (id == 42) try { awaitCancellation() } finally { cancelled = true }
            Decision.Allow
        }
        model.open(42)
        runCurrent()
        model.open(42)
        runCurrent()
        assertEquals(1, calls)
        model.open(43)
        advanceUntilIdle()
        assertTrue(cancelled)
        assertNull(model.state.value.movie)
        assertEquals(2, calls)
        model.close()
    }
}