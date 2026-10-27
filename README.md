# MovieApp

## Project overview

A native Android app for movie discovery, dynamic title-based search, movie details and cached browsing. Movies are checked against adult-content and US certification evidence before entering the displayable cache.

## Features

- **Home:** Paging 3 grid with posters, titles and years; cached content stays visible during refresh or network failure.
- **Search:** trimmed queries, 400 ms debounce, cancellation of obsolete searches and verified results in TMDb Search order.
- **Details:** poster, synopsis, date, rating out of 10, genres and verified US certifications, with missing-data fallbacks.
- **Navigation:** cards open Details by movie ID. On-screen and Android system Back preserve the originating screen's query and scroll position.
- Loading, empty, unavailable, error and Retry states, plus user-triggered continuation when a batch yields no eligible results.
- Offline Home/Details access to previously verified cached metadata. Posters depend on image-cache availability; new movies require connectivity.

## Tech stack

Kotlin, Jetpack Compose / Material 3, MVVM with ViewModels, StateFlow and coroutines, Hilt, Retrofit / OkHttp, Kotlinx Serialization, Paging 3, Room and Coil.

Dependencies are centralized in [gradle/libs.versions.toml](gradle/libs.versions.toml).

## Architecture

| Module | Responsibility |
| --- | --- |
| :app | Compose screens, ViewModels, navigation state and Hilt wiring. |
| :data | TMDb client/DTOs, verified movie repository, Room cache and Home/Search paging. |
| :domain | Pure Kotlin/JVM eligibility policy and input models. |

The repository retrieves Details and Release Dates for the same movie, evaluates the domain policy, then saves allowed metadata or removes denied content. Home reads Room through a PagingSource and RemoteMediator. Search uses a query-scoped PagingSource returning verified cache rows in search order. Details observes its Room row and requests a verified refresh.

Navigation uses Compose saved state and a selected movie ID, without a navigation library.

## Movie eligibility

- A positive movie ID must match the evidence. Trusted adult evidence must explicitly be false; true or unknown denies.
- At least one nonblank US certification is required. Every nonblank US value must be **G, PG or PG-13**.
- R, NC-17 and unrecognized US values deny, including conflicting allowed/restricted evidence. Blank records neither authorize nor veto separate valid proof. Non-US records cannot authorize US eligibility.
- Discover's adult/certification filters are only a candidate prefilter. Search candidates undergo the same independent verification.
- Failed or incomplete retrieval never authorizes an unverified movie. Request failures preserve previously verified cache rows; successfully verified denial removes them.

Details displays all distinct allowed US certification values deterministically, rather than arbitrarily selecting one.

## Caching and paging

Room is Home's display source. Persisted discoveryOrder places new verified rows after existing rows; updates and refresh preserve existing positions. Search can add verified movies to this shared cache, but its own result order follows the active TMDb query, not Home's order. The cache is not a continuously reranked popularity list.

Home tracks raw TMDb page progress within its Pager session. Home and Search scan at most three raw pages per load before offering continuation when needed; an empty eligible batch is not remote exhaustion. Movie IDs prevent duplicate stored rows.

Room version 3 includes data-preserving migrations **1 → 2** (ordering) and **2 → 3** (genres/certifications). Exported schemas are in data/schemas. Older rows receive new metadata after a successful verified refresh.

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
| Domain JVM | Classifications, identity, adult evidence and conflicting/missing certification. |
| Data JVM | MockWebServer API contracts/errors, verified cache behavior and Home/Search paging. |
| App JVM | Search debounce/cancellation, Details state handling and poster URL validation. |
| Room instrumentation | Persistence, Flow/PagingSource behavior and migrations. |
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

No user accounts, favorites or watchlists. Search-query ranking is session-based and is not persisted for offline Search.
