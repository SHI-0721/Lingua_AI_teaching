package com.lingua.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import com.lingua.core.*

@Entity(tableName="words")
data class WordEntity(@PrimaryKey val id: String, val languageCode: String, val lessonId: String, val text: String, val meaning: String, val pronunciation: String, val example: String, val translation: String, val collocations: String, val stage: Int=0, val streak: Int=0, val studied: Int=0, val correct: Int=0, val errors: Int=0, val lastReview: Long=0, val nextReview: Long=0, val unresolved: Boolean=false, val partOfSpeech: String="", val similarWords: String="", val antonyms: String="", val favorite: Boolean=false, @ColumnInfo(defaultValue="0") val inLearning: Boolean=true) {
    // Legacy example/translation columns are kept only for nondestructive v1 migration.
    fun vocabulary(sentence: ExampleSentence?=null) = Vocabulary(id,languageCode,lessonId,text,meaning,pronunciation,sentence ?: ExampleSentence(example,translation),collocations)
    fun progress() = Progress(stage,streak,studied,correct,errors,lastReview,nextReview)
    fun updated(p: Progress, result: Recall) = copy(stage=p.stage,streak=p.streak,studied=p.studied,correct=p.correct,errors=p.errors,lastReview=p.lastReview,nextReview=p.nextReview,unresolved=when(result) { Recall.AGAIN -> true; Recall.KNOW -> false; Recall.FUZZY -> unresolved })
}
@Entity(tableName="reviews")
data class ReviewEntity(@PrimaryKey(autoGenerate=true) val id: Long=0, val wordId: String, val languageCode: String, val result: String, val time: Long, val seconds: Long, val mode: String, val suppliedAnswer: String, val errorKind: String)
@Entity(tableName="sessions")
data class SessionEntity(@PrimaryKey(autoGenerate=true) val id: Long=0, val languageCode: String, val time: Long, val seconds: Long, val count: Int=1)
@Entity(tableName="examples", indices=[Index("vocabularyId")])
data class ExampleEntity(@PrimaryKey val id: String, val languageCode: String, val vocabularyId: String, val sentence: String, val translation: String, val difficulty: Int=1, val audioSource: String?=null, val keywords: String="", val grammarNotes: String="") {
    fun domain() = ExampleSentence(sentence,translation,id,languageCode,vocabularyId,difficulty,audioSource,keywords.split(";").mapNotNull { it.split("=").takeIf { pair -> pair.size==2 }?.let { pair -> pair[0] to pair[1] } })
}
@Entity(tableName="daily", primaryKeys=["date","languageCode"])
data class DailyEntity(val date: String, val languageCode: String, val studyMinutes: Double=0.0, val newWords: Int=0, val reviewCount: Int=0, val correctCount: Int=0, val wrongCount: Int=0, val completed: Boolean=false, val learnedWords: Int=0, val targetWords: Int=10, val seconds: Long=0, val testCorrect: Int=0, val testWrong: Int=0) {
    fun domain() = StudyDailyRecord(date,languageCode,studyMinutes,newWords,reviewCount,correctCount,wrongCount,completed,learnedWords,targetWords)
}
@Entity(tableName="daily_words",primaryKeys=["date","languageCode","wordId"])
data class DailyWordEntity(val date: String,val languageCode: String,val wordId: String)
@Entity(tableName="content_sources")
data class ContentAttributionEntity(@PrimaryKey val vocabularyId: String,val sourceName: String,val sourceUrl: String,val entryId: String,val senseId: String,val license: String,val attribution: String)
@Dao interface LearningDao {
    @Query("SELECT * FROM teacher_courses WHERE languageCode=:language ORDER BY updatedAt DESC LIMIT 1") suspend fun teacherCourse(language: String): TeacherCourseEntity?
    @Query("SELECT * FROM teacher_courses WHERE id=:id") suspend fun teacherCourseById(id: String): TeacherCourseEntity?
    @Upsert suspend fun putTeacherCourse(course: TeacherCourseEntity)
    @Query("SELECT * FROM difficulty_tags") fun observeDifficulties(): Flow<List<DifficultyTagEntity>>
    @Insert(onConflict=OnConflictStrategy.IGNORE) suspend fun insertDifficulties(tags: List<DifficultyTagEntity>)
    @Upsert suspend fun upsertDifficulties(tags: List<DifficultyTagEntity>)
    @Query("UPDATE words SET inLearning=:included WHERE id=:id") suspend fun setInLearning(id: String,included: Boolean)
    @Query("SELECT * FROM content_sources") fun observeSources(): Flow<List<ContentAttributionEntity>>
    @Upsert suspend fun upsertSources(sources: List<ContentAttributionEntity>)
    @Query("SELECT * FROM words ORDER BY id") fun observeWords(): Flow<List<WordEntity>>
    @Query("SELECT * FROM reviews ORDER BY time DESC") fun observeReviews(): Flow<List<ReviewEntity>>
    @Query("SELECT * FROM sessions ORDER BY time DESC") fun observeSessions(): Flow<List<SessionEntity>>
    @Query("SELECT * FROM words WHERE id = :id") suspend fun word(id: String): WordEntity?
    @Insert(onConflict=OnConflictStrategy.IGNORE) suspend fun insertWords(words: List<WordEntity>)
    @Update suspend fun updateWord(word: WordEntity)
    @Insert suspend fun insertReview(review: ReviewEntity)
    @Insert suspend fun insertSession(session: SessionEntity)
    @Query("SELECT * FROM examples") fun observeExamples(): Flow<List<ExampleEntity>>
    @Insert(onConflict=OnConflictStrategy.IGNORE) suspend fun insertExamples(examples: List<ExampleEntity>)
    @Upsert suspend fun upsertExamples(examples: List<ExampleEntity>)
    @Query("SELECT * FROM daily ORDER BY date DESC") fun observeDaily(): Flow<List<DailyEntity>>
    @Query("SELECT * FROM daily WHERE date=:date AND languageCode=:language") suspend fun daily(date: String,language: String): DailyEntity?
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun putDaily(record: DailyEntity)
    @Insert(onConflict=OnConflictStrategy.IGNORE) suspend fun insertDailyWord(word: DailyWordEntity): Long
    @Query("SELECT COUNT(*) FROM words WHERE languageCode=:language") suspend fun wordCount(language: String): Int
    @Query("SELECT COUNT(*) FROM words WHERE languageCode=:language AND inLearning=1") suspend fun learningWordCount(language: String): Int
    @Query("SELECT * FROM reviews ORDER BY time") suspend fun reviewHistory(): List<ReviewEntity>
    @Query("SELECT COUNT(*) FROM daily") suspend fun dailyCount(): Int
    @Query("UPDATE words SET partOfSpeech=:part,similarWords=:similar,antonyms=:antonyms WHERE id=:id AND partOfSpeech=''") suspend fun updateMetadata(id: String,part: String,similar: String,antonyms: String)
}
@Entity(tableName="difficulty_tags",primaryKeys=["vocabularyId","framework"])
data class DifficultyTagEntity(val vocabularyId: String,val framework: String,val level: String,
    val source: String,val confidence: Double,val version: String) {
    fun domain() = DifficultyTag(vocabularyId,framework,level,source,confidence,version)
}
@Entity(tableName="teacher_courses")
data class TeacherCourseEntity(@PrimaryKey val id: String,val languageCode: String,val payload: String,val revision: Int,val updatedAt: Long)
@Database(entities=[WordEntity::class, ReviewEntity::class, SessionEntity::class,ExampleEntity::class,DailyEntity::class,DailyWordEntity::class,ContentAttributionEntity::class,DifficultyTagEntity::class,TeacherCourseEntity::class], version=5, exportSchema=true)
abstract class LearningDatabase : RoomDatabase() { abstract fun dao(): LearningDao }
