package com.cascadiacollections.sir.core.playback

/**
 * The stream the player should be pointed at, plus the label to show for it.
 */
data class StreamSource(
    val url: String,
    val title: String? = null,
    val stationId: String? = null
)

/**
 * Decides which of the competing stream URLs wins.
 *
 * Precedence used to be implicit in the order of assignments inside the playback
 * service's `onCreate`, which made it impossible to test and easy to break. Stated
 * explicitly, highest priority first:
 *
 * 1. a debug override typed into settings — it exists precisely to trump everything;
 * 2. the station the user picked from the directory;
 * 3. the configured stream quality, i.e. the app's own station.
 */
object StreamSourceResolver {

    fun resolve(
        debugOverrideUrl: String?,
        selectedStation: StreamSource?,
        qualityUrl: String,
        defaultTitle: String? = null
    ): StreamSource {
        debugOverrideUrl?.takeIf { it.isNotBlank() }?.let {
            return StreamSource(url = it, title = defaultTitle)
        }
        selectedStation?.takeIf { it.url.isNotBlank() }?.let { return it }
        return StreamSource(url = qualityUrl, title = defaultTitle)
    }
}
