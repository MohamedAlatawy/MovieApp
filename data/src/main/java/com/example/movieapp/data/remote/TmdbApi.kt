package com.example.movieapp.data.remote

import retrofit2.http.GET
import retrofit2.Response
import retrofit2.http.Path
import retrofit2.http.Query

internal interface TmdbApi {
    @GET("discover/movie?include_adult=false&certification_country=US&certification.lte=PG-13&sort_by=popularity.desc")
    suspend fun discoverMovies(@Query("page") page: Int): Response<MoviePageDto>

    @GET("search/movie?include_adult=false")
    suspend fun searchMovies(@Query("query") query: String, @Query("page") page: Int): Response<MoviePageDto>

    @GET("movie/{movie_id}")
    suspend fun movieDetails(@Path("movie_id") movieId: Int): Response<MovieDetailsDto>

}
