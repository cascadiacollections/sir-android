package com.cascadiacollections.sir.core.playback

/**
 * What is currently known about the stream, derived from ICY metadata.
 *
 * ICY is unreliable: many stations emit a constant placeholder title instead of the
 * current track, and some emit nothing at all. Keeping the "is this real track info?"
 * decision here means it can be tested without a player.
 */
data class StreamMetadata(
    val trackTitle: String? = null,
    val artist: String? = null,
    val station: String? = null,
)

/**
 * Raw ICY fields as reported by the player, before interpretation.
 */
data class RawStreamMetadata(
    val title: String? = null,
    val artist: String? = null,
    val station: String? = null,
)

/**
 * The outcome of folding a metadata update into the previous state.
 *
 * [notifyChanged] is false when nothing user-visible moved, so the service can skip
 * rebuilding the notification. Streams push metadata frequently, often unchanged.
 */
data class StreamMetadataUpdate(
    val metadata: StreamMetadata,
    val notifyChanged: Boolean,
)

/**
 * Interprets ICY metadata updates.
 *
 * The raw title arrives as whatever the broadcaster put on the wire — Media3 passes
 * `StreamTitle` through untouched — so it goes through [IcyMetadataParser] to separate
 * artist from title and then [SongTitleFilter] to reject junk, before any of it is treated
 * as the current track.
 *
 * @param staticTitles titles the stream emits as a constant placeholder rather than as
 *   the current track. These are never treated as track info.
 * @param staticArtists the same, for the artist field.
 */
class StreamMetadataResolver(
    private val staticTitles: Set<String> = emptySet(),
    private val staticArtists: Set<String> = emptySet(),
) {

    /**
     * @param stationName the station's display name, used by [SongTitleFilter] to reject a
     *   title that is only the station plugging itself. Defaults to whatever the stream
     *   reports as its station.
     */
    fun resolve(
        previous: StreamMetadata,
        raw: RawStreamMetadata,
        stationName: String? = null,
    ): StreamMetadataUpdate {
        val station = raw.station?.takeUnless { it.isBlank() } ?: previous.station
        val stationChanged = !raw.station.isNullOrBlank() && previous.station != raw.station

        /** Nothing usable in this update: keep the last known track, note only the station. */
        fun unchanged() = StreamMetadataUpdate(
            metadata = previous.copy(station = station),
            notifyChanged = stationChanged,
        )

        val isStaticTitle = raw.title.isNullOrBlank() || raw.title in staticTitles
        if (isStaticTitle) return unchanged()

        val parsed = IcyMetadataParser.parseTrack(raw.title)
        // The parser suppresses ad-break cues and undecomposable wire format outright; a
        // placeholder can also surface only after the artist half is split off.
        val parsedTitle = parsed.title?.takeUnless { it in staticTitles } ?: return unchanged()

        // Blank is treated as "unknown", matching title and station. Keeping "" here
        // overwrote a known artist with an empty subtitle and reported it as a change,
        // rebuilding the notification for nothing.
        val artist = (parsed.artist ?: raw.artist)
            ?.takeUnless { it.isBlank() || it in staticArtists }

        // Filtered on the effective pair rather than the parsed one: an artist the player
        // reported is still an artist, and it is what tells a one-token title like "1901"
        // apart from a placeholder ID.
        val track = IcyTrack(title = parsedTitle, artist = artist)
        if (!SongTitleFilter.isLikelySongTitle(track, stationName ?: station)) return unchanged()

        val next = StreamMetadata(trackTitle = parsedTitle, artist = artist, station = station)
        return StreamMetadataUpdate(metadata = next, notifyChanged = next != previous)
    }
}
