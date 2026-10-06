package com.lingua.app

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.lingua.app.data.*
import com.lingua.app.ui.*
import com.lingua.app.ui.theme.LinguaTheme
import com.lingua.core.Recall
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=LinguaApplication::class)
@LooperMode(LooperMode.Mode.PAUSED)
class TeacherFlowTest {
    @get:Rule val compose=createComposeRule()
    @Test fun assessmentPlanRetryReinforcementAndSummaryComplete() {
        val app=RuntimeEnvironment.getApplication() as LinguaApplication
        val words=listOf(WordEntity("a","ko","basic","학교","学校","","","",""),WordEntity("b","ko","basic","친구","朋友","","","",""),WordEntity("c","ko","basic","가다","去","","","",""))
        val examples=listOf(ExampleEntity("s","ko","a","학교에 가요.","我去学校。"),ExampleEntity("t","ko","b","친구를 만나요.","我见朋友。"))
        runBlocking { app.repository.applyRemoteContent(RemoteContent(words,examples)) }
        fun learning()=runBlocking { LearningState(words=app.repository.words.first(),reviews=app.repository.reviews.first(),examples=examples,loading=false,languageName="韩语") }
        val vm=TeacherViewModel(app)
        compose.setContent { val ui by vm.state.collectAsState();LinguaTheme(false) { TeacherContent(learning(),ui,vm,"ko-KR") } }
        compose.runOnUiThread { vm.start(learning(),"语法与翻译","初级",15) }
        fun settled() { compose.waitUntil(10000) { compose.waitForIdle();!vm.state.value.busy } }
        settled()
        compose.runOnUiThread { vm.submit("未知表达") };settled()
        assertFalse(vm.state.value.course!!.revealed)
        assertTrue(runBlocking { app.repository.reviews.first().isEmpty() })
        compose.runOnUiThread { vm.reveal() };settled()
        compose.runOnUiThread { vm.rate(Recall.AGAIN,learning()) };settled()
        var turns=1
        while(!vm.state.value.course!!.complete && turns<60) {
            compose.runOnUiThread { vm.reveal() };settled()
            compose.runOnUiThread { vm.rate(Recall.KNOW,learning()) };settled()
            turns++
        }
        val course=vm.state.value.course!!
        assertTrue(course.complete);assertTrue(course.planned);assertTrue(course.reinforced)
        assertTrue(course.questions.any { it.id.endsWith("-reinforce") })
        assertTrue(course.questions.any { it.kind.contains("语法") })
        assertTrue(course.questions.any { it.kind.contains("译") })
        assertEquals(turns,runBlocking { app.repository.reviews.first().size })
        assertTrue(course.messages.last().text.startsWith("课程完成"))
        assertEquals(course,runBlocking { TeacherCourse.from(app.repository.teacherCourse("ko")!!) })
    }
}
