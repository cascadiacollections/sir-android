<div align="center">

# SIR - Internet Radio for Android

[![Build Status](https://github.com/cascadiacollections/sir-android/workflows/Android%20CI/badge.svg)](https://github.com/cascadiacollections/sir-android/actions)
[![API](https://img.shields.io/badge/API-24%2B-brightgreen.svg?style=flat)](https://android-arsenal.com/api?level=24)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.4.20-blue.svg?logo=kotlin)](https://kotlinlang.org)
[![Latest Release](https://img.shields.io/github/v/release/cascadiacollections/sir-android?include_prereleases&label=nightly)](https://github.com/cascadiacollections/sir-android/releases/tag/nightly)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

*A lightweight, battery-efficient internet radio app for Android, optimized for streaming audio playback.*

[Features](#features) • [Download](#nightly-builds) • [Building](#building) • [Tech Stack](#tech-stack) • [Privacy](#privacy)

</div>

---

## Features

- **One-tap playback** — Tap to start/stop streaming
- **Media controls** — Notification, lock screen, and Quick Settings tile
- **Smart audio** — Auto-pause on headphone disconnect, resume on reconnect
- **Sleep timer** — 15/30/60/90 minute auto-stop
- **Equalizer** — Normal, Bass Boost, Vocal, and Treble presets
- **Chromecast** — Cast to any device (on-demand dynamic module)
- **Home screen widget** — Glance widget with Material 3 theming
- **Android Auto** — Hands-free media controls
- **Wear OS** — Standalone companion app
- **ChromeOS** — Resizable windows, keyboard/mouse support
- **App shortcuts** — Long-press to instantly start playback
- **Auto-reconnect** — Exponential backoff retry on stream errors
- **Open source licenses** — In-app OSS license viewer
- **Automation** — Play links for routines, Tasker and home-screen shortcuts (below)

## Automation

Any station can be started without opening the app, e.g. the NPR hourly newscast every
morning. Long-press a station on Listen Now, in search results, or among Library's saved
stations, then:

- **Copy automation link** copies `sir://play/<station id>`.
- **Add to home screen** pins a shortcut that plays the station (launchers that support
  pinning only).

Opening `sir://play/<id>` plays that station with no UI. `sir://play` with no id plays the
last selected station. (`sir://station/<id>` still opens the app on the station.) The NPR
Newscast link is `sir://play/a5314180-7573-4b46-aafc-51ed2d5b9e71`. It is a ~5 minute file,
not a live stream, so it plays once and stops unless Settings → "Loop finished broadcasts"
is on.

**Google Assistant routine.** Google Home app → Routines → e.g. "Good morning" → Add
action → "Try adding your own" (custom command) → `play NPR Newscast on SIR`. This uses
SIR's voice search, which checks your saved stations by name and then the directory. Which
Assistant routine features are available depends on your device and on Google's rollout
of Gemini, so this one is untested.

**Tasker / MacroDroid / Automate.** Trigger at 7:00, then either:

- open the URL `sir://play/<id>` (Tasker: *Net → Browse URL*), or
- send an intent to an **activity** with action
  `com.cascadiacollections.sir.action.PLAY_STATION` and data `sir://play/<id>` (Tasker:
  *System → Send Intent*, Target: Activity).

Android only lets a background app open an activity in limited cases, so the automation
app needs its "Display over other apps" permission (Tasker and MacroDroid ask for it).

**Samsung Modes & Routines.** Untested on a Samsung device. If your version has an action
that opens a link, use `sir://play/<id>`. Otherwise, pin the station with **Add to home
screen** and use that shortcut where Routines lets you pick an app shortcut.

**Testing from a computer:**

```bash
adb shell am start -a android.intent.action.VIEW -d sir://play/<id>
```

## Variants

| Variant | Description |
|---------|-------------|
| **Play** | Google Play build with Firebase Crashlytics + Analytics |
| **FOSS** | Fully open-source build, no Google dependencies |

## Optimizations

### Lightweight APK

- **~2.5 MB release APK** with aggressive R8 optimizations
- On-demand Chromecast module (~750KB) downloaded only when needed
- ARM + x86_64 ABIs (phones, tablets, ChromeOS)

### Audio Playback

- **HTTP/2** via OkHttp 5 with connection pooling and DNS caching
- **Modern TLS only** — `ConnectionSpec.MODERN_TLS` rejects legacy ciphers
- **Network security config** — cleartext traffic blocked, debug proxy support
- **Optimized buffering** tuned for 64kbps live radio (2.5s initial, 15-60s adaptive)
- **ICY metadata** for track info display
- **IPv4 preference** on mobile networks for faster connections

### Power Management

- Partial wake lock + WiFi lock during playback only
- Automatic lock release when paused/stopped
- Foreground service with efficient notification updates

## Tech Stack

- **Kotlin** 2.4.20 with **Compose** UI + Material 3 Expressive
- **Media3** 1.11.1 (ExoPlayer + MediaSession + Cast)
- **OkHttp** 5.5.0 for HTTP/2 networking
- **DataStore** for preferences
- **Glance** for home screen widget
- **Firebase Crashlytics/Analytics** (Play builds only)
- **aboutlibraries** for OSS license display
- **LeakCanary** in debug builds

### Build Toolchain

- **AGP 9.4.1** with configuration cache
- **Eclipse Adoptium Temurin JDK 21** via Foojay resolver
- **Gradle 9.7.1** with parallel builds and build cache
- **JaCoCo** coverage with 5% minimum threshold
- **Baseline profiles** for AOT compilation
- **Convention plugins** (`build-logic/`) for shared config

## Building

```bash
# Install mise (https://mise.jdx.dev/getting-started.html)
mise install

# Preferred local build commands (clean, minimal Gradle output)
just build       # uses default flavor ("foss")
just build-foss

# Debug build (Play variant)
./gradlew assemblePlayDebug

# Debug build (FOSS variant)
./gradlew assembleFossDebug

# Release build (requires signing config)
./gradlew assemblePlayRelease assembleFossRelease

# Run tests
./gradlew testPlayDebugUnitTest :wear:testDebugUnitTest

# Lint
./gradlew lintPlayDebug

# Refactor baseline validation
just verify
```

## Architecture

The app is split into a core/feature module graph that mirrors the boundaries of its
sibling iOS client:

| Module | Responsibility |
| --- | --- |
| `:app` | Composition root, Compose UI, Android surfaces (Auto, Tile, Widget, shortcuts) |
| `:core:model` | Platform-neutral domain types (`Station`, `StationQuery`) |
| `:core:directory` | `RadioDirectory` boundary: radio-browser client, mirror rotation, caching, curated fallback |
| `:core:playback` | Playback policy: equalizer curves, buffer config, sleep timer restore, audio route, stream source |
| `:core:persistence` | Settings store, favourites/recents rules, station serialization |
| `:cast` | On-demand Chromecast dynamic feature (Play flavor only) |
| `:libs:media3-timeshift` | DVR/time-shift Media3 `DataSource` (publishable) |
| `:libs:okhttp-streaming` | Streaming-tuned OkHttp client factory (publishable) |

See [`docs/architecture.md`](docs/architecture.md) for the design rationale.

## Nightly Builds

<div align="center">

**Development builds are automatically published on every commit to `main`**

[![Download Nightly](https://img.shields.io/badge/Download-Nightly%20Build-blue?style=for-the-badge&logo=android)](https://github.com/cascadiacollections/sir-android/releases/tag/nightly)

</div>

| Artifact | Description |
|----------|-------------|
| `app-play-release.aab` | Signed AAB for Play Store |
| `app-play-release-unsigned.apk` | Play release build with Crashlytics |
| `app-foss-release-unsigned.apk` | FOSS release build (no Google deps) |
| `app-play-debug.apk` | Debug build with LeakCanary + StrictMode |

## Requirements

- Android 7.0 (API 24) or higher
- Internet connection for streaming

## Privacy

SIR collects no personal data. All preferences are stored locally on-device. See the [Privacy Policy](https://cascadiacollections.github.io/sir-android/privacy/).

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

Copyright © 2026 Cascadia Collections
