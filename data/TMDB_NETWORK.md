# TMDb network client

`TmdbClient.create()` is the remote entry point used by MovieRepository and Hilt.
Its three suspend functions return DTOs or throw `TmdbException` inside data.
The repository and paging boundaries translate these into domain `MovieAccessException` values
before presentation consumes them. Suspend calls and native Paging LoadState errors retain
network, HTTP/status, malformed-response and missing-configuration distinctions. Invalid
Search Details identity is rejected before returning domain metadata. Cancellation propagates unchanged.

## Local token configuration

Add `tmdb.readAccessToken=<your TMDb API Read Access Token>` to the existing root
`local.properties`, which is already ignored by Git. Supply the token alone,
without the `Bearer` prefix. Do not commit that file or share its contents.
Rebuild after changing the token. Leaving the property absent is supported:
builds and tests succeed, but client requests throw `MissingToken` before any
network request. Tests explicitly supply a synthetic test-only token.

The token is generated into the data module's BuildConfig. This protects source
control, not the compiled application: tokens embedded in an Android binary can
be extracted. Do not distribute generated artifacts with a private credential.
Production credential distribution is a later decision; a trusted backend is
needed if a credential must remain secret from app users.

There is no HTTP logging interceptor. Never log request headers, token values,
local properties or BuildConfig. Mapped exceptions contain only fixed messages
and HTTP status codes, not response bodies or credentials.

## Contract and errors

Discover requests use `include_adult=false`, `certification_country=US`,
`certification.lte=PG-13`, `sort_by=popularity.desc` and the requested page.
Search uses only `query`, `page` and `include_adult=false`.
Both retain pagination metadata, without orchestration.

The supported Discover filters are the required Home filtering mechanism. No
per-candidate Details or Release Dates audit is performed. This is not independent
certification evidence. Home Details makes one metadata enrichment request when
opened, updating only an existing cached row. Search uses only `include_adult=false`
as clarified by TrianglZ; Search summaries and Details remain network-only and
nonpersisting. Home revalidation updates encountered metadata, not all historical
rows' current eligibility. Neither endpoint's request filter is a certificate badge.
Required IDs have no default. Missing optional fields remain null, rather than
inventing values. Unknown JSON fields are ignored for forward compatibility.

HTTP failures retain their status code (including 401/404/429/5xx). I/O failures
map to `Network`; malformed JSON/schema failures map to `InvalidResponse`.
Cancellation is rethrown. Home persistence failures surface as a retryable invalid-data load error.
Underlying response bodies/causes are deliberately omitted from mapped errors
to keep the boundary small and avoid leaking sensitive diagnostic data.

The client does not orchestrate paging or persistence; these belong
to its callers. Constructor-independent client creation keeps local
MockWebServer testing possible without Android or real credentials.

Contract references:
- https://developer.themoviedb.org/reference/discover-movie
- https://developer.themoviedb.org/reference/search-movie
- https://developer.themoviedb.org/reference/movie-details
- https://developer.themoviedb.org/docs/authentication-application
