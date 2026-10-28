package com.example.movieapp.ui.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.movieapp.domain.model.*
import com.example.movieapp.domain.usecase.*
import com.example.movieapp.push.MovieUpdateSignal
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class MovieDetailsState(
    val movie: MovieDetailsContent? = null,
    val loading: Boolean = true,
    val error: Boolean = false,
    val origin: MovieOrigin = MovieOrigin.HOME,
)

@HiltViewModel
class MovieDetailsViewModel internal constructor(
    private val observeCachedMovie: (Int) -> Flow<CachedMovie?>,
    private val fetchSearchDetails: suspend (Int) -> MovieDetails,
    private val refreshHomeMovieDetails: suspend (Int) -> Unit,
    updates: Flow<Int> = emptyFlow(),
) : ViewModel() {
    @Inject constructor(observe: ObserveCachedMovieUseCase, search: GetSearchMovieDetailsUseCase,
        refresh: RefreshHomeMovieDetailsUseCase, signal: MovieUpdateSignal) :
        this(observe::invoke, search::invoke, refresh::invoke, signal.events)

    private val mutableState = MutableStateFlow(MovieDetailsState())
    val state = mutableState.asStateFlow()
    private var movieId: Int? = null
    private var generation = 0
    private var active = true
    private var observation: Job? = null
    private var refresh: Job? = null
    private var pendingEvent = false

    init {
        viewModelScope.launch {
            updates.collect { id ->
                if (active && movieId == id && state.value.origin == MovieOrigin.SEARCH) {
                    if (refresh?.isActive == true) pendingEvent = true else retry()
                }
            }
        }
    }

    /** Activity visibility gates ephemeral events. Resume fetches current Search metadata without replay. */
    fun setActive(value: Boolean) {
        if (active == value) return
        active = value
        if (state.value.origin == MovieOrigin.SEARCH) {
            if (!value) {
                generation++
                refresh?.cancel()
                refresh = null
                pendingEvent = false
                mutableState.update { it.copy(loading = false) }
            } else retry()
        }
    }

    fun open(id: Int, origin: MovieOrigin = MovieOrigin.HOME) {
        if (movieId == id && state.value.origin == origin) return
        close()
        movieId = id
        mutableState.value = MovieDetailsState(origin = origin)
        if (id <= 0) {
            mutableState.value = MovieDetailsState(loading = false, error = true, origin = origin)
            return
        }
        val version = generation
        if (origin == MovieOrigin.HOME) observation = viewModelScope.launch {
            observeCachedMovie(id).collect { movie ->
                if (version == generation) mutableState.update { it.copy(movie = movie?.toDetailsContent()) }
            }
        }
        retry()
    }

    fun retry() {
        val id = movieId?.takeIf { it > 0 } ?: return
        val origin = state.value.origin
        if ((origin == MovieOrigin.SEARCH && !active) || refresh?.isActive == true) return
        val version = generation
        refresh = viewModelScope.launch {
            do {
                pendingEvent = false
                mutableState.update { it.copy(loading = true, error = false) }
                try {
                    if (origin == MovieOrigin.SEARCH) {
                        val movie = fetchSearchDetails(id).toDetailsContent()
                        ensureActive()
                        if (version != generation) return@launch
                        mutableState.update { it.copy(movie = movie, loading = false) }
                    } else {
                        refreshHomeMovieDetails(id)
                        ensureActive()
                        if (version != generation) return@launch
                        mutableState.update { it.copy(loading = false) }
                    }
                } catch (cancelled: CancellationException) { throw cancelled
                } catch (_: MovieAccessException) {
                    if (version != generation) return@launch
                    mutableState.update { it.copy(loading = false, error = true) }
                }
                // Coalesce hints arriving during a request into one subsequent fetch; never overlap.
            } while (pendingEvent && active && version == generation && origin == MovieOrigin.SEARCH)
        }
    }

    fun close() {
        generation++
        observation?.cancel()
        refresh?.cancel()
        observation = null
        refresh = null
        pendingEvent = false
        movieId = null
        mutableState.value = MovieDetailsState()
    }
}
