package com.cascadiacollections.sir.core.playback

/**
 * Audio equalizer presets.
 *
 * Each preset owns its own gain [curve] — a pure function from normalized band
 * position (0.0 = lowest frequency band, 1.0 = highest) to a normalized gain in the
 * device's supported millibel range. Keeping the curve on the preset removes the
 * `when` block that previously lived inside the playback service and makes every
 * preset independently testable without an Android `AudioEffect`.
 *
 * [NORMAL] has no curve: it means "flat", i.e. 0 mB on every band, which is not the
 * same as the midpoint of an asymmetric band level range.
 */
enum class EqualizerPreset(
    val label: String,
    val curve: ((Float) -> Float)?
) {
    NORMAL("Normal", null),
    BASS_BOOST("Bass Boost", { position -> (1f - position) * TILT }),
    VOCAL("Vocal/Podcast", { position ->
        when {
            position < 0.3f -> 0.1f // cut bass
            position < 0.7f -> 0.7f // boost mids
            else -> 0.4f // slight boost highs
        }
    }),
    TREBLE("Treble Boost", { position -> position * TILT });

    companion object {
        /** Slope applied by the tilt-shaped presets. */
        private const val TILT = 0.6f

        fun fromOrdinal(ordinal: Int): EqualizerPreset = entries.getOrNull(ordinal) ?: NORMAL
    }
}
