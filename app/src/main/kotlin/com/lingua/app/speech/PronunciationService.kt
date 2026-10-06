package com.lingua.app.speech
import kotlinx.coroutines.flow.StateFlow
enum class SpeechPhase { INITIALIZING, READY, SPEAKING, ERROR, CLOSED }
data class SpeechState(val phase: SpeechPhase=SpeechPhase.INITIALIZING,val text: String="",val message: String?=null)
interface PronunciationService {
    val state: StateFlow<SpeechState>
    fun speak(text: String,localeTag: String)
    fun stop()
    fun close()
}
