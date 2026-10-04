package com.cascadiacollections.sir

import android.content.Intent
import android.net.Uri
import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.cascadiacollections.sir.StationDeepLink.PlayRequest
import com.cascadiacollections.sir.core.directory.RadioDirectory
import com.cascadiacollections.sir.core.model.Station
import com.cascadiacollections.sir.core.model.StationQuery
import com.cascadiacollections.sir.core.persistence.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StationDeepLinkTest {

    private fun view(uri: String) = Intent(Intent.ACTION_VIEW, Uri.parse(uri))

    @Test
    fun `reads the station id from a station link`() {
        assertThat(StationDeepLink.stationId(view("sir://station/abc-123"))).isEqualTo("abc-123")
    }

    @Test
    fun `ignores other links, actions and missing ids`() {
        assertThat(StationDeepLink.stationId(null)).isNull()
        assertThat(StationDeepLink.stationId(view("https://station/abc"))).isNull()
        assertThat(StationDeepLink.stationId(view("sir://other/abc"))).isNull()
        assertThat(StationDeepLink.stationId(view("sir://station/"))).isNull()
        assertThat(StationDeepLink.stationId(Intent(Intent.ACTION_MAIN, Uri.parse("sir://station/abc")))).isNull()
    }

    @Test
    fun `a phone is not a television`() {
        assertThat(TvActivity.isTelevision(RuntimeEnvironment.getApplication())).isFalse()
    }

    @Test
    @Config(qualifiers = "television")
    fun `a TV is detected, so the phone UI hands over`() {
        assertThat(TvActivity.isTelevision(RuntimeEnvironment.getApplication())).isTrue()
    }

    @Test
    fun `forwarding to the TV keeps the link`() {
        val original = view("sir://station/abc")
        val forwarded = TvActivity.forward(RuntimeEnvironment.getApplication(), original)

        assertThat(forwarded.component?.className).isEqualTo(TvActivity::class.java.name)
        assertThat(StationDeepLink.stationId(forwarded)).isEqualTo("abc")
    }

    // --- sir://play automation links ---

    private val newscast = "a5314180-7573-4b46-aafc-51ed2d5b9e71"

    @Test
    fun `a play link with a station uuid asks for that station`() {
        assertThat(StationDeepLink.playRequest(view("sir://play/$newscast")))
            .isEqualTo(PlayRequest.Station(newscast))
    }

    @Test
    fun `a play link without an id, or the bare action, resumes`() {
        assertThat(StationDeepLink.playRequest(view("sir://play"))).isEqualTo(PlayRequest.Resume)
        assertThat(StationDeepLink.playRequest(view("sir://play/"))).isEqualTo(PlayRequest.Resume)
        assertThat(StationDeepLink.playRequest(Intent(PlayStationActivity.ACTION_PLAY_STATION)))
            .isEqualTo(PlayRequest.Resume)
    }

    @Test
    fun `the explicit action carries the same link`() {
        val intent = Intent(PlayStationActivity.ACTION_PLAY_STATION, Uri.parse("sir://play/$newscast"))
        assertThat(StationDeepLink.playRequest(intent)).isEqualTo(PlayRequest.Station(newscast))
    }

    @Test
    fun `ids SIR never issues are invalid`() {
        listOf(
            "sir://play/not-a-station",
            "sir://play/a5314180-7573-4b46-aafc-51ed2d5b9e7",
            "sir://play/$newscast/extra",
            "sir://play/..%2F..%2Fdata",
            "sir://play/imported:file:%2F%2F%2Fsdcard%2Fx.mp3",
            "sir://play/curated-UPPER",
            "sir://play/" + "a".repeat(5000)
        ).forEach { link ->
            assertThat(StationDeepLink.playRequest(view(link)), link).isEqualTo(PlayRequest.Invalid)
        }
    }

    @Test
    fun `other links and actions are not play requests`() {
        assertThat(StationDeepLink.playRequest(null)).isNull()
        assertThat(StationDeepLink.playRequest(view("sir://station/$newscast"))).isNull()
        assertThat(StationDeepLink.playRequest(view("https://play/$newscast"))).isNull()
        assertThat(StationDeepLink.playRequest(Intent(Intent.ACTION_VIEW))).isNull()
        assertThat(StationDeepLink.playRequest(Intent(Intent.ACTION_MAIN, Uri.parse("sir://play/$newscast"))))
            .isNull()
    }

    @Test
    fun `accepts every id format SIR issues`() {
        assertThat(StationDeepLink.isValidId(newscast)).isTrue()
        assertThat(StationDeepLink.isValidId("sir-default")).isTrue()
        assertThat(StationDeepLink.isValidId("curated-worldwide-fm")).isTrue()
        assertThat(StationDeepLink.isValidId("imported:https://example.com/live.mp3?x=1")).isTrue()
        assertThat(StationDeepLink.isValidId("")).isFalse()
        assertThat(StationDeepLink.isValidId("imported:")).isFalse()
        assertThat(StationDeepLink.isValidId("imported:javascript:alert(1)")).isFalse()
        assertThat(StationDeepLink.isValidId("imported:https://example.com/a b")).isFalse()
    }

    @Test
    fun `an imported id survives the round trip through its play link`() {
        val id = "imported:https://example.com/live.mp3?x=1"
        val link = StationDeepLink.playLink(id)

        assertThat(link.toString()).isEqualTo("sir://play/imported%3Ahttps%3A%2F%2Fexample.com%2Flive.mp3%3Fx%3D1")
        assertThat(StationDeepLink.playRequest(Intent(Intent.ACTION_VIEW, link))).isEqualTo(PlayRequest.Station(id))
        assertThat(StationDeepLink.playLink().toString()).isEqualTo("sir://play")
    }

    // --- resolution, shared by sir://station and sir://play ---

    private class RecordingDirectory(private val stations: List<Station>) : RadioDirectory {
        val lookups = mutableListOf<String>()
        override suspend fun search(query: StationQuery) = Result.success(emptyList<Station>())
        override suspend fun topStations(limit: Int) = Result.success(emptyList<Station>())
        override suspend fun stationsByTag(tag: String, limit: Int) = Result.success(emptyList<Station>())
        override suspend fun getStation(id: String): Result<Station?> {
            lookups += id
            return Result.success(stations.firstOrNull { it.id == id })
        }
    }

    private val repository get() = SettingsRepository(RuntimeEnvironment.getApplication())

    private suspend fun resetRepository() {
        val repo = repository
        repo.savedStations.first().forEach { repo.removeStation(it.id) }
        repo.clearRecentStations()
        repo.clearSelectedStation()
    }

    // Directory stations are only checked unplayable here: selecting a radio-browser id would
    // wake the play reporter of every SirApp an earlier test in this JVM left running.
    @Test
    fun `a radio-browser id is looked up in the directory, and an unplayable one is refused`() = runBlocking {
        resetRepository()
        val directory = RecordingDirectory(listOf(Station(id = newscast, name = "NPR Newscast")))

        assertThat(StationDeepLink.play(newscast, directory, repository)).isFalse()
        assertThat(directory.lookups).isEqualTo(listOf(newscast))
        assertThat(repository.selectedStation.first()).isNull()
    }

    @Test
    fun `non-directory ids resolve locally without a directory request`() = runBlocking {
        resetRepository()
        val imported = Station(id = "imported:https://example.com/a.mp3", name = "A", url = "https://example.com/a.mp3")
        repository.saveStation(imported)
        val directory = RecordingDirectory(emptyList())

        assertThat(StationDeepLink.play(imported.id, directory, repository)).isTrue()
        assertThat(StationDeepLink.play("curated-worldwide-fm", directory, repository)).isTrue()
        assertThat(repository.selectedStation.first()?.id).isEqualTo("curated-worldwide-fm")
        assertThat(directory.lookups).isEmpty()
    }

    @Test
    fun `a recently played station that was never saved still resolves`() = runBlocking {
        resetRepository()
        val station = Station(id = "imported:https://example.com/r.mp3", name = "R", url = "https://example.com/r.mp3")
        repository.selectStation(station)
        repository.clearSelectedStation()

        assertThat(StationDeepLink.play(station.id, RecordingDirectory(emptyList()), repository)).isTrue()
        assertThat(repository.selectedStation.first()?.id).isEqualTo(station.id)
    }

    @Test
    fun `an unknown station selects nothing`() = runBlocking {
        resetRepository()

        assertThat(StationDeepLink.play(newscast, RecordingDirectory(emptyList()), repository)).isFalse()
        assertThat(repository.selectedStation.first()).isNull()
    }
}
