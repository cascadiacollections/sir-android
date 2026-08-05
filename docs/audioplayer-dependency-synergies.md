# AudioPlayer dependency synergies

An exploration of what SIR's playback stack should take from — and deliberately not take
from — ShoutKit's iOS playback engine, now that ShoutKit runs on AudioStreaming's
`AudioPlayer` rather than `AVPlayer`.

A1–A3 and B3 have since landed on this branch; each is marked below, and the rest are
filed as issues. The rest of the document is the survey as written, so the reasoning behind
each decision stays with the decision.

## The asymmetry that shapes everything below

ShoutKit adopted **AudioStreaming 1.4.4** (`dimitris-c/AudioStreaming`, MIT) as its
concrete `RadioPlaybackEngine`, backed by that library's `AudioPlayer` (`AVAudioEngine`).
Critically, AudioStreaming *by design* does not touch `AVAudioSession`. So
`AudioStreamingPlaybackEngine` (+`Session`) owns session configuration, activation with
retries, deactivation, interruptions, route changes, and media-services resets by hand —
about 550 lines of policy the dependency declines to have an opinion about. On top of
that, `PlaybackController` (+`Internals`/`+Recovery`/`+Interruptions`) owns orchestration:
bounded reconnect, stall ceiling, paused release, resume watchdog, hintless-resume window.

SIR's dependency is the opposite kind of dependency. Media3 `ExoPlayer` *does* own the
session-shaped concerns — audio focus, becoming-noisy, wake/WiFi locks, foreground
notification, load-error retry, join-time statistics — and exposes them as configuration.

So the synergies run in two directions, and the distinction matters more than any
individual item:

- **Behaviour parity is worth porting.** The things ShoutKit learned from real listeners
  (a resume that silently does nothing, a live stream the server closed reported as
  healthy, ICY junk on screen, a 404 station retried like a flaky one) are platform-neutral
  bugs. Android has most of the same holes.
- **Mechanism parity is not.** Porting `AudioOutput`, the interruption state machine, or
  the session-activation retry ladder to Android would re-implement, worse, what Media3
  already does — and the ShoutKit code exists precisely because iOS's dependency refused
  to.

One honest observation while comparing shapes: ShoutKit splits files at 400 lines because
`swiftlint --strict` fails the build otherwise, which is why `PlaybackController` is four
files. `RadioPlaybackService.kt` is 1032 lines and owns player construction, HTTP, the
media session, Android Auto browsing, notifications, the sleep timer, the equalizer and
DVR bookkeeping. Every item in group B below is also a way to shrink it.

## A. Behaviour gaps worth closing (port the policy, keep it pure)

Each of these lands in `:core:playback` as pure Kotlin with JVM tests, the pattern already
established by `RetryBackoff`, `AudioRoutePolicy` and `StreamMetadataResolver`.

### A1. ICY metadata is never parsed into artist + title

> **Landed.** `IcyMetadataParser` + `SongTitleFilter` in `:core:playback`, wired ahead of
> `StreamMetadataResolver`.

Verified against Media3 1.10.1: `IcyInfo.populateMediaMetadata` does exactly one thing —

```java
if (title != null) {
  builder.setTitle(title);
}
```

It sets `title` to the raw `StreamTitle` and never sets `artist`. So
`onMediaMetadataChanged` hands `RawStreamMetadata(title = "Artist - Song", artist = <the
static placeholder from our own MediaItem>)`, and `StreamMetadataResolver`'s artist branch
is effectively dead code in production: the notification's content title carries the whole
`"Artist - Song"` string and the artist line shows `stream_description`. Media3 will never
fix this — surfacing the raw ICY string is deliberate on its part.

ShoutKit handles four wire dialects in `ICYMetadataParser` (classic semicolon-terminated
`StreamTitle='…'`, comma-separated double-quoted broadcaster HLS, Triton Digital cue
metadata, and iHeart's *nested* cue block inside a classic ICY field, captured live from
WHTZ), suppresses ad-break markers (`Spot Block End`), and refuses to display
`key="value"` soup. `SongTitleFilter` then drops positive junk signals — a bare URL, the
station's own name, promo copy, a bare single-word ID — and drops the *update* rather than
blanking, so the last good track stays on screen.

**Recommendation:** port both as pure Kotlin into `:core:playback`, wired ahead of
`StreamMetadataResolver` (`raw title → parse → filter → resolve`). Highest user-visible
value per line in this document, no new dependency, and every surface benefits at once —
notification, Auto, Wear, widget, tile, Cast — because they all read the same
`MediaMetadata`. The Swift is already pure string logic with a test suite to mirror.
Roughly 250 lines of Kotlin plus tests.

### A2. An unexpectedly ended live stream is not treated as a drop

> **Landed.** `RadioPlaybackService.handleUnexpectedEnd()`.

This was ShoutKit's hardest-won lesson (`DECISIONS.md`, 2026-07-24): AudioStreaming
reports `.stopped` both for a requested stop and for its end-of-stream path, which a live
stream reaches whenever the server closes the connection. Swallowing it left a dead stream
displayed as playing, and — once paused — a player that would never resume.

Android has the same hole in a different vocabulary. `RadioPlaybackService`'s
`onPlaybackStateChanged` handles only `STATE_READY`; there is no `STATE_ENDED` branch. A
progressive live stream whose server drops the connection ends the media item rather than
raising `onPlayerError`, so nothing resets, nothing retries, and `RetryBackoff` — which
only runs from the error path — never sees it.

**Recommendation:** treat `STATE_ENDED` on a live item as a retryable drop and route it
through the existing `RetryBackoff` + `prepare()` path. That is
`handleUnexpectedStop()` translated. Small change; the decision it encodes is the whole
value.

### A3. Every failure is retried identically, including the permanent ones

> **Landed.** `StreamFailure` + `StreamFailureClassifier` in `:core:playback`, with the
> Media3 mapping in `:app`'s `PlaybackFailureMapping`.

`onPlayerError` currently takes any `PlaybackException` and retries five times on a
doubling 2s→30s schedule (~62s of wake-locked retrying) before showing a generic
`radio_error`. A station that answers 404 or 410, or serves an HTML error page where audio
should be, is retried exactly like a mobile handover.

ShoutKit types this: `PlaybackError` carries `isRetryable`, `userMessage` and
`shortUserMessage`, and `PlaybackFailure.classify` maps the underlying `NSURLError` into
`noInternet` / `stationNotAvailable(errorCode:)` / `playback(message:)`. The bounded
reconnect then spends attempts only on failures that can plausibly recover.

Media3 already carries the same information — this is a classification gap, not a
plumbing one: `PlaybackException.errorCode` (`ERROR_CODE_IO_BAD_HTTP_STATUS` with
`HttpDataSource.InvalidResponseCodeException.responseCode`,
`ERROR_CODE_IO_NETWORK_CONNECTION_FAILED`, `…_TIMEOUT`,
`ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE` for the "server sent a playlist or an HTML page"
case that is common in station directories).

**Recommendation:** a pure `StreamFailure` classifier in `:core:playback` mapping
`errorCode`/`responseCode` → a typed reason with `isRetryable`, mirroring `PlaybackError`'s
shape, consumed by `RetryBackoff` (skip retries entirely when not retryable) and by the
notification text so "this station isn't available" is distinguishable from "reconnecting".
The localized strings for both already exist.

### A4. A stalled stream can rebuffer forever

ShoutKit bounds this with a 90s stall ceiling: park the stream as `.paused` (not
`.failed` — a stall is not a user error, and paused keeps a working play button on the lock
screen) after attempting a reconnect. `DefaultLoadControl` has no equivalent give-up
behaviour, and with `WAKE_MODE_NETWORK` a stalled stream keeps the CPU and WiFi locks
alive while it retries.

**Recommendation:** a `StallCeiling` policy in `:core:playback` — injectable timeout, armed
on entering `STATE_BUFFERING` while `playWhenReady`, cleared on `STATE_READY` — that
triggers one reconnect and then stops playback. Cheap, and directly battery-relevant on a
phone in a pocket.

## B. Consolidations: things Media3 already does that we hand-rolled

### B1. The notification

~170 lines of `NotificationCompat` building, manual `startForeground` /
`stopForeground(DETACH)` transitions, a `POST_NOTIFICATIONS` permission check on the update
path, and a `setCustomLayout` dance that must avoid an empty button list because that
crashes the legacy `PlaybackStateCompat` stub. The comment says the override exists to keep
the seek-back action — but `SEEKBACK_ENABLED` is `false`, so the reason no longer holds.

Media3's `DefaultMediaNotificationProvider` renders play/pause plus `CommandButton`s from
the session's custom layout, owns the channel, and owns foreground promotion/demotion
(which is also where the current `updateNotificationSafe` permission gymnastics comes
from). **Recommendation:** drop the `onUpdateNotification` override and customize the
provider only where the default is genuinely wrong. Verify on-device — the notification's
appearance will change — and this pairs naturally with deciding whether the disabled DVR
feature stays.

### B2. `PlaybackLocks` duplicates locks ExoPlayer already holds

Verified against Media3 1.10.1's `ExoPlayer.Builder.setWakeMode` javadoc: with a wake mode
set, the player holds a `PowerManager.WakeLock` and a `WifiManager.WifiLock` whenever it is
in `STATE_READY`/`STATE_BUFFERING` with `playWhenReady = true`. `RadioPlaybackService` sets
`WAKE_MODE_NETWORK` *and* acquires its own pair from `onIsPlayingChanged` — so during
buffering (before `isPlaying` goes true) only ExoPlayer's locks are working, and during
playback both are held.

The one thing our version adds is `WIFI_MODE_FULL_LOW_LATENCY` on API 29+, where
ExoPlayer historically used `WIFI_MODE_FULL_HIGH_PERF`. **Recommendation:** confirm which
mode Media3 1.10.1 actually requests before deleting anything; if it is already
low-latency, `PlaybackLocks` is pure duplication and should go. If it is not, keep the
WiFi lock and drop the wake lock. (This is the one claim below that I could not verify from
source — the class moved between Media3 versions.)

### B3. Tap-to-audio latency: the dependency already measures it

> **Landed** in part: a `PlaybackStatsListener` now logs join and rebuffer numbers under the
> `SirPlaybackStats` tag. Routing them to Firebase on the `play` flavor is filed separately.

ShoutKit had to build `TapToAudioLatencyTrace` (`OSSignposter` intervals, resolve →
output-start → first-playing timings) because nothing on the iOS side reports it. Media3
ships it: `PlaybackStatsListener` yields `getTotalJoinTimeMs()`, `getMeanJoinTimeMs()`,
`validJoinTimeCount`, `totalRebufferCount`, `getMeanSingleRebufferTimeMs()` (verified in
1.10.1's `PlaybackStats`), and `AnalyticsListener.onAudioPositionAdvancing` reports
`playoutStartSystemTimeMs` — literally "audio started coming out".

**Recommendation:** attach a `PlaybackStatsListener` and route the numbers to the existing
`:benchmark` macrobenchmarks and, on the `play` flavor only, to Firebase. This is the
cleanest synergy in the document: an entire hand-written iOS subsystem costs about fifteen
lines here.

### B4. Two retry layers stacked without a combined budget

Media3 retries load errors *inside* the media source via `DefaultLoadErrorHandlingPolicy`
before ever surfacing `onPlayerError`; `RetryBackoff` then adds five more attempts around
that. A genuinely offline station therefore burns the inner retries times the outer ones,
and the wall-clock total is nobody's stated intention.

**Recommendation:** set a `LoadErrorHandlingPolicy` explicitly — it is handed the load
error and returns a retry delay or "don't retry", which is exactly where A3's
classification belongs — and re-derive the outer budget from the inner one. This is
measurement work, not a one-line change; the point of listing it is that the two layers
currently do not know about each other.

### B5. The equalizer can silently never initialize

`initializeEqualizer()` reads `player.audioSessionId` immediately after `prepare()`, logs
`"Audio session ID not set, skipping equalizer init"` when it is `AUDIO_SESSION_ID_UNSET`,
and returns — permanently, since nothing retries. Whether it works is a race with renderer
initialization.

**Recommendation:** generate the id up front with `AudioManager.generateAudioSessionId()`,
hand the same value to `ExoPlayer.Builder`/`setAudioSessionId` and to the `Equalizer`, and
the race disappears. `AnalyticsListener.onAudioSessionIdChanged` is the alternative if the
id must stay player-chosen.

### B6. Connection prewarming is *more* effective here than on iOS

ShoutKit's `StationConnectionPrewarmer` opens and immediately tears down an `NWConnection`
to the top few most-played hosts at launch, priming the process-wide DNS cache and TCP/TLS
path state. Its own comment notes the limit: AudioStreaming has its own socket, so only
OS-level warmth is shared.

On Android the same trick is strictly better, because `OkHttpDataSource` and the warm-up
would share one `OkHttpClient` — the `CachingDns` entry (5-minute TTL, already
implemented) *and* a pooled HTTP/2 connection are reused by the data source itself, not
just the DNS answer.

Two Android-specific caveats ShoutKit does not have to think about:
`StreamingHttpClientFactory` sets `maxIdleConnections = 2`, so warming five hosts would
thrash the pool and could evict the connection to the station actually about to play; and
warming must be skipped under battery saver (`PowerManager.isPowerSaveMode()`), the
analogue of the Low Power Mode gate ShoutKit chose — including its explicit rejection of
polling raw battery level, which remains the right call here.

**Recommendation:** a small, default-off prewarmer in `:libs:okhttp-streaming` (it fits
that module's publishable story), warming one or two hosts, gated on battery saver.
Measure with B3's join-time numbers before enabling — this is the one item that should not
ship on reasoning alone.

## C. Deliberately not ported

- **The interruption state machine.** `PlaybackController+Interruptions` — arming per
  interruption, the 90s hintless-resume window, the `otherAudioIsPlaying` check — exists
  because `AVAudioSession` hands you a notification and no policy, and because iOS omits
  the `shouldResume` hint for interruptions that plainly should resume. Media3's
  `setAudioAttributes(…, handleAudioFocus = true)` already implements the equivalent
  policy: transient loss suppresses and auto-resumes, permanent loss pauses and does not,
  ducking is handled. The useful Android-side addition is not the state machine, it is
  *surfacing* the state: `Player.getPlaybackSuppressionReason()` would let the mini player
  say "paused — another app is using audio" instead of showing a bare pause.
- **The media-services-reset rebuild.** Rebuilding the player and re-configuring the
  session after `mediaServicesWereResetNotification` has no Android analogue worth
  engineering. The nearest failures (`ERROR_CODE_AUDIO_TRACK_INIT_FAILED`, audio-track
  write errors) are already recovered by Media3 re-initializing the track, and anything
  that does surface is covered by A2/A3's `prepare()` path.
- **Session activation with a retry ladder.** There is no Android step between "we want
  audio" and "audio is ours" that can fail the way `AVAudioSession.setActive(true)` can;
  audio focus is a request Media3 makes and reports on.
- **An `AudioOutput`/`RadioPlaybackEngine` protocol plus DI container.** Tempting to mirror
  one-for-one, but the seam already exists in the dependency: `Player` is the interface,
  and `:cast`'s `SirCastPlayer` is already a second implementation of it. What SIR is
  actually missing is not an engine protocol — it is `PlaybackController`'s *role*: a
  testable orchestrator holding the reconnect/stall/metadata policy, talking to a `Player`,
  so that `RadioPlaybackService` shrinks to Android plumbing. That is the natural next
  step after A1–A4 exist as pure policy objects, and it continues the parity refactor from
  #121 rather than starting a parallel one.
- **AudioStreaming's own tradeoffs.** Its Ogg/Vorbis support pulls prebuilt
  `ogg-binary-xcframework` / `vorbis-binary-xcframework` binaries — a provenance concession
  ShoutKit records explicitly in `THIRD_PARTY_LICENSES.md`. Media3 decodes Vorbis and Opus
  in-process with no equivalent cost, so this is one place where the Android dependency
  graph is simply better off and nothing needs mirroring.

## Suggested order

| # | Item | Why first | Status |
|---|------|-----------|--------|
| 1 | A1 ICY parse + junk filter | Only item listeners see on every track, on every surface | landed |
| 2 | A2 `STATE_ENDED` as a drop | Bug-shaped; the fix is a branch and a decision | landed |
| 3 | A3 Typed failure classification | Unblocks B4 and improves the error copy | landed |
| 4 | B3 `PlaybackStatsListener` | Cheap, and it is the measurement A4/B6 need | landed (logging only) |
| 5 | A4 Stall ceiling | Battery, and reuses A3's plumbing | filed |
| 6 | B1 / B2 Notification + locks | Deletion, but needs on-device verification | filed |
| 7 | B4 / B5 / B6 | Each wants a measurement before it lands | filed |

## Verification notes

Checked against Media3 1.10.1 source: `IcyInfo.populateMediaMetadata` (A1),
`PlaybackStats` join/rebuffer accessors (B3), `ExoPlayer.Builder.setWakeMode`'s documented
lock behaviour and the presence of `setSuppressPlaybackOnUnsuitableOutput` (B2). Checked
against this repo: every "we currently do X" claim. **Not** verified: which WiFi lock mode
Media3 1.10.1 requests (B2), and whether a progressive live stream's `STATE_ENDED` arrives
in every server-drop case or only some (A2) — that one wants a real flaky station or a
mitmproxy'd connection drop.
