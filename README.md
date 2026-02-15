# SIR - Internet Radio for Android

A lightweight, battery-efficient internet radio app for Android, optimized for streaming audio
playback.

## Features

- 🎵 **One-tap playback** - Tap anywhere to start/stop streaming
- 📱 **Media controls** - Full notification and lock screen controls
- 🎧 **Smart audio handling** - Auto-pause on headphone disconnect, resume on reconnect
- 😴 **Sleep timer** - Auto-stop playback after 15/30/60/90 minutes
- 🎛️ **Equalizer presets** - Normal, Bass Boost, Vocal, and Treble modes
- 🔋 **Battery optimized** - Minimal resource usage during playback
- 📶 **Network resilient** - Handles network changes gracefully
- 📺 **Chromecast support** - Cast to any Chromecast device (on-demand module)
- 💻 **ChromeOS ready** - Resizable windows, keyboard/mouse support

## Optimizations

### Ultra-Lightweight APK

- **~2.5 MB release APK** - Fast download and install
- **On-demand Chromecast module** (~750KB) - Only downloaded when needed
- Aggressive R8/ProGuard optimizations with dead code elimination
- ARM + x86_64 ABIs included (phones, tablets, and ChromeOS)
- Compressed native libraries for faster app startup
- Unused dependencies stripped (no video/UI modules)

### Audio Playback Performance

- **HTTP/2 streaming** via OkHttp with HTTP/1.1 fallback for maximum compatibility
- **Connection pooling** with 5-minute keep-alive for instant reconnects on network switches
- **Zero call timeout** for uninterrupted live streaming
- **Optimized buffering** tuned for 64kbps live radio streams:
  - 2.5 second initial buffer for fast playback start
  - 15-60 second adaptive buffer range
  - Low-latency prioritization for live content
- **ICY metadata support** for displaying track information (when available)
- **Live stream sync** with adaptive playback speed (0.98x-1.02x) to stay synced

### Power Management

- **Partial wake lock** keeps CPU active only during playback
- **WiFi lock** prevents network power-saving during streaming
- Automatic lock release when paused/stopped
- Foreground service with efficient notification updates

### Modern Android Integration

- Built with **Media3/ExoPlayer** for best-in-class audio playback
- **MediaSession** support for system media controls
- Notification actions with play/pause controls
- No seek controls (optimized for live streaming)
- Targets **Android 7.0+** (API 24) with **Android 14** (API 36) SDK

### Chromecast Support

- **On-demand dynamic feature** - Cast module downloaded only when needed
- **Battery-efficient detection** - Only scans for Cast devices on WiFi when app is visible
- **Auto-download** - Automatically downloads Cast module when devices detected (if enabled)
- **Manual enable** - Settings toggle to enable Chromecast support
- Uses **default Google Cast receiver** - No custom receiver app required

### ChromeOS Compatibility

- **Resizable windows** - Proper desktop windowing support
- **Keyboard/mouse ready** - No touchscreen required
- **x86_64 ABI** - Native performance on Intel/AMD Chromebooks

## Tech Stack

- **Kotlin** 2.0 with Compose UI
- **Media3** 1.5.1 (ExoPlayer + Cast)
- **OkHttp** 4.12 for HTTP/2 networking with connection pooling
- **Material 3** dynamic theming
- **Coroutines** for async operations
- **Play Feature Delivery** for on-demand modules
- **DataStore** for preferences persistence

### Build Toolchain

- **Eclipse Adoptium Temurin JDK 17** - OSS Java runtime with long-term support
- **Gradle 9.1** with configuration cache for fast incremental builds
- **Foojay Toolchain Resolver** - Auto-downloads Adoptium JDK if not present
- **G1GC** with optimized pause times for responsive builds
- **Kotlin JVM Toolchain** with explicit vendor specification for reproducible builds

JVM optimizations applied:

- `-XX:+UseG1GC` - Low-latency garbage collector
- `-XX:MaxGCPauseMillis=200` - Target GC pause time
- `-XX:+UseStringDeduplication` - Reduce memory for duplicate strings
- `-XX:+ParallelRefProcEnabled` - Parallel reference processing

## Building

```bash
# Debug build
./gradlew assembleDebug

# Release build (requires signing config)
./gradlew assembleRelease
```

## Nightly Builds

Development builds are automatically published on every commit to `main`:

📦 **[Download Latest Nightly](../../releases/tag/nightly)**

- Unsigned APK for sideloading
- Replaced on each commit (no storage accumulation)
- Includes latest features and fixes

## Requirements

- Android 7.0 (API 24) or higher
- Internet connection for streaming

## ⚙️ Build & Runtime Optimization

### Baseline Profiles (Implemented ✅)

Baseline profiles enable ahead-of-time (AOT) compilation on app install, reducing cold startup time by 15-30% on first launch.

**What's Included:**
- Manual baseline profile (`app/src/main/baseline-prof.txt`) with critical startup paths bundled in APK
- `profileinstaller` library for AOT compilation on install  
- Benchmark module (`benchmark/`) for measuring startup performance and generating device-specific profiles
- Automated benchmarks for cold/warm/hot startup with different compilation modes

**Running Benchmarks:**
```bash
# Measure startup performance on connected device
./gradlew :benchmark:connectedBenchmarkAndroidTest

# Generate device-specific baseline profile  
./gradlew :benchmark:connectedBenchmarkAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.cascadiacollections.sir.benchmark.BaselineProfileGenerator

# Results will be in: benchmark/build/outputs/managed_device_android_test_additional_output/
```

**How It Works:**
1. Manual baseline profile defines critical hot paths (MainActivity, RadioPlaybackService, etc.)
2. Profileinstaller library extracts and installs profile on app install
3. Android Runtime (ART) uses profile for AOT compilation during install
4. Benchmark module exercises user journeys to generate device-specific profiles
5. Generated profiles can replace manual ones for production builds

**Future Optimizations:**

Consider these optimizations documented for future implementation:

- **Remote build cache** - Share build artifacts across developers and CI
- **Modularization** - Split into `:core`, `:playback`, `:ui` modules for better parallelization
- **Kotlin 2.1+ K2 compiler** - Faster compilation with new compiler backend
- **Java 21 toolchain** - When AGP adds support, upgrade from Java 17
- **Per-ABI APK splits** - Reduce APK size by splitting ARM/x86 variants
- **CI integration** - Monitor APK size, startup time, and memory usage in CI pipeline

## Roadmap

Future features planned for implementation:

### 🚗 Android Auto Support

- Hands-free voice control for car playback
- `MediaBrowserService` integration for Auto compatibility
- Simple play/pause interface optimized for driving

### ⌚ Wear OS Companion

- Lightweight Wear OS tile for quick play/pause
- Minimal battery impact using Horologist library
- Glanceable "Now Playing" information

### 📱 Home Screen Widget

- 1x1 or 2x1 widget with play/pause button
- Current track display (when metadata available)
- One-tap playback from home screen

## License

Copyright © 2026 Cascadia Collections. All rights reserved.

