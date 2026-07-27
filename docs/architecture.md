# Architecture

SIR is being restructured from a single-module app into a core/feature graph that
mirrors the package layout of its sibling iOS client, ShoutKit. The goal is parity of
*boundaries*, not of code: the same seams (station directory, playback policy,
persistence, design system) exist on both platforms so behaviour can be reasoned about
once.

## Module graph

```
:app                      composition root, Android surfaces (Auto, Tile, Widget, shortcuts)
├── :core:model           platform-neutral domain types (Station, StationQuery)
├── :core:directory       station catalogue boundary + radio-browser client
├── :core:playback        playback policy: equalizer, buffering, sleep timer, metadata,
│                         audio route, retry backoff, stream source and quality
├── :core:persistence     settings store, favourites/recents rules, station serialization
├── :cast                 on-demand dynamic feature (Chromecast)
├── :libs:media3-timeshift    DVR/time-shift DataSource (publishable)
└── :libs:okhttp-streaming    streaming-tuned OkHttp client factory (publishable)

:wear                     Wear OS companion
:benchmark                macrobenchmark / baseline profiles
```

`core` modules are internal to this repo and free to change; `libs` modules are the
ones intended for external publication (see `library-extraction-plan.md`).

## `:core:model`

`Station` is the single station type used by the network layer, the UI and DataStore
persistence. Its `@SerialName`s intentionally match the radio-browser.info payload so
the API response and the user's saved-station blob share one schema — this is what
allowed the type to move out of `:app` without a data migration.

## `:core:directory`

`RadioDirectory` is the only thing the app knows about station discovery. Concrete
behaviour is assembled from decorators by `RadioDirectories.create()`:

```
CuratedFallbackDirectory( CachingRadioDirectory( RadioBrowserDirectory ) )
```

- `RadioBrowserDirectory` — talks to radio-browser.info, builds URLs with
  `HttpUrl.Builder` (no hand-rolled escaping) and fails over across mirrors supplied by
  `RotatingMirrorProvider`. radio-browser has no single stable host, so rotation both
  spreads load and survives a single-mirror outage.

  Failover only applies to `IOException`. A decode error or a 4xx is a property of the
  request, so it fails identically on every mirror and retrying just multiplies the wait;
  those stop at the first mirror. The loop checks `ensureActive()` per iteration because
  OkHttp's `execute()` blocks and never observes cancellation on its own, and it holds a
  wall-clock budget so a run of slow mirrors cannot hold the search spinner for the sum of
  their timeouts. A blank HTTP body is treated as a malfunction rather than "no results" —
  radio-browser answers an empty search with `[]`, so a blank body means the mirror is
  broken and the next one should be tried.
- `CachingRadioDirectory` — short-TTL, LRU-bounded, in-memory. Only successful
  responses are stored, so an error never poisons the cache. Built once per process by
  `AppDirectory`, not per composition, so it survives rotation and back-navigation.
- `CuratedFallbackDirectory` — sits *outside* the cache so its results are never
  memoized. It converts a failure into bundled `CuratedStations`; an empty but
  successful response is passed through untouched, because "no such station" is a real
  answer and must not be masked.

Ordering is owned by the factory rather than by call sites, so the chain can be
re-tuned in one place.

## `:core:playback`

Pure policy extracted from `RadioPlaybackService`, which had grown to own ExoPlayer
setup, HTTP, wake locks, equalizer, sleep timer and media session in one class.

- `EqualizerPreset` carries its own gain curve, replacing a `when` block inside the
  service. `NORMAL` has a `null` curve, meaning literally 0 mB per band — deliberately
  not the midpoint of the range, which would be wrong on devices with an asymmetric
  `bandLevelRange`.
- `EqualizerCurves` does the band math with no dependency on
  `android.media.audiofx.Equalizer`, so it is covered by plain JVM tests.
- `PlaybackBufferConfig` turns the `DefaultLoadControl` magic numbers into validated,
  documented data (`LIVE_RADIO` is tuned for a ~64 kbps stream).
- `SleepTimerRestore` decides how a persisted deadline is restored after process
  death, independent of the service lifecycle.
- `StreamSourceResolver` decides what actually plays, with a fixed precedence:
  debug stream override > user-selected station > the quality-derived SIR URL. The
  service applies the result and no-ops when the URL is unchanged, so unrelated
  settings writes never interrupt playback.

  Both paths that can re-point the player — the service reacting to the persisted
  selection, and Media3 setting whatever `onAddMediaItems` returns — go through
  `adoptStreamSource`, which does the bookkeeping once and hands back the item. Whichever
  runs second sees an unchanged URL and no-ops, so the two cannot install differently
  built items. A selection change also forces playback to start: picking a station is a
  request to hear it, and a cold-launched player is prepared with `playWhenReady = false`,
  so inferring intent from "were we already playing" left the tap silent.

  There is no stream-quality intent. All three `StreamQuality` values resolve to the same
  mount today, so the selector was removed from settings and the service action with it;
  the enum and its persisted value remain because the resolver reads them for the default
  stream.
- `StreamMetadataResolver` interprets ICY metadata. Stations routinely emit a constant
  placeholder title instead of the current track, so the resolver knows which titles and
  artists are static and reports whether anything user-visible actually changed — the
  service then rebuilds the notification only when it must.
- `AudioRoutePolicy` is the noisy/resume state machine. Pausing on
  `ACTION_AUDIO_BECOMING_NOISY` is mandatory; resuming when the route returns is only
  correct if *we* paused. The claim is released when playback **starts** again, from
  `onIsPlayingChanged` — the one point every transport converges on, so the notification,
  the mini player, Auto, Wear and Bluetooth are all covered. Releasing it on the pause
  transition instead would be self-defeating: the route-loss pause is itself a pause, so
  it would cancel the claim it exists to protect. An explicit stop clears it too, since
  stopping never produces a start event.
- `RetryBackoff` holds the reconnect schedule (2s doubling, capped at 30s, 5 attempts).
- `PlaybackLocks` pairs the partial wake lock with the WiFi lock. Both acquires are
  idempotent — double-acquiring corrupts the refcount and leaks the lock past playback,
  which surfaces as battery drain rather than a crash.

## `:core:persistence`

Collection rules for saved and recently-heard stations, kept out of
`SettingsRepository` so they are testable without DataStore or Robolectric.

- `StationCodec` encodes/decodes station lists to the single JSON blob DataStore
  stores. Because `Station`'s serial names match the radio-browser payload, existing
  `saved_stations` values decode unchanged — no migration was needed.
- `StationCollections` owns the ordering and de-duplication rules: favourites are
  append-only and keyed by station id; recents are most-recent-first, de-duplicated by
  id and capped, so replaying a station moves it to the top rather than duplicating it.
- `SettingsRepository` is the only DataStore-aware layer. `selectStation` writes the
  selection and the recents entry in a single transaction so the two can never diverge.
  It also owns `StreamQuality` and `StreamConfig`, which moved here from `:app` so that
  nothing in the settings layer depends on the application module.

The DataStore instance is tracked against its owning application context rather than
through the `preferencesDataStore` property delegate. The delegate caches against the
first context it ever sees, which is correct in production but wrong under Robolectric,
where a test class can get a fresh Application and a fresh files directory — the cached
store would then write through a directory that no longer exists, and its tests would hang
until they timed out.

Exactly one store is held at a time. When a different Application appears, the previous
store's scope is cancelled before the replacement is built: DataStore only releases its
claim on the file when that scope completes, so skipping the cancel leaves two stores
contending for one file. An earlier attempt used a `WeakHashMap` keyed by context, which
looked self-bounding but was not — the cached value's `produceFile` lambda closes over the
very context used as its key, so no entry was ever weakly reachable and no scope was ever
cancelled.

Note for tests: DataStore does real IO on real threads, so `runTest`'s virtual clock
skips past it and turbine times out. Use `runBlocking` with direct `.first()` /
`StateFlow.value` reads, and reset the keys under test in `@Before` — the DataStore
file is shared across tests in a class.

## UI

`:app` hosts a four-tab shell (`SirAppShell`): listen, browse, library and settings.
The shell owns the scaffold, so the navigation bar and the mini player survive tab
changes; each tab contributes content only. The mini player is hidden on the listen tab,
where the full transport controls are already on screen.

Screens are still in `:app` rather than in `:feature:*` modules. `SettingsContent` is
bound to the Cast dynamic feature, the playback service and `BuildConfig.DEBUG`, and the
browse and library screens share one `RadioBrowserViewModel`; extracting them today would
relocate that coupling rather than remove it. The screens are already split into
content-only composables (`ListenScreen`, `BrowseScreen`, `LibraryScreen`,
`SettingsContent`), which is the prerequisite for the move.

## Distribution flavors

The `distribution` dimension has two flavors, `play` and `foss`. They are not a
packaging detail — they partition which features exist at all.

| | `play` | `foss` |
|---|---|---|
| Firebase Analytics / Crashlytics | yes | no |
| Play Core (`feature-delivery-ktx`) | yes | **no** |
| `androidx.mediarouter` | yes | **no** |
| `:cast` dynamic feature | installable on demand | not offered |
| Chromecast row in settings | shown | **absent** |

Everything else — playback, directory, persistence, Auto, tile, widget — is identical.

Cast is the whole of the difference, and it is structural rather than conditional.
Split installation is a Play Store service, so a FOSS build can never obtain the module;
shipping the client anyway put a proprietary dependency in the APK and gave FOSS users a
toggle whose install could not succeed.

`CastFeatureManager` and `CastDeviceDetector` therefore live in `src/play` and `src/foss`
as two implementations of one public API. Shared code in `src/main` names them without a
flavor check and the active source set decides which it gets. The FOSS pair is inert: the
manager reports `CastModuleState.Unavailable` permanently and the detector never reports a
receiver.

`Unavailable` is deliberately distinct from `Failed`. A failure invites a retry; this is a
permanent property of the build, and it is the signal `SettingsContent` uses to omit the
Chromecast row entirely rather than render a disabled switch.

Play-only tests live in `src/testPlay`; `src/testFoss` holds `FossCastPartitionTest`,
which fails if the Play implementations are ever moved back into `main`. Both flavors'
unit tests run in CI and in `just verify` — the FOSS variant is a shipped artifact and
had never been tested before this split.

Known gap: `dynamicFeatures` is declared once in the `android {}` block and AGP has no
per-flavor form of it, so `:cast` is still attached to every variant. This does not affect
`assembleFossRelease` — the artifact published to GitHub Pages — because feature code is
not part of the base APK. A FOSS *bundle* would still carry it.

## Platform surfaces

Android Auto, the quick-settings tile, the Glance widget and the Wear app all read from
the same core APIs as the phone UI rather than assuming the SIR stream is what is
playing.

- **Android Auto** browses `BROWSE_ROOT_ID` as the SIR stream followed by the saved
  stations from `SettingsRepository.savedStations`. Choosing one calls `selectStation`,
  the same persisted selection the phone UI writes, so the two surfaces stay in sync and
  the choice survives a service restart. Unplayable stations are filtered out.
- **Quick-settings tile** takes its subtitle from the media session metadata, so it
  follows a directory station and ICY title updates. It falls back to the app's station
  name when no controller is connected.
- **Glance widget** reads `selectedStation` directly — `provideGlance` is suspending, so
  no controller connection is needed to render the correct name.
- **Wear** is a standalone player and does not share a session with the phone, but it now
  takes its URL from `StreamConfig.DEFAULT_STREAM_URL` in `:core:playback` instead of
  duplicating the literal.

## Verification

`just verify` is the gate for this refactor: it assembles the FOSS debug variant, runs
the app, Wear and every `:core` unit test suite, and runs lint with
`warningsAsErrors`. Run it after each extraction step.
