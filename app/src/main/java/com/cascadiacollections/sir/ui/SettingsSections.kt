package com.cascadiacollections.sir.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.cascadiacollections.sir.BuildConfig
import com.cascadiacollections.sir.R
import com.cascadiacollections.sir.core.persistence.SettingsRepository
import kotlinx.coroutines.launch

/**
 * Settings → Playback: options that change how playback starts rather than how it sounds.
 * The sleep timer and equalizer above it predate the heading and stay where they are.
 */
@Composable
internal fun PlaybackSettingsSection(settingsRepository: SettingsRepository) {
    val scope = rememberCoroutineScope()
    val prewarm by settingsRepository.connectionPrewarmingEnabled.collectAsState(initial = false)
    val loopBroadcasts by settingsRepository.loopFinishedBroadcasts.collectAsState(initial = false)

    SettingsSectionHeading(stringResource(R.string.playback_heading))
    // Read once at launch by SirApp, so a change takes effect from the next launch.
    ListItem(
        headlineContent = { Text(stringResource(R.string.prewarm_connections)) },
        supportingContent = { Text(stringResource(R.string.prewarm_connections_summary)) },
        trailingContent = {
            Switch(
                checked = prewarm,
                onCheckedChange = { enabled ->
                    scope.launch { settingsRepository.setConnectionPrewarmingEnabled(enabled) }
                }
            )
        }
    )
    // Read live by RadioPlaybackService, so a change applies to whatever is playing now.
    ListItem(
        headlineContent = { Text(stringResource(R.string.loop_finished_broadcasts)) },
        supportingContent = { Text(stringResource(R.string.loop_finished_broadcasts_summary)) },
        trailingContent = {
            Switch(
                checked = loopBroadcasts,
                onCheckedChange = { enabled ->
                    scope.launch { settingsRepository.setLoopFinishedBroadcasts(enabled) }
                }
            )
        }
    )
}

/** Settings → About, at the end of the screen. */
@Composable
internal fun AboutSettingsSection(onOpenLicenses: () -> Unit) {
    val context = LocalContext.current

    HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
    SettingsSectionHeading(stringResource(R.string.about_heading))
    ListItem(
        headlineContent = { Text(stringResource(R.string.about_version)) },
        supportingContent = {
            Text(
                stringResource(
                    R.string.about_version_value,
                    BuildConfig.VERSION_NAME,
                    BuildConfig.VERSION_CODE,
                    BuildConfig.GIT_COMMIT
                )
            )
        }
    )
    AboutLink(R.string.about_source_code) { context.openUrl(R.string.about_source_code_url) }
    AboutLink(R.string.about_report_issue) { context.openUrl(R.string.about_report_issue_url) }
    // ShoutKit's "Support Holmdel" link. FOSS builds only: Google Play's payments policy
    // restricts steering users to outside payment, and nothing here is gated on it anyway.
    if (BuildConfig.FLAVOR == "foss") {
        AboutLink(R.string.about_support) { context.openUrl(R.string.about_support_url) }
    }
    AboutLink(R.string.about_station_data) { context.openUrl(R.string.about_station_data_url) }
    AboutLink(R.string.open_source_licenses, onOpenLicenses)
}

@Composable
private fun SettingsSectionHeading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
    )
}

@Composable
private fun AboutLink(labelRes: Int, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(labelRes)) },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

private fun Context.openUrl(urlRes: Int) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, getString(urlRes).toUri()))
    } catch (_: ActivityNotFoundException) {
        // No browser installed; nothing sensible to fall back to.
    }
}
