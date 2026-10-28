package com.example.movieapp.ui.details

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import com.example.movieapp.MovieAppContent
import com.example.movieapp.data.backend.DemoMovie
import com.example.movieapp.ui.search.SearchState
import com.example.movieapp.ui.theme.MovieAppTheme
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class MovieDetailsNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val rows = (1..60).map {
        DemoMovie(it, "Movie $it", "A demo synopsis", null, "2026-01-01", 8.2,
            genres = "Adventure")
    }
    private val idle = LoadState.NotLoading(true)
    private val results = flowOf(PagingData.from(rows, LoadStates(idle, idle, idle)))

    private fun showApp() {
        compose.setContent {
            var query by remember { mutableStateOf("movie") }
            var details by remember { mutableStateOf(MovieDetailsState()) }
            MovieAppTheme {
                MovieAppContent(results.collectAsLazyPagingItems(), SearchState(query, results), details,
                    { query = it }, { id -> details = MovieDetailsState(rows.first { it.id == id }, loading = false) },
                    {}, {})
            }
        }
    }

    @Test fun homeDetailsAndOnScreenBackPreserveVisibleAnchor() {
        showApp()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(21)
        compose.onNodeWithText("Movie 21").assertIsDisplayed()
        val before = compose.onNodeWithText("Movie 21").fetchSemanticsNode().positionInRoot.y
        compose.onNodeWithText("Movie 21").performClick()
        compose.onNodeWithText("Movie details").assertIsDisplayed()
        compose.onNodeWithText("Synopsis").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("A demo synopsis").assertIsDisplayed()
        compose.onNodeWithText("Back").performClick()
        compose.onNodeWithText("Movie 21").assertIsDisplayed()
        val after = compose.onNodeWithText("Movie 21").fetchSemanticsNode().positionInRoot.y
        assertEquals(before, after, 2f)
    }

    @Test fun searchDetailsAndSystemBackPreserveQueryAndAnchor() {
        showApp()
        compose.onNodeWithText("Search", useUnmergedTree = true).performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(20)
        compose.onNodeWithText("Movie 21").assertIsDisplayed()
        val before = compose.onNodeWithText("Movie 21").fetchSemanticsNode().positionInRoot.y
        compose.onNodeWithText("Movie 21").performClick()
        compose.onNodeWithText("Movie details").assertIsDisplayed()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("Search movies").assertExists()
        compose.onNode(hasSetTextAction()).assertTextContains("movie")
        compose.onNodeWithText("Movie 21").assertIsDisplayed()
        assertEquals(before, compose.onNodeWithText("Movie 21").fetchSemanticsNode().positionInRoot.y, 2f)
    }

    @Test fun metadataAndRevocationAndRetryAreVisible() {
        var state by mutableStateOf(MovieDetailsState(rows.first(), loading = false))
        var retries = 0
        compose.setContent { MovieAppTheme { MovieDetailsScreen(state, {}, { retries++ }) } }
        compose.onNodeWithText("Demo rating: 8.2 / 10").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Demo Data · US certification unverified").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Adventure").assertExists()
        compose.runOnIdle { state = MovieDetailsState(loading = false, denied = true) }
        compose.onNodeWithText("Movie 1").assertDoesNotExist()
        compose.onNodeWithText("This demo movie is unavailable.").assertIsDisplayed()
        compose.runOnIdle { state = MovieDetailsState(loading = false, error = true) }
        compose.onNodeWithText("Retry").performClick()
        compose.runOnIdle { assertEquals(1, retries) }
    }
}