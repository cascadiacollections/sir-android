# SIR - Internet Radio for Android

A lightweight, battery-efficient internet radio app for Android, optimized for streaming audio playback.

## Features

- 🎵 **One-tap playback** - Tap anywhere to start/stop streaming
- 📱 **Media controls** - Full notification and lock screen controls
- 🎧 **Smart audio handling** - Auto-pause on headphone disconnect, resume on reconnect
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

## License

Copyright © 2026 Cascadia Collections. All rights reserved.

