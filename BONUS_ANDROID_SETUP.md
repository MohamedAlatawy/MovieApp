# Custom Backend Android demo

This branch adapts the existing Home, Search and Details screens to the C# backend.
All rows are labeled Demo Data: synthetic metadata is **not** TMDb eligibility evidence.
The original policy, verified database and migrations are retained. Demo rows live in
`backend-demo.db`, with their own exported version-1 schema. There is no provider switch.

## Local backend and device

From the existing backend project, use its documented startup:

```powershell
$env:ASPNETCORE_ENVIRONMENT = 'Development'
$env:Firebase__Mode = 'Local'
# Optional isolated database for edit demonstrations:
$env:Database__Path = '<absolute-path-to-demo-verification.db>'
dotnet run --project src/MovieApp.Api --no-launch-profile --urls http://127.0.0.1:5187
```

Startup applies migrations and seeds 31 fictional movies once; it preserves subsequent
edits. Do not erase the normal database. Swagger is at `http://127.0.0.1:5187/swagger`
and the development editor at `/admin`. PATCH requires the owner's backend administrator
key; configure it only in the backend, never Android. Use movie 9001 in an isolated database.
Keep Firebase Local mode for deterministic demonstrations: PATCH commits but sends no push.

For a USB-connected physical device:

```powershell
adb reverse tcp:5187 tcp:5187
.\gradlew.bat :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Debug defaults to `http://127.0.0.1:5187/`. Override with
`-PbackendBaseUrl=http://10.0.2.2:5187/` for an emulator, and allow that hostname in the
backend process configuration. ADB reverse also works with emulators. Debug HTTP is
limited to loopback and the emulator host; release disallows cleartext and defaults to
an unavailable HTTPS placeholder. Set `-PbackendReleaseBaseUrl=https://your-host/` only
when a real HTTPS deployment exists. No TMDb Bearer or backend admin key is sent.

## Firebase client

Place the owner's Android client file at `app/google-services.json` (ignored by Git).
It must have project `movieapp-19491` and Android package `com.example.movieapp`.
The Google Services plugin is applied only when the file exists. Without it, the app
builds and browses the backend normally; Firebase messaging is disabled with a safe log.
This file is not an Admin SDK service-account credential.

The application subscribes to topic `movies` once at application startup. Check the
`MovieFirebase` log for subscription success; an attempted subscription is not proof.
Never install the previously exposed Admin key. The owner must revoke it and configure
a newly generated server credential securely before separately authorizing any live send.

## Update behavior and tests

`movie_updated` events require a positive decimal `movieId` and ISO UTC `updatedAt`.
Only the ID enters WorkManager. Network-constrained work refetches the authoritative
backend object for existing Home rows only; an uncached ID is a successful no-op.
Newer cached timestamps win over stale responses. Unique per-movie work replaces
pending work for repeated hints; failures preserve cache and receive at most three
retries. Foreground reconciliation refetches cached IDs without periodic polling.

Independently, an app-scoped non-replaying ID signal refreshes an active matching
SEARCH Details screen directly into memory through the domain Use Case. It does not
read or write Room and does not wait for a Worker. Repeated in-flight hints coalesce
into a subsequent request; navigation/backgrounding cancels obsolete Search work.
Failed refresh retains the displayed movie with Retry. Home Details observes Room.

The signal is transient, not guaranteed across process death or inactive observers.
WorkManager is the deferred Home path. Resuming an open Search Details screen fetches
current data normally. Search lists use network Paging only, with no offline fallback,
and do not derive result membership from Room.

Home page writes are atomic and preserve legacy rows. Backend order is ascending ID,
so no schema/order migration is needed. Unchanged/empty nonterminal pages invalidate
the session's Room PagingSource after progress commits, allowing native boundary
progress without manual scanning. Both databases and existing data are preserved.

```powershell
.\gradlew.bat :domain:test :data:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug
.\gradlew.bat :data:connectedDebugAndroidTest :app:connectedDebugAndroidTest
```

Backend JVM tests use MockWebServer. Demo Room tests cover transactions, rollback,
ordering, stale/duplicate responses, existing-row-only writes and cache isolation.
The real Room/Pager regression covers empty pages, append error/retry and unchanged
duplicate pages. Compose tests retain Search zero-item append Retry and Back/scroll
restoration. BackendUpdateTest uses local HTTP fixtures for Worker-to-Room-to-UI and
direct Search-event-to-UI refresh, verifying that Search leaves Home rows unchanged.
No real Firebase send, normal-database PATCH or external delivery is tested.

The reviewer must use an Android client configuration for the same Firebase project
as the backend sender (movieapp-19491 for the owner's environment), with package
com.example.movieapp. A reviewer using a different project must arrange matching
client/server configuration separately; topic names alone do not bridge projects.
The /admin portal remains a local development page, not a public hosted service.

## Android notification feedback

The backend payload remains data-only. Android locally posts generic **Movie updated**
notifications on the **Movie updates** channel, without fetching a title. Android 13+
requests POST_NOTIFICATIONS once from the Activity; the merged manifest already supplies
the permission through the messaging dependency. A denial is respected without repeated
prompts. App/channel notification settings remain authoritative; enable them manually in
Android settings if desired. Permission is not required for FCM data handling or cache work.

Notification taps use an immutable per-movie PendingIntent and open network-only Details,
so uncached movies work without insertion into Home Room. Cold starts and existing-task
intents use the same screen. Repeated updates replace the same movie's system notification.
No navigation happens merely because an event arrives.

Foreground receipt shows a brief **Movie update received** Snackbar, not a claim that
the Worker finished. It is dismissed on backgrounding and is not replayed on resume.
Both independent data paths remain active even if notification posting fails or is denied.
No immediate delivery guarantee is made for background, force-stopped or dead processes.

The published Backend integration document's earlier Room-backed Search description
is historical; this Android branch now follows the network-only contracts above.

For a real end-to-end send, separately confirm a fresh server credential, explicit
bounded-send permission, a controlled demo record and successful device topic subscription.
Verify PATCH commit, Firebase acceptance, device receipt, worker refetch, Room change and
visible UI change independently. None of these stages alone proves the later stages.

SDK references: [Firebase Android setup](https://firebase.google.com/docs/android/setup),
[WorkManager releases](https://developer.android.com/jetpack/androidx/releases/work).
