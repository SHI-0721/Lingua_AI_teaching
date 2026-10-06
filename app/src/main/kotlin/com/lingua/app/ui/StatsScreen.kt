package com.lingua.app.ui
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.lingua.app.ui.theme.*
import com.lingua.core.StudyCalendarAnalytics
import java.time.LocalDate
@Composable fun StatsScreen(state: LearningState) = Page {
    val today=LocalDate.now()
    val summary=StudyCalendarAnalytics.summarize(state.daily.map { it.domain() },today)
    val weekStart=today.minusDays((today.dayOfWeek.value-1).toLong())
    val week=state.daily.filter { LocalDate.parse(it.date)>=weekStart && LocalDate.parse(it.date)<=today }
    Text("看见每一步积累",style=MaterialTheme.typography.headlineLarge)
    LearningCard(highlight=true) {
        Text("${state.languageName} · 学习总览",style=MaterialTheme.typography.titleLarge)
        Text("连续学习 ${state.streak()} 天 · 连续打卡 ${summary.currentStreak} 天")
        Text("累计学习时间 ${"%.1f".format(state.daily.sumOf { it.studyMinutes })} 分钟",style=MaterialTheme.typography.headlineSmall)
        Text("累计正确率：${if(state.reviews.isEmpty()) 0 else state.reviews.count { it.result=="KNOW" }*100/state.reviews.size}%")
    }
    Row(horizontalArrangement=Arrangement.spacedBy(Space.md)) {
        Metric("已掌握",state.words.count { it.stage>=4 && !it.unresolved }.toString(),Modifier.weight(1f))
        Metric("待复习",state.words.count { it.inLearning && it.studied>0 && it.nextReview<=state.now }.toString(),Modifier.weight(1f))
    }
    LearningCard {
        Text("本周",style=MaterialTheme.typography.titleLarge)
        Text("学习 ${week.size} 天 · ${"%.1f".format(week.sumOf { it.studyMinutes })} 分钟")
        Text("新增 ${week.sumOf { it.newWords }} 个词 · 复习 ${week.sumOf { it.reviewCount }} 次")
        Text("当前错题 ${state.words.count { it.unresolved }} 个")
    }
    Text("最近 7 天",style=MaterialTheme.typography.titleLarge)
    LearningCard {
        (6 downTo 0).forEach { offset ->
            val date=today.minusDays(offset.toLong())
            val record=state.daily.firstOrNull { it.date==date.toString() }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                Text("${date.monthValue}/${date.dayOfMonth}")
                Text("${record?.learnedWords ?: 0} 个 · ${"%.1f".format(record?.studyMinutes ?: 0.0)} 分")
            }
            LinearProgressIndicator(progress={ if(record==null) 0f else (record.learnedWords.toFloat()/record.targetWords).coerceIn(0f,1f) },modifier=Modifier.fillMaxWidth())
        }
    }
    Text("最长打卡 ${summary.longestStreak} 天 · 总学习 ${summary.totalStudyDays} 天")
    Text("学习时间来自已提交练习的作答时间；掌握标准为复习阶段至少 14 天且无未解决错题。",style=MaterialTheme.typography.bodySmall)
}
