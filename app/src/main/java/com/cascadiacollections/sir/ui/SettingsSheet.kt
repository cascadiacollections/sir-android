@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.cascadiacollections.sir.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cascadiacollections.sir.BuildConfig
import com.cascadiacollections.sir.CastFeatureManager
import com.cascadiacollections.sir.CastModuleState
import com.cascadiacollections.sir.EqualizerPreset
import com.cascadiacollections.sir.R
import com.cascadiacollections.sir.RadioPlaybackService
import com.cascadiacollections.sir.SettingsRepository
import com.cascadiacollections.sir.SleepTimerDuration
import com.cascadiacollections.sir.StreamQuality
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    settingsRepository: SettingsRepository,
    castFeatureManager: CastFeatureManager,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val chromecastEnabled by settingsRepository.chromecastEnabled.collectAsState(initial = false)
    val castModuleState by castFeatureManager.moduleState.collectAsState()
    val sleepTimerDuration by settingsRepository.sleepTimerDuration.collectAsState(initial = SleepTimerDuration.OFF)
    val equalizerPreset by settingsRepository.equalizerPreset.collectAsState(initial = EqualizerPreset.NORMAL)
    val streamQuality by settingsRepository.streamQuality.collectAsState(initial = StreamQuality.HIGH)
    val customStreamUrl by settingsRepository.customStreamUrl.collectAsState(initial = null)

    var sleepTimerExpanded by remember { mutableStateOf(false) }
    var equalizerExpanded by remember { mutableStateOf(false) }
    var qualityExpanded by remember { mutableStateOf(false) }
    var customStreamText by remember { mutableStateOf(customStreamUrl ?: "") }

    LaunchedEffect(customStreamUrl) {
        customStreamText = customStreamUrl ?: ""
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = stringResource(R.string.settings),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // Sleep Timer
            Text(
                text = stringResource(R.string.sleep_timer),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp)
            )
            ExposedDropdownMenuBox(
                expanded = sleepTimerExpanded,
                onExpandedChange = { sleepTimerExpanded = it },
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                OutlinedTextField(
                    value = sleepTimerDuration.label,
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sleepTimerExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(
                    expanded = sleepTimerExpanded,
                    onDismissRequest = { sleepTimerExpanded = false }
                ) {
                    SleepTimerDuration.entries.forEach { duration ->
                        DropdownMenuItem(
                            text = { Text(duration.label) },
                            onClick = {
                                sleepTimerExpanded = false
                                scope.launch {
                                    settingsRepository.setSleepTimerDuration(duration)
                                    context.startService(
                                        Intent(context, RadioPlaybackService::class.java).apply {
                                            action = RadioPlaybackService.ACTION_SET_SLEEP_TIMER
                                            putExtra(RadioPlaybackService.EXTRA_SLEEP_TIMER_MINUTES, duration.minutes)
                                        }
                                    )
                                    val message = if (duration == SleepTimerDuration.OFF)
                                        context.getString(R.string.sleep_timer_off)
                                    else context.getString(R.string.sleep_timer_set, duration.label)
                                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Equalizer
            Text(
                text = stringResource(R.string.equalizer),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp)
            )
            ExposedDropdownMenuBox(
                expanded = equalizerExpanded,
                onExpandedChange = { equalizerExpanded = it },
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                OutlinedTextField(
                    value = equalizerPreset.label,
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = equalizerExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(
                    expanded = equalizerExpanded,
                    onDismissRequest = { equalizerExpanded = false }
                ) {
                    EqualizerPreset.entries.forEach { preset ->
                        DropdownMenuItem(
                            text = { Text(preset.label) },
                            onClick = {
                                equalizerExpanded = false
                                scope.launch {
                                    settingsRepository.setEqualizerPreset(preset)
                                    context.startService(
                                        Intent(context, RadioPlaybackService::class.java).apply {
                                            action = RadioPlaybackService.ACTION_SET_EQUALIZER
                                            putExtra(RadioPlaybackService.EXTRA_EQUALIZER_PRESET, preset.ordinal)
                                        }
                                    )
                                }
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Stream quality
            Text(
                text = stringResource(R.string.stream_quality),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp)
            )
            ExposedDropdownMenuBox(
                expanded = qualityExpanded,
                onExpandedChange = { qualityExpanded = it },
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                OutlinedTextField(
                    value = streamQuality.label,
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = qualityExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(
                    expanded = qualityExpanded,
                    onDismissRequest = { qualityExpanded = false }
                ) {
                    StreamQuality.entries.forEach { quality ->
                        DropdownMenuItem(
                            text = { Text(quality.label) },
                            onClick = {
                                qualityExpanded = false
                                context.startService(
                                    Intent(context, RadioPlaybackService::class.java).apply {
                                        action = RadioPlaybackService.ACTION_SET_STREAM_QUALITY
                                        putExtra(RadioPlaybackService.EXTRA_STREAM_QUALITY, quality.ordinal)
                                    }
                                )
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()

            // Chromecast toggle
            val castStatusText = when (castModuleState) {
                is CastModuleState.Installed -> stringResource(R.string.chromecast_enabled)
                is CastModuleState.Installing -> {
                    val progress = (castModuleState as CastModuleState.Installing).progress
                    "${stringResource(R.string.chromecast_downloading)} ${(progress * 100).toInt()}%"
                }
                is CastModuleState.Failed -> stringResource(R.string.chromecast_not_available)
                else -> null
            }
            ListItem(
                headlineContent = { Text(stringResource(R.string.enable_chromecast)) },
                supportingContent = castStatusText?.let { { Text(it) } },
                trailingContent = {
                    when (castModuleState) {
                        is CastModuleState.Installing -> LoadingIndicator(modifier = Modifier.size(24.dp))
                        is CastModuleState.Installed -> Switch(checked = true, onCheckedChange = null, enabled = false)
                        else -> Switch(
                            checked = chromecastEnabled,
                            onCheckedChange = { enabled ->
                                scope.launch {
                                    settingsRepository.setChromecastEnabled(enabled)
                                    if (enabled) castFeatureManager.installCastModule()
                                }
                            }
                        )
                    }
                }
            )

            HorizontalDivider()

            // Privacy Policy
            TextButton(
                onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(context.getString(R.string.privacy_policy_url))))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Text(stringResource(R.string.privacy_policy))
            }

            // Debug-only: Custom Stream URL
            if (BuildConfig.DEBUG) {
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.debug_options),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                Text(
                    text = stringResource(R.string.custom_stream_url),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp)
                )
                OutlinedTextField(
                    value = customStreamText,
                    onValueChange = { customStreamText = it },
                    placeholder = { Text(stringResource(R.string.custom_stream_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                )
                if (customStreamUrl != null) {
                    Text(
                        text = stringResource(R.string.custom_stream_active),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, top = 4.dp)
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = {
                            scope.launch {
                                settingsRepository.setCustomStreamUrl(null)
                                customStreamText = ""
                                Toast.makeText(context, context.getString(R.string.custom_stream_reset), Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = customStreamUrl != null
                    ) {
                        Text(stringResource(R.string.reset_to_default))
                    }
                    TextButton(
                        onClick = {
                            if (customStreamText.startsWith("http://") || customStreamText.startsWith("https://")) {
                                scope.launch {
                                    settingsRepository.setCustomStreamUrl(customStreamText)
                                    Toast.makeText(context, context.getString(R.string.custom_stream_saved), Toast.LENGTH_LONG).show()
                                }
                            } else {
                                Toast.makeText(context, context.getString(R.string.custom_stream_invalid), Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = customStreamText.isNotBlank() && customStreamText != customStreamUrl
                    ) {
                        Text(stringResource(R.string.save))
                    }
                }
            }
        }
    }
}

