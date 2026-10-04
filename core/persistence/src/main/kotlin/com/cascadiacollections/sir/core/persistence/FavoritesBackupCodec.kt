package com.cascadiacollections.sir.core.persistence

import com.cascadiacollections.sir.core.model.Station
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The favourites backup document shared with ShoutKit (iOS), so a user can move their
 * saved stations between the two apps:
 *
 * ```
 * {"schemaVersion":1,"favorites":[{"id","name","streamURL","genre","artworkURL","sortIndex"}]}
 * ```
 *
 * Both apps key stations by radio-browser's `stationuuid`, so the mapping is direct:
 * `id`↔[Station.id], `streamURL`↔[Station.streamUrl] (written resolved, since ShoutKit
 * stores radio-browser's `url_resolved` and does not unwrap `.pls`/`.m3u` playlists;
 * read back into [Station.url]), `artworkURL`↔[Station.favicon] and
 * `genre`↔[Station.tags]. ShoutKit models a single genre, so an export writes the first
 * tag and an import stores the genre as the only tag.
 *
 * Properties are declared in sorted order and nulls are omitted, matching ShoutKit's
 * `JSONEncoder` (`.sortedKeys`, absent optionals), so the two apps' exports read alike.
 */
object FavoritesBackupCodec {

    const val SCHEMA_VERSION: Int = 1

    /** Default name for an exported backup — the same name ShoutKit suggests. */
    const val DEFAULT_FILE_NAME: String = "shoutkit-favorites.json"

    const val MIME_TYPE: String = "application/json"

    @Serializable
    internal data class Document(val favorites: List<Favorite> = emptyList(), val schemaVersion: Int)

    @Serializable
    internal data class Favorite(
        val artworkURL: String? = null,
        val genre: String = "",
        val id: String,
        val name: String = "",
        val sortIndex: Int = 0,
        val streamURL: String? = null
    )

    /** Why a document could not be imported. */
    class UnsupportedSchemaException(val schemaVersion: Int) :
        IllegalArgumentException("Unsupported favorites backup format version: $schemaVersion")

    private val json = Json {
        ignoreUnknownKeys = true
        // ShoutKit decodes `genre`, `sortIndex` and `schemaVersion` as required keys, so
        // defaults must be written; only absent optionals (null) are omitted.
        encodeDefaults = true
        explicitNulls = false
        prettyPrint = true
    }

    /** Renders [stations], in saved order, as a backup document. */
    fun encode(stations: List<Station>): String = json.encodeToString(
        Document(
            favorites = stations.mapIndexed { index, station ->
                Favorite(
                    artworkURL = station.favicon?.takeIf { it.isNotBlank() },
                    genre = station.tagList.firstOrNull().orEmpty(),
                    id = station.id,
                    name = station.name,
                    sortIndex = index,
                    streamURL = station.streamUrl.takeIf { it.isNotBlank() }
                )
            },
            schemaVersion = SCHEMA_VERSION
        )
    )

    /**
     * Parses a backup document into stations in the order they should be appended.
     *
     * As in ShoutKit's importer, entries are ordered by `sortIndex` (stable, so equal
     * indices keep file order), entries without an id are dropped, and only the first of
     * any duplicated id is kept. Entries with no stream URL are kept with a blank
     * [Station.url], as ShoutKit keeps them: the importer re-resolves those by id from
     * the directory, and drops any it cannot.
     *
     * @throws UnsupportedSchemaException for a schema version this build doesn't know.
     * @throws kotlinx.serialization.SerializationException when [text] isn't a backup document.
     */
    fun decode(text: String): List<Station> {
        val document = json.decodeFromString<Document>(text)
        if (document.schemaVersion != SCHEMA_VERSION) {
            throw UnsupportedSchemaException(document.schemaVersion)
        }
        val seen = mutableSetOf<String>()
        return document.favorites
            .sortedBy { it.sortIndex }
            .filter { it.id.isNotBlank() && seen.add(it.id) }
            .map { favorite ->
                Station(
                    id = favorite.id,
                    name = favorite.name.ifBlank {
                        favorite.streamURL?.takeIf { it.isNotBlank() } ?: favorite.id
                    },
                    url = favorite.streamURL.orEmpty(),
                    favicon = favorite.artworkURL?.takeIf { it.isNotBlank() },
                    tags = favorite.genre.trim()
                )
            }
    }

    /**
     * Whether a picked file is a JSON backup rather than an M3U/PLS playlist. The content
     * decides when it can — pickers often report a generic name and MIME type — and the
     * extension is the fallback.
     */
    fun looksLikeBackup(text: String, fileName: String?): Boolean {
        val firstChar = text.trimStart('\uFEFF', ' ', '\t', '\r', '\n').firstOrNull()
        if (firstChar == '{') return true
        if (firstChar == '#' || firstChar == '[') return false
        return fileName?.endsWith(".json", ignoreCase = true) == true
    }
}
