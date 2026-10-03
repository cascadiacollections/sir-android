package com.cascadiacollections.sir.core.artwork

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.Locale

/**
 * The pure half of the iTunes Search lookup: building the request and reading the answer.
 *
 * Mirrors ShoutKit's `AlbumArtLookup`: one song (`entity=song&limit=1`) searched by
 * "artist title" in the listener's storefront, with the 100 px thumbnail rewritten to the
 * 600 px rendition Apple serves from the same path.
 */
object ITunesSearch {

    private val BASE_URL = "https://itunes.apple.com/search".toHttpUrl()

    /** The storefront used when the device's region is not a two-letter country code. */
    const val DEFAULT_COUNTRY = "US"

    /** Edge length of the artwork rendition requested, in pixels. */
    const val ARTWORK_SIZE = 600

    private const val THUMBNAIL_TOKEN = "100x100bb"
    private const val ARTWORK_TOKEN = "${ARTWORK_SIZE}x${ARTWORK_SIZE}bb"

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * The search URL for [artist] and [title] in [country]'s storefront. The query string is
     * encoded by [HttpUrl.Builder], so neither half needs escaping by the caller.
     */
    fun searchUrl(artist: String, title: String, country: String?): HttpUrl =
        BASE_URL.newBuilder()
            .addQueryParameter("term", "${artist.trim()} ${title.trim()}")
            .addQueryParameter("media", "music")
            .addQueryParameter("entity", "song")
            .addQueryParameter("limit", "1")
            .addQueryParameter("country", storefront(country))
            .build()

    /**
     * A two-letter storefront code. `Locale.getCountry()` can be empty (a language-only
     * locale) or a three-digit UN M.49 area like `419`, neither of which iTunes accepts.
     */
    fun storefront(country: String?): String {
        val code = country?.trim().orEmpty()
        return if (code.length == 2 && code.all { it.isLetter() }) code.uppercase(Locale.ROOT) else DEFAULT_COUNTRY
    }

    /**
     * The first result's artwork, or null when the search found nothing usable.
     *
     * @throws SerializationException when [body] is not a search response at all — a
     *   malfunction, which the caller must not cache as "this song has no artwork".
     */
    fun parse(body: String): AlbumArt? {
        val response = json.decodeFromString(SearchResponse.serializer(), body)
        val first = response.results.firstOrNull() ?: return null
        val thumbnail = first.artworkUrl100?.takeIf { it.isNotBlank() } ?: return null
        return AlbumArt(
            artworkUrl = upscale(thumbnail),
            trackViewUrl = first.trackViewUrl?.takeIf { it.isNotBlank() },
        )
    }

    /** Rewrites a `…/100x100bb.jpg` thumbnail URL to its [ARTWORK_SIZE] rendition. */
    fun upscale(artworkUrl100: String): String = artworkUrl100.replace(THUMBNAIL_TOKEN, ARTWORK_TOKEN)

    @Serializable
    private data class SearchResponse(val results: List<SearchResult> = emptyList())

    @Serializable
    private data class SearchResult(
        val artworkUrl100: String? = null,
        val trackViewUrl: String? = null,
    )
}
