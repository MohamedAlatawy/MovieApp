package com.example.movieapp.domain.model

sealed class MovieAccessException(message: String) : Exception(message) {
    class Network : MovieAccessException("Unable to connect to the movie service.")
    class Http(val statusCode: Int) : MovieAccessException("Movie service error: $statusCode")
    class InvalidResponse : MovieAccessException("Invalid movie response.")
    class InvalidIdentity : MovieAccessException("Invalid movie identity.")
    class MissingConfiguration : MovieAccessException("Movie service is not configured.")
}