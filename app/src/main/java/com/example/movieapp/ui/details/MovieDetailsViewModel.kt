package com.example.movieapp.ui.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.movieapp.domain.model.CachedMovie
import com.example.movieapp.domain.model.MovieAccessException
import com.example.movieapp.domain.usecase.ObserveCachedMovieUseCase
import com.example.movieapp.domain.usecase.GetSearchMovieDetailsUseCase
import com.example.movieapp.domain.usecase.RefreshHomeMovieDetailsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MovieDetailsState(
    val movie: MovieDetailsContent? = null,
    val loading: Boolean = true,
    val error: Boolean = false,
    val origin: MovieOrigin = MovieOrigin.HOME,
)

@HiltViewModel
class MovieDetailsViewModel internal constructor(
    private val observeCachedMovie: (Int) -> Flow<CachedMovie?>,
    private val fetchSearchDetails: suspend (Int) -> com.example.movieapp.domain.model.MovieDetails,
    private val refreshHomeMovieDetails: suspend (Int) -> Unit,
) : ViewModel() {
    @Inject constructor(
        observeCachedMovie: ObserveCachedMovieUseCase,
        fetchSearchDetails: GetSearchMovieDetailsUseCase,
        refreshHomeMovieDetails: RefreshHomeMovieDetailsUseCase,
    ) : this(observeCachedMovie::invoke, fetchSearchDetails::invoke, refreshHomeMovieDetails::invoke)

    private val mutableState = MutableStateFlow(MovieDetailsState())
    val state = mutableState.asStateFlow()
    private var movieId: Int? = null
    private var observation: Job? = null
    private var refresh: Job? = null

    fun open(id: Int, origin: MovieOrigin = MovieOrigin.HOME) {
        if (movieId == id && state.value.origin == origin) return
        close()
        movieId = id
        mutableState.value = MovieDetailsState(origin = origin)
        if (id <= 0) {
            mutableState.value = MovieDetailsState(loading = false, error = true, origin = origin)
            return
        }
        observation = if (origin == MovieOrigin.HOME) viewModelScope.launch {
            observeCachedMovie(id).collect { movie ->
                mutableState.update { it.copy(movie = movie?.toDetailsContent()) }
            }
        } else null
        retry()
    }

    fun retry() {
        val id = movieId?.takeIf { it > 0 } ?: return
        if (refresh?.isActive == true) return
        refresh = viewModelScope.launch {
            mutableState.update { it.copy(loading = true, error = false) }
            try {
                if (state.value.origin == MovieOrigin.SEARCH) {
                    val movie = fetchSearchDetails(id).toDetailsContent()
                    mutableState.update { it.copy(movie = movie, loading = false, error = false) }
                    return@launch
                }
                refreshHomeMovieDetails(id)
                mutableState.update { it.copy(loading = false) }
            } catch (_: MovieAccessException) {
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
