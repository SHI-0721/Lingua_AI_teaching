package com.lingua.app.ui
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import com.lingua.app.ui.theme.LearningCard
@Composable fun MistakesScreen(state: LearningState,onTrain: ()->Unit) = Page {
    val words=state.words.filter { it.inLearning && it.unresolved }.sortedByDescending { it.errors }
    Text("优先练习容易忘的内容",style=MaterialTheme.typography.titleLarge)
    Text("答错自动加入，答对后移出；历史错误仍保留。")
    Button(onClick=onTrain,enabled=words.isNotEmpty()) { Text("重新训练 · ${words.size}") }
    if(words.isEmpty()) Text("目前没有未解决错题。")
    words.forEach { word -> LearningCard {
        Text("${word.text} · ${word.meaning}",style=MaterialTheme.typography.titleLarge)
        Text("累计错误 ${word.errors} 次 · 下次复习 ${formatTime(word.nextReview)}")
        state.reviews.firstOrNull { it.wordId==word.id && it.result=="AGAIN" }?.let { Text("最近错误：${it.errorKind.ifBlank { "主动回忆" }}\n你的答案：${it.suppliedAnswer.ifBlank { "自评不会" }}") }
    } }
}
