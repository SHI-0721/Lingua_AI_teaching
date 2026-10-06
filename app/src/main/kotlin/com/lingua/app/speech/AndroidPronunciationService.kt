package com.lingua.app.speech

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

class AndroidPronunciationService(context: Context) : PronunciationService {
    private val status=MutableStateFlow(SpeechState())
    override val state=status.asStateFlow()
    private val handler=Handler(Looper.getMainLooper())
    private var engine: TextToSpeech?=null
    private var initialized=false
    private var closed=false
    @Volatile private var activeId: String?=null
    private val timeout=Runnable { if(!initialized && !closed) status.value=SpeechState(SpeechPhase.ERROR,message="语音服务初始化超时，请检查系统文字转语音设置") }
    init {
        handler.postDelayed(timeout,10_000)
        engine=TextToSpeech(context.applicationContext) { result -> handler.post {
            if(!closed) {
                handler.removeCallbacks(timeout)
                initialized=result==TextToSpeech.SUCCESS
                status.value=if(initialized) SpeechState(SpeechPhase.READY) else SpeechState(SpeechPhase.ERROR,message="无法初始化语音服务，请安装或启用系统 TTS 引擎")
            }
        } }
        engine?.setOnUtteranceProgressListener(object: UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) { if(utteranceId==activeId && !closed) status.value=status.value.copy(phase=SpeechPhase.SPEAKING) }
            override fun onDone(utteranceId: String?) { if(utteranceId==activeId && !closed) { activeId=null; status.value=SpeechState(SpeechPhase.READY) } }
            @Deprecated("Android legacy callback") override fun onError(utteranceId: String?) { fail(utteranceId) }
            override fun onError(utteranceId: String?,errorCode: Int) { fail(utteranceId) }
            override fun onStop(utteranceId: String?,interrupted: Boolean) { if(utteranceId==activeId && !closed) status.value=SpeechState(SpeechPhase.READY) }
        })
    }
    private fun fail(id: String?) { if(id==activeId && !closed) status.value=SpeechState(SpeechPhase.ERROR,message="发音播放失败，请检查语音包") }
    override fun speak(text: String,localeTag: String) {
        if(closed) return
        if(!initialized) { status.value=SpeechState(if(status.value.phase==SpeechPhase.ERROR) SpeechPhase.ERROR else SpeechPhase.INITIALIZING,message="语音服务尚未就绪"); return }
        if(text.isBlank() || text.length>TextToSpeech.getMaxSpeechInputLength()) { status.value=SpeechState(SpeechPhase.ERROR,message="朗读内容为空或过长"); return }
        val tts=engine ?: return
        val locale=Locale.forLanguageTag(localeTag)
        if(tts.isLanguageAvailable(locale)<TextToSpeech.LANG_AVAILABLE || tts.setLanguage(locale)<TextToSpeech.LANG_AVAILABLE) {
            status.value=SpeechState(SpeechPhase.ERROR,message="系统不支持 $localeTag，请在系统文字转语音设置中安装对应语言语音包"); return
        }
        // Prefer downloaded voices so practice can work offline when the engine provides them.
        tts.voices?.filter { it.locale.language==locale.language && !it.isNetworkConnectionRequired }?.minByOrNull { if(it.locale==locale) 0 else 1 }?.let { tts.voice=it }
        val id=UUID.randomUUID().toString()
        activeId=id
        status.value=SpeechState(SpeechPhase.SPEAKING,text)
        if(tts.speak(text,TextToSpeech.QUEUE_FLUSH,null,id)==TextToSpeech.ERROR) fail(id)
    }
    override fun stop() { activeId=null; engine?.stop(); if(initialized && !closed) status.value=SpeechState(SpeechPhase.READY) }
    override fun close() { if(!closed) { closed=true; activeId=null; handler.removeCallbacks(timeout); engine?.stop(); engine?.shutdown(); engine=null; status.value=SpeechState(SpeechPhase.CLOSED) } }
}
