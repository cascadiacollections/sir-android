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
 * @param staticTitles titles the stream emits as a constant placeholder rather than as
 *   the current track. These are never treated as track info.
 * @param staticArtists the same, for the artist field.
 */
class StreamMetadataResolver(
    private val staticTitles: Set<String> = emptySet(),
    private val staticArtists: Set<String> = emptySet(),
) {

    fun resolve(
        previous: StreamMetadata,
        raw: RawStreamMetadata,
    ): StreamMetadataUpdate {
        val station = raw.station?.takeUnless { it.isBlank() } ?: previous.station
        val stationChanged = !raw.station.isNullOrBlank() && previous.station != raw.station

        val isStaticTitle = raw.title.isNullOrBlank() || raw.title in staticTitles
        if (isStaticTitle) {
            return StreamMetadataUpdate(
                metadata = previous.copy(station = station),
                notifyChanged = stationChanged,
            )
        }

        val artist = raw.artist?.takeUnless { it in staticArtists }
        val next = StreamMetadata(trackTitle = raw.title, artist = artist, station = station)
        return StreamMetadataUpdate(metadata = next, notifyChanged = next != previous)
    }
}
