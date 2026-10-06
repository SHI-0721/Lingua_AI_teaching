package com.lingua.app.ui
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.lingua.app.ui.theme.*
import com.lingua.core.StudyCalendarAnalytics
import java.time.*
@Composable fun CalendarScreen(state: LearningState) = Page {
    var monthText by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    var selectedText by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    val month=YearMonth.parse(monthText)
    val summary=StudyCalendarAnalytics.summarize(state.daily.map { it.domain() },LocalDate.now())
    LearningCard(highlight=true) {
        Text("连续打卡 ${summary.currentStreak} 天",style=MaterialTheme.typography.headlineMedium)
        Text("最长 ${summary.longestStreak} 天 · 总学习 ${summary.totalStudyDays} 天")
        Text("本月学习 ${summary.monthStudyDays} 天 · 本月完成率 ${summary.monthCompletionRate}%")
    }
    LearningCard {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
            TextButton(onClick={ monthText=month.minusMonths(1).toString() }) { Text("上月") }
            Text("${month.year} 年 ${month.monthValue} 月",style=MaterialTheme.typography.titleMedium)
            TextButton(onClick={ monthText=month.plusMonths(1).toString() }) { Text("下月") }
        }
        Row { listOf("一","二","三","四","五","六","日").forEach { Box(Modifier.weight(1f),contentAlignment=Alignment.Center) { Text(it,style=MaterialTheme.typography.labelMedium) } } }
        val offset=month.atDay(1).dayOfWeek.value-1
        val cells=((offset+month.lengthOfMonth()+6)/7)*7
        (0 until cells step 7).forEach { start -> Row(Modifier.fillMaxWidth()) {
            (start until start+7).forEach { cell ->
                val number=cell-offset+1
                if(number !in 1..month.lengthOfMonth()) Spacer(Modifier.weight(1f).height(52.dp))
                else {
                    val date=month.atDay(number)
                    val record=state.daily.firstOrNull { it.date==date.toString() }
                    val selected=date.toString()==selectedText
                    Surface(modifier=Modifier.weight(1f).height(52.dp).semantics { contentDescription="$date ${if(record?.completed==true) "已完成" else if(record!=null) "学习过但未完成" else "未学习"}" }.clickable { selectedText=date.toString() },shape=MaterialTheme.shapes.small,color=if(selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface) {
                        Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) { Text(number.toString(),style=MaterialTheme.typography.labelMedium); Text(if(record?.completed==true) "✓" else if(record!=null) "●" else " ",color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.labelMedium) }
                    }
                }
            }
        } }
        Text("✓ 完成目标   ● 学习过   空白 未学习",style=MaterialTheme.typography.bodySmall)
    }
    val record=state.daily.firstOrNull { it.date==selectedText }
    LearningCard {
        Text(selectedText,style=MaterialTheme.typography.titleLarge)
        if(record==null) Text("当天没有学习记录。")
        else {
            Text("学习时间：${"%.1f".format(record.studyMinutes)} 分钟")
            Text("学习单词 ${record.learnedWords} 个 · 新词 ${record.newWords} 个 · 复习 ${record.reviewCount} 次")
            val tests=record.testCorrect+record.testWrong
            Text("测试正确率：${if(tests==0) "暂无测试" else "${record.testCorrect*100/tests}%"}")
            val wrongWords=state.reviews.filter { it.time.localDate().toString()==selectedText && it.result=="AGAIN" }.map { it.wordId }.distinct().size
            Text("错题数量 $wrongWords 个 · 错误次数 ${record.wrongCount} · 正确次数 ${record.correctCount}")
            Text(if(record.completed) "当天目标已完成" else "当天进度 ${record.learnedWords}/${record.targetWords}")
        }
    }
    Text("本月完成率 = 本月已完成目标的天数 / 本月至今天数。打卡目标在当天首次练习时固定。")
}
