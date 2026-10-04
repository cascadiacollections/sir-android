package com.cascadiacollections.sir

import android.content.Context
import android.os.Bundle
import androidx.media3.common.Player

/**
 * What "What's playing?" says — ShoutKit's Siri intent of the same name, answered by
 * [NowPlayingAnnounceActivity] from the session's metadata.
 *
 * Pure: the session state comes in as plain values and the copy as [Templates], so every
 * case is a JVM test and the activity only does the Android plumbing.
 */
sealed interface NowPlayingAnnouncement {

    /** A resolved ICY track; [artist] is null when the stream only sent a title. */
    data class Track(val title: String, val artist: String?, val station: String) : NowPlayingAnnouncement

    /** Playing, but no track is known — the station's own placeholder or no ICY at all. */
    data class StationOnly(val station: String) : NowPlayingAnnouncement

    /** Paused, stopped, failed, or the service is not running. */
    data object NothingPlaying : NowPlayingAnnouncement

    /** Localised format strings, one per case. */
    data class Templates(
        /** `%1$s` title, `%2$s` artist, `%3$s` station. */
        val trackByArtistOnStation: String,
        /** `%1$s` title, `%2$s` station. */
        val trackOnStation: String,
        /** `%1$s` station. */
        val stationPlaying: String,
        val nothingPlaying: String,
    ) {
        companion object {
            fun from(context: Context) = Templates(
                trackByArtistOnStation = context.getString(R.string.announce_track_by_artist_on_station),
                trackOnStation = context.getString(R.string.announce_track_on_station),
                stationPlaying = context.getString(R.string.announce_station_playing),
                nothingPlaying = context.getString(R.string.announce_nothing_playing),
            )
        }
    }

    fun text(templates: Templates): String = when (this) {
        is Track -> if (artist != null) {
            templates.trackByArtistOnStation.format(title, artist, station)
        } else {
            templates.trackOnStation.format(title, station)
        }
        is StationOnly -> templates.stationPlaying.format(station)
        NothingPlaying -> templates.nothingPlaying
    }

    companion object {

        /**
         * Builds the announcement from session state.
         *
         * @param isPlaying playback is wanted and the player is buffering or ready — a
         *   stream that is still connecting counts, since that is what the listener hears next.
         * @param hasResolvedTrack [RadioPlaybackService.EXTRA_HAS_RESOLVED_TRACK]: without it
         *   the session title is the station name and the artist a generic description.
         * @param hasResolvedArtist [RadioPlaybackService.EXTRA_HAS_RESOLVED_ARTIST]; the
         *   artist slot otherwise holds the same generic description.
         * @param station [RadioPlaybackService.EXTRA_STATION_NAME], else [fallbackStation].
         */
        fun from(
            isPlaying: Boolean,
            title: String?,
            artist: String?,
            station: String?,
            hasResolvedTrack: Boolean,
            hasResolvedArtist: Boolean,
            fallbackStation: String,
        ): NowPlayingAnnouncement {
            if (!isPlaying) return NothingPlaying
            val stationName = station.clean() ?: fallbackStation
            val track = title.clean()?.takeIf { hasResolvedTrack }
                ?: return StationOnly(stationName)
            return Track(track, artist.clean()?.takeIf { hasResolvedArtist }, stationName)
        }

        /** Reads [player] (a connected `MediaController`) the same way. */
        fun from(player: Player, fallbackStation: String): NowPlayingAnnouncement {
            val metadata = player.mediaMetadata
            val extras: Bundle? = metadata.extras
            val active = player.playWhenReady &&
                (player.playbackState == Player.STATE_READY || player.playbackState == Player.STATE_BUFFERING)
            return from(
                isPlaying = active,
                title = metadata.title?.toString(),
                artist = metadata.artist?.toString(),
                station = extras?.getString(RadioPlaybackService.EXTRA_STATION_NAME),
                hasResolvedTrack = extras?.getBoolean(RadioPlaybackService.EXTRA_HAS_RESOLVED_TRACK) == true,
                hasResolvedArtist = extras?.getBoolean(RadioPlaybackService.EXTRA_HAS_RESOLVED_ARTIST) == true,
                fallbackStation = fallbackStation,
            )
        }

        private fun String?.clean(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
    }
}
