package com.cascadiacollections.sir.wear.complication

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.data.SmallImage
import androidx.wear.watchface.complications.data.SmallImageComplicationData
import androidx.wear.watchface.complications.data.SmallImageType
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.cascadiacollections.sir.wear.R
import com.cascadiacollections.sir.wear.WearActivity
import com.cascadiacollections.sir.wear.WearStations
import com.cascadiacollections.sir.wear.sync.WatchStationStore

/**
 * ShoutKit's "Play Last" complication: shows the station last played on the phone and
 * plays it on the watch when tapped. With nothing synced yet it offers the SIR stream.
 *
 * The tap opens [WearActivity] with [WearActivity.EXTRA_PLAY_LAST] rather than starting
 * the playback service directly — a foreground activity may start a foreground service
 * on every API level, a complication tap's PendingIntent may not.
 */
class PlayLastComplicationService : SuspendingComplicationDataSourceService() {

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val name = WatchStationStore.from(this).load().last?.name?.takeIf { it.isNotBlank() }
            ?: getString(R.string.station_name)
        return build(this, request.complicationType, name, playLastIntent(this))
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        build(this, type, getString(R.string.station_name), tapAction = null)

    companion object {

        fun playLastIntent(context: Context): PendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, WearActivity::class.java)
                .putExtra(WearActivity.EXTRA_PLAY_LAST, true)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        internal fun build(
            context: Context,
            type: ComplicationType,
            stationName: String,
            tapAction: PendingIntent?
        ): ComplicationData? {
            val icon = Icon.createWithResource(context, R.drawable.ic_play_last)
            val description = PlainComplicationText.Builder(
                context.getString(R.string.play_station, stationName)
            ).build()
            return when (type) {
                ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(
                    text = PlainComplicationText.Builder(WearStations.abbreviate(stationName)).build(),
                    contentDescription = description
                )
                    .setMonochromaticImage(MonochromaticImage.Builder(icon).build())
                    .setTapAction(tapAction)
                    .build()

                ComplicationType.SMALL_IMAGE -> SmallImageComplicationData.Builder(
                    smallImage = SmallImage.Builder(icon, SmallImageType.ICON).build(),
                    contentDescription = description
                )
                    .setTapAction(tapAction)
                    .build()

                else -> null
            }
        }
    }
}
