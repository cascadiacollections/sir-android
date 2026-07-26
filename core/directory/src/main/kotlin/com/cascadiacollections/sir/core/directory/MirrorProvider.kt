package com.cascadiacollections.sir.core.directory

/**
 * Supplies radio-browser.info API mirrors.
 *
 * radio-browser has no single stable host: clients are expected to spread load across
 * mirrors and fail over when one is unreachable. Extracting this lets the HTTP client
 * stay dumb and keeps mirror policy unit-testable.
 */
fun interface MirrorProvider {
    /** Base URLs to try, in preference order, for a single logical request. */
    fun mirrors(): List<String>
}

/**
 * Rotates over a fixed mirror list so load is spread across app launches while each
 * individual request still has deterministic failover order.
 */
class RotatingMirrorProvider(
    private val mirrors: List<String> = DEFAULT_MIRRORS,
    private val shuffle: (List<String>) -> List<String> = { it.shuffled() }
) : MirrorProvider {

    init {
        require(mirrors.isNotEmpty()) { "At least one mirror is required" }
    }

    override fun mirrors(): List<String> = shuffle(mirrors)

    companion object {
        /**
         * Known radio-browser.info mirrors. `all.api.radio-browser.info` resolves to
         * these hosts; hard-coding them avoids a DNS-SRV round trip on cold start.
         */
        val DEFAULT_MIRRORS: List<String> = listOf(
            "https://de1.api.radio-browser.info",
            "https://de2.api.radio-browser.info",
            "https://at1.api.radio-browser.info",
            "https://nl1.api.radio-browser.info"
        )
    }
}
