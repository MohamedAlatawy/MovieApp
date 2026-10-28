package com.example.movieapp.domain.usecase

import androidx.paging.PagingData
import com.example.movieapp.domain.model.*
import com.example.movieapp.domain.repository.MovieRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test

class MovieUseCasesTest {
    @Test fun operationsKeepFlowsSeparateAndPreserveValuesAndErrors() = runBlocking {
        val repository = RecordingRepository()
        assertSame(repository.home, GetHomeMoviesUseCase(repository)())
        assertSame(repository.search, SearchMoviesUseCase(repository)("query"))
        assertEquals("query", repository.query)
        assertSame(repository.observed, ObserveCachedMovieUseCase(repository)(7))
        assertEquals(listOf("observe:7"), repository.calls)
        assertEquals(Unit, RefreshHomeMovieDetailsUseCase(repository)(7))
        assertEquals(MovieDetails(9, "Search"), GetSearchMovieDetailsUseCase(repository)(9))
        assertEquals(listOf("observe:7", "refresh:7", "details:9"), repository.calls)
        val failure = MovieAccessException.Network()
        repository.failure = failure
        assertSame(failure, runCatching { GetSearchMovieDetailsUseCase(repository)(9) }.exceptionOrNull())
    }

    @Test fun cancellationReachesTheRepositoryWithoutConversion() = runBlocking {
        val started = CompletableDeferred<Unit>()
        var cancelled = false
        val repository = object : RecordingRepository() {
            override suspend fun fetchSearchDetails(movieId: Int): MovieDetails {
                started.complete(Unit)
                try { awaitCancellation() } finally { cancelled = true }
            }
        }
        val work = launch { GetSearchMovieDetailsUseCase(repository)(7) }
        started.await()
        work.cancelAndJoin()
        assertTrue(cancelled)
    }

    private open class RecordingRepository : MovieRepository {
        val home = flowOf(PagingData.empty<CachedMovie>())
        val search = flowOf(PagingData.empty<MovieSummary>())
        val observed = flowOf<CachedMovie?>(null)
        val calls = mutableListOf<String>()
        var query = ""
        var failure: MovieAccessException? = null
        override fun homeMovies() = home
        override fun searchMovies(query: String) = search.also { this.query = query }
        override fun observeCachedMovie(id: Int) = observed.also { calls += "observe:$id" }
        override suspend fun refreshHomeMovieDetails(id: Int) = Unit.also { calls += "refresh:$id" }
        override suspend fun fetchSearchDetails(movieId: Int): MovieDetails {
            calls += "details:$movieId"
            failure?.let { throw it }
            return MovieDetails(movieId, "Search")
        }
    }
}
