package com.example.movieapp.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import coil3.compose.SubcomposeAsyncImage
import com.example.movieapp.data.backend.DemoMovie
import com.example.movieapp.data.paging.SearchPagingSource
import com.example.movieapp.data.remote.TmdbException
import com.example.movieapp.ui.home.posterUrl
import kotlinx.coroutines.flow.Flow

@Composable
fun SearchScreen(query: String, onQueryChange: (String) -> Unit, results: Flow<PagingData<DemoMovie>>,
    onMovieClick: (Int) -> Unit = {}) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Text("Find a movie · Demo Data", Modifier.padding(start = 16.dp, top = 16.dp),
            style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = query, onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            label = { Text("Search movies") }, singleLine = true,
            shape = RoundedCornerShape(16.dp),
            trailingIcon = {
                if (query.isNotEmpty()) TextButton(onClick = { onQueryChange("") }) { Text("Clear") }
            },
        )
        key(query.trim()) {
            val movies = results.collectAsLazyPagingItems()
            when {
                query.isBlank() -> SearchMessage("Search by movie title", "Browse demo movies from the backend.")
                movies.itemCount == 0 -> when (val refresh = movies.loadState.refresh) {
                    is LoadState.Loading -> SearchLoading()
                    is LoadState.Error -> SearchError(refresh.error, movies::retry)
                    is LoadState.NotLoading -> SearchMessage("No results", "Try another movie title.")
                }
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(150.dp), modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    items(movies.itemCount, key = movies.itemKey { it.id }) { index ->
                        movies[index]?.let { SearchMovieCard(it, onMovieClick) }
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(Modifier.fillMaxWidth().heightIn(min = 144.dp), contentAlignment = Alignment.Center) {
                            when (val append = movies.loadState.append) {
                                is LoadState.Loading -> SearchLoading()
                                is LoadState.Error -> SearchError(append.error, movies::retry)
                                else -> Unit
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchMovieCard(movie: DemoMovie, onMovieClick: (Int) -> Unit) {
    Column(Modifier.clickable { onMovieClick(movie.id) }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SubcomposeAsyncImage(
            model = posterUrl(movie.posterPath), contentDescription = "Poster for ${movie.title}",
            modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(16.dp)),
            contentScale = ContentScale.Crop,
            loading = { SearchPosterPlaceholder() }, error = { SearchPosterPlaceholder() },
        )
        Text(movie.title, style = MaterialTheme.typography.titleSmall,
            minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(movie.releaseDate?.take(4)?.takeIf { it.length == 4 && it.all { c -> c in '0'..'9' } }
            ?: "Year unavailable", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SearchPosterPlaceholder() {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center) {
        Text("Poster unavailable", style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun SearchMessage(title: String, message: String) {
    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(message, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SearchLoading() {
    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CircularProgressIndicator(Modifier.size(28.dp))
        Text("Finding demo movies…")
    }
}

@Composable
private fun SearchError(error: Throwable, retry: () -> Unit) {
    val paused = error is SearchPagingSource.ContinueSearching
    val message = when {
        paused -> "No eligible movies in this batch."
        error is TmdbException.MissingToken -> "Movie service is not configured."
        error is TmdbException.Http && error.statusCode == 401 -> "Movie service credentials were not accepted."
        else -> "Couldn't search movies. Try again."
    }
    Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(message, textAlign = TextAlign.Center)
        Button(onClick = retry, modifier = Modifier.padding(top = 8.dp)) {
            Text(if (paused) "Continue searching" else "Retry")
        }
    }
}
