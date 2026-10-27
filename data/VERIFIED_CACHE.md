# Verified movie cache

Create one MovieDatabase and one MovieRepository per application/database and share
the repository across future List and Details consumers. App/Hilt wiring is deferred.
Call refreshMovie(id) for either consumer; observeMovies()/observeMovie(id) expose
only persisted rows. MovieDao writes are an internal persistence boundary by convention:
production callers must not insert candidates directly.

Each refresh fetches fresh Details (identity, adult flag, presentation fields) and
release dates before invoking the existing domain policy. All countries and all
certification records are mapped without filtering. Both identities must match the
requested positive ID. Mismatches throw InvalidResponse and preserve the cache.

A successful complete response that the policy denies deletes an existing row,
including unknown adult flags or insufficient certification proof. Transport/HTTP,
malformed-response or identity failures propagate and leave the cache unchanged.
No new movie can be authorized using a partially completed request pair. Previously
verified content is preserved on a failure; this is availability behavior, not a new
eligibility decision or a guarantee that remote facts have not changed.

Concurrent refreshes are not serialized. An older in-flight positive response can
write after a newer restrictive refresh and restore a row. Sharing one repository
instance does not prevent this race. Same-movie List/Details concurrency protection
is deliberately deferred to integration; callers must not assume ordering safety.
There is no replacement lock, queue, coordinator or scheduler.
Cancellation propagates. Room transactions contain only database writes and never
span a network call. No retry, TTL, background refresh or paging is included.

Room schema version 1 is exported under data/schemas using the existing KSP plugin.
There is one movies table and no migrations for this new database. Keep the exported
schema under version control when staging is separately authorized.
Room runtime and compiler are used; no migration-testing or Paging dependency is needed.

JVM repository tests use the real TmdbClient with MockWebServer and a fake DAO.
They test verification, preservation and revocation, not SQLite or concurrent ordering.
The mutex-specific ordering test was removed with the synchronization guarantee.
MovieDatabaseTest uses real in-memory Room on Android for persistence and reactive
Flow emissions. No actual TMDb token or live TMDb request is needed by these tests.
