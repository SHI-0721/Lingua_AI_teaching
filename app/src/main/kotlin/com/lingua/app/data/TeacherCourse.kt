package com.lingua.app.data

import com.lingua.core.*
import org.json.JSONArray
import org.json.JSONObject

data class TeacherMessage(val role: String,val text: String)
data class TeacherCourse(val id: String=java.util.UUID.randomUUID().toString(),val language: String,
    val goal: String,val level: String,val minutes: Int,val questions: List<TeachingQuestion>,val index: Int=0,
    val diagnosticCount: Int=3,val planned: Boolean=false,val attempts: Int=0,val revealed: Boolean=false,
    val answer: String="",val grade: TeachingGrade=TeachingGrade.UNGRADED,val correct: Int=0,val partial: Int=0,
    val wrong: Int=0,val weakIds: List<String> = emptyList(),val reinforced: Boolean=false,
    val messages: List<TeacherMessage> = emptyList(),val revision: Int=0,val startedAt: Long=System.currentTimeMillis()) {
    val question get()=questions.getOrNull(index)
    val complete get()=index>=questions.size && planned
    fun entity()=TeacherCourseEntity(id,language,toJson().toString(),revision,System.currentTimeMillis())
    private fun toJson()=JSONObject().put("id",id).put("language",language).put("goal",goal).put("level",level).put("minutes",minutes)
        .put("questions",JSONArray(questions.map { q -> JSONObject().put("id",q.id).put("word",q.vocabularyId).put("kind",q.kind).put("prompt",q.prompt).put("reference",q.reference).put("speech",q.speech).put("explanation",q.explanation).put("aliases",JSONArray(q.aliases)) }))
        .put("index",index).put("diagnosticCount",diagnosticCount).put("planned",planned).put("attempts",attempts).put("revealed",revealed).put("answer",answer).put("grade",grade.name)
        .put("correct",correct).put("partial",partial).put("wrong",wrong).put("weakIds",JSONArray(weakIds)).put("reinforced",reinforced).put("revision",revision).put("startedAt",startedAt)
        .put("messages",JSONArray(messages.map { JSONObject().put("role",it.role).put("text",it.text) }))
    companion object {
        fun from(entity: TeacherCourseEntity): TeacherCourse {
            val j=JSONObject(entity.payload)
            fun strings(a:JSONArray)=(0 until a.length()).map { a.getString(it) }
            val qs=j.getJSONArray("questions")
            val ms=j.getJSONArray("messages")
            return TeacherCourse(j.getString("id"),j.getString("language"),j.getString("goal"),j.getString("level"),j.getInt("minutes"),
                (0 until qs.length()).map { i -> qs.getJSONObject(i).let { TeachingQuestion(it.getString("id"),it.getString("word"),it.getString("kind"),it.getString("prompt"),it.getString("reference"),it.getString("speech"),it.getString("explanation"),strings(it.getJSONArray("aliases"))) } },
                j.getInt("index"),j.getInt("diagnosticCount"),j.getBoolean("planned"),j.getInt("attempts"),j.getBoolean("revealed"),j.getString("answer"),TeachingGrade.valueOf(j.getString("grade")),j.getInt("correct"),j.getInt("partial"),j.getInt("wrong"),strings(j.getJSONArray("weakIds")),j.getBoolean("reinforced"),
                (0 until ms.length()).map { i -> ms.getJSONObject(i).let { TeacherMessage(it.getString("role"),it.getString("text")) } },entity.revision,j.getLong("startedAt"))
        }
    }
}

fun teacherQuestions(words: List<WordEntity>,examples: List<ExampleEntity>,module: LanguageModule): List<TeachingQuestion> = words.flatMapIndexed { index,w ->
    val meaningAliases=w.meaning.substringBefore('；').split('，','、','/').map { it.trim() }.filter { it.isNotEmpty() }
    val base=TeachingQuestion("${w.id}-word",w.id,"单词回忆","请写出 ${w.text} 的中文意思。",w.meaning,w.text,"词典释义：${w.meaning}",meaningAliases)
    val sentence=examples.filter { it.vocabularyId==w.id && it.sentence.isNotBlank() && it.translation.isNotBlank() }.randomOrNull()
    val translation=sentence?.let { TeachingQuestion("${w.id}-sentence",w.id,if(index%2==0) "中译目标语言" else "目标语言译中",
        "请翻译：${if(index%2==0) it.translation else it.sentence}",if(index%2==0) it.sentence else it.translation,it.sentence,
        "关键词：${it.keywords.replace(';','；')}\n${it.grammarNotes}") }
    val grammar=module.grammar.introductoryGrammar().filter { it.anchor==w.text && it.prompt.isNotBlank() }.map {
        TeachingQuestion(it.id,w.id,"助词 / 语法 / 造句",it.prompt,it.reference,it.reference,it.explanation,it.aliases)
    }
    listOfNotNull(base,translation)+grammar
}
