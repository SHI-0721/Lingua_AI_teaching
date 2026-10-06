package com.lingua.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LearningFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun click(text: String) { compose.onNodeWithText(text,substring=true).performScrollTo().performClick() }
    @Test fun learningAndTestingPersistMistakesAndStatistics() {
        compose.waitUntil(15000) { compose.onAllNodesWithText("开始学习").fetchSemanticsNodes().any { it.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled).not() } }
        click("开始学习")
        click("开始今日学习")
        compose.onNodeWithText("查看答案").assertExists()
        compose.onNodeWithText("常见搭配",substring=true).assertDoesNotExist()
        click("查看答案")
        compose.onNodeWithText("常见搭配",substring=true).assertExists()
        click("不会")
        compose.waitUntil(10000) { compose.onAllNodesWithText("查看答案").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("返回",substring=false).performClick()
        click("错题本")
        compose.onAllNodesWithText("累计错误",substring=true).onFirst().assertExists()
        compose.onNodeWithText("返回",substring=false).performClick()
        click("测试")
        click("拼写")
        compose.onNode(hasSetTextAction()).performTextInput("wrong-answer")
        click("提交答案")
        compose.waitUntil(10000) { compose.onAllNodesWithText("已记录错题").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("参考答案：",substring=true).assertExists()
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        compose.onNodeWithText("返回",substring=false).performClick()
        compose.onNodeWithText("返回",substring=false).performClick()
        click("学习统计")
        compose.onNodeWithText("最近 7 天").assertExists()
        compose.onNodeWithText("累计正确率",substring=true).assertExists()
    }
}
