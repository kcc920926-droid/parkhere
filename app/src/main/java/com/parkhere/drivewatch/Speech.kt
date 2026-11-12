package com.parkhere.drivewatch

import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale
import ai.pleos.playground.tts.TextToSpeech as PleosTts

/**
 * Korean voice. On Pleos the system engine refuses plain Android TTS, so use the Pleos TextToSpeech SDK
 * (on-device mode, no server key needed). Anywhere else, fall back to Android TTS.
 */
class Speaker(context: Context) {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var pleos: PleosTts? = null
    private var pleosReady = false
    private var android: TextToSpeech? = null
    private var done: (() -> Unit)? = null

    init {
        val t = runCatching { PleosTts(app, Mode.ON_DEVICE) }.getOrNull()
        if (t == null) fallback() else runCatching {
            pleos = t
            t.initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    main.post {
                        runCatching {
                            t.setLocale(Locale.KOREA) // engine needs "ko-KR"; Locale.KOREAN gives "ko-" and fails
                            t.addEventListener(object : EventListener { // listeners only after initialize()
                                override fun onStart() {}
                                override fun onReady() {}
                                override fun onDone() = finish()
                                override fun onError(e: ErrorType) { Log.w(TAG, "pleos tts error $e"); finish() }
                                override fun onStop() = finish()
                                override fun onUpdatedRms(rms: Double) {}
                            })
                            pleosReady = true
                            Log.i(TAG, "pleos tts ready")
                        }.onFailure { Log.w(TAG, "pleos tts setup", it); fallback() }
                    }
                }
                override fun onFailure(e: ErrorType) { main.post { Log.w(TAG, "pleos tts init failed $e"); fallback() } }
            })
        }.onFailure { Log.w(TAG, "pleos tts unavailable", it); fallback() }
    }

    private fun fallback() {
        if (android != null) return
        android = TextToSpeech(app) { status ->
            Log.i(TAG, "android tts init $status")
            if (status == TextToSpeech.SUCCESS) android?.language = Locale.KOREAN
        }.also {
            it.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) {}
                override fun onDone(id: String?) = finish()
                @Deprecated("Deprecated in Java") override fun onError(id: String?) = finish()
            })
        }
    }

    private fun finish() { main.post { done?.invoke(); done = null } }

    fun speak(line: String, onDone: (() -> Unit)? = null) {
        done = onDone
        val p = pleos
        if (p != null && pleosReady) runCatching { p.speak(line, true) }.onFailure { Log.w(TAG, "pleos speak", it); finish() }
        else android?.speak(line, TextToSpeech.QUEUE_FLUSH, null, "dw") ?: finish()
    }

    fun release() {
        runCatching { pleos?.release() }
        android?.shutdown()
    }

    private companion object { const val TAG = "DriveWatch" }
}
