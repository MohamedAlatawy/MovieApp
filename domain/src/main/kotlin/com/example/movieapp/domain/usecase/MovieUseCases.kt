package com.example.movieapp.domain.usecase

import com.example.movieapp.domain.repository.MovieRepository

class GetHomeMoviesUseCase(private val repository: MovieRepository) {
    operator fun invoke() = repository.homeMovies()
}

class SearchMoviesUseCase(private val repository: MovieRepository) {
    operator fun invoke(query: String) = repository.searchMovies(query)
}

class ObserveCachedMovieUseCase(private val repository: MovieRepository) {
    operator fun invoke(id: Int) = repository.observeCachedMovie(id)
}

class RefreshHomeMovieDetailsUseCase(private val repository: MovieRepository) {
    suspend operator fun invoke(id: Int) = repository.refreshHomeMovieDetails(id)
}

class GetSearchMovieDetailsUseCase(private val repository: MovieRepository) {
    suspend operator fun invoke(id: Int) = repository.fetchSearchDetails(id)
}
