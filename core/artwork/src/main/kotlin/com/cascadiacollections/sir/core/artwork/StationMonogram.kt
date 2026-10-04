package com.cascadiacollections.sir.core.artwork

import java.util.Locale

/**
 * The generated placeholder shown when a station has no artwork, or its favicon fails to
 * load: the station's initials on a colour derived from its identity (as ShoutKit does).
 *
 * The colour is a pure function of the station, so a station keeps the same tile across
 * launches, lists and devices — which is what makes it recognisable.
 */
data class StationMonogram(
    /** One or two uppercase characters, never empty. */
    val initials: String,
    /** Hue in degrees, `[0, 360)`. Saturation and lightness are a theming decision for the UI. */
    val hue: Float
) {
    companion object {
        /** Shown when a name has no letters or digits at all. */
        const val FALLBACK_INITIAL = "#"

        /**
         * The monogram for a station. [id] is preferred for the colour because a name can be
         * edited; a station without one (the app's own stream, an imported entry) falls back
         * to its name.
         */
        fun of(id: String?, name: String?): StationMonogram {
            val key = id?.takeIf { it.isNotBlank() } ?: name.orEmpty()
            return StationMonogram(initials = initials(name.orEmpty()), hue = hue(key))
        }

        /**
         * The first letter or digit of each of the first two words. Punctuation is a word
         * boundary, so "Radio-Canada" and "radio canada" both give "RC".
         */
        fun initials(name: String): String {
            val words = name.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
            if (words.isEmpty()) return FALLBACK_INITIAL
            return words.take(2)
                .joinToString(separator = "") { word ->
                    String(Character.toChars(word.codePointAt(0)))
                }
                .uppercase(Locale.ROOT)
        }

        /**
         * A stable hue for [key]: FNV-1a over its UTF-8 bytes. Not `String.hashCode`, whose
         * value is a JVM detail — FNV is trivially reproducible on the iOS side too.
         */
        fun hue(key: String): Float {
            var hash = FNV_OFFSET_BASIS
            for (byte in key.toByteArray(Charsets.UTF_8)) {
                hash = hash xor (byte.toInt() and 0xFF)
                hash *= FNV_PRIME
            }
            return (hash.toUInt() % 360u).toFloat()
        }

        private const val FNV_OFFSET_BASIS = -0x7ee3623b // 0x811C9DC5 as a signed Int
        private const val FNV_PRIME = 0x01000193
    }
}
