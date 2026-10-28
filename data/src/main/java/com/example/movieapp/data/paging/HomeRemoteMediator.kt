package com.example.movieapp.data.paging

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import com.example.movieapp.data.local.MovieDao
import com.example.movieapp.data.local.MovieEntity
import com.example.movieapp.data.remote.MoviePageDto
import com.example.movieapp.data.remote.TmdbClient
import com.example.movieapp.data.remote.TmdbException
import com.example.movieapp.data.repository.toCacheEntity
import com.example.movieapp.data.repository.toMovieAccessException
import com.example.movieapp.domain.model.MovieAccessException
import kotlinx.coroutines.CancellationException

@OptIn(ExperimentalPagingApi::class)
class HomeRemoteMediator(
    private val discoverMovies: suspend (Int) -> MoviePageDto,
    private val dao: MovieDao,
    private val invalidateEmptyPage: () -> Unit = {},
) : RemoteMediator<Int, MovieEntity>() {
    constructor(client: TmdbClient, dao: MovieDao, invalidateEmptyPage: () -> Unit = {}) :
        this(client::discoverMovies, dao, invalidateEmptyPage)

    private var nextPage = 1
    private var reachedEnd = false

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, MovieEntity>,
    ): MediatorResult {
        if (loadType == LoadType.PREPEND) return MediatorResult.Success(true)
        if (loadType == LoadType.REFRESH) {
            nextPage = 1
            reachedEnd = false
        }
        if (reachedEnd) return MediatorResult.Success(true)
        val requestedPage = nextPage
        return try {
            val page = discoverMovies(requestedPage)
            if (page.page != requestedPage || page.totalPages < 0 || page.totalResults < 0 ||
                (page.totalPages == 0 && (requestedPage != 1 || page.totalResults != 0 || page.results.isNotEmpty())) ||
                (page.totalPages > 0 && requestedPage > page.totalPages) ||
                page.results.any { it.id <= 0 } || page.results.size > page.totalResults
            ) throw TmdbException.InvalidResponse()
            dao.saveDiscoverPage(page.results.distinctBy { it.id }.map { it.toCacheEntity() })
            nextPage = requestedPage + 1
            reachedEnd = requestedPage >= minOf(page.totalPages, 500)
            // Empty nonterminal pages write no Room rows; invalidate after progress commits so Paging can request the next boundary.
            if (page.results.isEmpty() && !reachedEnd) invalidateEmptyPage()
            MediatorResult.Success(reachedEnd)
        } catch (error: CancellationException) {
            throw error
        } catch (error: MovieAccessException) {
            MediatorResult.Error(error)
        } catch (error: TmdbException) {
            MediatorResult.Error(error.toMovieAccessException())
        } catch (_: android.database.SQLException) {
            MediatorResult.Error(MovieAccessException.InvalidResponse())
        }
    }
}
