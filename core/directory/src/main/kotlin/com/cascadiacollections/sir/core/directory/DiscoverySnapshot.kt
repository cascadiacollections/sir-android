package com.cascadiacollections.sir.core.directory

import com.cascadiacollections.sir.core.model.Station
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * The last successful discovery answers — the browse tab's popular stations and the genre
 * list — as ShoutKit's `DirectoryDiscoverySnapshot` keeps them, so a cold start can paint
 * the browse tab before (or without) the network.
 *
 * Each section records the `limit` it was fetched with and when, so a different request
 * misses instead of being answered with the wrong number of results. [schemaVersion]
 * invalidates the whole file when this shape changes.
 *
 * Only discovery is persisted. Search and genre-browse results are never written here.
 */
@Serializable
data class DiscoverySnapshot(
    val schemaVersion: Int = SCHEMA_VERSION,
    val topStations: StationsSection? = null,
    val topTags: TagsSection? = null
) {
    @Serializable
    data class StationsSection(val limit: Int, val savedAtMillis: Long, val stations: List<Station>)

    @Serializable
    data class TagsSection(val limit: Int, val savedAtMillis: Long, val tags: List<Tag>)

    companion object {
        /** Bump when the shape changes; an older file is then ignored rather than misread. */
        const val SCHEMA_VERSION: Int = 1
    }
}

/** Where [SnapshotRadioDirectory] keeps its [DiscoverySnapshot]; injectable for tests. */
interface DiscoverySnapshotStore {
    /** The stored snapshot, or null when there is none or it cannot be used. Never throws for bad data. */
    suspend fun read(): DiscoverySnapshot?

    /** Replaces the stored snapshot. May throw [IOException]. */
    suspend fun write(snapshot: DiscoverySnapshot)
}

/**
 * A [DiscoverySnapshotStore] backed by one JSON file.
 *
 * Writes go to a sibling temp file which is flushed to disk and then renamed over [file],
 * so a crash or a full disk mid-write leaves the previous snapshot intact rather than a
 * truncated one. A file that does not decode — corrupt, or written by another schema
 * version — reads as absent.
 */
class FileDiscoverySnapshotStore(
    private val file: File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : DiscoverySnapshotStore {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override suspend fun read(): DiscoverySnapshot? = withContext(ioDispatcher) {
        try {
            if (!file.isFile) return@withContext null
            json.decodeFromString<DiscoverySnapshot>(file.readText())
                .takeIf { it.schemaVersion == DiscoverySnapshot.SCHEMA_VERSION }
        } catch (_: IOException) {
            null
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    override suspend fun write(snapshot: DiscoverySnapshot) = withContext(ioDispatcher) {
        val parent = file.absoluteFile.parentFile
        if (parent != null && !parent.isDirectory && !parent.mkdirs()) {
            throw IOException("Cannot create $parent")
        }
        val temp = File(parent, "${file.name}.tmp")
        try {
            FileOutputStream(temp).use { out ->
                out.write(json.encodeToString(DiscoverySnapshot.serializer(), snapshot).toByteArray())
                out.flush()
                out.fd.sync()
            }
            // rename(2) replaces the target atomically on the same filesystem.
            if (!temp.renameTo(file)) throw IOException("Cannot replace $file")
        } finally {
            if (temp.exists()) temp.delete()
        }
    }

    companion object {
        const val FILE_NAME: String = "discovery-snapshot.json"
    }
}
