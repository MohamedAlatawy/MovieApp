package com.example.movieapp.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.example.movieapp.domain.model.MovieSummary
import com.example.movieapp.domain.usecase.SearchMoviesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf

data class SearchState(
    val query: String = "",
    val movies: Flow<PagingData<MovieSummary>> = flowOf(PagingData.empty()),
)

@HiltViewModel
class SearchViewModel internal constructor(
    private val searchMovies: (String) -> Flow<PagingData<MovieSummary>>,
) : ViewModel() {
    @Inject constructor(search: SearchMoviesUseCase) : this(search::invoke)

    private val mutableState = MutableStateFlow(SearchState())
    val state = mutableState.asStateFlow()
    private var queryJob: Job? = null

    fun updateQuery(query: String) {
        val term = query.trim()
        if (term == state.value.query.trim()) {
            mutableState.value = state.value.copy(query = query)
            return
        }
        queryJob?.cancel()
        val job = SupervisorJob(viewModelScope.coroutineContext[Job])
        queryJob = job
        val movies = if (term.isEmpty()) flowOf(PagingData.empty<MovieSummary>()) else flow {
            delay(400)
            emitAll(searchMovies(term))
        }.cachedIn(CoroutineScope(viewModelScope.coroutineContext + job))
        mutableState.value = SearchState(query, movies)
    }
}
