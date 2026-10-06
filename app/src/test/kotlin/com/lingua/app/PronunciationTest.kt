package com.lingua.app
import android.app.Application
import android.os.Looper
import android.speech.tts.TextToSpeech
import com.lingua.app.speech.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowTextToSpeech
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.util.Locale
import java.time.Duration
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class PronunciationTest {
    @Test fun engineUsesLocaleFlushesRepeatedSpeechAndReleasesResources() {
        ShadowTextToSpeech.addLanguageAvailability(Locale.KOREA)
        ShadowTextToSpeech.addLanguageAvailability(Locale.US)
        val service=AndroidPronunciationService(RuntimeEnvironment.getApplication())
        val engine=ShadowTextToSpeech.getLastTextToSpeechInstance()
        val shadow=shadowOf(engine)
        shadow.onInitListener.onInit(TextToSpeech.SUCCESS); shadowOf(Looper.getMainLooper()).idle()
        assertEquals(SpeechPhase.READY,service.state.value.phase)
        service.speak("약속","ko-KR"); service.speak("약속","ko-KR")
        assertEquals(TextToSpeech.QUEUE_FLUSH,shadow.queueMode)
        assertEquals(2,shadow.spokenTextList.size)
        assertEquals(Locale.KOREA,shadow.currentLanguage)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(SpeechPhase.READY,service.state.value.phase)
        service.speak("hello","en-US"); assertEquals(Locale.US,shadow.currentLanguage)
        service.close(); service.close()
        assertTrue(shadow.isShutdown); assertTrue(shadow.isStopped); assertEquals(SpeechPhase.CLOSED,service.state.value.phase)
    }
    @Test fun unsupportedLanguageHasActionableError() {
        val service=AndroidPronunciationService(RuntimeEnvironment.getApplication())
        val shadow=shadowOf(ShadowTextToSpeech.getLastTextToSpeechInstance())
        shadow.onInitListener.onInit(TextToSpeech.SUCCESS); shadowOf(Looper.getMainLooper()).idle()
        service.speak("약속","ko-KR")
        assertEquals(SpeechPhase.ERROR,service.state.value.phase)
        assertTrue(service.state.value.message!!.contains("语音包")); service.close()
    }
    @Test fun initializationTimeoutAndFailureDoNotLeaveButtonLoadingForever() {
        val service=AndroidPronunciationService(RuntimeEnvironment.getApplication())
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(11))
        assertEquals(SpeechPhase.ERROR,service.state.value.phase)
        shadowOf(ShadowTextToSpeech.getLastTextToSpeechInstance()).onInitListener.onInit(TextToSpeech.ERROR)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(service.state.value.message!!.contains("初始化")); service.close()
    }
}
