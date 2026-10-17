package com.jarvis.companion.audio

/**
 * STTProvider — Interface for Speech-to-Text providers.
 * Supports native Gemini Live streaming as well as fallback to Uzbek STT
 * (e.g. Muxlisa, UzbekVoice API, or Android SpeechRecognizer).
 */
interface STTProvider {
    val name: String
    val isAvailable: Boolean

    fun startListening(
        onPartialResult: (text: String) -> Unit,
        onFinalResult: (text: String) -> Unit,
        onError: (errorMessage: String) -> Unit
    )

    fun processAudioChunk(pcmChunk: ByteArray) {}

    fun stopListening()
}
