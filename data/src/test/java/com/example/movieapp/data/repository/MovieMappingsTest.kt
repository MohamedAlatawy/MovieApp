package com.example.movieapp.data.repository

import com.example.movieapp.data.local.MovieEntity
import com.example.movieapp.data.remote.*
import com.example.movieapp.domain.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class MovieMappingsTest {
    @Test fun mappingsPreserveUsedMetadataAndKeepSearchUnchecked() {
        val row = MovieEntity(7, "Verified", "Overview", "/poster.jpg", "/unused.jpg",
            "2026-01-02", 8.5, discoveryOrder = 99, genres = "Drama", usCertifications = "PG")
        assertEquals(CachedMovie(7, "Verified", "Overview", "/poster.jpg", "2026-01-02", 8.5,
            "Drama"), row.toCachedMovie())
        assertEquals(MovieSummary(7, "Search", "/poster.jpg", "2026"),
            MovieSummaryDto(7, "Search", posterPath = "/poster.jpg", releaseDate = "2026").toMovieSummary())
        assertEquals(MovieDetails(7, "Unchecked", genres = "Drama · Comedy"),
            MovieDetailsDto(7, "Unchecked", genres = listOf(GenreDto(1, " Drama "), GenreDto(2, ""),
                GenreDto(1, "Drama"), GenreDto(3, "Comedy"))).toMovieDetails())
        assertEquals(MovieDetails(7, "Missing"), MovieDetailsDto(7, "Missing").toMovieDetails())
        assertNull(MovieDetailsDto(7, "Blank", genres = listOf(GenreDto(1, " "))).toMovieDetails().genres)
    }

    @Test fun boundaryTranslatesExpectedErrorsAndPreservesCancellation() = runBlocking {
        assertTrue(TmdbException.Network().toMovieAccessException() is MovieAccessException.Network)
        assertEquals(503, (TmdbException.Http(503).toMovieAccessException() as MovieAccessException.Http).statusCode)
        assertTrue(TmdbException.InvalidResponse().toMovieAccessException() is MovieAccessException.InvalidResponse)
        assertTrue(TmdbException.MissingToken().toMovieAccessException() is MovieAccessException.MissingConfiguration)
        assertTrue(runCatching { movieAccess { throw TmdbException.Network() } }.exceptionOrNull() is MovieAccessException.Network)
        val cancellation = CancellationException("cancelled")
        assertSame(cancellation, runCatching { movieAccess { throw cancellation } }.exceptionOrNull())
    }
}