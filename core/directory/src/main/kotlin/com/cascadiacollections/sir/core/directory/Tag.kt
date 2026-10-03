package com.cascadiacollections.sir.core.directory

import java.util.Locale

/**
 * A radio-browser tag, used as a genre on the browse surface.
 *
 * [name] is the lowercase value radio-browser stores and expects back in `tagList`;
 * [displayName] is the capitalized form for UI.
 */
data class Tag(
    val name: String,
    val stationCount: Int = 0
) {
    /** Each word capitalized ("hip hop" -> "Hip Hop"), locale-independent. */
    val displayName: String
        get() = name.split(' ')
            .filter { it.isNotEmpty() }
            .joinToString(" ") { word ->
                word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
            }

    companion object {
        /** Bundled genres for when the tag list cannot be fetched, matching ShoutKit. */
        val CURATED: List<Tag> = listOf(
            "pop", "rock", "jazz", "classical", "news", "talk", "electronic", "dance",
            "hip hop", "country", "ambient", "chillout", "oldies", "80s", "90s", "indie",
            "metal", "reggae"
        ).map { Tag(it) }
    }
}
