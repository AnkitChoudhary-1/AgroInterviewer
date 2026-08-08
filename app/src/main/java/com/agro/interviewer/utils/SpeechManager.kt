package com.agro.interviewer.utils

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import timber.log.Timber
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

sealed class SpeechEvent {
    data class SpeechRecognized(val text: String, val isFinal: Boolean) : SpeechEvent()
    data object ListeningStarted : SpeechEvent()
    data object ListeningStopped : SpeechEvent()
    data class Error(val message: String) : SpeechEvent()
}

@Singleton
class SpeechManager @Inject constructor(
    @ApplicationContext private val context: Context
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private var pendingSpeechText: String? = null
    private var onUtteranceComplete: (() -> Unit)? = null

    private var activeUtteranceId: String? = null
    private var safetyRunnable: Runnable? = null

    private var speechRecognizer: SpeechRecognizer? = null
    private var isUserListeningRequested = false
    private var consecutiveErrorCount = 0
    private val maxRetries = 5

    private val _events = MutableSharedFlow<SpeechEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<SpeechEvent> = _events.asSharedFlow()

    private val mainHandler = Handler(Looper.getMainLooper())

    init {
        mainHandler.post {
            try {
                tts = TextToSpeech(context, this)
            } catch (e: Exception) {
                Timber.e(e, "TTS Initialization error")
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.US)
            if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                isTtsReady = true
                val audioAttributes = android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                tts?.setAudioAttributes(audioAttributes)
                Timber.d("TextToSpeech initialized successfully")

                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        Timber.d("TTS started speaking %s", utteranceId)
                    }

                    override fun onDone(utteranceId: String?) {
                        Timber.d("TTS finished speaking %s", utteranceId)
                        if (utteranceId == activeUtteranceId || activeUtteranceId == null) {
                            mainHandler.post {
                                safetyRunnable?.let { mainHandler.removeCallbacks(it) }
                                safetyRunnable = null
                                onUtteranceComplete?.invoke()
                                onUtteranceComplete = null
                            }
                        }
                    }

                    override fun onError(utteranceId: String?) {
                        Timber.w("TTS error on %s", utteranceId)
                        mainHandler.post {
                            safetyRunnable?.let { mainHandler.removeCallbacks(it) }
                            safetyRunnable = null
                            onUtteranceComplete?.invoke()
                            onUtteranceComplete = null
                        }
                    }
                })

                pendingSpeechText?.let { queuedText ->
                    pendingSpeechText = null
                    speak(queuedText, onUtteranceComplete)
                }
            }
        } else {
            Timber.e("TextToSpeech onInit failed with status $status")
        }
    }

    fun speak(text: String, onComplete: (() -> Unit)? = null) {
        stopListeningInternal()
        this.onUtteranceComplete = onComplete
        safetyRunnable?.let { mainHandler.removeCallbacks(it) }

        if (!isTtsReady || tts == null) {
            Timber.w("TTS not ready yet, queuing text: %s", text)
            pendingSpeechText = text
            val fallback = Runnable {
                onUtteranceComplete?.invoke()
                onUtteranceComplete = null
            }
            safetyRunnable = fallback
            mainHandler.postDelayed(fallback, 4000L)
            return
        }

        mainHandler.post {
            val utteranceId = "utt_${System.currentTimeMillis()}"
            activeUtteranceId = utteranceId

            val result = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
            if (result == TextToSpeech.ERROR) {
                Timber.e("TTS speak failed with ERROR status")
                onUtteranceComplete?.invoke()
                onUtteranceComplete = null
            } else {
                val fallback = Runnable {
                    if (onUtteranceComplete != null) {
                        Timber.w("TTS safety timeout for %s — proceeding to listen", utteranceId)
                        onUtteranceComplete?.invoke()
                        onUtteranceComplete = null
                    }
                }
                safetyRunnable = fallback
                mainHandler.postDelayed(fallback, 15_000L)
            }
        }
    }

    fun stopSpeaking() {
        mainHandler.post {
            safetyRunnable?.let { mainHandler.removeCallbacks(it) }
            safetyRunnable = null
            tts?.stop()
            onUtteranceComplete = null
        }
    }

    fun startListening() {
        isUserListeningRequested = true
        consecutiveErrorCount = 0
        mainHandler.post {
            startListeningInternal()
        }
    }

    private fun startListeningInternal() {
        try {
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                _events.tryEmit(SpeechEvent.Error("Speech recognition not available on this device"))
                return
            }

            stopListeningInternal()

            val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    Timber.d("SpeechRecognizer ready for speech")
                    consecutiveErrorCount = 0
                    _events.tryEmit(SpeechEvent.ListeningStarted)
                }

                override fun onBeginningOfSpeech() {
                    Timber.d("SpeechRecognizer beginning of speech")
                }

                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    Timber.d("SpeechRecognizer end of speech")
                }

                override fun onError(error: Int) {
                    val msg = getErrorMessage(error)
                    Timber.w("SpeechRecognizer error (%d): %s", error, msg)

                    val isRetryable = error == SpeechRecognizer.ERROR_NO_MATCH ||
                            error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ||
                            error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY ||
                            error == SpeechRecognizer.ERROR_CLIENT ||
                            error == SpeechRecognizer.ERROR_AUDIO ||
                            error == SpeechRecognizer.ERROR_SERVER

                    if (isUserListeningRequested && isRetryable && consecutiveErrorCount < maxRetries) {
                        consecutiveErrorCount++
                        val backoffMs = 400L + (consecutiveErrorCount * 150L)
                        Timber.d("Speech error $error (retry $consecutiveErrorCount/$maxRetries) — retrying in ${backoffMs}ms...")
                        mainHandler.postDelayed({
                            if (isUserListeningRequested) {
                                startListeningInternal()
                            }
                        }, backoffMs)
                    } else {
                        isUserListeningRequested = false
                        consecutiveErrorCount = 0
                        _events.tryEmit(SpeechEvent.ListeningStopped)
                        _events.tryEmit(SpeechEvent.Error(msg))
                    }
                }

                override fun onResults(results: Bundle?) {
                    consecutiveErrorCount = 0
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull() ?: ""
                    Timber.d("SpeechRecognizer result: %s", text)
                    if (text.isNotEmpty()) {
                        _events.tryEmit(SpeechEvent.SpeechRecognized(text, isFinal = true))
                    }
                    if (isUserListeningRequested) {
                        mainHandler.postDelayed({
                            if (isUserListeningRequested) startListeningInternal()
                        }, 500L)
                    } else {
                        _events.tryEmit(SpeechEvent.ListeningStopped)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull() ?: ""
                    if (text.isNotEmpty()) {
                        _events.tryEmit(SpeechEvent.SpeechRecognized(text, isFinal = false))
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            speechRecognizer = recognizer

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US.language)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }

            recognizer.startListening(intent)
        } catch (e: Exception) {
            Timber.e(e, "Failed to start SpeechRecognizer")
            isUserListeningRequested = false
            _events.tryEmit(SpeechEvent.Error("Speech recognition failed: ${e.message}"))
        }
    }

    fun stopListening() {
        isUserListeningRequested = false
        mainHandler.post {
            stopListeningInternal()
            _events.tryEmit(SpeechEvent.ListeningStopped)
        }
    }

    private fun stopListeningInternal() {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Timber.w(e, "Error destroying SpeechRecognizer")
        }
        speechRecognizer = null
    }

    private fun getErrorMessage(errorCode: Int): String = when (errorCode) {
        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
        SpeechRecognizer.ERROR_CLIENT -> "Client side error"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
        SpeechRecognizer.ERROR_NETWORK -> "Network error"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
        SpeechRecognizer.ERROR_NO_MATCH -> "No speech match"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognition service busy"
        SpeechRecognizer.ERROR_SERVER -> "Server error"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
        else -> "Speech error ($errorCode)"
    }

    fun destroy() {
        isUserListeningRequested = false
        mainHandler.post {
            try {
                tts?.stop()
                tts?.shutdown()
                stopListeningInternal()
            } catch (e: Exception) {
                Timber.w(e, "Error destroying SpeechManager")
            }
        }
    }
}
