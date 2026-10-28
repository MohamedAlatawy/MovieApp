package com.example.movieapp.ui.search

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.PagingData
import com.example.movieapp.domain.model.MovieSummary
import com.example.movieapp.domain.model.MovieAccessException
import com.example.movieapp.ui.theme.MovieAppTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Rule
import org.junit.Test

class SearchScreenTest {
    @get:Rule val compose = createComposeRule()
    private val idle = LoadState.NotLoading(true)
    private fun results(rows: List<MovieSummary> = emptyList(), refresh: LoadState = idle) =
        flowOf(PagingData.from(rows, LoadStates(refresh, idle, idle)))

    @Test fun emptyQueryAndClearHidePreviousResults() {
        var query by mutableStateOf("toy")
        val movies = results(listOf(MovieSummary(1, "Toy Story", releaseDate = "1995-01-01")))
        compose.setContent { MovieAppTheme { SearchScreen(query, { query = it }, movies) } }
        compose.onNodeWithText("Toy Story").assertIsDisplayed()
        compose.onNodeWithText("1995").assertIsDisplayed()
        compose.onNodeWithText("Clear").performClick()
        compose.onNodeWithText("Search by movie title").assertIsDisplayed()
        compose.onNodeWithText("Toy Story").assertDoesNotExist()
    }

    @Test fun changedQueryShowsLoadingInsteadOfOldResults() {
        var query by mutableStateOf("toy")
        var movies by mutableStateOf(results(listOf(MovieSummary(1, "Toy Story"))))
        compose.setContent { MovieAppTheme { SearchScreen(query, {
            query = it
            movies = results(refresh = LoadState.Loading)
        }, movies) } }
        compose.onNodeWithText("Toy Story").assertIsDisplayed()
        compose.onNodeWithText("Search movies").performTextReplacement("batman")
        compose.onNodeWithText("Toy Story").assertDoesNotExist()
        compose.onNodeWithText("Finding demo movies…").assertIsDisplayed()
        compose.onNodeWithText("No results").assertDoesNotExist()
        compose.onNodeWithText("Search movies").assertIsFocused()
    }

    @Test fun emptyFirstPageAppendLoadingAndFailureCanRetryToResults() {
        val finishFirstAppend = CompletableDeferred<Unit>()
        val requestedPages = CopyOnWriteArrayList<Int>()
        var appendAttempts = 0
        val pager = Pager(PagingConfig(pageSize = 20, initialLoadSize = 20, enablePlaceholders = false)) {
            object : PagingSource<Int, MovieSummary>() {
                override fun getRefreshKey(state: PagingState<Int, MovieSummary>): Int? = null

                override suspend fun load(params: LoadParams<Int>): LoadResult<Int, MovieSummary> {
                    val page = params.key ?: 1
                    requestedPages += page
                    if (page == 1) return LoadResult.Page(emptyList(), null, 2)
                    appendAttempts++
                    if (appendAttempts == 1) {
                        finishFirstAppend.await()
                        return LoadResult.Error(MovieAccessException.Network())
                    }
                    return LoadResult.Page(listOf(MovieSummary(42, "Recovered movie")), 1, null)
                }
            }
        }
        compose.setContent { MovieAppTheme { SearchScreen("movie", {}, pager.flow) } }
        try {
            compose.waitUntil(5_000) { requestedPages.size == 2 }
            compose.onNodeWithText("Finding demo movies…").assertIsDisplayed()
            compose.onNodeWithText("No results").assertDoesNotExist()
            compose.runOnIdle { finishFirstAppend.complete(Unit) }
            compose.waitUntil(5_000) {
                compose.onAllNodesWithText("Retry").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("No results").assertDoesNotExist()
            compose.onNodeWithText("Couldn't search movies. Try again.").assertIsDisplayed()
            compose.onNodeWithText("Retry").assertIsDisplayed().performClick()
            compose.waitUntil(5_000) {
                compose.onAllNodesWithText("Recovered movie").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Recovered movie").assertIsDisplayed()
            compose.onNodeWithText("Retry").assertDoesNotExist()
            compose.onNodeWithText("No results").assertDoesNotExist()
            assertEquals(listOf(1, 2, 2), requestedPages.toList())
        } finally {
            finishFirstAppend.complete(Unit)
        }
    }
    @Test fun completedEmptySearchShowsNoResults() {
        compose.setContent { MovieAppTheme { SearchScreen("unknown", {}, results()) } }
        compose.onNodeWithText("No results").assertIsDisplayed()
    }

    @Test fun networkErrorOffersRetryInsteadOfNoResults() {
        compose.setContent { MovieAppTheme { SearchScreen("query", {},
            results(refresh = LoadState.Error(MovieAccessException.Network()))) } }
        compose.onNodeWithText("Retry").assertHasClickAction()
        compose.onNodeWithText("No results").assertDoesNotExist()
    }
}
