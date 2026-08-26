@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3WindowSizeClassApi::class)

package com.cascadiacollections.sir.ui

import android.content.Intent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
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
 * The shell owns the scaffold so that the mini player and navigation chrome persist across
 * tab changes; each tab supplies content only. The mini player is hidden on the listen tab,
 * where the full transport controls are already on screen, but stays mounted (with an idle
 * placeholder) on every other tab so its slot never changes height as playback state changes.
 *
 * Navigation renders as a bottom bar on compact widths and a rail on medium/expanded widths
 * (tablets, unfolded foldables) via [NavigationSuiteScaffold], driven by [windowSizeClass]
 * computed once by the caller.
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
    windowSizeClass: WindowSizeClass,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val isWideLayout = windowSizeClass.widthSizeClass != WindowWidthSizeClass.Compact
    val layoutType = if (isWideLayout) {
        NavigationSuiteType.NavigationRail
    } else {
        NavigationSuiteType.NavigationBar
    }

    NavigationSuiteScaffold(
        modifier = modifier,
        navigationSuiteItems = {
            SirTab.entries.forEach { tab ->
                item(
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
        },
        layoutType = layoutType,
    ) {
    Scaffold(
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
            // Always mounted (outside Listen, which already shows full transport controls
            // inline) so the bar's height never changes as playback starts/stops — only its
            // content swaps between the idle placeholder and the live now-playing row.
            if (selectedTab != SirTab.LISTEN) {
                MiniPlayer(
                    isPlaying = uiState.isPlaying,
                    isBuffering = uiState.isBuffering,
                    isIdle = !uiState.isConnected,
                    title = uiState.trackTitle?.takeIf { it.isNotBlank() },
                    subtitle = uiState.artist,
                    onToggle = onToggle,
                    onClick = { onSelectTab(SirTab.LISTEN) }
                )
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
                isWideLayout = isWideLayout,
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
}
