package com.example.movieapp.data.paging

import com.example.movieapp.data.repository.toMovieAccessException
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.example.movieapp.data.remote.MovieSummaryDto
import com.example.movieapp.data.remote.TmdbClient
import com.example.movieapp.data.remote.TmdbException

class SearchPagingSource(
    private val query: String,
    private val client: TmdbClient,
) : PagingSource<Int, MovieSummaryDto>() {
    private val loadedIds = mutableMapOf<Int, Set<Int>>()

    override fun getRefreshKey(state: PagingState<Int, MovieSummaryDto>): Int? = null

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, MovieSummaryDto> {
        if (query.isBlank()) return LoadResult.Page(emptyList(), null, null)
        val number = params.key ?: 1
        return try {
            if (number !in 1..500) throw TmdbException.InvalidResponse()
            val page = client.searchMovies(query.trim(), number)
            if (page.page != number || page.totalPages < 0 || page.totalResults < 0 ||
                page.results.any { it.id <= 0 }) throw TmdbException.InvalidResponse()
            val otherIds = loadedIds.filterKeys { it != number }.values.flatten().toSet()
            val movies = page.results.distinctBy { it.id }.filterNot { it.id in otherIds }
            loadedIds[number] = movies.map { it.id }.toSet()
            LoadResult.Page(
                data = movies,
                prevKey = (number - 1).takeIf { it >= 1 },
                nextKey = (number + 1).takeIf { number < minOf(page.totalPages, 500) },
            )
        } catch (error: TmdbException) {
            LoadResult.Error(error.toMovieAccessException())
        }
    }
}