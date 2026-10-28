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
import com.example.movieapp.domain.model.CachedMovie
import com.example.movieapp.ui.search.SearchState
import com.example.movieapp.ui.theme.MovieAppTheme
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class MovieDetailsNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val rows = (1..60).map {
        CachedMovie(it, "Movie $it", "A cached synopsis", null, "2026-01-01", 8.2,
            genres = "Adventure")
    }
    private val idle = LoadState.NotLoading(true)
    private val results = flowOf(PagingData.from(rows, LoadStates(idle, idle, idle)))

    private var openedOrigin: MovieOrigin? = null
    private val searchResults = flowOf(PagingData.from(rows.map {
        com.example.movieapp.domain.model.MovieSummary(it.id, it.title)
    }, LoadStates(idle, idle, idle)))

    private fun showApp() {
        compose.setContent {
            var query by remember { mutableStateOf("movie") }
            var details by remember { mutableStateOf(MovieDetailsState()) }
            MovieAppTheme {
                MovieAppContent(results.collectAsLazyPagingItems(), SearchState(query, searchResults), details,
                    { query = it }, { id, origin -> openedOrigin = origin; details = MovieDetailsState(rows.first { it.id == id }.toDetailsContent(), loading = false, origin = origin) },
                    {}, {})
            }
        }
    }

    @Test fun notificationTargetsUseNetworkOriginOnColdAndExistingScreen() {
        var target by mutableStateOf<Int?>(9001)
        val opened = mutableListOf<Pair<Int,MovieOrigin>>()
        compose.setContent {
            var details by remember { mutableStateOf(MovieDetailsState()) }
            MovieAppTheme {
                MovieAppContent(results.collectAsLazyPagingItems(), SearchState("movie",searchResults),details,
                    {}, { id, origin ->
                        opened += id to origin
                        details = MovieDetailsState(com.example.movieapp.domain.model.MovieDetails(id,"Notification $id")
                            .toDetailsContent(),loading=false,origin=origin)
                    }, {}, {}, target, { target = null })
            }
        }
        compose.onNodeWithText("Notification 9001").assertIsDisplayed()
        compose.runOnIdle { assertTrue(opened.all { it.second == MovieOrigin.SEARCH }); target = 9002 }
        compose.onNodeWithText("Notification 9002").assertIsDisplayed()
        compose.runOnIdle { assertEquals(9002 to MovieOrigin.SEARCH,opened.last()) }
        compose.onNodeWithText("Back").performClick()
        compose.onNodeWithText("Your movie collection").assertIsDisplayed()
    }

    @Test fun homeDetailsAndOnScreenBackPreserveVisibleAnchor() {
        showApp()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(21)
        compose.onNodeWithText("Movie 21").assertIsDisplayed()
        val before = compose.onNodeWithText("Movie 21").fetchSemanticsNode().positionInRoot.y
        compose.onNodeWithText("Movie 21").performClick()
        compose.onNodeWithText("Movie details").assertIsDisplayed()
        compose.runOnIdle { assertEquals(MovieOrigin.HOME, openedOrigin) }
        compose.onNodeWithText("Synopsis").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("A cached synopsis").assertIsDisplayed()
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
        compose.runOnIdle { assertEquals(MovieOrigin.SEARCH, openedOrigin) }
        compose.onNodeWithText("Demo Data · US certification unverified").performScrollTo().assertIsDisplayed()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("Search movies").assertExists()
        compose.onNode(hasSetTextAction()).assertTextContains("movie")
        compose.onNodeWithText("Movie 21").assertIsDisplayed()
        assertEquals(before, compose.onNodeWithText("Movie 21").fetchSemanticsNode().positionInRoot.y, 2f)
    }

    @Test fun metadataAndUnavailableAndRetryAreVisible() {
        var state by mutableStateOf(MovieDetailsState(rows.first().toDetailsContent(), loading = false))
        var retries = 0
        compose.setContent { MovieAppTheme { MovieDetailsScreen(state, {}, { retries++ }) } }
        compose.onNodeWithText("Demo rating: 8.2 / 10").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Demo Data · US certification unverified").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Adventure").assertExists()
        compose.runOnIdle { state = MovieDetailsState(loading = false) }
        compose.onNodeWithText("Movie 1").assertDoesNotExist()
        compose.onNodeWithText("No saved demo movie is available.").assertIsDisplayed()
        compose.runOnIdle { state = MovieDetailsState(loading = false, error = true) }
        compose.onNodeWithText("Retry").performClick()
        compose.runOnIdle { assertEquals(1, retries) }
    }
}
