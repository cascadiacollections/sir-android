# media3-timeshift

DVR-style time-shift buffering for live media streams using [AndroidX Media3](https://developer.android.com/media/media3).

## Overview

`media3-timeshift` provides a `DataSource` wrapper that buffers live stream data in a thread-safe circular byte buffer, enabling **seek-back** (replay) and **go-live** (resume real-time) on streams that don't natively support seeking.

## Components

| Class | Description |
|---|---|
| `CircularByteBuffer` | Thread-safe circular byte buffer with seek-back and go-live cursor control |
| `TimeShiftDataSource` | Media3 `DataSource` that proxies an upstream source through the buffer |
| `PlaybackMode` | Sealed interface representing `Live` or `TimeShifted` state |

## Usage

```kotlin
// 1. Create a buffer (512KB ≈ 64s at 64kbps)
val buffer = CircularByteBuffer(capacity = 524_288)

// 2. Wrap your upstream DataSource factory
val timeShiftFactory = TimeShiftDataSource.Factory(
    upstreamFactory = okHttpDataSourceFactory,
    buffer = buffer,
    threadName = "MyApp-TimeShift"  // optional
)

// 3. Wire into ExoPlayer
val mediaSourceFactory = DefaultMediaSourceFactory(context)
    .setDataSourceFactory(timeShiftFactory)

val player = ExoPlayer.Builder(context)
    .setMediaSourceFactory(mediaSourceFactory)
    .build()

// 4. Control time-shift
val dataSource = timeShiftFactory.lastCreated

// Seek back 30 seconds (at 64kbps = 8KB/s → 240,000 bytes)
dataSource?.seekBack(bytesToSeek = 30 * 8_000)

// Return to live
dataSource?.goLive()

// Check state
val isLive = dataSource?.isLive() ?: true
```

## Buffer Sizing

Choose `capacity` based on your stream's bitrate and desired rewind window:

| Bitrate | 30s | 60s | 120s |
|---------|-----|-----|------|
| 64 kbps | 240 KB | 480 KB | 960 KB |
| 128 kbps | 480 KB | 960 KB | 1.9 MB |
| 256 kbps | 960 KB | 1.9 MB | 3.8 MB |

Formula: `capacity = (bitrate_bps / 8) * seconds`

## Threading Model

- **Producer thread**: `TimeShiftDataSource.open()` starts a daemon thread that continuously reads from the upstream `DataSource` into the buffer.
- **Consumer thread**: ExoPlayer's loading thread calls `read()`, which blocks when no data is available.
- **Thread safety**: All buffer operations are synchronized via `ReentrantLock`.

## Limitations

- Seek operations use byte offsets, not time-based durations. Consumers must convert time to bytes based on stream bitrate.
- Designed for single-stream use cases. The `Factory.lastCreated` pattern assumes one active `DataSource` at a time.
- The buffer does not persist across process death.

## Requirements

- **minSdk**: 24 (Android 7.0)
- **Media3**: 1.10.0+
- **Kotlin**: 2.0+

## License

See [LICENSE](../../LICENSE) in the repository root.
