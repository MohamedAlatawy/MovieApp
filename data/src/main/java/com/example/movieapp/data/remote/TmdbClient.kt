package com.example.movieapp.data.remote

import com.example.movieapp.data.BuildConfig
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/** Small remote entry point. Returned movies and certification records are untrusted data. */
class TmdbClient private constructor(
    private val api: TmdbApi,
    private val tokenConfigured: Boolean,
) {
    suspend fun discoverMovies(page: Int = 1): MoviePageDto = request { api.discoverMovies(page) }

    suspend fun searchMovies(query: String, page: Int = 1): MoviePageDto =
        request { api.searchMovies(query, page) }

    suspend fun movieDetails(movieId: Int): MovieDetailsDto = request { api.movieDetails(movieId) }

    suspend fun movieReleaseDates(movieId: Int): MovieReleaseDatesDto =
        request { api.movieReleaseDates(movieId) }

    private suspend fun <T> request(call: suspend () -> Response<T>): T {
        if (!tokenConfigured) throw TmdbException.MissingToken()
        return try {
            val response = call()
            if (!response.isSuccessful) {
                response.errorBody()?.close()
                throw TmdbException.Http(response.code())
            }
            response.body() ?: throw TmdbException.InvalidResponse()
        } catch (error: CancellationException) {
            throw error
        } catch (_: SerializationException) {
            throw TmdbException.InvalidResponse()
        } catch (_: IOException) {
            throw TmdbException.Network()
        }
    }

    companion object {
        /** Override token/base URL for deterministic local tests; never log the token. */
        fun create(
            token: String = BuildConfig.TMDB_READ_ACCESS_TOKEN,
            baseUrl: HttpUrl = "https://api.themoviedb.org/3/".toHttpUrl(),
        ): TmdbClient {
            val bearerToken = token.trim()
            require(bearerToken.all { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it in "._-" }) {
                "TMDb token contains unsupported characters."
            }
            val httpClient = OkHttpClient.Builder()
                .retryOnConnectionFailure(false)
                .addInterceptor { chain ->
                    chain.proceed(
                        chain.request().newBuilder()
                            .header("Authorization", "Bearer $bearerToken")
                            .build(),
                    )
                }
                .build()
            val json = Json { ignoreUnknownKeys = true }
            val api = Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(httpClient)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(TmdbApi::class.java)
            return TmdbClient(api, bearerToken.isNotEmpty())
        }
    }
}
