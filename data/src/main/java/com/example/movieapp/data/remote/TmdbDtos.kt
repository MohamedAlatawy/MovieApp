package com.example.movieapp.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Discover and Search return candidates, never verified eligible movies.
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

@Serializable
data class MovieReleaseDatesDto(val id: Int, val results: List<CountryReleaseDatesDto>)

@Serializable
data class CountryReleaseDatesDto(
    @SerialName("iso_3166_1") val countryCode: String,
    @SerialName("release_dates") val releaseDates: List<ReleaseDateDto>,
)

// Preserve all countries and records, including blank or absent certification.
@Serializable
data class ReleaseDateDto(
    val certification: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    val type: Int? = null,
    @SerialName("iso_639_1") val languageCode: String? = null,
    val note: String? = null,
)
