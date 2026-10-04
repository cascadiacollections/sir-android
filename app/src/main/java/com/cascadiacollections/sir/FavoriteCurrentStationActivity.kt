package com.cascadiacollections.sir

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.cascadiacollections.sir.core.persistence.FavoriteCurrentStation.Outcome
import com.cascadiacollections.sir.core.persistence.SettingsRepository
import kotlinx.coroutines.launch

/**
 * "Favorite this station" — ShoutKit's Siri intent — from the static app shortcut or the
 * Assistant capability: saves the selected station to My Stations without opening the app.
 *
 * Headless (translucent, nothing drawn): it writes through [SettingsRepository] directly,
 * so the playback service is neither started nor bound for a one-line write, shows the
 * result as a Toast and finishes. Add-only, so saying it twice never unsaves; the heart in
 * the media notification is the toggle.
 */
class FavoriteCurrentStationActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            // Recreated (e.g. a configuration change) after the write already ran.
            finish()
            return
        }
        val app = applicationContext
        lifecycleScope.launch {
            val outcome = runCatching { SettingsRepository(app).favoriteSelectedStation() }
                .getOrDefault(Outcome.NothingSelected)
            Toast.makeText(app, message(app, outcome), Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    companion object {
        const val ACTION_FAVORITE_CURRENT = "com.cascadiacollections.sir.action.FAVORITE_CURRENT_STATION"

        /** The Toast for [outcome]. */
        fun message(context: Context, outcome: Outcome): String = when (outcome) {
            is Outcome.Added -> context.getString(R.string.favorite_current_added, outcome.station.name)

            is Outcome.AlreadySaved -> context.getString(R.string.favorite_current_already_saved, outcome.station.name)

            // Add-only here, but the outcome type is shared with the notification heart.
            is Outcome.Removed -> context.getString(R.string.favorite_current_removed, outcome.station.name)

            Outcome.DefaultStream ->
                context.getString(R.string.favorite_current_default_stream, context.getString(R.string.station_name))

            Outcome.NothingSelected -> context.getString(R.string.favorite_current_nothing_selected)
        }
    }
}
