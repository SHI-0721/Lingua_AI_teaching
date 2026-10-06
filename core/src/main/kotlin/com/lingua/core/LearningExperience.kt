package com.lingua.core

import java.time.LocalDate

data class StudyDailyRecord(val date: String, val languageCode: String, val studyMinutes: Double, val newWords: Int, val reviewCount: Int, val correctCount: Int, val wrongCount: Int, val completed: Boolean, val learnedWords: Int, val targetWords: Int)
data class CalendarSummary(val currentStreak: Int, val longestStreak: Int, val monthStudyDays: Int, val monthCompletionRate: Int, val totalStudyDays: Int)
object StudyCalendarAnalytics {
    fun summarize(records: List<StudyDailyRecord>, today: LocalDate): CalendarSummary {
        val completed=records.filter { it.completed }.map { LocalDate.parse(it.date) }.toSortedSet()
        var day=if(today in completed) today else today.minusDays(1)
        var current=0
        while(day in completed) { current++; day=day.minusDays(1) }
        var longest=0; var run=0; var previous: LocalDate?=null
        completed.forEach { date -> run=if(previous?.plusDays(1)==date) run+1 else 1; longest=maxOf(longest,run); previous=date }
        val month=records.filter { LocalDate.parse(it.date).let { d -> d.year==today.year && d.month==today.month && !d.isAfter(today) } }
        return CalendarSummary(current,longest,month.size,month.count { it.completed }*100/today.dayOfMonth,records.size)
    }
}
data class EvaluationRequest(val answer: String, val expected: String, val languageCode: String, val aliases: List<String> = emptyList(), val features: List<String> = emptyList())
data class AnswerEvaluation(val correct: Boolean, val feedback: String, val details: List<Pair<String,String>>)
interface AnswerEvaluator { suspend fun evaluate(request: EvaluationRequest): AnswerEvaluation }
/** Literal/reference comparison only; does not pretend to infer grammatical correctness. */
class RuleAnswerEvaluator(private val tokenizer: Tokenizer=StandardTokenizer()) : AnswerEvaluator {
    override suspend fun evaluate(request: EvaluationRequest): AnswerEvaluation {
        val exact=(listOf(request.expected)+request.aliases).any { tokenizer.normalize(it)==tokenizer.normalize(request.answer) }
        val expectedWords=request.expected.split(Regex("\\s+")).filter { it.isNotBlank() }
        val missing=expectedWords.filter { !tokenizer.normalize(request.answer).contains(tokenizer.normalize(it)) }
        return AnswerEvaluation(exact,if(exact) "与参考答案一致" else "与参考答案有差异；可参考提示自评", listOf(
            "单词 / 拼写" to if(exact) "规范化后匹配" else "对照：${missing.joinToString("、").ifBlank { request.expected }}",
            "助词 / 语法" to request.features.joinToString("；").ifBlank { "请对照参考句；规则评分不判断自然度" }
        ))
    }
}
