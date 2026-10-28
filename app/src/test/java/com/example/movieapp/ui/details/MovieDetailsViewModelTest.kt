package com.example.movieapp.ui.details

import com.example.movieapp.domain.model.CachedMovie
import com.example.movieapp.domain.model.MovieAccessException
import com.example.movieapp.domain.repository.MovieRepository
import com.example.movieapp.domain.model.MovieDetails
import com.example.movieapp.domain.model.MovieSummary
import com.example.movieapp.domain.usecase.*
import androidx.paging.PagingData
import kotlinx.coroutines.flow.Flow
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
    private val movie = CachedMovie(42, "Verified", "Synopsis", null, "2026-01-01", 8.0)

    private fun model(
        observe: (Int) -> Flow<CachedMovie?>,
        details: suspend (Int) -> MovieDetails,
        refresh: suspend (Int) -> Unit,
    ): MovieDetailsViewModel {
        val repository = object : MovieRepository {
            override fun homeMovies(): Flow<PagingData<CachedMovie>> = error("Unexpected Home paging")
            override fun searchMovies(query: String): Flow<PagingData<MovieSummary>> = error("Unexpected Search paging")
            override fun observeCachedMovie(id: Int) = observe(id)
            override suspend fun refreshHomeMovieDetails(id: Int) = refresh(id)
            override suspend fun fetchSearchDetails(movieId: Int) = details(movieId)
        }
        return MovieDetailsViewModel(ObserveCachedMovieUseCase(repository),
            GetSearchMovieDetailsUseCase(repository), RefreshHomeMovieDetailsUseCase(repository))
    }
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun networkFailurePreservesCachedContentAndRetryUpdatesIt() = runTest(dispatcher) {
        val cache = MutableStateFlow<CachedMovie?>(movie)
        var fail = true
        val model = model({ cache }, { error("Unexpected Search") }) {
            if (fail) throw MovieAccessException.Network()
            cache.value = movie.copy(title = "Fresh verified")
            Unit
        }
        model.open(42)
        advanceUntilIdle()
        assertEquals(movie.toDetailsContent(), model.state.value.movie)
        assertTrue(model.state.value.error)
        fail = false
        model.retry()
        advanceUntilIdle()
        assertEquals("Fresh verified", model.state.value.movie?.title)
        assertFalse(model.state.value.error)
        model.close()
    }

    @Test fun noCacheFailureDoesNotAuthorizeAndInvalidIdDoesNotRequest() = runTest(dispatcher) {
        var calls = 0
        val model = model({ MutableStateFlow(null) }, { error("Unexpected Search") }) {
            calls++
            throw MovieAccessException.Network()
        }
        model.open(-1)
        runCurrent()
        assertEquals(0, calls)
        assertTrue(model.state.value.error)
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
        val model = model({ id -> MutableStateFlow(if (id == 42) movie else null) }, { error("Unexpected Search") }) { id ->
            calls++
            if (id == 42) try { awaitCancellation() } finally { cancelled = true }
            Unit
        }
        model.open(42)
        runCurrent()
        assertEquals(movie.toDetailsContent(), model.state.value.movie)
        assertTrue(model.state.value.loading)
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
    @Test fun searchRetriesWithoutRoomAndKeepsLoadedMetadataOnRefreshFailure() = runTest(dispatcher) {
        var fail = true
        var requests = 0
        val model = model(
            { error("Search must not observe Room") },
            { id ->
                requests++
                if (fail) throw MovieAccessException.Network()
                com.example.movieapp.domain.model.MovieDetails(id, "Uncertified", overview = "Synopsis")
            },
            { error("Search must not verify or persist") },
        )
        model.open(42, MovieOrigin.SEARCH)
        assertTrue(model.state.value.loading)
        advanceUntilIdle()
        assertTrue(model.state.value.error)
        assertNull(model.state.value.movie)
        fail = false
        model.retry()
        advanceUntilIdle()
        assertEquals("Uncertified", model.state.value.movie?.title)
        model.open(42, MovieOrigin.SEARCH)
        advanceUntilIdle()
        assertEquals(2, requests)
        fail = true
        model.retry()
        advanceUntilIdle()
        assertEquals("Uncertified", model.state.value.movie?.title)
        assertTrue(model.state.value.error)
        model.close()
    }

    @Test fun changingIdOrOriginCancelsSearchAndUsesHomeForTheSameId() = runTest(dispatcher) {
        val cancelled = mutableListOf<Int>()
        var homeCalls = 0
        val model = model(
            { MutableStateFlow(movie) },
            { id -> try { awaitCancellation() } finally { cancelled += id } },
            { homeCalls++; Unit },
        )
        model.open(42, MovieOrigin.SEARCH)
        runCurrent()
        model.open(43, MovieOrigin.SEARCH)
        runCurrent()
        assertEquals(listOf(42), cancelled)
        assertNull(model.state.value.movie)
        model.open(43, MovieOrigin.HOME)
        advanceUntilIdle()
        assertEquals(listOf(42, 43), cancelled)
        assertEquals(1, homeCalls)
        assertEquals(MovieOrigin.HOME, model.state.value.origin)
        assertEquals(movie.toDetailsContent(), model.state.value.movie)
        model.close()
    }}