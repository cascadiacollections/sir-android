package com.cascadiacollections.sir

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.background
import androidx.glance.layout.Spacer
import androidx.glance.layout.width
import androidx.glance.material3.ColorProviders
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cascadiacollections.sir.ui.theme.Amber40
import com.cascadiacollections.sir.ui.theme.Amber80
import com.cascadiacollections.sir.ui.theme.AmberGrey40
import com.cascadiacollections.sir.ui.theme.AmberGrey80
import com.cascadiacollections.sir.ui.theme.Coral40
import com.cascadiacollections.sir.ui.theme.Coral80

class RadioWidget : GlanceAppWidget() {

    companion object {
        private val colors = ColorProviders(
            light = lightColorScheme(
                primary = Amber40,
                secondary = AmberGrey40,
                tertiary = Coral40
            ),
            dark = darkColorScheme(
                primary = Amber80,
                secondary = AmberGrey80,
                tertiary = Coral80
            )
        )
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme(colors = colors) {
                Row(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(GlanceTheme.colors.surface)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = context.getString(R.string.station_name),
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurface,
                            fontSize = 18.sp
                        ),
                        modifier = GlanceModifier.defaultWeight()
                    )
                    Spacer(modifier = GlanceModifier.width(8.dp))
                    Image(
                        provider = ImageProvider(R.drawable.ic_play),
                        contentDescription = context.getString(R.string.play),
                        modifier = GlanceModifier
                            .size(40.dp)
                            .clickable(actionRunCallback<TogglePlaybackAction>())
                    )
                }
            }
        }
    }
}

class TogglePlaybackAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val intent = Intent(context, RadioPlaybackService::class.java).apply {
            action = RadioPlaybackService.ACTION_PLAY
        }
        ContextCompat.startForegroundService(context, intent)
    }
}

class RadioWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RadioWidget()
}
