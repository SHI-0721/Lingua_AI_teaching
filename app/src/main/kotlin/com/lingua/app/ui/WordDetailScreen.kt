package com.lingua.app.ui
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import com.lingua.app.ui.theme.*
import com.lingua.core.*
@Composable fun VocabularyScreen(state: LearningState,onDetail: (String)->Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var favorites by rememberSaveable { mutableStateOf(false) }
    var learningOnly by rememberSaveable { mutableStateOf(false) }
    var level by rememberSaveable { mutableStateOf("all") }
    val sources=remember(state.sources) { state.sources.associateBy { it.vocabularyId } }
    val labels=remember(state.difficulties) { state.difficulties.groupBy { it.vocabularyId } }
    val words=remember(state.words,query,favorites,learningOnly,level,state.difficulties) {
        searchVocabulary(state.words,query,favorites,learningOnly,level,state.difficulties)
    }
    LazyColumn(Modifier.fillMaxSize().imePadding().testTag("dictionary-list"),contentPadding=PaddingValues(Space.lg),verticalArrangement=Arrangement.spacedBy(Space.md)) {
        item {
            Column(verticalArrangement=Arrangement.spacedBy(Space.sm)) {
                Text("我的词库 · ${state.words.size} 个词",style=MaterialTheme.typography.headlineMedium)
                Text("按单词或中文释义搜索；相同单词的不同义项分别保存。")
                OutlinedTextField(query,{ query=it },label={ Text("搜索单词 / 中文释义") },modifier=Modifier.fillMaxWidth(),singleLine=true)
                FilterChip(favorites,{ favorites=!favorites },label={ Text("只看收藏") })
                FilterChip(learningOnly,{ learningOnly=!learningOnly },label={ Text("我的学习词库") })
                Text("词典难度（不是 TOPIK 等级）")
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(Space.xs)) {
                    listOf("all","beginner","intermediate","advanced","unclassified").forEach { value ->
                        FilterChip(level==value,{ level=value },label={ Text(if(value=="all") "全部" else difficultyName(value)) })
                    }
                }
                Text("找到 ${words.size} 个义项")
            }
        }
        if(words.isEmpty()) item { Text("没有匹配内容，试试其他关键词。") }
        items(words,key={ it.id }) { word -> LearningCard {
            Text("${if(word.favorite) "★ " else ""}${word.text}",style=MaterialTheme.typography.titleLarge)
            Text(word.meaning)
            Text("${difficultyName(dictionaryLevel(labels[word.id].orEmpty()))} · ${if(word.inLearning) "已加入学习" else "仅词典"}",style=MaterialTheme.typography.labelMedium)
            sources[word.id]?.let { Text("官方词典 · 义项 ${it.senseId}",style=MaterialTheme.typography.labelSmall) }
            TextButton(onClick={ onDetail(word.id) }) { Text("${if(word.id in sources) "词典详情" else "查看详情"} · ${word.text}") }
        } }
    }
}
@Composable fun WordDetailScreen(id: String,state: LearningState,vm: LearningViewModel) = Page {
    val word=state.words.firstOrNull { it.id==id }
    if(word==null) Text("未找到单词。") else {
        val locale=vm.localeTag()
        LearningCard(highlight=true) {
            Text(word.text,style=MaterialTheme.typography.headlineLarge)
            Text(word.meaning,style=MaterialTheme.typography.titleLarge)
            Text("${word.partOfSpeech.ifBlank { "未标注词性" }} · [${word.pronunciation}]")
            SpeakButton(word.text,locale,"单词发音")
            OutlinedButton(onClick={ vm.favorite(id) }) { Text(if(word.favorite) "★ 已收藏" else "☆ 收藏") }
            PrimaryAction(if(word.inLearning) "移出我的学习词库" else "加入我的学习词库") { vm.setInLearning(id,!word.inLearning) }
            Text("移出仅暂停安排练习，熟练度与学习历史仍保留。",style=MaterialTheme.typography.bodySmall)
        }
        LearningCard {
            Text("难度分级",style=MaterialTheme.typography.titleLarge)
            val tags=state.difficulties.filter { it.vocabularyId==id }
            Text("词典：${difficultyName(dictionaryLevel(tags))}")
            Text("词典初 / 中 / 高级不等于 TOPIK 1–6 级。")
            tags.forEach { tag ->
                Text("${tag.framework} · ${if(tag.framework=="krdict") difficultyName(tag.level) else tag.level}")
                Text("来源：${tag.source}\n可信度：${(tag.confidence*100).toInt()}% · 版本：${tag.version}",style=MaterialTheme.typography.bodySmall)
                if(tag.framework=="topik") Text("TOPIK 参考标签，非官方认证。",style=MaterialTheme.typography.bodySmall)
            }
            if(tags.none { it.framework=="topik" }) Text("TOPIK：待评估（未自动推测等级）")
        }
        state.examples.filter { it.vocabularyId==id }.forEach { sentence -> LearningCard {
            Text(sentence.sentence,style=MaterialTheme.typography.titleLarge)
            Text(sentence.translation)
            SpeakButton(sentence.sentence,locale,"例句发音")
            if(sentence.keywords.isNotBlank()) Text(sentence.keywords.replace(";","\n").replace("="," = "))
            if(sentence.grammarNotes.isNotBlank()) Text(sentence.grammarNotes,style=MaterialTheme.typography.bodySmall)
        } }
        LearningCard {
            Text("表达关联",style=MaterialTheme.typography.titleLarge)
            Text("常见搭配：${word.collocations}")
            Text("相似词：${word.similarWords.ifBlank { "暂无" }}")
            Text("反义词：${word.antonyms.ifBlank { "暂无" }}")
        }
        LearningCard {
            Text("学习状态",style=MaterialTheme.typography.titleLarge)
            Text(if(word.unresolved) "待强化" else if(word.stage>=4) "已掌握" else if(word.studied==0) "未学习" else "学习中")
            LinearProgressIndicator(progress={ word.stage/5f },modifier=Modifier.fillMaxWidth())
            Text("熟练度 ${word.stage}/5 · 正确率 ${if(word.studied==0) 0 else word.correct*100/word.studied}%")
            Text("累计学习 ${word.studied} 次 · 错误 ${word.errors} 次")
            Text("下次复习：${formatTime(word.nextReview)}")
            PrimaryAction("加入复习") { vm.addToReview(id) }
            OutlinedButton(onClick={ vm.markMastered(id) },modifier=Modifier.fillMaxWidth()) { Text("标记已掌握") }
        }
        SpeechNotice()
        state.sources.firstOrNull { it.vocabularyId==id }?.let { source ->
            val uriHandler=LocalUriHandler.current
            LearningCard {
                Text("内容来源",style=MaterialTheme.typography.titleLarge)
                Text(source.sourceName)
                Text("词条 ${source.entryId} · 义项 ${source.senseId}")
                Text(source.attribution,style=MaterialTheme.typography.bodySmall)
                Text("许可：${source.license}")
                Text("未提供官方中文例句翻译的内容，不自动生成句子训练。")
                if(source.sourceUrl.startsWith("https://")) TextButton(onClick={ uriHandler.openUri(source.sourceUrl) }) { Text("查看原始词条") }
                if(source.license=="CC BY-SA 2.0 KR") TextButton(onClick={ uriHandler.openUri("https://creativecommons.org/licenses/by-sa/2.0/kr/") }) { Text("查看许可条款") }
            }
        }
    }
}
