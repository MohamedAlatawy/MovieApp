package com.example.movieapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.paging.compose.collectAsLazyPagingItems
import com.example.movieapp.ui.theme.MovieAppTheme
import com.example.movieapp.ui.home.HomeScreen
import com.example.movieapp.ui.home.HomeViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: HomeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MovieAppTheme {
                HomeScreen(viewModel.movies.collectAsLazyPagingItems())
            }
        }
    }
}
