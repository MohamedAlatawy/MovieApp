# MovieApp

## Project overview

A native Android app for movie discovery, dynamic title-based search, movie details and cached browsing. Home persists results from TMDb Discover using the required adult and US certification request filters. Search follows TrianglZ's clarification: use `include_adult=false` without US certification filtering.

## Features

- **Home:** Paging 3 grid with posters, titles and years; cached content stays visible during refresh or network failure.
- **Search:** trimmed queries, 400 ms debounce, cancellation of obsolete searches and results in TMDb Search order, without per-result Details or Release Dates requests.
- **Details:** poster, synopsis, date, rating out of 10, genres and missing-data fallbacks. Neither path claims independently verified certification; Home identifies its filtered Discover cache and Search indicates that certification is not checked.
- **Navigation:** cards open Details by movie ID. On-screen and Android system Back preserve the originating screen's query and scroll position.
- Loading, empty, unavailable, error and Retry states, including append failures with no displayed movies.
- Offline Home and Home-origin Details access to previously cached metadata. Posters depend on image-cache availability; new movies require connectivity.

## Tech stack

Kotlin, Jetpack Compose / Material 3, MVVM with ViewModels, StateFlow and coroutines, Hilt, Retrofit / OkHttp, Kotlinx Serialization, Paging 3, Room and Coil.

Dependencies are centralized in [gradle/libs.versions.toml](gradle/libs.versions.toml).

## Architecture

| Module | Responsibility |
| --- | --- |
| :app | Compose screens, ViewModels, navigation state and Hilt wiring. |
| :data | Domain repository implementation, DTO/entity mapping, TMDb client, Room and native paging. |
| :domain | Movie contracts/models, semantic failures, application Use Cases. |

Presentation depends on domain Use Cases and models; data implements the domain `MovieRepository` contract. Hilt's composition root in app still depends on data to construct the client, database and repository. Screens and ViewModels do not import DTOs, Room entities or data errors.

Five small Use Cases expose Home paging, Search paging, cached observation, Home Details enrichment and on-demand Search Details. They delegate without adding scopes, dispatchers or result wrappers. `MovieSummary` and `MovieDetails` represent network Search metadata; `CachedMovie` represents Home metadata without certification claims. Data maps these types and translates transport failures to semantic `MovieAccessException` values, including native Paging load states.
Domain remains Kotlin/JVM but deliberately uses Coroutines and Paging-common for `Flow<PagingData<...>>`; it is not completely framework-independent. Room, Retrofit and Android-specific APIs remain outside domain. State management is MVVM with MVI-style one-way actions/state rendering, not a formal reducer/effect framework.

Home reads Room through a PagingSource and RemoteMediator. Each ordinary mediator load requests one Discover page and merges it atomically into Room. Home Details observes its existing cached row and makes one on-demand Details request for enrichment; failed requests preserve the cache. Search uses a query-scoped network PagingSource without Room access. Search Details fetches `/movie/{id}` into ViewModel memory without Room access.
Navigation uses Compose saved state, a selected movie ID and HOME/SEARCH origin, without a navigation library.

## Filtering and caching

Discover sends `include_adult=false`, `certification_country=US`, `certification.lte=PG-13` and `sort_by=popularity.desc`, as specified by the assignment. Search sends `include_adult=false` without unsupported certification restrictions, following TrianglZ's clarification. Search results are not rejected for R, NC-17 or unknown US classifications.

There is no independent per-candidate certification audit or Release Dates request. Removing that extra Home verification is an owner-approved interpretation of the original request configuration requirements, not a separate employer instruction. Server filtering must not be described as independently verified certification.

Room is Home's display source. Each Discover page is validated before a transaction merges distinct IDs. Existing rows retain discoveryOrder and enriched metadata absent from summaries; new IDs follow response order. Refresh starts at page 1 without clearing offline data. Revalidation updates encountered metadata, not every cached movie's current filter membership. Rows absent from refreshed pages remain, potentially indefinitely. The accumulated cache is not continuously reranked by popularity.

Home tracks raw page progress within each Pager session, up to TMDb's 500-page limit. Empty, short and duplicate-only pages use response metadata for exhaustion. A nonterminal empty page invalidates the Room PagingSource through Paging's standard factory after progress advances, allowing the next native boundary load without manual scanning. Errors retry the same page. Search remains normal network paging without local fallback or persistence.

Home Details can enrich an existing row with genres and other metadata, preserving order and missing fields. An absent Home row is not inserted from an unfiltered Details response. Search Details stays nonpersisting even for an ID already cached at Home. Offline Home can show saved metadata; images depend on Coil's cache and genre names may require an earlier successful Details request.

The existing `verified-movies.db` stays at Room version 3 with migrations **1 → 2** and **2 → 3**. Legacy rows, including historical Search contributions, remain; their provenance cannot be reconstructed. The legacy certification column is retained for compatibility but is not exposed as current evidence. No cache clearing, schema migration, TTL or automatic deletion is introduced.
## Setup and run

1. Open the repository root in Android Studio with support for the configured **Android Gradle Plugin 9.4.1**.
2. Install **Android SDK Platform 37**. Use an Android 8.0 / API 26 or newer device or emulator. The app compiles and targets API 37.
3. Use the checked-in **Gradle 9.6.0** wrapper. The daemon criteria in gradle/gradle-daemon-jvm.properties select **Java 25**; use a compatible JDK for Android Studio/command-line Gradle. Java/Kotlin bytecode targets remain 11.
4. Let Android Studio configure the SDK location in root local.properties. Add your TMDb **API Read Access Token**, without quotes or the Bearer prefix:

   ~~~properties
   tmdb.readAccessToken=YOUR_TMDB_API_READ_ACCESS_TOKEN
   ~~~

   Keep the existing SDK entry. local.properties is ignored and must not be committed. Do not commit or log credentials.

5. Sync Gradle, select the app run configuration and run on the device. To build from PowerShell at the repository root:

   ~~~powershell
   .\gradlew.bat :app:assembleDebug
   ~~~

   APK output: app/build/outputs/apk/debug/app-debug.apk.

The Data build reads this key into BuildConfig; the existing client supplies Bearer authentication. Rebuild after changing the token. An absent token permits compilation and deterministic tests; live requests report a configuration error. A token bundled into an APK is extractable, so a distributed APK is not secret storage.

## Tests

| Category | Coverage |
| --- | --- |
| Domain JVM | Use Case contracts, operation separation and cancellation. |
| Data JVM | MockWebServer API contracts/errors, metadata mapping, Home enrichment and Home/Search paging. |
| App JVM | Search debounce/cancellation, Details state handling and poster URL validation. |
| Room instrumentation | Atomic merges/rollback, real Room/Pager empty-page progression, metadata preservation and migrations. |
| Compose instrumentation | Home/Search states, Details presentation and navigation/query/scroll preservation. |

Run from the repository root in PowerShell:

~~~powershell
.\gradlew.bat :domain:test
.\gradlew.bat :data:testDebugUnitTest
.\gradlew.bat :app:testDebugUnitTest
~~~

For instrumentation, connect an API 26+ Android device or start a suitable emulator:

~~~powershell
.\gradlew.bat :data:connectedDebugAndroidTest
.\gradlew.bat :app:connectedDebugAndroidTest
~~~

Automated tests use local fixtures/fakes and MockWebServer rather than live TMDb credentials.

## Known scope

No user accounts, favorites or watchlists. Search results and Search Details use short-lived memory only; offline Search Details is not guaranteed, even if the same ID exists in Home's cache.
