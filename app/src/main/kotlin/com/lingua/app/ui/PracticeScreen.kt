package com.lingua.app.ui
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.Modifier
import com.lingua.core.*
import com.lingua.app.ui.theme.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
@Composable fun PracticeScreen(state: LearningState,p: PracticeState,vm: LearningViewModel,onFinish: ()->Unit,onDetail: (String)->Unit={}) = Page {
    if(p.complete) {
        LearningCard(highlight=true) {
            Text(if(p.queue.isEmpty()) "暂时没有待练习内容" else "本轮练习完成",style=MaterialTheme.typography.headlineMedium)
            Text("结果已保存。坚持一点点，你会记得更久。")
            p.lastDue?.let { Text("最近一词下次复习：${formatTime(it)}") }
            PrimaryAction("返回首页",onClick=onFinish)
        }
    } else {
        val word=state.words.find { it.id==p.queue[p.index] }
        val locale=vm.localeTag()
        Text("${state.languageName} · ${p.index+1} / ${p.queue.size}",style=MaterialTheme.typography.labelLarge)
        LinearProgressIndicator(progress={ p.index.toFloat()/p.queue.size },modifier=Modifier.fillMaxWidth())
        if(word!=null) {
            val test=p.mode=="test"
            val listening=p.mode in listOf("listen","listenSentence")
            val speechText=if(p.mode=="listen") word.text else p.sentence?.sentence ?: word.text
            LearningCard(highlight=true) {
                Text(if(listening) "听一听，再回忆" else if(test) p.exercise?.prompt.orEmpty() else p.question,style=MaterialTheme.typography.headlineLarge)
                Text(if(p.mode=="toTarget") "试着写出目标语言句子" else "先形成自己的答案，再查看参考。")
                if(listening) SpeakButton(speechText,locale,"播放题目")
                else if(p.mode in listOf("toChinese","sentence")) SpeakButton(speechText,locale,"原句发音")
                if(test && p.exercise?.choices?.isNotEmpty()==true) p.exercise.choices.forEach { choice -> OutlinedButton(onClick={ vm.answer(choice) },enabled=!p.revealed && !p.saving,modifier=Modifier.fillMaxWidth()) { Text((if(p.answer==choice) "✓ " else "")+choice) } }
                else OutlinedTextField(value=p.answer,onValueChange=vm::answer,label={ Text("你的答案${if(test) "" else "（可选）"}") },enabled=!p.revealed && !p.saving,modifier=Modifier.fillMaxWidth())
                if(!p.revealed) PrimaryAction(if(test) "提交答案" else "查看答案",enabled=!p.saving && (!test || p.answer.isNotBlank())) { if(test) vm.submitTest() else vm.reveal() }
            }
            AnimatedVisibility(p.revealed) {
                LearningCard {
                    if(test) Text(if(p.correctAnswer==true) "回答正确" else "已记录错题",color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.titleLarge)
                    Text("参考答案：${p.expected}",style=MaterialTheme.typography.titleLarge)
                    Text(word.text,style=MaterialTheme.typography.headlineMedium)
                    Text(word.meaning)
                    Text("发音：[${word.pronunciation}]")
                    SpeakButton(word.text,locale,"单词发音")
                    p.sentence?.let { sentence ->
                        Text(sentence.sentence,style=MaterialTheme.typography.titleLarge)
                        Text(sentence.translation)
                        SpeakButton(sentence.sentence,locale,"例句发音")
                        if(sentence.keywords.isNotBlank()) Text("关键词解释\n${sentence.keywords.replace(";","\n").replace("="," = ")}")
                    }
                    Text("常见搭配：${word.collocations}")
                    p.evaluation?.let { evaluation -> Text(evaluation.feedback); evaluation.details.forEach { (label,detail) -> Text("$label：$detail",style=MaterialTheme.typography.bodySmall) } }
                    if(test) { p.lastDue?.let { Text("下次复习：${formatTime(it)}") }; PrimaryAction("下一题",!p.saving,vm::next) }
                    else {
                        listOf(Recall.KNOW to "会",Recall.FUZZY to "模糊",Recall.AGAIN to "不会").forEach { (result,label) -> OutlinedButton(onClick={ vm.rate(result) },enabled=!p.saving,modifier=Modifier.fillMaxWidth()) { Text(label) } }
                    }
                    TextButton(onClick={ onDetail(word.id) }) { Text("查看单词详情") }
                }
            }
            SpeechNotice()
        }
    }
}
fun formatTime(time: Long): String = if(time==0L) "尚未安排" else Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))
