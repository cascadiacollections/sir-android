package com.cascadiacollections.sir

import android.content.ComponentName
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture

/**
 * "What's playing?" — ShoutKit's Siri intent of the same name — from the static app
 * shortcut or the Assistant capability.
 *
 * Headless: reads the session's metadata through a [MediaController] (asynchronously,
 * nothing blocks the main thread), shows the answer as a Toast and speaks it with
 * [TextToSpeech], then finishes. While it speaks it holds transient *may-duck* audio
 * focus, so the radio dips under the voice instead of pausing, and comes back after.
 *
 * When the playback service is not running nothing can be playing, and it is not bound —
 * binding would create it and prepare a stream only to say "Nothing is playing".
 */
class NowPlayingAnnounceActivity : ComponentActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var tts: TextToSpeech? = null
    private var focusRequest: AudioFocusRequest? = null
    private var holdsLegacyFocus = false
    private val audioManager by lazy { getSystemService(AudioManager::class.java) }

    // Legacy (pre-O) focus needs a listener; there is nothing to do on a change, the
    // utterance is a few seconds long and ends on its own.
    private val focusListener = AudioManager.OnAudioFocusChangeListener { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Nothing is drawn; let touches reach whatever is underneath while it speaks.
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
        handler.postDelayed(::finish, TIMEOUT_MS)
        if (savedInstanceState != null) {
            finish()
            return
        }
        if (!RadioPlaybackService.isRunning) {
            announce(NowPlayingAnnouncement.NothingPlaying)
            return
        }
        val token = SessionToken(this, ComponentName(this, RadioPlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        controllerFuture = future
        future.addListener({
            val announcement = runCatching {
                NowPlayingAnnouncement.from(future.get(), getString(R.string.station_name))
            }.getOrElse { error ->
                Log.w(TAG, "Couldn't read the session", error)
                NowPlayingAnnouncement.NothingPlaying
            }
            releaseController()
            if (!isFinishing) announce(announcement)
        }, ContextCompat.getMainExecutor(this))
    }

    private fun announce(announcement: NowPlayingAnnouncement) {
        val text = announcement.text(NowPlayingAnnouncement.Templates.from(this))
        Toast.makeText(applicationContext, text, Toast.LENGTH_LONG).show()
        tts = TextToSpeech(applicationContext) { status ->
            // Called on the main thread.
            val engine = tts
            if (status != TextToSpeech.SUCCESS || engine == null || isFinishing) {
                finish()
                return@TextToSpeech
            }
            engine.setAudioAttributes(SPEECH_ATTRIBUTES)
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) = finishOnMain()

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) = finishOnMain()
                override fun onError(utteranceId: String?, errorCode: Int) = finishOnMain()
            })
            requestDuckingFocus()
            if (engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID) != TextToSpeech.SUCCESS) {
                finish()
            }
        }
    }

    private fun finishOnMain() {
        handler.post(::finish)
    }

    private fun requestDuckingFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(SPEECH_ATTRIBUTES)
                .setOnAudioFocusChangeListener(focusListener)
                .build()
            focusRequest = request
            audioManager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                focusListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK,
            )
            holdsLegacyFocus = true
        }
    }

    private fun abandonFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            focusRequest = null
        } else if (holdsLegacyFocus) {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(focusListener)
            holdsLegacyFocus = false
        }
    }

    private fun releaseController() {
        controllerFuture?.let(MediaController::releaseFuture)
        controllerFuture = null
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        releaseController()
        tts?.run {
            stop()
            shutdown()
        }
        tts = null
        abandonFocus()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "NowPlayingAnnounce"
        const val ACTION_WHATS_PLAYING = "com.cascadiacollections.sir.action.WHATS_PLAYING"
        private const val UTTERANCE_ID = "now-playing"

        /** Upper bound on the whole thing, should the engine never report the utterance done. */
        private const val TIMEOUT_MS = 15_000L

        // USAGE_ASSISTANT is API 26; spoken guidance is the closest older usage that ducks music.
        private val SPEECH_ATTRIBUTES: AudioAttributes = AudioAttributes.Builder()
            .setUsage(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    AudioAttributes.USAGE_ASSISTANT
                } else {
                    AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE
                }
            )
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
    }
}
