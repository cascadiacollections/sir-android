package com.cascadiacollections.sir.core.playback

/**
 * Pure band-level math for the equalizer.
 *
 * Isolated from `android.media.audiofx.Equalizer` so the curve behaviour can be
 * verified with plain JVM unit tests; the playback service only has to read the
 * device's band count and level range and push the resulting values.
 */
object EqualizerCurves {

    /**
     * Maps [preset] onto [bandCount] bands within the device-reported millibel range.
     *
     * @param minLevel lowest supported band level, in millibels.
     * @param maxLevel highest supported band level, in millibels.
     */
    fun levelsFor(
        preset: EqualizerPreset,
        bandCount: Int,
        minLevel: Short,
        maxLevel: Short
    ): List<Short> {
        val curve = preset.curve ?: return List(bandCount.coerceAtLeast(0)) { 0.toShort() }
        return calculateEqualizerLevels(
            bandCount = bandCount,
            minLevel = minLevel,
            maxLevel = maxLevel,
            range = maxLevel - minLevel,
            curve = curve
        )
    }
}

/**
 * Distributes [curve] across [bandCount] bands.
 *
 * Band `i` sits at normalized position `i / (bandCount - 1)`; the curve's output is
 * treated as a fraction of [range] above [minLevel] and clamped into the supported
 * `[minLevel, maxLevel]` window.
 */
fun calculateEqualizerLevels(
    bandCount: Int,
    minLevel: Short,
    maxLevel: Short,
    range: Int,
    curve: (Float) -> Float
): List<Short> = List(bandCount) { band ->
    val position = band.toFloat() / (bandCount - 1).coerceAtLeast(1)
    (minLevel + range * curve(position)).toInt().toShort().coerceIn(minLevel, maxLevel)
}
