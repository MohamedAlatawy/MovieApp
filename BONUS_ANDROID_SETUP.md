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
backend object and upserts Demo Room. Newer cached timestamps win over stale responses.
Unique per-movie work replaces pending work for repeated hints; failures retain cache
and receive at most three retries. Foreground reconciliation refetches cached IDs without
periodic polling. Home and Details observe Room; Search uses the same title predicate and
ID order as the backend, so reconciled title edits also update visible membership.

```powershell
.\gradlew.bat :domain:test :data:testDebugUnitTest :app:testDebugUnitTest
.\gradlew.bat :data:connectedDebugAndroidTest :app:connectedDebugAndroidTest
```

Backend JVM tests use MockWebServer. Demo Room instrumentation verifies ordering,
title reconciliation, stale/duplicate safety and verified-cache isolation.
`BackendUpdateTest` simulates an event, runs the real refresh worker against a local
HTTP fixture, and asserts both Room and visible Details update. It requires no Firebase
credential and does not claim Firebase delivery. Existing navigation and scroll tests
exercise the demo model; original Data/Domain TMDb tests remain intact.

For a real end-to-end send, separately confirm a fresh server credential, explicit
bounded-send permission, a controlled demo record and successful device topic subscription.
Verify PATCH commit, Firebase acceptance, device receipt, worker refetch, Room change and
visible UI change independently. None of these stages alone proves the later stages.

SDK references: [Firebase Android setup](https://firebase.google.com/docs/android/setup),
[WorkManager releases](https://developer.android.com/jetpack/androidx/releases/work).
