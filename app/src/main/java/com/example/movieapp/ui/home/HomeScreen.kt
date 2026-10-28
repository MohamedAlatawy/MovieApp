package com.example.movieapp.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import coil3.compose.SubcomposeAsyncImage
import com.example.movieapp.domain.model.CachedMovie
import com.example.movieapp.domain.model.MovieAccessException


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(movies: LazyPagingItems<CachedMovie>, onMovieClick: (Int) -> Unit = {}) {
    val states = movies.loadState
    val refresh = states.mediator?.refresh ?: states.refresh
    val append = states.mediator?.append ?: states.append
    val error = (refresh as? LoadState.Error) ?: (states.source.refresh as? LoadState.Error)
    val refreshing = refresh is LoadState.Loading
    val loading = refresh is LoadState.Loading || states.source.refresh is LoadState.Loading
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("MovieApp", fontWeight = FontWeight.Bold)
                        Text("Find your next movie night", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    TextButton(onClick = movies::refresh, enabled = !refreshing && error == null) { Text("Refresh") }
                },
            )
        },
    ) { padding ->
        if (movies.itemCount == 0) {
            Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), contentAlignment = Alignment.Center) {
                when {
                    error != null -> HomeMessage(error.error, movies::retry)
                    loading || append is LoadState.Loading -> LoadingMovies("Finding movies for you")
                    append is LoadState.Error -> HomeMessage(append.error, movies::retry)
                    else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No movies yet", style = MaterialTheme.typography.headlineSmall)
                        Text("Try refreshing to discover more movies.", textAlign = TextAlign.Center)
                        Button(onClick = movies::refresh, modifier = Modifier.padding(top = 16.dp)) {
                            Text("Refresh movies")
                        }
                    }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(150.dp),
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Your movie collection", style = MaterialTheme.typography.titleLarge)
                        Text("Demo Data · Backend order", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Box(Modifier.fillMaxWidth().height(4.dp)) {
                            if (refreshing) LinearProgressIndicator(Modifier.fillMaxWidth())
                        }
                        if (error != null) HomeMessage(error.error, movies::retry)
                    }
                }
                items(movies.itemCount, key = movies.itemKey { it.id }) { index ->
                    MovieCard(movies[index], onMovieClick)
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    val appendError = (append as? LoadState.Error)
                        ?: (states.source.append as? LoadState.Error)
                    Box(
                        Modifier.fillMaxWidth().heightIn(min = 144.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        when {
                            appendError != null -> HomeMessage(appendError.error, movies::retry)
                            append is LoadState.Loading ->
                                LoadingMovies("Finding more movies")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MovieCard(movie: CachedMovie?, onMovieClick: (Int) -> Unit) {
    Column(Modifier.clickable(enabled = movie != null) { movie?.let { onMovieClick(it.id) } },
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SubcomposeAsyncImage(
            model = posterUrl(movie?.posterPath),
            contentDescription = movie?.let { "Poster for ${it.title}" },
            modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(16.dp)),
            contentScale = ContentScale.Crop,
            loading = { PosterPlaceholder() },
            error = { PosterPlaceholder() },
        )
        Text(movie?.title.orEmpty(), style = MaterialTheme.typography.titleSmall,
            minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(if (movie == null) "" else movie.releaseDate?.take(4)?.takeIf { year -> year.length == 4 && year.all { it in '0'..'9' } }
            ?: "Year unavailable", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PosterPlaceholder() {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center) {
        Text("MOVIE\nPoster unavailable", textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LoadingMovies(label: String) {
    Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CircularProgressIndicator(Modifier.size(28.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun HomeMessage(error: Throwable, retry: () -> Unit) {
    val message = when (error) {
        is MovieAccessException.MissingConfiguration -> "Movie service is not configured."
        is MovieAccessException.Http -> if (error.statusCode == 401) "Movie service credentials were not accepted."
            else "The movie service is unavailable. Try again."
        else -> "Couldn't load movies. Check your connection and try again."
    }
    Column(Modifier.fillMaxWidth().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(message, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
        Button(onClick = retry, modifier = Modifier.padding(top = 8.dp)) {
            Text("Retry")
        }
    }
}

internal fun posterUrl(path: String?): String? = path?.takeIf {
    runCatching { java.net.URI(it).let { uri ->
        uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null
    } }.getOrDefault(false)
}