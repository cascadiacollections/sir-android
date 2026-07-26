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
├── :core:playback        playback policy: equalizer curves, buffering, sleep timer
├── :core:persistence     favourites/recents collection rules + station serialization
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
- `CachingRadioDirectory` — short-TTL, LRU-bounded, in-memory. Only successful
  responses are stored, so an error never poisons the cache.
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
- `StreamMetadataResolver` interprets ICY metadata. Stations routinely emit a constant
  placeholder title instead of the current track, so the resolver knows which titles and
  artists are static and reports whether anything user-visible actually changed — the
  service then rebuilds the notification only when it must.
- `AudioRoutePolicy` is the noisy/resume state machine. Pausing on
  `ACTION_AUDIO_BECOMING_NOISY` is mandatory; resuming when the route returns is only
  correct if *we* paused, so a user-initiated pause clears the claim.
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

Note for tests: DataStore does real IO on real threads, so `runTest`'s virtual clock
skips past it and turbine times out. Use `runBlocking` with direct `.first()` /
`StateFlow.value` reads, and reset the keys under test in `@Before` — the DataStore
file is shared across tests in a class.

## Verification

`just verify` is the gate for this refactor: it assembles the FOSS debug variant, runs
the app, Wear and every `:core` unit test suite, and runs lint with
`warningsAsErrors`. Run it after each extraction step.
