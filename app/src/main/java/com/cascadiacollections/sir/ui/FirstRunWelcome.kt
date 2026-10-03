package com.cascadiacollections.sir.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.cascadiacollections.sir.R

/**
 * One-time welcome shown over the shell on first launch (ShoutKit's first-run sheet).
 *
 * Dismissing it any way — the button, back, or a tap outside — counts as done, so the
 * welcome can never trap someone, nor a macrobenchmark that taps through cold starts.
 */
@Composable
fun FirstRunWelcome(onDone: () -> Unit, modifier: Modifier = Modifier) {
    AlertDialog(
        onDismissRequest = onDone,
        modifier = modifier,
        icon = { Icon(Icons.Filled.Radio, contentDescription = null) },
        title = { Text(stringResource(R.string.welcome_title, stringResource(R.string.app_name))) },
        text = { Text(stringResource(R.string.welcome_message)) },
        confirmButton = {
            Button(onClick = onDone) {
                Text(stringResource(R.string.welcome_get_started))
            }
        }
    )
}
