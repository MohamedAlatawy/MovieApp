package com.example.movieapp.data.backend

import com.example.movieapp.domain.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException

internal fun validateId(id: Int) {
    if (id <= 0) throw MovieAccessException.InvalidIdentity()
}

internal fun BackendPage.validatedMovies(requested: Int): List<DemoMovie> {
    require(requested in 1..1_000_000 && page == requested && pageSize == 20)
    require(totalItems >= 0 && totalPages >= 0)
    require(totalPages.toLong() == (totalItems.toLong() + pageSize - 1) / pageSize)
    require(hasNextPage == (page < totalPages))
    require(items.size <= pageSize && items.size <= totalItems)
    require(page <= totalPages || items.isEmpty())
    // Validate every item before deduplication or opening a Room transaction.
    return items.map { it.toMovie() }.distinctBy { it.id }
}

internal fun DemoMovie.toCachedMovie() =
    CachedMovie(id, title, overview, posterPath, releaseDate, voteAverage, genres, lastModified)
internal fun DemoMovie.toDetails() =
    MovieDetails(id, title, overview, posterPath, releaseDate, voteAverage, genres, lastModified)

internal suspend fun <T> backendAccess(block: suspend () -> T): T = try {
    block()
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: MovieAccessException) {
    throw error
} catch (error: HttpException) {
    throw MovieAccessException.Http(error.code())
} catch (_: SerializationException) {
    throw MovieAccessException.InvalidResponse()
} catch (_: IOException) {
    throw MovieAccessException.Network()
} catch (_: IllegalArgumentException) {
    throw MovieAccessException.InvalidResponse()
} catch (_: java.time.format.DateTimeParseException) {
    throw MovieAccessException.InvalidResponse()
} catch (_: android.database.SQLException) {
    throw MovieAccessException.InvalidResponse()
}
