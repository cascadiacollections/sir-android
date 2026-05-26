# SIR vs Transistor: Comprehensive Feature Parity Analysis

## Executive Summary

This document maps SIR's current capabilities against Transistor (FOSS competitor) to identify gaps and prioritize feature implementation. SIR is positioned as a **focused single-stream player** with modern architecture (Media3, Compose, DataStore), while Transistor is a **full-featured station collection manager** with legacy codebase (ExoPlayer 1.9.2, XML layouts, SharedPreferences).

**Feature Parity Goal**: Achieve feature equivalence in core user workflows while maintaining SIR's focused UX philosophy.

---

## ✅ CURRENT IMPLEMENTATION (feature branch)

### Core Playback
- [x] ExoPlayer-based streaming (Media3)
- [x] Live stream playback with auto-reconnect
- [x] Audio focus handling
- [x] Media session integration (lock screen controls)
- [x] Wake lock management
- [x] Hardware media buttons support
- [x] Foreground service with notification

### Station Management (New)
- [x] Radio-browser.info API integration
- [x] Station search by name/keyword
- [x] Save/favorite stations to DataStore
- [x] Persistent station list
- [x] Quick-select via preset dropdown
- [x] Debug stream override presets

### User Preferences
- [x] Sleep timer (15/30/60/90 minutes)
- [x] Dark mode (system + light/dark override)
- [x] Equalizer presets (Normal, Bass Boost, Vocal, Treble)
- [x] Chromecast detection (CastContext)
- [x] Settings persistence (DataStore)

### Debug/Developer Features
- [x] FIFO buffer export (real-time pipe streaming)
- [x] Offline capture (rolling 5-min snapshots for airplane mode)
- [x] Live stream URL switching without restart
- [x] HTTP logging interceptor

---

## 🚀 HIGH PRIORITY GAPS (Phase 1 - Essential for Parity)

### Station Management (Critical) — ~40 hours

| Feature | Status | Transistor Reference | Complexity | UX Impact |
|---------|--------|----------------------|-----------|-----------|
| **Edit saved stations** | ❌ | CollectionAdapter.kt:133-150 | HIGH | CRITICAL |
| Station rename | ❌ | Long-press to expand card | HIGH | CRITICAL |
| Change stream URL | ❌ | EditText in expanded card | HIGH | CRITICAL |
| Update artwork | ❌ | Image picker + color extract | HIGH | MEDIUM |
| **M3U/PLS Import** | ❌ | AndroidManifest:45-80 | MEDIUM | HIGH |
| **M3U Export** | ❌ | SettingsFragment.kt:228-239 | MEDIUM | HIGH |
| Station grouping | ❌ | Not explicitly visible | MEDIUM | MEDIUM |
| Drag-to-reorder | ❌ | RecyclerView ItemTouchHelper | MEDIUM | MEDIUM |
| Duplicate detection | ❌ | Station.kt validation | LOW | LOW |

**Rationale**: Users can discover stations but cannot customize them. This is the #1 UX complaint blocking adoption.

**Implementation Strategy**:
1. **Edit sheet**: Long-press saved station → expand with edit fields
2. **M3U parsing**: Simple regex-based parser for stream URLs
3. **M3U export**: Format saved stations as M3U playlist
4. **Reordering**: LazyColumn with reorderable state

**Estimated Effort**: 30-40 hours over 2 sprints

---

### UI/UX Polish (High Priority) — ~35 hours

| Feature | Status | Transistor Reference | Notes |
|---------|--------|----------------------|-------|
| **Material You dynamic colors** | ❌ | DynamicColors.isDynamicColorAvailable() | Android 12+ |
| **Station artwork display** | ❌ | CollectionAdapter.kt image loading | Glide + color extraction |
| **Metadata history** | ❌ | IcyCast parsing + 25-entry history | Show current track info |
| **Expandable station cards** | ❌ | Long-press interaction | Long-click to edit |
| **Voice search** | ❌ | MEDIA_PLAY_FROM_SEARCH intent | "Play [station]" voice commands |
| **Home screen shortcuts** | ❌ | ShortcutHelper.kt | Station quick-launch |
| **Adaptive icons** | ❌ | Shortcuts with adaptive icons | Station artwork as icon |

**Implementation Strategy**:
- Use Material 3 `dynamicColorResource()` (requires Material3 1.2+)
- Extract dominant color from station artwork
- Parse IcyInfo headers for metadata extraction
- Implement voice action via `setPlayFromSearch`

**Estimated Effort**: 25-35 hours

---

## 📋 MEDIUM PRIORITY GAPS (Phase 2 - Enhanced Experience) — ~50 hours

### Playback & Audio
- [ ] **Audio equalizer UI**: Multi-band EQ display (Transistor basic preset-only)
- [ ] **Large buffer toggle**: "Use large buffer for slow connections"
- [ ] **Codec/bitrate display**: Show stream metadata in UI
- [ ] **HLS support**: HTTP Live Streaming (Transistor: experimental)
- [ ] **Metadata copy**: Copy now-playing to clipboard

### Data & Search
- [ ] **Live search debounce**: 1-second delay to reduce API calls
- [ ] **Collection size tracking**: "X stations saved" badge
- [ ] **Last played station**: Auto-resume functionality
- [ ] **Per-app language selector**: User-selectable UI language
- [ ] **RTL layout support**: Right-to-left text support

### Search Enhancements
- [ ] **Advanced search filters**: Genre, country, language
- [ ] **Popular stations**: Top 20 featured stations
- [ ] **Search history**: Recent searches with quick re-play
- [ ] **Trending stations**: Time-based popularity

---

## 📊 LOWER PRIORITY GAPS (Phase 3-4 - Advanced Features) — ~60+ hours

### System Integration
- [ ] **Google Cast**: Full Chromecast integration (currently detected but not integrated)
- [ ] **Android Auto/Automotive**: MediaBrowser service for car head unit support
- [ ] **App shortcuts**: Deep linking and intent handling
- [ ] **Notification actions**: Play/pause/next in notification
- [ ] **Broadcast receivers**: System event listening

### Platform Features
- [ ] **Widgets**: Home screen player widget
- [ ] **Complications**: Wear OS watch app
- [ ] **Backup & sync**: Cloud station backup (Firebase, Nextcloud)
- [ ] **Offline mode**: Download station list for offline search

### Advanced Search
- [ ] **Local M3U parser**: Import station collections from files
- [ ] **Shoutcast directory**: Direct Shoutcast server integration
- [ ] **TuneIn API**: Access to TuneIn directory (requires API key)
- [ ] **Community ratings**: User-submitted station quality ratings

---

## 🎯 PHASED ROADMAP

### Phase 1: Essential Parity (Weeks 1-4) — ~75 hours
**Goal**: Achieve feature parity with core station discovery + management

- [x] ✅ Radio-browser.info search (DONE)
- [x] ✅ Save stations (DONE)
- [x] ✅ Stream override (DONE)
- [ ] Edit saved stations (rename, URL, artwork)
- [ ] M3U import/export
- [ ] Material You dynamic colors
- [ ] Station card UI with artwork
- [ ] Metadata history display

**Deliverable**: User can discover, save, edit, and manage stations end-to-end

**Branch**: `feature` (current)

---

### Phase 2: UX Excellence (Weeks 5-8) — ~50 hours
**Goal**: Polish and modernize user experience

- [ ] Voice search integration
- [ ] Home screen shortcuts
- [ ] Live search with debounce
- [ ] Metadata copy & sharing
- [ ] Equalizer improvements
- [ ] Expandable station cards with long-press edit

**Deliverable**: App feels complete and modern compared to Transistor

---

### Phase 3: System Integration (Weeks 9-12) — ~40 hours
**Goal**: Deep Android platform integration

- [ ] Google Cast (Chromecast support)
- [ ] Android Auto integration
- [ ] Notification action buttons
- [ ] App shortcuts with intents
- [ ] Wear OS complications

**Deliverable**: Works seamlessly with Android ecosystem

---

### Phase 4: Advanced Features (Ongoing) — ~60+ hours
**Goal**: Exceed Transistor capabilities in focused areas

- [ ] Backup/restore (cloud sync)
- [ ] Advanced search filters
- [ ] Offline support
- [ ] Station recommendations
- [ ] Community features

---

## 📐 TECHNICAL ARCHITECTURE

### Data Flow
```
Radio-browser API
    ↓
RadioBrowserService (HTTP client + JSON parsing)
    ↓
RadioBrowserViewModel (state management)
    ↓
StationSearchSheet (search UI)
    ↓
SettingsRepository (DataStore persistence)
    ↓
SettingsSheet (station selection) + RadioPlaybackService (playback)
```

### File Organization
```
app/src/main/java/com/cascadiacollections/sir/
├── RadioBrowserService.kt          (API client)
├── RadioBrowserViewModel.kt        (search state)
├── SettingsRepository.kt           (persistence)
├── RadioPlaybackService.kt         (playback engine)
├── ui/
│   ├── StationSearchSheet.kt       (search UI)
│   └── SettingsSheet.kt            (settings + station list)
└── StreamConfig.kt                 (fallback test streams)

libs/media3-timeshift/
└── CircularByteBuffer.kt           (DVR replay buffer - used for offline capture)
```

---

## 🏗️ IMPLEMENTATION GUIDELINES

### Code Quality Standards
- **Kotlin 1.9+**: Coroutines, Flow, Data classes
- **Jetpack Compose**: All new UI (no XML layouts)
- **Material Design 3**: System-aware theming
- **DataStore**: No SharedPreferences
- **Media3**: Latest ExoPlayer wrapper

### Testing Requirements
- **Unit Tests**: 80%+ coverage for new features
- **Integration Tests**: RadioBrowser API mocking
- **UI Tests**: Compose testing framework
- **E2E**: Device deployment validation

### Performance Targets
- **Search**: <500ms response time
- **UI Render**: 60 fps on mid-range devices
- **Memory**: <100MB peak usage
- **Battery**: <5% drain per hour playback

---

## 🚨 KNOWN LIMITATIONS & DECISIONS

### By Design (Won't Fix)
| Limitation | Reason |
|-----------|--------|
| Single-stream only | Focused UX philosophy vs Transistor's collection manager |
| Radio-browser only | No M3U parsing initially (can add later) |
| No streaming to file | Airplane mode uses snapshot captures + ffmpeg conversion |
| No native HLS | Media3 HLS support incomplete vs legacy ExoPlayer support |
| No Chromecast native | Delegated to Media3 (requires user intent) |

### Phase-Out Candidates
- FOSS and Play Store flavor split (consider consolidation)
- Manual stream quality selection (auto-detect more reliable)
- Timeshift DVR (feature-complete, low usage)

---

## 🎓 REFERENCES

### Transistor Architecture
- **Repository**: https://codeberg.org/y20k/Transistor (v4.3)
- **Tech Stack**: ExoPlayer 1.9.2, XML layouts, SharedPreferences
- **Key Files**:
  - `BasePlayerService.kt`: Playback engine
  - `CollectionAdapter.kt`: Station UI
  - `FindStationDialog.kt`: Search integration
  - `RadioBrowserSearch.kt`: API client

### SIR Architecture
- **Repository**: https://github.com/cascadiacollections/sir-android
- **Tech Stack**: Media3, Jetpack Compose, DataStore
- **Key Files**:
  - `RadioPlaybackService.kt`: Playback engine
  - `RadioBrowserService.kt`: API client (new)
  - `SettingsSheet.kt`: Station UI (new)
  - `RadioBrowserViewModel.kt`: Search state (new)

---

## 📝 NEXT STEPS

1. **Immediate** (This week):
   - Code review of feature branch
   - Rubber duck testing on device
   - Merge to main

2. **Short-term** (Next sprint):
   - Create GitHub issues for Phase 1 tasks
   - Assign priority labels and points
   - Begin edit UI implementation

3. **Medium-term** (Following sprint):
   - M3U import/export support
   - Material You integration
   - Metadata history display

4. **Long-term** (Next quarter):
   - Phase 2: UX polish
   - Phase 3: System integration
   - Phase 4: Advanced features

---

## 👥 CONTRIBUTORS & NOTES

- **Feature Analysis**: Radio-browser API analysis + Transistor codebase review
- **Architecture**: Media3/Compose modernization vs ExoPlayer 1.9.2 legacy
- **UX Philosophy**: Focused single-stream player (SIR) vs collection manager (Transistor)
- **Implementation**: Staged rollout with incremental feature additions

**Last Updated**: 2026-05-25  
**Next Review**: After Phase 1 completion
