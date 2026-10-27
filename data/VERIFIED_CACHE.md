# Verified movie cache

Create `MovieDatabase` with an application context, then pass its DAO and a configured
`TmdbClient` to `MovieRepository`. See [TMDb setup](TMDB_NETWORK.md) for token configuration.

Use `refreshMovie(id)` to fetch and verify a movie before saving it. Observe cached
movies through `observeMovies()` or `observeMovie(id)`.

The repository reuses the domain eligibility policy. Denied eligibility removes a
cached movie; request failures preserve it. Route production writes through the
repository rather than inserting unverified candidates through the DAO.

Room stores one `movies` table. The version-1 schema is exported to `data/schemas`.
