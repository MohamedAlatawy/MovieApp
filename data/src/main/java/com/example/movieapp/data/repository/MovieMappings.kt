package com.example.movieapp.data.repository

import com.example.movieapp.data.local.MovieEntity
import com.example.movieapp.data.remote.MovieDetailsDto
import com.example.movieapp.data.remote.MovieSummaryDto
import com.example.movieapp.data.remote.TmdbException
import com.example.movieapp.domain.model.MovieAccessException
import com.example.movieapp.domain.model.MovieDetails
import com.example.movieapp.domain.model.MovieSummary
import com.example.movieapp.domain.model.CachedMovie

fun MovieEntity.toCachedMovie() = CachedMovie(
    id, title, overview, posterPath, releaseDate, voteAverage, genres,
)

internal fun MovieSummaryDto.toMovieSummary() = MovieSummary(id, title, posterPath, releaseDate)

internal fun MovieDetailsDto.toMovieDetails() = MovieDetails(
    id, title, overview, posterPath, releaseDate, voteAverage,
    genres?.map { it.name.trim() }?.filter { it.isNotEmpty() }
        ?.distinct()?.joinToString(" · ")?.takeIf { it.isNotEmpty() },
)

internal fun TmdbException.toMovieAccessException(): MovieAccessException = when (this) {
    is TmdbException.Network -> MovieAccessException.Network()
    is TmdbException.Http -> MovieAccessException.Http(statusCode)
    is TmdbException.InvalidResponse -> MovieAccessException.InvalidResponse()
    is TmdbException.MissingToken -> MovieAccessException.MissingConfiguration()
}

internal suspend fun <T> movieAccess(block: suspend () -> T): T = try {
    block()
} catch (error: TmdbException) {
    throw error.toMovieAccessException()
} catch (_: android.database.SQLException) {
    throw MovieAccessException.InvalidResponse()
}
internal fun MovieSummaryDto.toCacheEntity() = MovieEntity(
    id, title, overview, posterPath, backdropPath, releaseDate, voteAverage,
)

internal fun MovieDetailsDto.toCacheEntity() = MovieEntity(
    id, title, overview, posterPath, backdropPath, releaseDate, voteAverage,
    genres = genres?.map { it.name.trim() }?.filter { it.isNotEmpty() }
        ?.distinct()?.joinToString(" · ")?.takeIf { it.isNotEmpty() },
)
