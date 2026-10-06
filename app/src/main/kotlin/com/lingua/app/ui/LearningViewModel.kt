package com.lingua.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lingua.app.LinguaApplication
import com.lingua.app.data.*
import com.lingua.core.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.time.*

data class LearningState(val words: List<WordEntity> = emptyList(), val reviews: List<ReviewEntity> = emptyList(), val settings: Settings = Settings(), val error: String? = null, val loading: Boolean = true, val now: Long=System.currentTimeMillis(),val examples: List<ExampleEntity> = emptyList(),val daily: List<DailyEntity> = emptyList(),val languageName: String="",val sources: List<ContentAttributionEntity> = emptyList(),val difficulties: List<DifficultyTag> = emptyList())
data class PracticeState(val mode: String="learn", val queue: List<String> = emptyList(), val index: Int=0, val revealed: Boolean=false, val answer: String="", val exercise: Exercise?=null, val saving: Boolean=false, val startedAt: Long=System.currentTimeMillis(), val lastDue: Long?=null, val correctAnswer: Boolean?=null,val sentence: ExampleEntity?=null,val question: String="",val expected: String="",val evaluation: AnswerEvaluation?=null) { val complete get() = index >= queue.size }
class LearningViewModel(private val app: LinguaApplication) : ViewModel() {
    private val error = MutableStateFlow<String?>(null)
    private val loading = MutableStateFlow(true)
    private val clock = flow { while(true) { emit(System.currentTimeMillis()); delay(30_000) } }
    val state = combine(app.repository.words,app.repository.reviews,app.settings.settings,error) { words,reviews,settings,problem -> LearningState(words.filter { it.languageCode==settings.language },reviews.filter { it.languageCode==settings.language },settings,problem,false,languageName=app.registry.module(settings.language).language.name) }.combine(app.repository.examples) { s,examples -> s.copy(examples=examples.filter { it.languageCode==s.settings.language }) }.combine(app.repository.daily) { s,daily -> s.copy(daily=daily.filter { it.languageCode==s.settings.language }) }.combine(app.repository.sources) { s,sources -> s.copy(sources=sources) }.combine(app.repository.difficulties) { s,tags -> s.copy(difficulties=tags.map { it.domain() }) }.combine(loading) { s,l -> s.copy(loading=l || (s.words.isEmpty() && s.error==null)) }.combine(clock) { s,time -> s.copy(now=time) }.stateIn(viewModelScope,SharingStarted.Eagerly,LearningState())
    private val _practice = MutableStateFlow(PracticeState())
    val practice = _practice.asStateFlow()
    val languages get() = app.registry.languages
    private val _syncMessage = MutableStateFlow("")
    val syncMessage = _syncMessage.asStateFlow()
    private val _syncing = MutableStateFlow(false)
    val syncing = _syncing.asStateFlow()
    fun syncContent() {
        if(_syncing.value || loading.value) return
        _syncing.value=true
        viewModelScope.launch {
            try {
                val result=app.repository.syncContent(state.value.settings.language, RemoteContentSource(com.lingua.app.BuildConfig.SUPABASE_URL,com.lingua.app.BuildConfig.SUPABASE_PUBLISHABLE_KEY))
                _syncMessage.value="同步完成：${result.first} 个单词、${result.second} 个例句（${java.time.LocalTime.now().withNano(0)}）"
            } catch(e: kotlinx.coroutines.CancellationException) { throw e }
            catch(e: Exception) { _syncMessage.value="同步失败，本地词库仍可使用。请检查网络、项目地址与读取权限。" }
            finally { _syncing.value=false }
        }
    }
    init { viewModelScope.launch { try { app.registry.languages.filter { it.available }.forEach { app.repository.seed(it.code) } } catch(e: Exception) { error.value="词库加载失败：${e.message}" } finally { loading.value=false } } }
    fun localeTag() = app.registry.module(state.value.settings.language).pronunciation.localeTag
    fun favorite(id: String) = action { app.repository.favorite(id) }
    fun setInLearning(id: String,included: Boolean) = action { app.repository.setInLearning(id,included) }
    fun addToReview(id: String) = action { app.repository.addToReview(id) }
    fun markMastered(id: String) = action { app.repository.markMastered(id) }
    private fun action(block: suspend ()->Unit) { viewModelScope.launch { try { block() } catch(e: Exception) { error.value="操作失败：${e.message}" } } }
    fun selectLanguage(code: String) { viewModelScope.launch { app.settings.setLanguage(code) } }
    fun setTheme(theme: String) { viewModelScope.launch { app.settings.setTheme(theme) } }
    fun setGoal(goal: Int) { viewModelScope.launch { app.settings.setGoal(goal) } }
    fun start(mode: String, type: ExerciseType?=null) {
        if(_practice.value.saving || state.value.loading) return
        val sentenceIds=state.value.examples.filter { it.sentence.isNotBlank() && it.translation.isNotBlank() }.map { it.vocabularyId }.toSet()
        val needsSentence=mode in listOf("toChinese","toTarget","sentence","listenSentence") || type==ExerciseType.SENTENCE
        val words=state.value.words.filter { it.inLearning && (!needsSentence || it.id in sentenceIds) }
        val now=System.currentTimeMillis()
        val limit=if(mode=="today") (effectiveGoal(state.value)-(state.value.todayDaily()?.learnedWords ?: 0)).coerceAtLeast(0) else state.value.settings.dailyGoal
        val queue=learningQueue(words,state.value.difficulties,mode,now,limit,
            state.value.reviews.filter { it.time.localDate()==LocalDate.now() }.map { it.wordId }.toSet())
        selectedType=type
        _practice.value=prepare(PracticeState(mode=mode,queue=queue.map { it.id }),queue.firstOrNull())
    }
    private var selectedType: ExerciseType?=null
    private fun prepare(p: PracticeState,word: WordEntity?): PracticeState {
        if(word==null) return p
        val sentence=state.value.examples.filter { it.vocabularyId==word.id }.randomOrNull()
        val exercise=if(p.mode=="test") app.registry.module(state.value.settings.language).exercises.create(word.vocabulary(sentence?.domain()),state.value.words.map { it.vocabulary() },selectedType ?: ExerciseType.entries.random()) else null
        val reverse=p.mode=="toTarget"
        val sentenceMode=p.mode in listOf("toChinese","toTarget","sentence","listenSentence")
        val question=if(sentenceMode && sentence!=null) { if(reverse) sentence.translation else sentence.sentence } else if(reverse) word.meaning else word.text
        val expected=if(p.mode=="test") exercise?.expected.orEmpty() else if(reverse) sentence?.sentence ?: word.text else if(sentenceMode) sentence?.translation ?: word.meaning else word.meaning
        return p.copy(sentence=sentence,exercise=exercise,question=question,expected=expected)
    }
    fun answer(value: String) { _practice.update { it.copy(answer=value) } }
    fun reveal() {
        val p=_practice.value
        _practice.update { it.copy(revealed=true) }
        if(p.answer.isNotBlank()) viewModelScope.launch { try { val result=evaluate(p); if(_practice.value.queue==p.queue && _practice.value.index==p.index) _practice.update { it.copy(evaluation=result) } } catch(e: Exception) { error.value="答案评估失败：${e.message}" } }
    }
    private suspend fun evaluate(p: PracticeState) = app.answerEvaluator.evaluate(EvaluationRequest(p.answer,p.expected,state.value.settings.language,features=p.sentence?.grammarNotes?.split("；").orEmpty()))
    fun submitTest() {
        val p=_practice.value
        if(p.revealed || p.answer.isBlank() || p.saving) return
        val ex=p.exercise ?: return
        _practice.update { it.copy(saving=true) }
        viewModelScope.launch {
            try {
                val evaluation=evaluate(p)
                _practice.update { it.copy(saving=false,evaluation=evaluation) }
                save(if(evaluation.correct) Recall.KNOW else Recall.AGAIN,advance=false,errorKind=ex.type.name)
            } catch(e: Exception) { error.value="答案评估失败：${e.message}"; _practice.update { it.copy(saving=false) } }
        }
    }
    fun rate(result: Recall) { if(_practice.value.revealed) save(result,true) }
    private fun save(result: Recall, advance: Boolean, errorKind: String="") {
        val p=_practice.value
        if(p.saving || p.complete) return
        _practice.update { it.copy(saving=true) }
        viewModelScope.launch {
            try {
                val due=app.repository.record(p.queue[p.index],result,((System.currentTimeMillis()-p.startedAt)/1000).coerceIn(1,1800),p.mode,p.answer,errorKind.ifBlank { p.mode },state.value.settings.dailyGoal)
                _practice.update { it.copy(saving=false,revealed=true,lastDue=due,correctAnswer=result==Recall.KNOW) }
                if(advance) next()
            } catch(e: Exception) { error.value="保存失败：${e.message}"; _practice.update { it.copy(saving=false) } }
        }
    }
    fun next() {
        val p=_practice.value
        if(p.saving || !p.revealed) return
        val index=p.index+1
        val word=p.queue.getOrNull(index)?.let { id -> state.value.words.find { it.id==id } }
        _practice.value=prepare(p.copy(index=index,revealed=false,answer="",exercise=null,startedAt=System.currentTimeMillis(),correctAnswer=null,evaluation=null),word)
    }
}
fun Long.localDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()
fun LearningState.todayDaily() = daily.firstOrNull { it.date==LocalDate.now().toString() }
fun effectiveGoal(state: LearningState) = state.todayDaily()?.targetWords ?: state.settings.dailyGoal.coerceAtMost(state.words.count { it.inLearning }).coerceAtLeast(1)
fun LearningState.streak(): Int {
    val dates=reviews.map { it.time.localDate() }.toSet()
    var day=if(LocalDate.now() in dates) LocalDate.now() else LocalDate.now().minusDays(1)
    var count=0
    while(day in dates) { count++; day=day.minusDays(1) }
    return count
}
