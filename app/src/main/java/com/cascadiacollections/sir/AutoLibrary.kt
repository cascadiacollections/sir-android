package com.cascadiacollections.sir

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaConstants
import com.cascadiacollections.sir.AutoBrowseTree.Category
import com.cascadiacollections.sir.core.directory.RadioDirectory
import com.cascadiacollections.sir.core.directory.StationIds
import com.cascadiacollections.sir.core.directory.search
import com.cascadiacollections.sir.core.model.Station
import com.cascadiacollections.sir.ui.browseSubtitle
import kotlin.coroutines.cancellation.CancellationException

/**
 * The Android Auto library: [AutoBrowseTree]'s rules over the user's stations and the
 * directory, rendered as Media3 [MediaItem]s. `RadioPlaybackService`'s
 * `MediaLibrarySession.Callback` delegates every browse, lookup and search to this.
 *
 * Directory calls never throw out of here: Top Stations is answered from the discovery
 * snapshot/cache chain (so it works offline once a snapshot exists) and a failure is an
 * empty list, because a browse error in the car is worse than an empty tab.
 *
 * Opted in for the content-style [MediaConstants], which Media3 still marks unstable.
 */
@OptIn(UnstableApi::class)
internal class AutoLibrary(
    context: Context,
    private val savedStations: suspend () -> List<Station>,
    private val recentStations: suspend () -> List<Station>,
    private val hiddenRecentIds: suspend () -> Set<String>,
    private val directory: () -> RadioDirectory,
) {
    private val resources = context.applicationContext.resources

    /** The root item; its title is what Auto shows above the tabs. */
    fun rootItem(): MediaItem = browsableItem(
        AutoBrowseTree.ROOT_ID,
        resources.getString(R.string.station_name),
        styleExtras = null,
    )

    /**
     * Extras returned with the root: stations default to a grid (they all carry artwork),
     * categories to a list, and search is supported.
     */
    fun rootExtras(): Bundle = Bundle().apply {
        putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE, MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM)
        putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE, MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM)
        putBoolean(EXTRAS_KEY_SEARCH_SUPPORTED, true)
    }

    /** The children of [parentId], or null when it is not a node of this tree. */
    suspend fun children(parentId: String, rootChildrenLimit: Int?): List<MediaItem>? {
        if (parentId == AutoBrowseTree.ROOT_ID) {
            return AutoBrowseTree.rootCategories(rootChildrenLimit).map(::categoryItem)
        }
        return when (Category.fromId(parentId) ?: return null) {
            Category.YOUR_STATIONS -> listOf(sirStreamItem()) + yourStations().map(::stationItem)
            Category.RECENTLY_PLAYED ->
                AutoBrowseTree.recentlyPlayed(recentStations(), hiddenRecentIds()).map(::stationItem)
            Category.TOP_STATIONS -> topStations().map(::stationItem)
        }
    }

    /** The item for [mediaId] — the root, a category, the SIR stream or any station. */
    suspend fun item(mediaId: String): MediaItem? = when {
        mediaId == AutoBrowseTree.ROOT_ID -> rootItem()
        mediaId == AutoBrowseTree.SIR_STREAM_ID -> sirStreamItem()
        else -> Category.fromId(mediaId)?.let(::categoryItem) ?: resolveStation(mediaId)?.let(::stationItem)
    }

    /**
     * The station [mediaId] names, wherever the car found it: saved, recent, Top Stations
     * or a search result. A station in none of those (a top station that has since dropped
     * out, a search hit) is looked up in the directory; only radio-browser ids are sent.
     */
    suspend fun resolveStation(mediaId: String): Station? {
        if (mediaId.isBlank() || mediaId == AutoBrowseTree.SIR_STREAM_ID || Category.fromId(mediaId) != null) {
            return null
        }
        AutoBrowseTree.findStation(mediaId, savedStations(), recentStations())?.let { return it }
        AutoBrowseTree.findStation(mediaId, topStations())?.let { return it }
        if (!StationIds.isRadioBrowserUuid(mediaId)) return null
        return directoryCall { this.getStation(mediaId) }?.takeIf { it.isPlayable }
    }

    /** Saved stations matching [query], then the directory's name search. */
    suspend fun search(query: String): List<MediaItem> {
        if (query.isBlank()) return emptyList()
        val remote = directoryCall { this.search(query.trim(), limit = AutoBrowseTree.SEARCH_LIMIT) }.orEmpty()
        return AutoBrowseTree.searchResults(query, savedStations(), remote).map(::stationItem)
    }

    private suspend fun yourStations(): List<Station> =
        AutoBrowseTree.yourStations(savedStations(), recentStations(), hiddenRecentIds())

    private suspend fun topStations(): List<Station> = AutoBrowseTree.topStations(
        directoryCall { this.topStations(AutoBrowseTree.TOP_STATIONS_LIMIT) }.orEmpty()
    )

    private suspend fun <T> directoryCall(block: suspend RadioDirectory.() -> Result<T>): T? = try {
        directory().block().getOrNull()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    private fun categoryItem(category: Category): MediaItem = when (category) {
        Category.YOUR_STATIONS -> browsableItem(category.id, resources.getString(R.string.auto_your_stations), null)
        // A history reads better as a list than as a wall of the same artwork.
        Category.RECENTLY_PLAYED -> browsableItem(
            category.id,
            resources.getString(R.string.recent_stations),
            MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM,
        )
        Category.TOP_STATIONS -> browsableItem(category.id, resources.getString(R.string.auto_top_stations), null)
    }

    private fun browsableItem(id: String, title: String, styleExtras: Int?): MediaItem = MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_RADIO_STATIONS)
                .apply {
                    if (styleExtras != null) {
                        setExtras(
                            Bundle().apply { putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE, styleExtras) }
                        )
                    }
                }
                .build()
        )
        .build()

    /**
     * The bundled SIR stream. Its id is fixed (not the current stream URL) so picking it
     * always means "the default stream", whatever is playing.
     */
    fun sirStreamItem(): MediaItem = MediaItem.Builder()
        .setMediaId(AutoBrowseTree.SIR_STREAM_ID)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(resources.getString(R.string.station_name))
                .setSubtitle(resources.getString(R.string.stream_description))
                .setArtist(resources.getString(R.string.stream_description))
                .setIsPlayable(true)
                .setIsBrowsable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_RADIO_STATION)
                .build()
        )
        .build()

    /**
     * A browse item only has to identify the station: picking it goes through
     * `onAddMediaItems`, which resolves the persisted selection (and any playlist) itself,
     * so no URI is attached.
     */
    fun stationItem(station: Station): MediaItem {
        val subtitle = station.browseSubtitle { resources.getString(R.string.bitrate_kbps, it) }
            .ifEmpty { null }
        return MediaItem.Builder()
            .setMediaId(station.id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(station.name)
                    .setSubtitle(subtitle)
                    .setArtist(subtitle)
                    .setIsPlayable(true)
                    .setIsBrowsable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_RADIO_STATION)
                    // Auto/Automotive load this URI themselves (their own image pipeline), so
                    // it's safe to pass through even though the phone UI has no image loader.
                    .setArtworkUri(station.favicon?.takeIf { it.isNotBlank() }?.let(Uri::parse))
                    .build()
            )
            .build()
    }

    companion object {
        /**
         * `MediaBrowserServiceCompat`'s "search supported" root extra. Media3 has no
         * constant for it; Auto reads it to decide whether to show its search button.
         */
        const val EXTRAS_KEY_SEARCH_SUPPORTED: String = "android.media.browse.SEARCH_SUPPORTED"
    }
}
