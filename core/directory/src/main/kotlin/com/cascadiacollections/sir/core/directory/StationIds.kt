package com.cascadiacollections.sir.core.directory

/**
 * Identifies stations that came from radio-browser.
 *
 * Only those carry a `stationuuid`; bundled (`sir-default`, `curated-*`) and imported
 * (`imported:<url>`) stations do not, and sending their ids to `/json/url/` would only
 * produce garbage requests against a volunteer-run service.
 */
object StationIds {

    private val UUID =
        Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

    /** True when [id] is a canonical 8-4-4-4-12 hex UUID, i.e. a radio-browser station. */
    fun isRadioBrowserUuid(id: String): Boolean = UUID.matches(id)
}
