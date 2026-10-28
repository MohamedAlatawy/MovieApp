package com.example.movieapp.domain.repository

import androidx.paging.PagingData
import com.example.movieapp.domain.model.MovieDetails
import com.example.movieapp.domain.model.MovieSummary
import com.example.movieapp.domain.model.CachedMovie
import kotlinx.coroutines.flow.Flow

/**
 * Home uses persisted Discover metadata; legacy cached rows are retained.
 * [observeCachedMovie] emits null when the requested cached row is absent.
 * Search results and [fetchSearchDetails] are network-backed and never read or write the cache.
 * Coroutine cancellation propagates rather than becoming an ordinary access failure.
 */
interface MovieRepository {
    fun homeMovies(): Flow<PagingData<CachedMovie>>
    fun searchMovies(query: String): Flow<PagingData<MovieSummary>>
    fun observeCachedMovie(id: Int): Flow<CachedMovie?>
    /** Enriches an existing cached movie only; never inserts an absent ID. */
    suspend fun refreshHomeMovieDetails(id: Int)
    suspend fun fetchSearchDetails(movieId: Int): MovieDetails
}
