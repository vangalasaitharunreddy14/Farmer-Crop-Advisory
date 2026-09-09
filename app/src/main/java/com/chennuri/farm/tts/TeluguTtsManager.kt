package com.chennuri.farm.tts

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

sealed class TtsState {
    data object Uninitialized : TtsState()
    data object Ready : TtsState()
    data object Speaking : TtsState()
    data object Idle : TtsState()
    data class Unavailable(val reason: String) : TtsState()
}

class TeluguTtsManager(context: Context) {

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    private var tts: TextToSpeech? = null
    private var isReady = false
    private var pendingText: String? = null
    private var currentState: TtsState = TtsState.Uninitialized
    private var onStateChange: ((TtsState) -> Unit)? = null

    init {
        tts = TextToSpeech(appContext) { status ->
            if (status != TextToSpeech.SUCCESS) {
                emit(
                    TtsState.Unavailable(
                        "Text-to-speech engine could not start."
                    )
                )
            } else {
                val languageResult = tts?.setLanguage(Locale("te", "IN"))
                    ?: TextToSpeech.ERROR

                val teluguAvailable =
                    languageResult == TextToSpeech.LANG_AVAILABLE ||
                            languageResult == TextToSpeech.LANG_COUNTRY_AVAILABLE ||
                            languageResult ==
                            TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE

                if (!teluguAvailable) {
                    emit(
                        TtsState.Unavailable(
                            "Telugu voice data is not installed. " +
                                    "Install Telugu (India) in text-to-speech settings."
                        )
                    )
                } else {
                    isReady = true
                    tts?.setSpeechRate(0.95f)
                    attachProgressListener()
                    emit(TtsState.Ready)

                    pendingText?.let { text ->
                        pendingText = null
                        speakNow(text)
                    }
                }
            }
        }
    }

    fun setOnStateChangeListener(listener: (TtsState) -> Unit) {
        onStateChange = listener

        mainHandler.post {
            listener(currentState)
        }
    }

    fun speak(
        teluguText: String,
        utteranceId: String = "crop_advisory_telugu"
    ) {
        if (teluguText.isBlank()) {
            emit(TtsState.Unavailable("No Telugu advisory text is available."))
            return
        }

        if (!isReady) {
            pendingText = teluguText
            return
        }

        speakNow(teluguText, utteranceId)
    }

    fun stop() {
        pendingText = null
        tts?.stop()

        if (isReady) {
            emit(TtsState.Idle)
        }
    }

    fun release() {
        pendingText = null
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady = false
        emit(TtsState.Uninitialized)
    }

    private fun speakNow(
        teluguText: String,
        utteranceId: String = "crop_advisory_telugu"
    ) {
        val result = tts?.speak(
            teluguText,
            TextToSpeech.QUEUE_FLUSH,
            null,
            utteranceId
        )

        if (result == TextToSpeech.ERROR) {
            emit(TtsState.Unavailable("Could not start Telugu speech playback."))
        }
    }

    private fun attachProgressListener() {
        tts?.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    emit(TtsState.Speaking)
                }

                override fun onDone(utteranceId: String?) {
                    emit(TtsState.Idle)
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    emit(
                        TtsState.Unavailable(
                            "Telugu speech playback failed."
                        )
                    )
                }
            }
        )
    }

    private fun emit(state: TtsState) {
        currentState = state

        mainHandler.post {
            onStateChange?.invoke(state)
        }
    }
}