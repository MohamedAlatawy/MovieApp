package com.example.movieapp.domain.model

data class MovieSummary(val id: Int, val title: String, val posterPath: String? = null, val releaseDate: String? = null)

data class MovieDetails(
    val id: Int,
    val title: String,
    val overview: String? = null,
    val posterPath: String? = null,
    val releaseDate: String? = null,
    val voteAverage: Double? = null,
    val genres: String? = null,
    val lastModified: String? = null,
)

data class CachedMovie(
    val id: Int,
    val title: String,
    val overview: String? = null,
    val posterPath: String? = null,
    val releaseDate: String? = null,
    val voteAverage: Double? = null,
    val genres: String? = null,
    val lastModified: String? = null,
)
