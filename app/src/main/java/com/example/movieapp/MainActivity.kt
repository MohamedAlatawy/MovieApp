package com.example.movieapp

import android.os.Bundle
import android.os.Build
import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import com.example.movieapp.push.MovieNotifications
import com.example.movieapp.push.MovieUpdateSignal
import com.example.movieapp.push.MovieUpdateSnackbar
import javax.inject.Inject
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.paging.compose.LazyPagingItems
import com.example.movieapp.domain.model.CachedMovie
import com.example.movieapp.ui.details.MovieOrigin
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
    @Inject lateinit var movieUpdates: MovieUpdateSignal
    private var notificationMovieId by mutableStateOf<Int?>(null)
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("pending_notification_movie", notificationMovieId ?: 0)
        super.onSaveInstanceState(outState)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        MovieNotifications.movieId(intent)?.let {
            detailsViewModel.close()
            notificationMovieId = it
        }
    }
    override fun onStart() {
        super.onStart()
        detailsViewModel.setActive(true)
        com.example.movieapp.push.MovieUpdates.reconcile(this)
    }
    override fun onStop() {
        detailsViewModel.setActive(false)
        super.onStop()
    }
    private val viewModel: HomeViewModel by viewModels()
    private val searchViewModel: SearchViewModel by viewModels()
    private val detailsViewModel: MovieDetailsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        notificationMovieId = if (savedInstanceState == null) MovieNotifications.movieId(intent)
            else savedInstanceState.getInt("pending_notification_movie").takeIf { it > 0 }
        val preferences = getSharedPreferences("notification_feedback", MODE_PRIVATE)
        if (Build.VERSION.SDK_INT >= 33 && !preferences.getBoolean("requested", false) &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            preferences.edit().putBoolean("requested", true).apply()
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        enableEdgeToEdge()
        setContent {
            MovieAppTheme {
                val search by searchViewModel.state.collectAsStateWithLifecycle()
                val details by detailsViewModel.state.collectAsStateWithLifecycle()
                Box(Modifier.fillMaxSize()) {
                MovieAppContent(
                    viewModel.movies.collectAsLazyPagingItems(), search, details,
                    searchViewModel::updateQuery, detailsViewModel::open,
                    detailsViewModel::close, detailsViewModel::retry,
                    notificationMovieId, { notificationMovieId = null },
                )
                MovieUpdateSnackbar(movieUpdates.events, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
                }
            }
        }
    }
}

@Composable
internal fun MovieAppContent(
    homeMovies: LazyPagingItems<CachedMovie>,
    search: SearchState,
    details: MovieDetailsState,
    onQueryChange: (String) -> Unit,
    onOpenDetails: (Int, MovieOrigin) -> Unit,
    onCloseDetails: () -> Unit,
    onRetryDetails: () -> Unit,
    notificationMovieId: Int? = null,
    onNotificationConsumed: () -> Unit = {},
) {
    var searchSelected by rememberSaveable { mutableStateOf(false) }
    var selectedMovieId by rememberSaveable { mutableStateOf<Int?>(null) }
    var selectedOrigin by rememberSaveable { mutableStateOf(MovieOrigin.HOME) }
    LaunchedEffect(notificationMovieId) {
        notificationMovieId?.takeIf { it > 0 }?.let { id ->
            selectedOrigin = MovieOrigin.SEARCH
            selectedMovieId = id
            onOpenDetails(id, MovieOrigin.SEARCH)
            onNotificationConsumed()
        }
    }
    val closeDetails: () -> Unit = { selectedMovieId = null; onCloseDetails() }
    BackHandler(enabled = selectedMovieId != null, onBack = closeDetails)
    LaunchedEffect(selectedMovieId, selectedOrigin) { selectedMovieId?.let { onOpenDetails(it, selectedOrigin) } }
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
                            onMovieClick = { id -> if (selectedMovieId == null) { selectedOrigin = if (searchSelected) MovieOrigin.SEARCH else MovieOrigin.HOME; selectedMovieId = id } })
                    } else {
                        HomeScreen(homeMovies,
                            onMovieClick = { id -> if (selectedMovieId == null) { selectedOrigin = if (searchSelected) MovieOrigin.SEARCH else MovieOrigin.HOME; selectedMovieId = id } })
                    }
                }
            }
        }
    }

}
