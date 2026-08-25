@file:OptIn(ExperimentalMaterial3Api::class)

package com.cascadiacollections.sir.ui

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import com.cascadiacollections.sir.CastFeatureManager
import com.cascadiacollections.sir.R
import com.cascadiacollections.sir.RadioBrowserViewModel
import com.cascadiacollections.sir.RadioUiState
import com.cascadiacollections.sir.core.persistence.SettingsRepository

/**
 * The four-tab app shell.
 *
 * The shell owns the scaffold so that the mini player and navigation bar persist across
 * tab changes; each tab supplies content only. The mini player is hidden on the listen
 * tab, where the full transport controls are already on screen.
 */
@Composable
fun SirAppShell(
    uiState: RadioUiState,
    selectedTab: SirTab,
    onSelectTab: (SirTab) -> Unit,
    onToggle: () -> Unit,
    browserViewModel: RadioBrowserViewModel,
    settingsRepository: SettingsRepository,
    castFeatureManager: CastFeatureManager,
    onOpenLicenses: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val resources = LocalResources.current

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(selectedTab.labelRes)) },
                actions = {
                    if (uiState.isPlaying && uiState.trackTitle != null) {
                        IconButton(onClick = {
                            val shareText = listOfNotNull(uiState.trackTitle, uiState.artist)
                                .joinToString(" — ")
                            context.startActivity(
                                Intent.createChooser(
                                    Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(
                                            Intent.EXTRA_TEXT,
                                            resources.getString(R.string.share_now_playing, shareText)
                                        )
                                    },
                                    null
                                )
                            )
                        }) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = stringResource(R.string.share),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            Column {
                if (selectedTab != SirTab.LISTEN && uiState.isConnected) {
                    MiniPlayer(
                        isPlaying = uiState.isPlaying,
                        isBuffering = uiState.isBuffering,
                        title = uiState.trackTitle?.takeIf { it.isNotBlank() }
                            ?: stringResource(R.string.station_name),
                        subtitle = uiState.artist,
                        onToggle = onToggle,
                        onClick = { onSelectTab(SirTab.LISTEN) }
                    )
                }
                NavigationBar {
                    SirTab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = tab == selectedTab,
                            onClick = { onSelectTab(tab) },
                            icon = {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = stringResource(tab.labelRes)
                                )
                            },
                            label = { Text(stringResource(tab.labelRes)) }
                        )
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)

        when (selectedTab) {
            SirTab.LISTEN -> ListenScreen(
                modifier = contentModifier,
                isConnected = uiState.isConnected,
                isPlaying = uiState.isPlaying,
                isBuffering = uiState.isBuffering,
                isError = uiState.isError,
                trackTitle = uiState.trackTitle,
                artist = uiState.artist,
                sleepTimerLabel = uiState.sleepTimerLabel,
                onToggle = onToggle
            )

            SirTab.BROWSE -> BrowseScreen(
                viewModel = browserViewModel,
                modifier = contentModifier
            )

            SirTab.LIBRARY -> LibraryScreen(
                viewModel = browserViewModel,
                modifier = contentModifier
            )

            SirTab.SETTINGS -> SettingsContent(
                settingsRepository = settingsRepository,
                castFeatureManager = castFeatureManager,
                modifier = contentModifier,
                onOpenLicenses = onOpenLicenses
            )
        }
    }
}
