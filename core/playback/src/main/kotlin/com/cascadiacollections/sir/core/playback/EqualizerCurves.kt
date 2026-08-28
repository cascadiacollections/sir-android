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

    /** Number of sliders in the custom-preset UI, independent of the device's real band count. */
    const val CUSTOM_BAND_COUNT = 5

    /**
     * Maps [gains] (one per UI slider, each -1f..1f where 0 is flat) onto [bandCount]
     * hardware bands, linearly interpolating between sliders.
     *
     * Unlike [levelsFor]'s preset curves — which treat their output as a fraction of the
     * full min-to-max range — a gain's sign here picks which half of the device's range
     * it scales against, so a full boost always reaches [maxLevel] and a full cut always
     * reaches [minLevel] even when the device's range is asymmetric around zero.
     */
    fun levelsForCustomBands(
        gains: List<Float>,
        bandCount: Int,
        minLevel: Short,
        maxLevel: Short
    ): List<Short> {
        if (gains.isEmpty() || bandCount <= 0) return List(bandCount.coerceAtLeast(0)) { 0.toShort() }
        return List(bandCount) { band ->
            val position = band.toFloat() / (bandCount - 1).coerceAtLeast(1)
            val gain = interpolate(gains, position).coerceIn(-1f, 1f)
            val level = if (gain >= 0f) gain * maxLevel else -gain * minLevel
            level.toInt().coerceIn(minLevel.toInt(), maxLevel.toInt()).toShort()
        }
    }

    /**
     * Samples [preset]'s curve at [bandCount] positions for slider display, as a
     * -1f..1f gain (0 = flat) — the same scale [levelsForCustomBands] takes — rather
     * than the 0f..1f fraction-of-range [levelsFor] applies to the device directly.
     *
     * [levelsFor]'s curves are a fraction of the *full* `[minLevel, maxLevel]` window,
     * so "flat" (0 mB) sits at whatever fraction `-minLevel / (maxLevel - minLevel)`
     * works out to — only exactly 0.5 when the range is symmetric around zero. This
     * locates that fraction from [minLevel]/[maxLevel] and maps around *it*, rather
     * than assuming it's the midpoint, so the displayed slider always lines up with
     * what [levelsFor] would actually apply for the same preset and range.
     *
     * The caller doesn't currently have the connected device's real range to pass in,
     * so this defaults to the symmetric ±1500 mB window essentially every real
     * `Equalizer` reports in practice — correct for the common case, and exact for any
     * range once one is threaded through.
     */
    fun displayGainsFor(
        preset: EqualizerPreset,
        bandCount: Int = CUSTOM_BAND_COUNT,
        minLevel: Short = -1500,
        maxLevel: Short = 1500
    ): List<Float> {
        val curve = preset.curve ?: return List(bandCount) { 0f }
        val range = (maxLevel - minLevel).coerceAtLeast(1)
        // Where curve()'s 0f..1f output lands on 0 mB — see kdoc above.
        val flatFraction = (-minLevel.toFloat() / range).coerceIn(0f, 1f)
        return List(bandCount) { band ->
            val position = band.toFloat() / (bandCount - 1).coerceAtLeast(1)
            val fraction = curve(position).coerceIn(0f, 1f)
            val gain = when {
                fraction >= flatFraction && flatFraction < 1f -> (fraction - flatFraction) / (1f - flatFraction)
                fraction < flatFraction && flatFraction > 0f -> (fraction - flatFraction) / flatFraction
                else -> 0f
            }
            gain.coerceIn(-1f, 1f)
        }
    }

    /**
     * Normalizes an arbitrary-length, possibly out-of-range gain list — e.g. one
     * persisted by an older build, or edited by hand — to exactly [bandCount] sliders,
     * each clamped to -1f..1f. Interpolates rather than truncating/padding so a
     * differently-sized list still maps onto the fixed slider UI sensibly instead of
     * silently dropping or ignoring entries.
     */
    fun normalizeCustomBands(gains: List<Float>, bandCount: Int = CUSTOM_BAND_COUNT): List<Float> {
        if (gains.isEmpty() || bandCount <= 0) return List(bandCount.coerceAtLeast(0)) { 0f }
        return List(bandCount) { band ->
            val position = band.toFloat() / (bandCount - 1).coerceAtLeast(1)
            interpolate(gains, position).coerceIn(-1f, 1f)
        }
    }

    /** Piecewise-linear interpolation of [values] at normalized [position] (0f..1f). */
    private fun interpolate(values: List<Float>, position: Float): Float {
        if (values.size == 1) return values[0]
        val scaled = position * (values.size - 1)
        val lowIndex = scaled.toInt().coerceIn(0, values.size - 2)
        val frac = scaled - lowIndex
        return values[lowIndex] + (values[lowIndex + 1] - values[lowIndex]) * frac
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
    // Clamp as Int and narrow afterwards. Narrowing first truncated to 16 bits before
    // the clamp could act, so a curve overshooting the device's range wrapped around and
    // was then clamped to the *opposite* rail — a bass boost pinned to minimum gain.
    (minLevel + range * curve(position)).toInt()
        .coerceIn(minLevel.toInt(), maxLevel.toInt())
        .toShort()
}
