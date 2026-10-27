package com.example.movieapp.ui.details

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.example.movieapp.ui.home.posterUrl
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovieDetailsScreen(state: MovieDetailsState, onBack: () -> Unit, onRetry: () -> Unit) {
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Movie details") },
            navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
            actions = {
                TextButton(onClick = onRetry, enabled = !state.loading) { Text("Refresh") }
            },
        )
    }) { padding ->
        val movie = state.movie.takeUnless { state.denied }
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(Modifier.fillMaxWidth().height(4.dp)) {
                if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            if (movie != null) {
                SubcomposeAsyncImage(
                    model = posterUrl(movie.posterPath),
                    contentDescription = "Poster for ${movie.title}",
                    modifier = Modifier.align(Alignment.CenterHorizontally).widthIn(max = 280.dp)
                        .fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(20.dp)),
                    contentScale = ContentScale.Crop,
                    loading = { PosterPlaceholder() }, error = { PosterPlaceholder() },
                )
                Text(movie.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(movie.releaseDate?.takeIf { it.isNotBlank() } ?: "Release date unavailable")
                val rating = movie.voteAverage?.takeIf { it.isFinite() && it in 0.0..10.0 }
                Text(rating?.let { "TMDb user rating: ${String.format(Locale.US, "%.1f", it)} / 10" }
                    ?: "Rating unavailable")
                Text(movie.genres?.takeIf { it.isNotBlank() } ?: "Genres unavailable",
                    style = MaterialTheme.typography.titleMedium)
                Text(movie.usCertifications?.takeIf { it.isNotBlank() }?.let { "Verified US certifications: $it" }
                    ?: "US certification details unavailable")
                Text("Synopsis", style = MaterialTheme.typography.titleLarge)
                Text(movie.overview?.takeIf { it.isNotBlank() } ?: "Synopsis unavailable",
                    style = MaterialTheme.typography.bodyLarge)
            } else if (!state.loading) {
                Text(if (state.denied) "This movie is not available for eligible browsing."
                    else if (state.error) "Couldn't load this movie."
                    else "No verified movie is available.", style = MaterialTheme.typography.titleLarge)
            } else {
                Text("Loading verified movie details")
            }
            if (state.error) {
                Text(if (movie != null) "Couldn't refresh. Showing your previously verified saved movie."
                    else "Check your connection and try again.")
                Button(onClick = onRetry, enabled = !state.loading) { Text("Retry") }
            }
        }
    }
}

@Composable
private fun PosterPlaceholder() {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Poster unavailable")
        }
    }
}