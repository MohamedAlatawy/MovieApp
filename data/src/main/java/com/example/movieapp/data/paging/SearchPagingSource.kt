package com.example.movieapp.data.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.example.movieapp.data.local.MovieDao
import com.example.movieapp.data.local.MovieEntity
import com.example.movieapp.data.remote.TmdbClient
import com.example.movieapp.data.remote.TmdbException
import com.example.movieapp.data.repository.MovieRepository
import com.example.movieapp.domain.eligibility.MovieEligibilityPolicy.Decision

class SearchPagingSource(
    private val query: String,
    private val client: TmdbClient,
    private val repository: MovieRepository,
    private val dao: MovieDao,
) : PagingSource<Int, MovieEntity>() {
    private val displayedIds = mutableSetOf<Int>()
    private var pausedKey: Int? = null
    private var resumePage = 1

    override fun getRefreshKey(state: PagingState<Int, MovieEntity>): Int? = null

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, MovieEntity> {
        if (query.isBlank()) return LoadResult.Page(emptyList(), null, null)
        val key = params.key ?: 1
        var pageNumber = if (pausedKey == key) resumePage else key
        return try {
            repeat(3) {
                val page = client.searchMovies(query.trim(), pageNumber)
                if (page.page != pageNumber || page.totalPages < 0) throw TmdbException.InvalidResponse()
                val verified = linkedMapOf<Int, MovieEntity>()
                for (candidate in page.results) {
                    if (candidate.id <= 0) throw TmdbException.InvalidResponse()
                    if (candidate.id in displayedIds || candidate.id in verified) continue
                    if (repository.refreshMovie(candidate.id) == Decision.Allow) {
                        val movie = dao.findMovie(candidate.id) ?: throw TmdbException.InvalidResponse()
                        verified[movie.id] = movie
                    }
                }
                val end = pageNumber >= minOf(page.totalPages, 500)
                pageNumber++
                if (verified.isNotEmpty() || end) {
                    displayedIds.addAll(verified.keys)
                    pausedKey = null
                    return LoadResult.Page(verified.values.toList(), null, if (end) null else pageNumber)
                }
            }
            pausedKey = key
            resumePage = pageNumber
            LoadResult.Error(ContinueSearching())
        } catch (error: TmdbException) {
            LoadResult.Error(error)
        }
    }

    class ContinueSearching : Exception("No eligible results in this batch.")
}
