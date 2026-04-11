# Library Extraction Plan

## Goal
Extract reusable, clean library APIs from `sir` into FOSS modules publishable under `com.cascadiacollections.android`.

---

## Proposed Libraries

### 1. `media3-timeshift` (Phase 1 — Ready now)
**Package:** `com.cascadiacollections.android.media3.timeshift`

**Components:**
- `CircularByteBuffer` — Thread-safe circular byte buffer for DVR-style buffering (pure Kotlin, zero deps)
- `TimeShiftDataSource` — Media3 DataSource wrapper enabling seek-back on live streams
- `PlaybackMode` — Sealed interface (`Live` / `TimeShifted`)

**Changes needed:**
- Parameterize thread name (currently hardcoded `"SIR-TimeShift"`)
- Expose chunk size as constructor parameter (currently `8192`)
- Move to new package namespace

**Tests to ship:** `CircularByteBufferTest`, `CircularByteBufferEdgeCaseTest`, `TimeShiftDataSourceTest`, `TimeShiftDataSourceFactoryTest`, `PlaybackModeTest`, `PlaybackModeExtendedTest`

**Dependencies:** `androidx.media3:media3-datasource` (compileOnly/api)

---

### 2. `dynamic-feature` (Phase 2)
**Package:** `com.cascadiacollections.android.dynamicfeature`

**Components:**
- `DynamicFeatureManager` — Generalized from `CastFeatureManager`, parameterized module name
- `FeatureModuleState` — Sealed interface (`NotInstalled`, `Installing(progress)`, `Installed`, `Failed(code)`)
- `DeviceDetector` — Generalized from `CastDeviceDetector`, WiFi-gated lifecycle-aware scanner

**Changes needed:**
- Rename `CastFeatureManager` → `DynamicFeatureManager(context, moduleName)`
- Rename `CastModuleState` → `FeatureModuleState`
- Rename `CastDeviceDetector` → `DeviceDetector`, add `requireWifi` param
- Remove `CAST_MODULE_NAME` constant, use constructor param

**Dependencies:** `com.google.android.play:feature-delivery-ktx`, `androidx.mediarouter`

---

### 3. `media3-streaming` (Phase 3 — Largest)
**Package:** `com.cascadiacollections.android.media3.streaming`

**Components extracted from RadioPlaybackService:**
- `CachingDns` — OkHttp DNS cache with configurable TTL, IPv4 preference
- `LiveStreamingOkHttpClient` — Builder with connection pooling, HTTP/2, keep-alive tuned for streaming
- `LowBitrateLoadControl` — Media3 LoadControl pre-configured for 64-128kbps streams
- `PlaybackWakeLockManager` — CPU + WiFi wake lock lifecycle management
- `SleepTimer` — Configurable sleep timer with callback, pause/resume
- `AudioRouteHandler` — Audio-becoming-noisy + headset/BT reconnect handling
- `EqualizerCurveCalculator` — `calculateEqualizerLevels()` with preset curves

**Dependencies:** `androidx.media3:media3-exoplayer`, `com.squareup.okhttp3:okhttp`

---

## Module Structure

```
sir/
├── app/                          # SIR radio app (depends on libraries)
├── libs/
│   ├── media3-timeshift/         # Phase 1
│   │   ├── build.gradle.kts
│   │   └── src/main/kotlin/com/cascadiacollections/android/media3/timeshift/
│   │       ├── CircularByteBuffer.kt
│   │       ├── TimeShiftDataSource.kt
│   │       └── PlaybackMode.kt
│   ├── dynamic-feature/          # Phase 2
│   │   └── ...
│   └── media3-streaming/         # Phase 3
│       └── ...
├── cast/
├── wear/
└── benchmark/
```

## Decision Log
- `ServiceUtils` — NOT extracted (too trivial, 5 lines of code)
- `StreamConfig` — NOT extracted (app-specific URL constant)
- `SettingsRepository` — NOT extracted (pattern is generic but DataStore wrappers are trivial; no unique value as library)
- `RadioWidget` / `RadioTileService` — NOT extracted (fully app-specific)

## Next Steps
1. ~~Create `libs/media3-timeshift` module~~ ✅ Done
2. ~~Move + refactor `CircularByteBuffer`, `TimeShiftDataSource`, `PlaybackMode`~~ ✅ Done
3. ~~Update `app` to depend on the new module~~ ✅ Done
4. ~~Verify all tests pass~~ ✅ Done (217 tests: 191 app + 26 library)
5. Repeat for Phase 2 and 3

## Phase 1 Review Notes (Professional Critique)
The following improvements are recommended before publishing as a standalone library:
- **API redesign**: Introduce a `TimeShiftController` to replace `Factory.lastCreated`
- **Time-based API**: Expose `seekBack(duration)` instead of `seekBack(bytes)` for consumer ergonomics
- **Buffer internals**: Consider making `CircularByteBuffer` internal, exposing only via controller
- **EOF handling**: Surface upstream EOF/errors to consumers instead of blocking forever
- **Publishing**: Add `maven-publish` plugin, POM metadata, Dokka for API docs
- **Binary compatibility**: Add Kotlin explicit API mode and API validation
