package com.cascadiacollections.sir.core.model

/**
 * A normalized directory query.
 *
 * Kept in `:core:model` so the directory, UI and persistence layers agree on how a
 * user's raw text input maps onto a request without each re-implementing trimming
 * and limit clamping.
 */
data class StationQuery(
    val text: String,
    val limit: Int = DEFAULT_LIMIT
) {
    val normalizedText: String = text.trim()

    val isBlank: Boolean get() = normalizedText.isEmpty()

    val effectiveLimit: Int = limit.coerceIn(1, MAX_LIMIT)

    companion object {
        const val DEFAULT_LIMIT = 30
        const val MAX_LIMIT = 100
    }
}
