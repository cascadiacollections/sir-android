package com.cascadiacollections.sir.core.directory

import java.io.IOException

/**
 * A non-2xx answer from a radio-browser mirror.
 *
 * An [IOException] so existing callers that treat directory failures as I/O keep
 * working, but typed so failover can tell a sick mirror (5xx, 408, 429 — try the next
 * one) from a bad request (other 4xx — every mirror would answer the same).
 */
class HttpStatusException(val code: Int) : IOException("radio-browser responded HTTP $code") {

    /** Whether another mirror could plausibly answer differently. */
    val isRetryable: Boolean
        get() = code >= 500 || code == 408 || code == 429
}
