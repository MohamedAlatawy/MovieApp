# MovieApp — Custom Backend Bonus

This branch keeps MovieApp's Home, Search and shared Details UI, but uses the local ASP.NET Core backend for every active movie request. Data is synthetic demo content, not independently verified certification evidence. Released Core remains on main; no Core history was merged into this branch.

See [BONUS_ANDROID_SETUP.md](BONUS_ANDROID_SETUP.md) for backend startup, ADB reverse, Firebase client configuration and deterministic tests.

## Architecture

Presentation → domain Use Cases/models/repository contract ← data implementation.

- Domain owns immutable MovieSummary, MovieDetails and CachedMovie, semantic MovieAccessException failures and five focused application Use Cases. It uses Coroutines and Paging-common JVM APIs; it is not completely framework-independent.
- BackendRepository implements that contract. Hilt in app constructs the backend client, isolated Room database and repository, and provides the domain operations.
- ViewModels/screens consume domain/presentation models. State management is MVVM with MVI-style one-way state flow, not a reducer framework.
- Home uses native Room Paging + BackendRemoteMediator. Search uses BackendSearchPagingSource directly; no Search cache or offline fallback.
- Details has explicit HOME and SEARCH modes with one UI. Movie ID and origin are saveable; Back preserves list/query state.

## Home cache

The unchanged version-1 backend-demo.db is separate from the historical TMDb database. Backend pages are ordered by stable ID. A whole validated page is merged in one transaction; failed persistence does not advance the raw page. Newer timestamps win, duplicate pages are idempotent, and null posters preserve existing usable URLs. Backend responses contain overview, date, rating and genres; explicit empty text/genres are authoritative.

Home accumulates loaded rows and retains legacy rows and offline content. It does not clear, evict or claim to be a continuously current full catalog. Empty or unchanged nonterminal pages do not invalidate Room themselves, so a session-owned InvalidatingPagingSourceFactory is invalidated after successful progress. This allows the next native boundary load without manual scanning.

Home Details observes Room immediately and enriches only an existing row with one Details request. Missing rows are a no-op; errors including 404 preserve existing content. The existence check is repeated inside the write transaction.

## Search and Details

Search trims query identity, delays requests 400 ms and immediately cancels superseded query work. Each normal paging load requests one backend page with query/page/pageSize only. Results retain server order; empty pages use server metadata. Zero-item append loading/errors offer loading feedback and Retry.

Search summaries and Search Details never read/write/delete Room, including for an ID already cached at Home. Details metadata stays in ViewModel memory. There is no offline Search guarantee or per-candidate Details request. Poster placeholders and demo/unverified labels remain honest.

## Independent movie-update paths

Validated movie_updated events carry an ID, not authoritative content:

1. WorkManager schedules network-constrained existing-Home-row refresh. An uncached ID does not create a row. Room Flow updates Home and Home Details.
2. A small app-scoped, non-replaying movie-ID signal reaches active matching SEARCH Details. It refetches directly through the domain Use Case, independently of cache existence, Worker completion or scheduling success.

Requests do not overlap in a Details ViewModel. Multiple hints during a request coalesce into one subsequent fetch. ID/origin changes and backgrounding cancel obsolete Search work; a generation guard also rejects late responses. Failure keeps the previous content with Retry. No exactly-once promise is made.

The UI signal exists only in-process; it does not survive process death or replay inactive events. WorkManager separately handles deferred Home work. Returning to the foreground reconciles cached IDs and refetches an open Search Details screen. Search lists are short-lived snapshots and are not rewritten by Home cache updates.

## Notification feedback

Received valid data-only updates also produce a local notification on the **Movie updates** channel. Android 13+ asks for notification permission once from the Activity; denial or disabled app/channel notifications leave both data-update paths working. Enable notifications later through Android settings if desired. No extra request is made to obtain notification text.

Tapping a notification opens the existing Details screen using the network-only SEARCH loading mode, including for movies absent from Home cache. Immutable per-movie PendingIntents reuse the existing Activity task; repeated updates replace that movie's notification. Arrival alone never navigates.

While the Activity is started, one Snackbar says **Movie update received**. It confirms receipt, not successful Room synchronization. Background hints are not replayed as Snackbars on resume. Notification posting failure cannot block Search Details refresh or Worker scheduling. Delivery remains dependent on Android/FCM; force-stop and process death do not provide guaranteed transient feedback.

## Compatibility and verification

The API routes/JSON, Firebase project/topic, localhost configuration, Room entity/schema/version and dependency versions are unchanged. Domain adds only the existing Coroutines/Paging-common dependencies.

The historical TMDb implementation and verified database/migrations remain isolated, inactive compatibility code on this Bonus branch. They are not bound into the active movie graph; no TMDb token or backend admin key is sent by BackendApi.

Run:
```powershell
.\gradlew.bat :domain:test :data:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug
.\gradlew.bat :data:connectedDebugAndroidTest :app:connectedDebugAndroidTest
```

Tests cover domain delegation/cancellation, backend contract/error mapping, no-Search-Room boundaries, native Paging retry, real Room atomicity/isolation, Details origins, FCM hints and Worker/UI updates through deterministic fixtures. They do not establish external FCM delivery or live server latency. No real FCM send or authenticated PATCH is needed for these tests.
