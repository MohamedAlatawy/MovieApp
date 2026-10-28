package com.example.movieapp.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.cachedIn
import com.example.movieapp.domain.usecase.GetHomeMoviesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(getHomeMovies: GetHomeMoviesUseCase) : ViewModel() {
    val movies = getHomeMovies().cachedIn(viewModelScope)
}
