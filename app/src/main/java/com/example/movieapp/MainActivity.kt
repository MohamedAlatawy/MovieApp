package com.example.movieapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.paging.compose.LazyPagingItems
import com.example.movieapp.data.local.MovieEntity
import com.example.movieapp.ui.search.SearchState
import com.example.movieapp.ui.details.MovieDetailsState
import com.example.movieapp.ui.details.MovieDetailsScreen
import com.example.movieapp.ui.details.MovieDetailsViewModel
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.movieapp.ui.search.SearchScreen
import com.example.movieapp.ui.search.SearchViewModel
import com.example.movieapp.ui.theme.MovieAppTheme
import com.example.movieapp.ui.home.HomeScreen
import com.example.movieapp.ui.home.HomeViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: HomeViewModel by viewModels()
    private val searchViewModel: SearchViewModel by viewModels()
    private val detailsViewModel: MovieDetailsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MovieAppTheme {
                val search by searchViewModel.state.collectAsStateWithLifecycle()
                val details by detailsViewModel.state.collectAsStateWithLifecycle()
                MovieAppContent(
                    viewModel.movies.collectAsLazyPagingItems(), search, details,
                    searchViewModel::updateQuery, detailsViewModel::open,
                    detailsViewModel::close, detailsViewModel::retry,
                )
            }
        }
    }
}

@Composable
internal fun MovieAppContent(
    homeMovies: LazyPagingItems<MovieEntity>,
    search: SearchState,
    details: MovieDetailsState,
    onQueryChange: (String) -> Unit,
    onOpenDetails: (Int) -> Unit,
    onCloseDetails: () -> Unit,
    onRetryDetails: () -> Unit,
) {
    var searchSelected by rememberSaveable { mutableStateOf(false) }
    var selectedMovieId by rememberSaveable { mutableStateOf<Int?>(null) }
    val closeDetails: () -> Unit = { selectedMovieId = null; onCloseDetails() }
    BackHandler(enabled = selectedMovieId != null, onBack = closeDetails)
    LaunchedEffect(selectedMovieId) { selectedMovieId?.let(onOpenDetails) }
    val screens = rememberSaveableStateHolder()
    val selectedId = selectedMovieId
    if (selectedId != null) {
        MovieDetailsScreen(details, closeDetails, onRetryDetails)
    } else {
        Scaffold(bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = !searchSelected, onClick = { searchSelected = false },
                    icon = { Text("⌂", fontSize = 24.sp) }, label = { Text("Home") })
                NavigationBarItem(selected = searchSelected, onClick = { searchSelected = true },
                    icon = { Text("⌕", fontSize = 24.sp) }, label = { Text("Search") })
            }
        }) { padding ->
            Box(Modifier.padding(bottom = padding.calculateBottomPadding())) {
                screens.SaveableStateProvider(searchSelected) {
                    if (searchSelected) {
                        SearchScreen(search.query, onQueryChange, search.movies,
                            onMovieClick = { id -> if (selectedMovieId == null) { selectedMovieId = id } })
                    } else {
                        HomeScreen(homeMovies,
                            onMovieClick = { id -> if (selectedMovieId == null) { selectedMovieId = id } })
                    }
                }
            }
        }
    }

}
