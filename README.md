<div align="center">

# SIR - Internet Radio for Android

[![Build Status](https://github.com/cascadiacollections/sir-android/workflows/Android%20CI/badge.svg)](https://github.com/cascadiacollections/sir-android/actions)
[![API](https://img.shields.io/badge/API-24%2B-brightgreen.svg?style=flat)](https://android-arsenal.com/api?level=24)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-blue.svg?logo=kotlin)](https://kotlinlang.org)
[![Latest Release](https://img.shields.io/github/v/release/cascadiacollections/sir-android?include_prereleases&label=nightly)](https://github.com/cascadiacollections/sir-android/releases/tag/nightly)
[![APK Size](https://img.shields.io/badge/APK%20size-~2.5%20MB-success.svg)](https://github.com/cascadiacollections/sir-android/releases/tag/nightly)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

*A lightweight, battery-efficient internet radio app for Android, optimized for streaming audio playback.*

[Features](#features) • [Download](#nightly-builds) • [Building](#building) • [Tech Stack](#tech-stack) • [Roadmap](#roadmap)

</div>

---

## ✨ Features

- 🎵 **One-tap playback** - Tap anywhere to start/stop streaming
- 📱 **Media controls** - Full notification and lock screen controls
- 🎧 **Smart audio handling** - Auto-pause on headphone disconnect, resume on reconnect
- 😴 **Sleep timer** - Auto-stop playback after 15/30/60/90 minutes
- 🎛️ **Equalizer presets** - Normal, Bass Boost, Vocal, and Treble modes
- 🔋 **Battery optimized** - Minimal resource usage during playback
- 📶 **Network resilient** - Handles network changes gracefully
- 📺 **Chromecast support** - Cast to any Chromecast device (on-demand module)
- 💻 **ChromeOS ready** - Resizable windows, keyboard/mouse support

## ⚡ Optimizations

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

## 🛠️ Tech Stack

- **Kotlin** 2.0 with Compose UI
- **Media3** 1.5.1 (ExoPlayer + Cast)
- **OkHttp** 4.12 for HTTP/2 networking with connection pooling
- **Material 3** dynamic theming
- **Coroutines** for async operations
- **Play Feature Delivery** for on-demand modules
- **DataStore** for preferences persistence

### Build Toolchain

- **[mise](https://mise.jdx.dev/)** - Dev tool manager; single `.mise.toml` drives local and CI Java versions
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

## 🔨 Building

```bash
# Install mise (https://mise.jdx.dev/getting-started.html)
# then install project tools:
mise install

# Debug build
./gradlew assembleDebug

# Release build (requires signing config)
./gradlew assembleRelease
```

## 📦 Nightly Builds

<div align="center">

**Development builds are automatically published on every commit to `main`**

[![Download Nightly](https://img.shields.io/badge/Download-Nightly%20Build-blue?style=for-the-badge&logo=android)](https://github.com/cascadiacollections/sir-android/releases/tag/nightly)

</div>

**What's included:**
- ✅ Unsigned APK for sideloading (~2.5 MB release build)
- ✅ Debug variant with development tools (~16 MB)
- ✅ Replaced on each commit (no storage accumulation)
- ✅ Latest features and fixes

## 📋 Requirements

- Android 7.0 (API 24) or higher
- Internet connection for streaming

## 🗺️ Roadmap

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

### 🎨 UI/UX Enhancements

- Album art extraction from stream metadata (when available)
- Animated playback visualization (waveform or equalizer bars)
- Dark/light theme toggle (currently follows system)
- Playback history/recently played metadata

### 🔊 Audio Features

- Streaming quality selector (if multiple bitrates available)
- Bluetooth metadata display (AVRCP track info)
- Audio ducking during navigation/notifications
- Normalization/loudness leveling

### 📊 Analytics & Monitoring

- Streaming quality metrics (buffering events, bitrate)
- Crash reporting integration (Firebase Crashlytics or Sentry)
- Anonymous usage analytics (opt-in)

### 🌐 Network & Reliability

- Offline detection with graceful degradation
- Multiple stream URL failover support
- Metered network warning/confirmation
- VPN/proxy compatibility testing

### 🔒 Security & Privacy

- Certificate pinning for stream endpoints
- Privacy-focused analytics (no PII collection)
- Transparent data usage policy in-app

### 🧪 Testing & Quality

- Automated UI tests with Compose Testing
- Stream playback integration tests
- Performance benchmarking (startup time, memory usage)
- Accessibility audit (TalkBack, large text, contrast)

### 📦 Distribution

- F-Droid publication (fully OSS build)
- Play Store listing optimization
- Beta testing channel via Play Console
- APK size monitoring in CI

### 🔧 Developer Experience

- Detekt/ktlint for code style enforcement
- Dependency update automation (Renovate/Dependabot)
- Module documentation (KDoc)
- Architecture Decision Records (ADRs)

### ⚙️ Build & Runtime Optimization

**Baseline Profiles (Implemented ✅)**

- Manual baseline profile with critical startup paths bundled in APK
- `profileinstaller` library enables AOT compilation on install
- Benchmark module for measuring startup performance improvements
- Run benchmarks: `./gradlew :benchmark:connectedBenchmarkAndroidTest`
- Reduces cold startup time by 15-30% on first launch

**Build Performance**

- Remote build cache (Gradle Enterprise or GitHub Actions cache)
- Build scan analysis for bottleneck identification
- Modularization (`:core`, `:playback`, `:ui`) for parallel compilation
- Kotlin Incremental Compilation tuning (`kotlin.incremental.useClasspathSnapshot`)

**R8/ProGuard Enhancements**

- Custom R8 rules for aggressive OkHttp shrinking
- Art profile rewriting for optimal dex layout
- Startup profile integration for class ordering
- Regular R8 rule auditing for dead code

**Gradle Modernization**

- Migrate to Gradle Version Catalogs TOML for plugins (already using for dependencies)
- Gradle 9.x Isolated Projects when stable
- Configuration cache compatibility auditing
- Build logic extraction to convention plugins

**Runtime Performance**

- Strict mode profiling in debug builds
- Memory leak detection via LeakCanary (when dynamic feature compatible)
- Frame timing analysis for Compose UI
- TraceProcessor analysis for system trace debugging

**JVM & Kotlin Evolution**

- Kotlin 2.1+ K2 compiler stabilization monitoring
- Java 21 toolchain when AGP supports
- Kotlin/Native considerations for shared logic (future)
- Context receivers adoption when stable

**APK/AAB Optimization**

- Per-ABI APK splits for Play Store (vs fat APK for sideloading)
- On-demand asset delivery for future large assets
- APK Analyzer CI integration for size regression detection
- Texture compression format optimization (if adding visuals)

**CI/CD Enhancements**

- Parallel debug/release builds in GitHub Actions
- Instrumented test runs on Firebase Test Lab
- Automated Play Store deployment (Fastlane/Gradle Play Publisher)
- Dependency vulnerability scanning (Dependabot/Snyk)

---

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

Copyright © 2026 Cascadia Collections

