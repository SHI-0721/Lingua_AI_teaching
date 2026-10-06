package com.lingua.app.ui
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.*
import com.lingua.core.*
import com.lingua.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun LinguaApp(vm: LearningViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val practiceState by vm.practice.collectAsStateWithLifecycle()
    val dark=when(state.settings.theme) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
    LinguaTheme(dark) {
        val nav=rememberNavController()
        val entry by nav.currentBackStackEntryAsState()
        val route=entry?.destination?.route ?: "languages"
        fun open(destination: String) { nav.navigate(destination) { launchSingleTop=true } }
        fun practice(mode: String,type: ExerciseType?=null) { vm.start(mode,type); open("practice") }
        Scaffold(topBar={ TopAppBar(title={ Text(when(route) { "languages" -> "Lingua"; "home" -> "学习首页"; "learn" -> "学习"; "practice" -> "主动回忆"; "tests" -> "测试中心"; "mistakes" -> "错题本"; "stats" -> "学习统计"; "calendar" -> "学习打卡日历"; "words" -> "我的词库"; "word/{id}" -> "单词详情"; "settings" -> "设置"; else -> "Lingua" }) },navigationIcon={ if(route!="languages") TextButton(onClick={ nav.popBackStack() }) { Text("返回") } },colors=TopAppBarDefaults.topAppBarColors(containerColor=MaterialTheme.colorScheme.background)) },bottomBar={
            if(route in listOf("home","learn","calendar","stats")) NavigationBar(containerColor=MaterialTheme.colorScheme.surface) {
                listOf(Triple("home","首页",Icons.Rounded.Home),Triple("learn","学习",Icons.Rounded.School),Triple("calendar","打卡",Icons.Rounded.CalendarMonth),Triple("stats","统计",Icons.Rounded.BarChart)).forEach { (target,label,icon) -> NavigationBarItem(selected=route==target,onClick={ nav.navigate(target) { popUpTo("home"); launchSingleTop=true } },icon={ Icon(icon,contentDescription=null) },label={ Text(label) },colors=NavigationBarItemDefaults.colors(indicatorColor=MaterialTheme.colorScheme.primaryContainer,selectedIconColor=MaterialTheme.colorScheme.primary)) }
            }
        }) { padding ->
            Column(Modifier.padding(padding).fillMaxSize()) {
                state.error?.let { Text(it,color=MaterialTheme.colorScheme.error,modifier=Modifier.padding(Space.md)) }
                if(state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                NavHost(nav,startDestination="languages",modifier=Modifier.weight(1f)) {
                    composable("languages") { Page {
                        Text("为记住而学习。",style=MaterialTheme.typography.headlineLarge)
                        Text("选择你的语言，开始今天的练习。",style=MaterialTheme.typography.bodyLarge)
                        vm.languages.forEach { language -> LearningCard(highlight=language.available) {
                            Text(language.name,style=MaterialTheme.typography.titleLarge)
                            Text(if(language.available) "主动回忆 · 翻译训练 · 每日打卡" else "新的语言旅程，敬请期待")
                            PrimaryAction(if(language.available) "开始学习" else "即将开放",language.available && !state.loading) { vm.selectLanguage(language.code); open("home") }
                        } }
                    } }
                    composable("home") { HomeScreen(state,onOpen={ open(it) },onPractice={ practice(it) }) }
                    composable("learn") { LearningModesScreen(state.languageName) { if(it=="teacher") open("teacher") else practice(it) } }
                    composable("teacher") { TeacherScreen(state) }
                    composable("practice") { SpeechScope { PracticeScreen(state,practiceState,vm,onFinish={ nav.popBackStack() },onDetail={ open("word/$it") }) } }
                    composable("tests") { Page {
                        Text("让回忆变得更可靠",style=MaterialTheme.typography.headlineMedium)
                        Text("混合新词、旧词和错题。规则评分对照标准答案，标点和空格不影响结果。")
                        PrimaryAction("随机混合测试") { practice("test") }
                        ExerciseType.entries.forEach { type -> OutlinedButton(onClick={ practice("test",type) },modifier=Modifier.fillMaxWidth()) { Text(type.label()) } }
                    } }
                    composable("mistakes") { MistakesScreen(state,onTrain={ practice("mistakes") }) }
                    composable("calendar") { CalendarScreen(state) }
                    composable("words") { VocabularyScreen(state,onDetail={ open("word/$it") }) }
                    composable("word/{id}") { backStack -> SpeechScope { WordDetailScreen(backStack.arguments?.getString("id").orEmpty(),state,vm) } }
                    composable("stats") { StatsScreen(state) }
                    composable("settings") { SettingsScreen(state.settings,vm) }
                }
            }
        }
    }
}
@Composable fun Page(content: @Composable ColumnScope.()->Unit) { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(Space.lg),verticalArrangement=Arrangement.spacedBy(Space.lg),content=content) }
fun ExerciseType.label() = when(this) { ExerciseType.TO_MEANING -> "目标语言 → 中文"; ExerciseType.TO_TARGET -> "中文 → 目标语言"; ExerciseType.CHOICE -> "单词选择题"; ExerciseType.CLOZE -> "填空"; ExerciseType.SPELLING -> "拼写"; ExerciseType.SENTENCE -> "句子翻译" }
