package com.example.movieapp.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Discover and Search summaries use their endpoint-specific server filters.
@Serializable
data class MoviePageDto(
    val page: Int,
    @SerialName("total_pages") val totalPages: Int,
    @SerialName("total_results") val totalResults: Int,
    val results: List<MovieSummaryDto>,
)

@Serializable
data class MovieSummaryDto(
    val id: Int,
    val title: String,
    val overview: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("vote_average") val voteAverage: Double? = null,
    val adult: Boolean? = null,
)

@Serializable
data class MovieDetailsDto(
    val id: Int,
    val title: String,
    val overview: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("vote_average") val voteAverage: Double? = null,
    val genres: List<GenreDto>? = null,
    val adult: Boolean? = null,
)

@Serializable
data class GenreDto(val id: Int, val name: String)
