package com.example.movieapp.ui.search

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import com.example.movieapp.data.backend.DemoMovie
import com.example.movieapp.data.paging.SearchPagingSource
import com.example.movieapp.ui.theme.MovieAppTheme
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test

class SearchScreenTest {
    @get:Rule val compose = createComposeRule()
    private val idle = LoadState.NotLoading(true)
    private fun results(rows: List<DemoMovie> = emptyList(), refresh: LoadState = idle) =
        flowOf(PagingData.from(rows, LoadStates(refresh, idle, idle)))

    @Test fun emptyQueryAndClearHidePreviousResults() {
        var query by mutableStateOf("toy")
        val movies = results(listOf(DemoMovie(1, "Toy Story", null, null, "1995-01-01", null)))
        compose.setContent { MovieAppTheme { SearchScreen(query, { query = it }, movies) } }
        compose.onNodeWithText("Toy Story").assertIsDisplayed()
        compose.onNodeWithText("1995").assertIsDisplayed()
        compose.onNodeWithText("Clear").performClick()
        compose.onNodeWithText("Search by movie title").assertIsDisplayed()
        compose.onNodeWithText("Toy Story").assertDoesNotExist()
    }

    @Test fun changedQueryShowsLoadingInsteadOfOldResults() {
        var query by mutableStateOf("toy")
        var movies by mutableStateOf(results(listOf(DemoMovie(1, "Toy Story", null, null, null, null))))
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

    @Test fun completedEmptySearchShowsNoEligibleResults() {
        compose.setContent { MovieAppTheme { SearchScreen("unknown", {}, results()) } }
        compose.onNodeWithText("No results").assertIsDisplayed()
    }

    @Test fun boundedEmptyBatchOffersContinuationInsteadOfNoResults() {
        compose.setContent { MovieAppTheme { SearchScreen("query", {},
            results(refresh = LoadState.Error(SearchPagingSource.ContinueSearching()))) } }
        compose.onNodeWithText("Continue searching").assertHasClickAction()
        compose.onNodeWithText("No results").assertDoesNotExist()
    }
}
