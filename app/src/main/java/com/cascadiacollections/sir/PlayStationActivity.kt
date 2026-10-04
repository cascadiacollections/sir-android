package com.cascadiacollections.sir

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.cascadiacollections.sir.StationDeepLink.PlayRequest

/**
 * Automation entry point: `sir://play/{id}`, `sir://play`, or
 * [ACTION_PLAY_STATION] (optionally with that URI as data) starts a station playing with
 * no UI — for a morning routine, Tasker/MacroDroid, a pinned home-screen shortcut, or
 * `adb shell am start`. `sir://station/{id}` still opens the app on the station.
 *
 * Headless (translucent, nothing drawn): the intent is untrusted, so the id is checked
 * here ([StationDeepLink.playRequest]) and a malformed one gets a Toast and nothing else.
 * A well-formed one is handed straight to [RadioPlaybackService], which resolves it the
 * same way the app's own links do ([StationDeepLink.play]) and starts playback.
 *
 * The service is started here, synchronously in `onCreate`, while this activity is still
 * the foreground app — resolving first could take a network round trip, by which time the
 * activity may be gone and a background start refused. It is a plain start, not
 * `startForegroundService`: an unknown station never begins playback, and a foreground
 * start that doesn't would ANR (see #238). Media3 promotes the service itself once audio
 * starts.
 */
class PlayStationActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Recreated (e.g. a configuration change) after the request already went out.
        if (savedInstanceState == null) handle(StationDeepLink.playRequest(intent))
        finish()
    }

    private fun handle(request: PlayRequest?) {
        val app = applicationContext
        when (request) {
            null, PlayRequest.Invalid -> {
                Log.w(TAG, "Ignoring an unusable play link")
                Toast.makeText(app, R.string.play_link_invalid, Toast.LENGTH_SHORT).show()
            }

            PlayRequest.Resume -> start(app, stationId = null)

            is PlayRequest.Station -> start(app, request.id)
        }
    }

    private fun start(context: Context, stationId: String?) {
        val intent = Intent(context, RadioPlaybackService::class.java)
            .setAction(RadioPlaybackService.ACTION_PLAY_LINK)
            .apply { stationId?.let { putExtra(RadioPlaybackService.EXTRA_STATION_ID, it) } }
        try {
            context.startService(intent)
        } catch (e: IllegalStateException) {
            // Background start refused: the system didn't treat this launch as foreground.
            Log.w(TAG, "Couldn't start playback", e)
            Toast.makeText(context, R.string.play_link_failed, Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private const val TAG = "PlayStation"
        const val ACTION_PLAY_STATION = "com.cascadiacollections.sir.action.PLAY_STATION"
    }
}
