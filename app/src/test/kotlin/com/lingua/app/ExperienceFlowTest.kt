package com.lingua.app
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.lingua.app.ui.*
import com.lingua.app.speech.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=LinguaApplication::class)
@LooperMode(LooperMode.Mode.PAUSED)
class ExperienceFlowTest {
    @get:Rule val compose=createComposeRule()
    private val played=mutableListOf<Pair<String,String>>()
    private var released=0
    private fun openApp() {
        val app=RuntimeEnvironment.getApplication() as LinguaApplication
        app.pronunciationFactory={ object: PronunciationService {
            override val state=MutableStateFlow(SpeechState(SpeechPhase.READY))
            override fun speak(text: String,localeTag: String) { played.add(text to localeTag); state.value=SpeechState(SpeechPhase.SPEAKING,text) }
            override fun stop() { state.value=SpeechState(SpeechPhase.READY) }
            override fun close() { released++; state.value=SpeechState(SpeechPhase.CLOSED) }
        } }
        compose.setContent { LinguaApp(LearningViewModel(app)) }
        compose.waitUntil(15000) { compose.onAllNodesWithText("开始学习").fetchSemanticsNodes().any { !it.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled) } }
        click("开始学习")
    }
    private fun click(text: String) { compose.onNodeWithText(text,substring=true).performScrollTo().performClick() }
    private fun back() { compose.onNodeWithText("返回",substring=false).performClick() }
    @Test fun translationAudioCalendarAndFavoriteSharePersistentState() {
        openApp(); click("学习模式"); click("进入韩 → 中")
        compose.onNodeWithText("关键词解释",substring=true).assertDoesNotExist()
        click("原句发音")
        assertEquals("ko-KR",played.last().second)
        compose.onNode(hasSetTextAction()).performTextInput("测试答案")
        click("查看答案")
        compose.onNodeWithText("关键词解释",substring=true).assertExists()
        compose.onNodeWithText("单词 / 拼写",substring=true).assertExists()
        click("不会")
        compose.waitUntil(15000) { compose.onAllNodesWithText("查看答案").fetchSemanticsNodes().isNotEmpty() }
        back(); compose.waitForIdle(); assertTrue(released>0); back()
        click("我的词库")
        compose.onNode(hasSetTextAction()).performTextInput("약속")
        compose.onNodeWithTag("dictionary-list").performScrollToNode(hasText("查看详情 · 약속",substring=false))
        compose.onNodeWithText("查看详情 · 약속",substring=false).performClick()
        compose.onAllNodesWithText("例句发音").assertCountEquals(3)
        click("移出我的学习词库")
        compose.waitUntil(15000) { compose.onAllNodesWithText("加入我的学习词库",substring=false).fetchSemanticsNodes().isNotEmpty() }
        click("加入我的学习词库")
        compose.waitUntil(15000) { compose.onAllNodesWithText("移出我的学习词库").fetchSemanticsNodes().isNotEmpty() }
        click("☆ 收藏")
        compose.waitUntil(15000) { compose.onAllNodesWithText("★ 已收藏").fetchSemanticsNodes().isNotEmpty() }
        back(); back(); click("查看打卡日历")
        compose.onNodeWithContentDescription("${LocalDate.now()} 学习过但未完成").assertExists()
        compose.onNodeWithText("错误次数 1",substring=true).assertExists()
    }
    @Test fun listeningHidesTextAndReverseTranslationProvidesRuleFeedback() {
        openApp(); click("学习模式"); click("进入听力 · 单词")
        compose.onNodeWithText("参考答案：",substring=true).assertDoesNotExist()
        click("播放题目"); assertTrue(played.last().first.isNotBlank())
        click("查看答案")
        compose.onNodeWithText("参考答案：",substring=true).assertExists()
        back(); click("进入中 → 韩")
        compose.onNode(hasSetTextAction()).performTextInput("wrong-answer")
        click("查看答案")
        compose.onNodeWithText("助词 / 语法",substring=true).assertExists()
        compose.onNodeWithText("会",substring=false).assertExists()
    }
}
