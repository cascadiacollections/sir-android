package com.cascadiacollections.sir.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * FOSS build: no Cast icon. Mirrors `src/play/.../ui/CastButton.kt`'s signature
 * exactly so shared code in `main` compiles against either flavor unchanged — see
 * `CastFeatureManager` for the same convention applied to the non-UI Cast API.
 */
@Composable
@Suppress("UNUSED_PARAMETER")
fun CastButton(modifier: Modifier = Modifier) = Unit
