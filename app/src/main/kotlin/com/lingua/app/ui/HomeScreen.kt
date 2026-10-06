package com.lingua.app.ui
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.lingua.app.ui.theme.*
import com.lingua.core.StudyCalendarAnalytics
import java.time.LocalDate
@Composable fun HomeScreen(state: LearningState,onOpen: (String)->Unit,onPractice: (String)->Unit) = Page {
    val today=state.todayDaily()
    val goal=effectiveGoal(state)
    val learned=today?.learnedWords ?: 0
    val remaining=(goal-learned).coerceAtLeast(0)
    val due=state.words.count { it.inLearning && it.studied>0 && it.nextReview<=state.now }
    val streak=StudyCalendarAnalytics.summarize(state.daily.map { it.domain() },LocalDate.now()).currentStreak
    Text("${state.languageName} · 每天进一步",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)
    Text("今天，学一点新的。",style=MaterialTheme.typography.headlineLarge)
    LearningCard(highlight=true) {
        Text("今日学习",style=MaterialTheme.typography.titleLarge)
        Text("今日需要学习：$remaining 个\n今日需要复习：$due 个",style=MaterialTheme.typography.bodyLarge)
        LinearProgressIndicator(progress={ (learned.toFloat()/goal).coerceIn(0f,1f) },modifier=Modifier.fillMaxWidth())
        Text("今日完成率 ${((learned.toFloat()/goal)*100).toInt().coerceAtMost(100)}% · $learned / $goal 个")
        PrimaryAction(if(today?.completed==true) "今日目标已完成 · 继续学习" else "开始今日学习",enabled=!state.loading && state.words.any { it.inLearning }) { onPractice(if(today?.completed==true) "learn" else "today") }
        Text("我的学习词库 ${state.words.count { it.inLearning }} 个 · 新词按初级 → 中级 → 高级 → 未分级安排。",style=MaterialTheme.typography.bodySmall)
        if(state.words.none { it.inLearning }) TextButton(onClick={ onOpen("words") }) { Text("去词典加入学习内容") }
    }
    Row(horizontalArrangement=Arrangement.spacedBy(Space.md)) {
        Metric("连续打卡", "$streak 天",Modifier.weight(1f))
        Metric("今日时长", "${"%.1f".format(today?.studyMinutes ?: 0.0)} 分",Modifier.weight(1f))
    }
    LearningCard {
        Text("你的积累",style=MaterialTheme.typography.titleLarge)
        Text("已掌握 ${state.words.count { it.stage>=4 && !it.unresolved }} 个词 · 当前语言 ${state.languageName}")
        Text("最近 7 天学习 ${state.daily.count { LocalDate.parse(it.date)>=LocalDate.now().minusDays(6) }} 天")
        OutlinedButton(onClick={ onOpen("calendar") },modifier=Modifier.fillMaxWidth()) { Text("查看打卡日历") }
    }
    Row(horizontalArrangement=Arrangement.spacedBy(Space.sm)) {
        OutlinedButton(onClick={ onOpen("learn") },modifier=Modifier.weight(1f)) { Text("学习模式") }
        OutlinedButton(onClick={ onOpen("words") },modifier=Modifier.weight(1f)) { Text("我的词库") }
    }
    listOf("tests" to "测试","mistakes" to "错题本","stats" to "学习统计","settings" to "设置").forEach { (route,title) -> TextButton(onClick={ onOpen(route) },modifier=Modifier.fillMaxWidth()) { Text(title) } }
}
@Composable fun Metric(label: String,value: String,modifier: Modifier=Modifier) { LearningCard(modifier) { Text(value,style=MaterialTheme.typography.headlineSmall); Text(label,style=MaterialTheme.typography.labelLarge) } }
