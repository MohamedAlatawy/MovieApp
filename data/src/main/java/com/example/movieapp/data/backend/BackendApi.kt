package com.example.movieapp.data.backend

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.time.Instant
import java.time.LocalDate

@Serializable
data class BackendMovieDto(
    val id: Int, val title: String, val overview: String, val releaseDate: String,
    val posterUrl: String? = null, val voteAverage: Double, val genres: List<String>,
    val adult: Boolean, val usCertification: String? = null, val lastModified: String,
    val dataSource: String, val eligibilityEvidence: String, val isDemo: Boolean,
) {
    fun toMovie(): DemoMovie {
        require(id > 0 && title.isNotBlank() && voteAverage.isFinite() && voteAverage in 0.0..10.0)
        require(isDemo && dataSource == "synthetic-demo" && eligibilityEvidence == "unverified")
        LocalDate.parse(releaseDate)
        Instant.parse(lastModified)
        return DemoMovie(id, title, overview, posterUrl, releaseDate, voteAverage,
            genres.joinToString(" · "), lastModified)
    }
}

@Serializable
data class BackendPage(val items: List<BackendMovieDto>, val page: Int, val pageSize: Int,
    val totalItems: Int, val totalPages: Int, val hasNextPage: Boolean)

interface BackendApi {
    @GET("api/movies") suspend fun movies(@Query("page") page: Int, @Query("pageSize") size: Int): BackendPage
    @GET("api/movies/search") suspend fun search(@Query("query") query: String,
        @Query("page") page: Int, @Query("pageSize") size: Int): BackendPage
    @GET("api/movies/{id}") suspend fun movie(@Path("id") id: Int): BackendMovieDto

    companion object {
        fun create(baseUrl: String): BackendApi = Retrofit.Builder().baseUrl(baseUrl)
            .client(OkHttpClient.Builder().build())
            .addConverterFactory(Json { ignoreUnknownKeys = true }.asConverterFactory("application/json".toMediaType()))
            .build().create(BackendApi::class.java)
    }
}
