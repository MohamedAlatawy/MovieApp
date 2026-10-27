package com.example.movieapp.data.repository

import com.example.movieapp.data.local.MovieDao
import com.example.movieapp.data.local.MovieEntity
import com.example.movieapp.data.remote.MovieReleaseDatesDto
import com.example.movieapp.data.remote.TmdbClient
import com.example.movieapp.data.remote.TmdbException
import com.example.movieapp.domain.eligibility.MovieCertificationEvidence
import com.example.movieapp.domain.eligibility.MovieEligibilityPolicy
import com.example.movieapp.domain.eligibility.ReleaseCertification

class MovieRepository(
    private val client: TmdbClient,
    private val dao: MovieDao,
) {
    fun observeMovies() = dao.observeMovies()
    fun observeMovie(id: Int) = dao.observeMovie(id)

    /** Denied eligibility revokes cached content; request failures preserve it. */
    suspend fun refreshMovie(id: Int): MovieEligibilityPolicy.Decision {
        require(id > 0) { "Movie ID must be positive." }
        val details = client.movieDetails(id)
        val releases = client.movieReleaseDates(id)
        if (details.id != id || releases.id != id) throw TmdbException.InvalidResponse()
        val decision = MovieEligibilityPolicy.evaluate(id, details.adult, releases.toEvidence())
        if (decision == MovieEligibilityPolicy.Decision.Allow) {
            dao.upsert(MovieEntity(
                id = details.id,
                title = details.title,
                overview = details.overview,
                posterPath = details.posterPath,
                backdropPath = details.backdropPath,
                releaseDate = details.releaseDate,
                voteAverage = details.voteAverage,
            ))
        } else {
            dao.delete(id)
        }
        return decision
    }
}

private fun MovieReleaseDatesDto.toEvidence() = MovieCertificationEvidence(
    movieId = id,
    records = results.flatMap { country ->
        country.releaseDates.map { ReleaseCertification(country.countryCode, it.certification) }
    },
)
