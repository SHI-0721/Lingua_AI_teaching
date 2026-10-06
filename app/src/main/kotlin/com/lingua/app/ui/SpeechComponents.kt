package com.lingua.app.ui
import androidx.compose.runtime.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lingua.app.LinguaApplication
import com.lingua.app.speech.*
val LocalSpeech=staticCompositionLocalOf<PronunciationService?> { null }
@Composable fun SpeechScope(content: @Composable ()->Unit) {
    val context=LocalContext.current
    val service=remember { (context.applicationContext as LinguaApplication).pronunciationFactory(context) }
    val owner=LocalLifecycleOwner.current
    DisposableEffect(service,owner) {
        val observer=LifecycleEventObserver { _,event -> if(event==Lifecycle.Event.ON_STOP) service.stop() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); service.close() }
    }
    CompositionLocalProvider(LocalSpeech provides service) { content() }
}
@Composable fun SpeakButton(text: String,locale: String,label: String) {
    val service=LocalSpeech.current ?: return
    val state by service.state.collectAsState()
    val playing=state.phase==SpeechPhase.SPEAKING && state.text==text
    val alpha by animateFloatAsState(if(playing) 0.65f else 1f,label="speech")
    OutlinedButton(onClick={ service.speak(text,locale) },enabled=state.phase!=SpeechPhase.INITIALIZING && state.phase!=SpeechPhase.CLOSED,modifier=Modifier.alpha(alpha)) {
        Icon(Icons.AutoMirrored.Rounded.VolumeUp,contentDescription=null)
        Text(if(playing) "播放中 · 点击重播" else label)
    }
}
@Composable fun SpeechNotice() { LocalSpeech.current?.let { service -> val state by service.state.collectAsState(); if(state.phase==SpeechPhase.INITIALIZING) Text("正在准备发音服务…",style=MaterialTheme.typography.bodySmall); state.message?.let { Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall) } } }
