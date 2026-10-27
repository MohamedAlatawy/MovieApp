package com.example.movieapp.data.remote

/** Transport failures for a future repository to interpret; no UI or policy decisions. */
sealed class TmdbException(message: String) : Exception(message) {
    class Http(val statusCode: Int) : TmdbException("TMDb HTTP error: $statusCode")
    class Network : TmdbException("Unable to communicate with TMDb.")
    class InvalidResponse : TmdbException("TMDb returned invalid response data.")
    class MissingToken : TmdbException("Configure tmdb.readAccessToken in local.properties before accessing TMDb.")
}
