package com.example.movieapp.data.paging

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import com.example.movieapp.data.local.MovieDao
import com.example.movieapp.data.local.MovieEntity
import com.example.movieapp.data.remote.TmdbClient
import com.example.movieapp.data.remote.TmdbException
import com.example.movieapp.data.repository.MovieRepository
import com.example.movieapp.domain.eligibility.MovieEligibilityPolicy.Decision

@OptIn(ExperimentalPagingApi::class)
class HomeRemoteMediator(
    private val client: TmdbClient,
    private val repository: MovieRepository,
    private val dao: MovieDao,
) : RemoteMediator<Int, MovieEntity>() {
    private var nextPage = 1
    private var reachedEnd = false
    private var refreshing = false

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, MovieEntity>,
    ): MediatorResult {
        if (loadType == LoadType.PREPEND) return MediatorResult.Success(true)
        if (loadType == LoadType.REFRESH && !refreshing) {
            nextPage = 1
            reachedEnd = false
            refreshing = true
        }
        if (reachedEnd) return MediatorResult.Success(true)

        return try {
            repeat(3) {
                val page = client.discoverMovies(nextPage)
                if (page.page != nextPage || page.totalPages < 0) throw TmdbException.InvalidResponse()
                var addedMovie = false
                var allowedMovie = false
                for (candidate in page.results) {
                    if (candidate.id <= 0) throw TmdbException.InvalidResponse()
                    val existed = dao.findMovie(candidate.id) != null
                    if (repository.refreshMovie(candidate.id) == Decision.Allow) {
                        allowedMovie = true
                        addedMovie = addedMovie || !existed
                    }
                }
                nextPage++
                reachedEnd = page.page >= minOf(page.totalPages, 500)
                if (reachedEnd || addedMovie || (loadType == LoadType.REFRESH && allowedMovie)) {
                    refreshing = false
                    return MediatorResult.Success(reachedEnd)
                }
            }
            // Pause empty-page scanning until the user requests another batch.
            MediatorResult.Error(ContinueBrowsing())
        } catch (error: TmdbException) {
            MediatorResult.Error(error)
        }
    }

    class ContinueBrowsing : Exception("No new movies in this batch. Continue browsing.")
}
