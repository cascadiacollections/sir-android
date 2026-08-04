package com.cascadiacollections.sir.ui

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.cascadiacollections.sir.R

/**
 * The top-level destinations of the app shell.
 *
 * Declared in display order; the shell renders the navigation bar straight from
 * [entries], so adding a tab here is the only change needed.
 */
enum class SirTab(
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    LISTEN(R.string.tab_listen, Icons.Default.PlayCircle),
    BROWSE(R.string.tab_browse, Icons.Default.Search),
    LIBRARY(R.string.tab_library, Icons.Default.LibraryMusic),
    SETTINGS(R.string.tab_settings, Icons.Default.Settings),
}
