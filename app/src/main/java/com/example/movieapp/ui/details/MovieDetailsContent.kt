package com.example.movieapp.ui.details

import com.example.movieapp.domain.model.CachedMovie
import com.example.movieapp.domain.model.MovieDetails

enum class MovieOrigin { HOME, SEARCH }

data class MovieDetailsContent(
    val id: Int, val title: String, val overview: String?, val posterPath: String?,
    val releaseDate: String?, val voteAverage: Double?, val genres: String?,
    val lastModified: String?,
)
internal fun CachedMovie.toDetailsContent() =
    MovieDetailsContent(id, title, overview, posterPath, releaseDate, voteAverage, genres, lastModified)
internal fun MovieDetails.toDetailsContent() =
    MovieDetailsContent(id, title, overview, posterPath, releaseDate, voteAverage, genres, lastModified)
