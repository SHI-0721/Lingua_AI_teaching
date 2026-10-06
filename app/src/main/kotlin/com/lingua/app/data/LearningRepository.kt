package com.lingua.app.data

import androidx.room.withTransaction
import com.lingua.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId

class LearningRepository(private val db: LearningDatabase, private val source: ContentSource, private val scheduler: ReviewScheduler) {
    val words = db.dao().observeWords()
    val reviews = db.dao().observeReviews()
    val sessions = db.dao().observeSessions()
    val examples = db.dao().observeExamples()
    val daily = db.dao().observeDaily()
    val sources = db.dao().observeSources()
    val difficulties = db.dao().observeDifficulties()
    suspend fun teacherCourse(language: String) = db.dao().teacherCourse(language)
    /** Cursor and learning result commit together: resuming cannot record the same turn twice. */
    suspend fun saveTeacherCourse(course: TeacherCourseEntity, previousRevision: Int?, wordId: String?=null,
        recall: Recall?=null, seconds: Long=0, answer: String="", kind: String="", dailyGoal: Int=10) = db.withTransaction {
        require(db.dao().teacherCourseById(course.id)?.revision==previousRevision) { "课程已更新，请重新打开" }
        if(wordId!=null && recall!=null) {
            val word=requireNotNull(db.dao().word(wordId))
            require(word.languageCode==course.languageCode)
            record(wordId,recall,seconds.coerceIn(1,1800),if(word.studied>0) "review" else "teacher",answer,kind,dailyGoal)
        }
        db.dao().putTeacherCourse(course)
    }
    suspend fun setInLearning(id: String,included: Boolean) = db.dao().setInLearning(id,included)
    suspend fun syncContent(language: String, remote: RemoteContentSource): Pair<Int,Int> {
        val content = remote.fetch(language)
        return applyRemoteContent(content)
    }
    suspend fun applyRemoteContent(content: RemoteContent): Pair<Int,Int> {
        db.withTransaction {
            content.words.forEach { word ->
                val old = db.dao().word(word.id)
                require(old == null || old.languageCode == word.languageCode)
                if (old == null) db.dao().insertWords(listOf(word))
                else db.dao().updateWord(old.copy(text=word.text,meaning=word.meaning,pronunciation=word.pronunciation,partOfSpeech=word.partOfSpeech,collocations=word.collocations,similarWords=word.similarWords,antonyms=word.antonyms))
            }
            db.dao().upsertExamples(content.examples)
            db.dao().upsertSources(content.sources)
            db.dao().upsertDifficulties(content.difficulties)
        }
        return content.words.size to content.examples.size
    }
    suspend fun seed(languageCode: String) = withContext(Dispatchers.IO) {
        val words=source.words(languageCode); val examples=source.examples(languageCode)
        val dictionary=source.dictionary(languageCode)
        db.withTransaction {
            db.dao().insertWords(words); db.dao().insertExamples(examples)
            db.dao().insertWords(dictionary.words); db.dao().upsertSources(dictionary.sources)
            db.dao().insertDifficulties(dictionary.difficulties)
            words.forEach { db.dao().updateMetadata(it.id,it.partOfSpeech,it.similarWords,it.antonyms) }
            if(db.dao().dailyCount()==0) {
                val seen=mutableSetOf<String>()
                db.dao().reviewHistory().forEach { review -> updateDaily(review,seen.add(review.wordId),10) }
            }
        }
    }
    suspend fun record(id: String, result: Recall, seconds: Long, mode: String, answer: String="", errorKind: String="", dailyGoal: Int=10): Long {
        val now = System.currentTimeMillis()
        return db.withTransaction {
            val old = requireNotNull(db.dao().word(id))
            require(old.inLearning) { "单词已移出学习，请重新开始练习" }
            val progress = scheduler.schedule(old.progress(),result,now)
            db.dao().updateWord(old.updated(progress,result))
            val review=ReviewEntity(wordId=id,languageCode=old.languageCode,result=result.name,time=now,seconds=seconds,mode=mode,suppliedAnswer=answer,errorKind=errorKind)
            db.dao().insertReview(review)
            db.dao().insertSession(SessionEntity(languageCode=old.languageCode,time=now,seconds=seconds))
            updateDaily(review,old.studied==0,dailyGoal)
            progress.nextReview
        }
    }
    private suspend fun updateDaily(review: ReviewEntity,firstStudy: Boolean,goal: Int) {
        val date=Instant.ofEpochMilli(review.time).atZone(ZoneId.systemDefault()).toLocalDate().toString()
        val target=goal.coerceAtMost(db.dao().learningWordCount(review.languageCode)).coerceAtLeast(1)
        val old=db.dao().daily(date,review.languageCode) ?: DailyEntity(date,review.languageCode,targetWords=target)
        val fresh=db.dao().insertDailyWord(DailyWordEntity(date,review.languageCode,review.wordId))!=-1L
        val learned=old.learnedWords+if(fresh) 1 else 0
        val seconds=old.seconds+review.seconds
        val isTest=review.mode=="test"
        db.dao().putDaily(old.copy(seconds=seconds,studyMinutes=seconds/60.0,newWords=old.newWords+if(firstStudy) 1 else 0,reviewCount=old.reviewCount+if(review.mode in listOf("review","mistakes")) 1 else 0,correctCount=old.correctCount+if(review.result=="KNOW") 1 else 0,wrongCount=old.wrongCount+if(review.result=="AGAIN") 1 else 0,learnedWords=learned,completed=old.completed || learned>=old.targetWords,testCorrect=old.testCorrect+if(isTest && review.result=="KNOW") 1 else 0,testWrong=old.testWrong+if(isTest && review.result=="AGAIN") 1 else 0))
    }
    suspend fun favorite(id: String) = db.withTransaction { db.dao().word(id)?.let { db.dao().updateWord(it.copy(favorite=!it.favorite)) } }
    suspend fun addToReview(id: String) = db.withTransaction { db.dao().word(id)?.let { db.dao().updateWord(it.copy(inLearning=true,studied=it.studied.coerceAtLeast(1),nextReview=System.currentTimeMillis())) } }
    suspend fun markMastered(id: String) = db.withTransaction { db.dao().word(id)?.let { db.dao().updateWord(it.copy(stage=5,unresolved=false,nextReview=System.currentTimeMillis()+30*86_400_000L)) } }
}
