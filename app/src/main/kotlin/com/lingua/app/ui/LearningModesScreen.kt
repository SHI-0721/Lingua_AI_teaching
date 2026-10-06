package com.lingua.app.ui
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import com.lingua.app.ui.theme.*
@Composable fun LearningModesScreen(language: String,onStart: (String)->Unit) = Page {
    Text("选择今天的练习",style=MaterialTheme.typography.headlineMedium)
    Text("先回忆，再看答案。所有训练结果都进入同一个复习系统。")
    LearningCard(highlight=true) {
        Text("AI 老师",style=MaterialTheme.typography.titleLarge)
        Text("先测基础，再制定今日计划；提示、重试、讲解与错题强化。支持离线自评。")
        PrimaryAction("进入 AI 老师") { onStart("teacher") }
    }
    val short=language.removeSuffix("语")
    listOf(Triple("learn","单词学习","目标语言单词 → 回忆中文意思"),Triple("toChinese","$short → 中","理解原句，输入或回忆中文"),Triple("toTarget","中 → $short","回忆目标语言表达，对照单词、助词与语法"),Triple("sentence","例句训练","随机例句，避免只记住固定句子"),Triple("test","随机测试","检验新词、旧词与错题"),Triple("mistakes","错题强化","优先解决容易混淆的内容"),Triple("review","今日复习","按计划巩固到期内容"),Triple("listen","听力 · 单词","听发音，回忆意思"),Triple("listenSentence","听力 · 例句","听完整句子，理解意思")).forEach { (mode,title,description) ->
        LearningCard { Text(title,style=MaterialTheme.typography.titleLarge); Text(description); OutlinedButton(onClick={ onStart(mode) },modifier=Modifier.fillMaxWidth()) { Text("进入$title") } }
    }
}
