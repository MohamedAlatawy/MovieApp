package com.example.movieapp.ui.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.movieapp.data.local.MovieEntity
import com.example.movieapp.data.remote.TmdbException
import com.example.movieapp.data.repository.MovieRepository
import com.example.movieapp.domain.eligibility.MovieEligibilityPolicy.Decision
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MovieDetailsState(
    val movie: MovieEntity? = null,
    val loading: Boolean = true,
    val denied: Boolean = false,
    val error: Boolean = false,
)

@HiltViewModel
class MovieDetailsViewModel internal constructor(
    private val observeMovie: (Int) -> Flow<MovieEntity?>,
    private val refreshMovie: suspend (Int) -> Decision,
) : ViewModel() {
    @Inject constructor(repository: MovieRepository) : this(repository::observeMovie, repository::refreshMovie)

    private val mutableState = MutableStateFlow(MovieDetailsState())
    val state = mutableState.asStateFlow()
    private var movieId: Int? = null
    private var observation: Job? = null
    private var refresh: Job? = null

    fun open(id: Int) {
        if (movieId == id) return
        close()
        movieId = id
        mutableState.value = MovieDetailsState()
        if (id <= 0) {
            mutableState.value = MovieDetailsState(loading = false, denied = true)
            return
        }
        observation = viewModelScope.launch {
            observeMovie(id).collect { movie ->
                mutableState.update { it.copy(movie = movie) }
            }
        }
        retry()
    }

    fun retry() {
        val id = movieId?.takeIf { it > 0 } ?: return
        if (refresh?.isActive == true) return
        refresh = viewModelScope.launch {
            mutableState.update { it.copy(loading = true, error = false) }
            try {
                val denied = refreshMovie(id) == Decision.Deny
                mutableState.update {
                    it.copy(loading = false, denied = denied, movie = if (denied) null else it.movie)
                }
            } catch (_: TmdbException) {
                mutableState.update { it.copy(loading = false, error = true) }
            }
        }
    }

    fun close() {
        observation?.cancel()
        refresh?.cancel()
        movieId = null
        mutableState.value = MovieDetailsState()
    }
}
