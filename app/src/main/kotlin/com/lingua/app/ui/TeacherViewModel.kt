package com.lingua.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lingua.app.LinguaApplication
import com.lingua.app.data.*
import com.lingua.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class TeacherUiState(val course: TeacherCourse?=null,val busy: Boolean=false,val notice: String="离线教学：参考答案匹配 + 自评，不判断自由表达自然度",val problem: String?=null)
class TeacherViewModel(private val app: LinguaApplication,initialService: TeachingService=OfflineTeachingService()): ViewModel() {
    private val mutable=MutableStateFlow(TeacherUiState())
    val state=mutable.asStateFlow()
    private var service: TeachingService=initialService
    fun connect(endpoint: String,token: String) {
        try { service=BackendTeachingService(endpoint,token);mutable.update { it.copy(notice="联网教学已配置：只发送当前练习与答案；学习记录由本地程序管理",problem=null) } }
        catch(e: Exception) { mutable.update { it.copy(problem="需要 HTTPS 教学端点和有效的短期用户访问令牌（不是模型密钥）") } }
    }
    fun disconnect() { service=OfflineTeachingService();mutable.update { it.copy(notice="已切换离线教学",problem=null) } }
    fun load(language: String) = act {
        val saved=app.repository.teacherCourse(language)
        mutable.update { it.copy(course=saved?.let(TeacherCourse::from)?.copy(startedAt=System.currentTimeMillis()),problem=null) }
    }
    fun start(s: LearningState,goal: String,level: String,minutes: Int) = act {
        require(goal.isNotBlank() && goal.length<=120 && minutes in 5..120)
        val words=learningQueue(s.words,s.difficulties,"learn",s.now,100)
        require(words.isNotEmpty()) { "请先把单词加入我的学习词库" }
        val questions=teacherQuestions(words,s.examples,app.registry.module(s.settings.language)).filter { it.kind=="单词回忆" }.take(3)
        val course=TeacherCourse(language=s.settings.language,goal=goal,level=level,minutes=minutes,questions=questions,diagnosticCount=questions.size,
            messages=listOf(TeacherMessage("teacher","目标：$goal。先用 ${questions.size} 道题测基础，不提前公布答案。随后按表现调整今天的新词与复习。"),TeacherMessage("teacher",questions.first().prompt)))
        app.repository.saveTeacherCourse(course.entity(),null)
        mutable.update { it.copy(course=course) }
    }
    private suspend fun persist(next: TeacherCourse,previous: TeacherCourse,recall: Recall?=null,s: LearningState?=null) {
        val updated=next.copy(revision=previous.revision+1)
        app.repository.saveTeacherCourse(updated.entity(),previous.revision,if(recall!=null) previous.question?.vocabularyId else null,recall,
            (System.currentTimeMillis()-previous.startedAt)/1000,previous.answer,previous.question?.kind.orEmpty(),s?.settings?.dailyGoal ?: 10)
        mutable.update { it.copy(course=updated) }
    }
    fun submit(answer: String) = act {
        val c=requireNotNull(mutable.value.course); val q=c.question ?: return@act
        if(c.revealed) return@act
        require(answer.isNotBlank() && answer.length<=1000)
        val result=try { validatedFeedback(service.evaluate(c.language,q,answer),q) }
        catch(e: CancellationException) { throw e }
        catch(e: Exception) {
            mutable.update { it.copy(notice="联网评估未完成，已采用离线规则；不会自动计错。可检查网络后重新连接。") }
            OfflineTeachingService().evaluate(c.language,q,answer)
        }
        val revealed=result.grade==TeachingGrade.CORRECT || c.attempts>=1
        // First failed attempt only gets a hint. Never render the provider's explanation before reveal.
        val suggestion=when(result.grade) { TeachingGrade.CORRECT -> "正确";TeachingGrade.PARTIAL -> "部分正确";TeachingGrade.INCORRECT -> "存在错误";TeachingGrade.UNGRADED -> "需自评，尚未判定" }
        val text=if(revealed) "评价：$suggestion。${result.feedback}\n参考表达：${q.reference}\n${q.explanation}\n请选择会 / 模糊 / 不会，确认后才保存学习结果。"
            else "先不要看答案。${if(q.reference.length>=3 && result.hint.contains(q.reference)) "回想关键词和语序，再试一次。" else result.hint}\n也可以查看答案，自行评价。"
        persist(c.copy(answer=answer,attempts=c.attempts+1,revealed=revealed,grade=result.grade,
            messages=c.messages+TeacherMessage("student",answer)+TeacherMessage("teacher",text)),c)
    }
    fun reveal() = act {
        val c=requireNotNull(mutable.value.course);val q=c.question ?: return@act
        if(!c.revealed) persist(c.copy(revealed=true,messages=c.messages+TeacherMessage("teacher","参考表达：${q.reference}\n${q.explanation}\n自由表达可能存在合理变体，请根据理解程度自评。")),c)
    }
    fun hint() = act {
        val c=requireNotNull(mutable.value.course);val q=c.question ?: return@act
        if(!c.revealed) persist(c.copy(messages=c.messages+TeacherMessage("teacher","提示：先辨认题型，再回忆关键词、助词和词尾。允许先猜，不必一次答对。")),c)
    }
    fun rate(recall: Recall,s: LearningState) = act {
        val c=requireNotNull(mutable.value.course);if(!c.revealed || c.complete) return@act
        val q=requireNotNull(c.question)
        val adjusted=if(recall==Recall.KNOW && c.attempts>1) Recall.FUZZY else recall
        var next=c.copy(index=c.index+1,attempts=0,revealed=false,answer="",grade=TeachingGrade.UNGRADED,startedAt=System.currentTimeMillis(),
            correct=c.correct+if(adjusted==Recall.KNOW) 1 else 0,partial=c.partial+if(adjusted==Recall.FUZZY) 1 else 0,wrong=c.wrong+if(adjusted==Recall.AGAIN) 1 else 0,
            weakIds=if(adjusted!=Recall.KNOW) (c.weakIds+q.vocabularyId).distinct() else c.weakIds)
        if(!next.planned && next.index>=next.diagnosticCount) {
            val measured=(next.correct+next.partial*0.5)/next.diagnosticCount
            val history=s.reviews.take(20);val prior=if(history.isEmpty()) measured else history.count { it.result=="KNOW" }.toDouble()/history.size
            val accuracy=(measured+prior)/2
            val limit=adaptiveNewWords(c.minutes,accuracy,s.settings.dailyGoal)
            val active=s.words.filter { it.inLearning }
            val urgent=learningQueue(active,s.difficulties,"review",s.now,100)+active.filter { it.unresolved || it.id in next.weakIds }
            val tags=s.difficulties.groupBy { it.vocabularyId }
            val ceiling=when(c.level) { "中级" -> 1; "高级" -> 2; else -> 0 }
            val fresh=learningQueue(active,s.difficulties,"learn",s.now,100).filter {
                val rank=difficultyRank(dictionaryLevel(tags[it.id].orEmpty()))
                it.studied==0 && it.id !in c.questions.map { q->q.vocabularyId } && (rank<=ceiling || rank==3)
            }.take(limit)
            val old=active.filter { it.studied>0 }.take(3)
            // ponytail: cap each chat lesson at 24 questions; remaining due items stay in the normal review queue.
            val lesson=teacherQuestions((urgent+fresh+old).distinctBy { it.id },s.examples,app.registry.module(c.language)).sortedBy {
                when { c.goal.contains("语法") -> if(it.kind.contains("语法")) 0 else 1
                    c.goal.contains("翻译") -> if(it.kind.contains("译")) 0 else 1
                    else -> 0 }
            }.take(24)
            next=next.copy(planned=true,questions=c.questions+lesson,messages=next.messages+TeacherMessage("teacher","基础测评完成。目标：${c.goal}；自报水平：${c.level}。按测评与历史，本轮最多加入 $limit 个新词，优先到期复习和薄弱点。计划 ${c.minutes} 分钟；剩余到期内容仍在今日复习。"))
        }
        if(next.index>=next.questions.size && !next.reinforced) {
            val reinforcement=next.weakIds.mapNotNull { id -> next.questions.firstOrNull { it.vocabularyId==id }?.let { it.copy(id=it.id+"-reinforce",prompt="错题强化：${it.prompt}") } }
            next=next.copy(reinforced=true,questions=next.questions+reinforcement)
        }
        val text=next.question?.prompt ?: "课程完成：会 ${next.correct}，模糊 ${next.partial}，不会 ${next.wrong}。薄弱词 ${next.weakIds.size} 个。已按你的确认保存复习时间；可以在今日复习和错题本继续训练。"
        persist(next.copy(messages=next.messages+TeacherMessage("teacher",text)),c,adjusted,s)
    }
    private fun act(block: suspend ()->Unit) {
        if(mutable.value.busy) return
        mutable.update { it.copy(busy=true,problem=null) }
        viewModelScope.launch {
            try { block() } catch(e: CancellationException) { throw e }
            catch(e: Exception) { mutable.update { it.copy(problem=if(e is IllegalArgumentException) e.message else "操作未保存，请重新打开课程后重试") } }
            finally { mutable.update { it.copy(busy=false) } }
        }
    }
}
