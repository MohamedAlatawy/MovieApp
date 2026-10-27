package com.example.movieapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MovieAppTheme {
                var searchSelected by rememberSaveable { mutableStateOf(false) }
                val screens = rememberSaveableStateHolder()
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
                                val state by searchViewModel.state.collectAsStateWithLifecycle()
                                SearchScreen(state.query, searchViewModel::updateQuery, state.movies)
                            } else {
                                HomeScreen(viewModel.movies.collectAsLazyPagingItems())
                            }
                        }
                    }
                }
            }
        }
    }
}
