package com.example.movieapp.data.repository

import com.example.movieapp.data.local.MovieDao
import com.example.movieapp.data.local.MovieEntity
import com.example.movieapp.data.remote.MovieReleaseDatesDto
import com.example.movieapp.data.remote.TmdbClient
import com.example.movieapp.data.remote.TmdbException
import com.example.movieapp.domain.eligibility.MovieCertificationEvidence
import com.example.movieapp.domain.eligibility.MovieEligibilityPolicy
import com.example.movieapp.domain.eligibility.ReleaseCertification

/**
 * Refreshes are not serialized. Concurrent same-movie refreshes can overwrite newer decisions.
 * Ordering protection is deferred until List/Details integration.
 * No network request runs inside a database transaction.
 */
class MovieRepository(
    private val client: TmdbClient,
    private val dao: MovieDao,
) {
    fun observeMovies() = dao.observeMovies()
    fun observeMovie(id: Int) = dao.observeMovie(id)

    /**
     * Both List and Details pass only an ID; stale candidate DTOs cannot authorize writes.
     * Transport, malformed response and identity errors propagate without changing cache.
     * Successful complete evidence that the policy denies revokes the existing row.
     * Cancellation propagates naturally.
     */
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

/** Preserve every country and release record, including blank/conflicting certifications. */
private fun MovieReleaseDatesDto.toEvidence() = MovieCertificationEvidence(
    movieId = id,
    records = results.flatMap { country ->
        country.releaseDates.map { ReleaseCertification(country.countryCode, it.certification) }
    },
)
