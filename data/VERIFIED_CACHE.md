# Discover Home cache

Home persists valid results from filtered TMDb Discover, without per-movie Details,
Release Dates or independent certification checks. `HomeRemoteMediator` makes one
raw page request per load; `MovieDao.saveDiscoverPage` merges the complete page in
one Room transaction. Validation precedes writes. Failures preserve the cache and
retry the same raw page. Empty/short/duplicate-only pages do not imply exhaustion.
Nonterminal empty pages invalidate Paging's standard source factory after progress
advances, since an empty transaction cannot trigger Room table invalidation.

Existing IDs retain discoveryOrder; new IDs receive consecutive values in response
order. Summary updates preserve useful missing metadata and Details-only genres.
All merge decisions read current rows inside the transaction. Revalidation refreshes
encountered metadata, not the current certification of every stored row. Missing
IDs are not purged; old rows may remain indefinitely.

`observeCachedMovie` exposes Room metadata immediately. `refreshHomeMovieDetails`
validates identity and enriches only an existing row using one Details request.
Omitted genres preserve cached values; an explicit empty genre list clears them.
Failures, including 404, preserve saved content. This path does not certify or revoke.

Search uses `include_adult=false` and never reads/writes/deletes Room. Search Details
stays in memory, even when the same ID exists in the Home cache. There is no offline
Search fallback.

The `movies` table in `verified-movies.db` remains at version 3 with migrations
1 -> 2 and 2 -> 3 unchanged. Legacy rows and usCertifications storage are retained,
including historically verified Search rows of unrecoverable provenance. That column
is not exposed as current verification. New Discover rows leave it null. There is no
schema migration, destructive clearing, TTL or automatic deletion.

Domain Use Cases and the repository contract expose `CachedMovie`, not Room entities.
Presentation remains domain-facing; data owns merging and native Paging. The old
filename of this document/database is retained for compatibility, not a trust claim.
See [network setup](TMDB_NETWORK.md).