package com.lingua.app
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.activity.ComponentActivity
import android.graphics.Bitmap
import android.graphics.Canvas
import com.lingua.app.ui.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Native Android graphics preview; not a substitute for device/voice-engine testing. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],qualifiers="w411dp-h891dp-xhdpi",application=LinguaApplication::class)
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NativePreviewTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private fun capture(name: String) {
        compose.waitForIdle()
        val view=compose.activity.window.decorView
        val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
        compose.runOnUiThread { view.draw(Canvas(bitmap)) }
        val file=File("build/screenshots/$name.png"); file.parentFile!!.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
    }
    @Test fun teacherChatHidesAnswersPersistsAndRendersBothThemes() {
        val app=RuntimeEnvironment.getApplication() as LinguaApplication
        val vm=LearningViewModel(app)
        compose.setContent { LinguaApp(vm) }
        compose.waitUntil(15000) { compose.onAllNodesWithText("开始学习").fetchSemanticsNodes().any { !it.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled) } }
        compose.onNodeWithText("开始学习").performScrollTo().performClick()
        compose.onNodeWithText("学习模式").performScrollTo().performClick()
        compose.onNodeWithText("进入 AI 老师").performScrollTo().performClick()
        compose.onNodeWithText("开始基础测评").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("提交回答").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("参考表达：",substring=true).assertDoesNotExist()
        capture("teacher-question-light")
        compose.onNode(hasSetTextAction()).performTextInput("测试回答")
        compose.onNodeWithText("提交回答").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("再次尝试").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("参考表达：",substring=true).assertDoesNotExist()
        compose.onNodeWithText("查看答案").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("确认你的掌握程度后才写入复习记录").fetchSemanticsNodes().isNotEmpty() }
        capture("teacher-answer-light")
        compose.onNodeWithText("不会",substring=false).performClick()
        compose.waitUntil(10000) { compose.waitForIdle();vm.state.value.reviews.size==1 }
        compose.onNodeWithText("返回",substring=false).performClick()
        compose.onNodeWithText("进入 AI 老师").performScrollTo().performClick()
        compose.onNodeWithText("基础测评 · 2/3").assertExists()
        compose.runOnUiThread { vm.setTheme("dark") }
        compose.waitUntil(10000) { compose.waitForIdle();vm.state.value.settings.theme=="dark" }
        capture("teacher-question-dark")
    }
    @Test fun rendersLightDarkCalendarAndRecallScreens() {
        val vm=LearningViewModel(RuntimeEnvironment.getApplication() as LinguaApplication)
        compose.setContent { LinguaApp(vm) }
        compose.waitUntil(15000) { compose.onAllNodesWithText("开始学习").fetchSemanticsNodes().any { !it.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled) } }
        compose.onNodeWithText("开始学习").performScrollTo().performClick()
        capture("home-light")
        compose.onNodeWithText("我的词库").performScrollTo().performClick()
        compose.onNode(hasSetTextAction()).performTextInput("학교")
        compose.onNodeWithText("我的词库 · 6011 个词").assertExists()
        capture("dictionary-search-light")
        compose.onNodeWithText("初级",substring=false).performClick()
        compose.onNodeWithText("未分级 · 已加入学习",substring=false).assertDoesNotExist()
        capture("dictionary-difficulty-light")
        compose.onNodeWithText("未分级",substring=false).performClick()
        compose.onNodeWithText("找到 1 个义项").assertExists()
        compose.onNodeWithText("全部",substring=false).performClick()
        compose.onNodeWithTag("dictionary-list").performScrollToNode(hasText("词典详情 · 학교"))
        compose.onAllNodesWithText("词典详情 · 학교").onFirst().performClick()
        compose.onNodeWithText("内容来源").assertExists()
        compose.onNodeWithText("许可：CC BY-SA 2.0 KR").assertExists()
        compose.onNodeWithText("内容来源").performScrollTo()
        capture("dictionary-source-light")
        compose.onNodeWithText("返回",substring=false).performClick()
        compose.onNodeWithText("返回",substring=false).performClick()
        compose.runOnUiThread { vm.setTheme("dark") }
        compose.waitUntil(10000) { compose.waitForIdle(); vm.state.value.settings.theme=="dark" }; compose.waitForIdle()
        capture("home-dark")
        compose.onNodeWithText("查看打卡日历").performScrollTo().performClick()
        capture("calendar-dark")
        compose.onNodeWithText("返回",substring=false).performClick()
        compose.onNodeWithText("开始今日学习").performScrollTo().performClick()
        capture("recall-hidden-dark")
        compose.onNodeWithText("查看答案").performScrollTo().performClick()
        compose.waitForIdle()
        capture("recall-answer-dark")
    }
}
