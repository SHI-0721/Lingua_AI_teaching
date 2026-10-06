package com.lingua.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lingua.app.LinguaApplication
import com.lingua.app.ui.theme.*
import com.lingua.core.Recall

@Composable fun TeacherScreen(learning: LearningState) {
    val app=LocalContext.current.applicationContext as LinguaApplication
    val vm:TeacherViewModel=viewModel(key="teacher-${learning.settings.language}",factory=object:ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T:ViewModel> create(modelClass:Class<T>)=TeacherViewModel(app) as T
    })
    val ui by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(learning.settings.language) { vm.load(learning.settings.language) }
    SpeechScope { TeacherContent(learning,ui,vm,app.registry.module(learning.settings.language).pronunciation.localeTag) }
}

@Composable fun TeacherContent(learning: LearningState,ui: TeacherUiState,vm:TeacherViewModel,locale: String) {
    var configure by remember { mutableStateOf(false) }
    var endpoint by rememberSaveable { mutableStateOf("") }
    var token by remember { mutableStateOf("") } // Short-lived token is memory-only, not Room, settings or saved state.
    var goal by rememberSaveable { mutableStateOf("日常交流与基础语法") }
    var level by rememberSaveable { mutableStateOf("初级") }
    var minutes by rememberSaveable { mutableStateOf("20") }
    var draft by rememberSaveable { mutableStateOf("") }
    val c=ui.course
    LaunchedEffect(c?.id,c?.index,c?.attempts) { draft="" }
    if(configure) AlertDialog(onDismissRequest={ configure=false;token="" },title={ Text("连接 AI 教学后端") },text={ Column(verticalArrangement=Arrangement.spacedBy(Space.sm)) {
        Text("仅发送当前题目和回答，不上传完整学习历史。需要部署后的 HTTPS 地址和已登录用户的短期访问令牌。不要输入模型密钥或管理员密钥。")
        OutlinedTextField(endpoint,{ endpoint=it },label={ Text("HTTPS 教学端点 /v1/teach") })
        OutlinedTextField(token,{ token=it },label={ Text("短期用户访问令牌") },visualTransformation=PasswordVisualTransformation())
    } },confirmButton={ TextButton(onClick={ vm.connect(endpoint,token);token="";configure=false }) { Text("确认连接") } },dismissButton={ TextButton(onClick={ vm.disconnect();token="";configure=false }) { Text("使用离线教学") } })
    Column(Modifier.fillMaxSize().imePadding().padding(horizontal=Space.lg),verticalArrangement=Arrangement.spacedBy(Space.sm)) {
        Text("${learning.languageName} · AI 老师",style=MaterialTheme.typography.headlineSmall)
        Text(ui.notice,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick={ configure=true },enabled=!ui.busy) { Text("连接设置 / 离线模式") }
        ui.problem?.let { Text(it,color=MaterialTheme.colorScheme.error) }
        if(ui.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if(c==null) {
            Text("先测基础，再安排今天的课程。提交后的对话与进度自动保存在本机。")
            OutlinedTextField(goal,{ if(it.length<=120) goal=it },label={ Text("学习目标（可填 TOPIK 备考，不代表官方分级）") },modifier=Modifier.fillMaxWidth())
            Row(horizontalArrangement=Arrangement.spacedBy(Space.sm)) { listOf("初级","中级","高级").forEach { value -> FilterChip(selected=level==value,onClick={ level=value },label={ Text(value) }) } }
            OutlinedTextField(minutes,{ minutes=it.filter(Char::isDigit).take(3) },label={ Text("本次学习时间：5—120 分钟") },modifier=Modifier.fillMaxWidth())
            PrimaryAction("开始基础测评",!ui.busy && !learning.loading && (minutes.toIntOrNull() ?: 0) in 5..120) { vm.start(learning,goal,level,minutes.toInt()) }
        } else {
            Text(if(c.complete) "课程已完成 · 记录已保存" else "${if(c.planned) "今日课程" else "基础测评"} · ${c.index+1}/${c.questions.size}",style=MaterialTheme.typography.labelLarge)
            LinearProgressIndicator(progress={ c.index.toFloat()/c.questions.size.coerceAtLeast(1) },modifier=Modifier.fillMaxWidth())
            val list=rememberLazyListState()
            LaunchedEffect(c.messages.size) { if(c.messages.isNotEmpty()) list.animateScrollToItem(c.messages.lastIndex) }
            LazyColumn(Modifier.weight(1f).fillMaxWidth(),state=list,verticalArrangement=Arrangement.spacedBy(Space.sm)) {
                itemsIndexed(c.messages) { i,m -> Card(colors=CardDefaults.cardColors(containerColor=if(m.role=="student") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer),modifier=Modifier.fillMaxWidth().padding(start=if(m.role=="student") Space.lg else 0.dp,end=if(m.role=="teacher") Space.sm else 0.dp)) {
                    Column(Modifier.padding(Space.md)) { Text(if(m.role=="teacher") "老师" else "你",style=MaterialTheme.typography.labelSmall);Text(m.text,modifier=Modifier.testTag("teacher-message-$i")) }
                } }
            }
            val q=c.question
            if(q!=null && !c.complete) {
                if(q.speech.isNotBlank() && (c.revealed || q.prompt.contains(q.speech))) SpeakButton(q.speech,locale,"播放原句 / 单词")
                SpeechNotice()
                if(!c.revealed) {
                    OutlinedTextField(draft,{ if(it.length<=1000) draft=it },label={ Text("先回答，再看答案") },modifier=Modifier.fillMaxWidth(),maxLines=3,enabled=!ui.busy)
                    Row(horizontalArrangement=Arrangement.spacedBy(Space.sm)) {
                        Button(onClick={ vm.submit(draft) },enabled=!ui.busy && draft.isNotBlank()) { Text(if(c.attempts==0) "提交回答" else "再次尝试") }
                        TextButton(onClick={ vm.hint() },enabled=!ui.busy) { Text("提示") }
                        TextButton(onClick={ vm.reveal() },enabled=!ui.busy) { Text("查看答案") }
                    }
                } else {
                    Text("确认你的掌握程度后才写入复习记录",style=MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement=Arrangement.spacedBy(Space.sm)) {
                        listOf(Recall.KNOW to "会",Recall.FUZZY to "模糊",Recall.AGAIN to "不会").forEach { (rating,label) ->
                            Button(onClick={ vm.rate(rating,learning) },enabled=!ui.busy) { Text(label) }
                        }
                    }
                }
            } else PrimaryAction("开始新课程",!ui.busy) { vm.start(learning,c.goal,c.level,c.minutes) }
        }
    }
}
