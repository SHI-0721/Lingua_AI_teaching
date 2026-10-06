package com.lingua.app

import android.app.Application
import androidx.room.Room
import com.lingua.app.data.*
import com.lingua.core.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class)
class TeacherTest {
    private val q=TeachingQuestion("q","w","造句","我去学校。","저는 학교에 가요.","저는 학교에 가요.","에 表示目的地",listOf("학교에 가요."))
    @Test fun gatewayRequiresTlsAndBoundedResponses() {
        assertThrows(IllegalArgumentException::class.java) { BackendTeachingService("http://localhost/v1/teach","a.b.c") }
        assertThrows(IllegalArgumentException::class.java) { BackendTeachingService("https://example.com/v1/teach","sk-model-key") }
        assertEquals(16000,readTeachingResponse(java.io.ByteArrayInputStream(ByteArray(16000){65})).length)
        assertThrows(IllegalArgumentException::class.java) { readTeachingResponse(java.io.ByteArrayInputStream(ByteArray(16001))) }
    }
    @Test fun offlineAcceptsKnownVariantsAndNeverMarksUnmatchedTextWrong()= runBlocking {
        val service=OfflineTeachingService()
        assertEquals(TeachingGrade.CORRECT,service.evaluate("ko",q,"학교에 가요").grade)
        assertEquals(TeachingGrade.UNGRADED,service.evaluate("ko",q,"오늘 학교 가요").grade)
        assertThrows(IllegalArgumentException::class.java) { validatedFeedback(TeachingFeedback("other",TeachingGrade.CORRECT,"ok","hint"),q) }
        assertTrue(adaptiveNewWords(20,0.5,10)<adaptiveNewWords(20,0.95,10))
        assertThrows(IllegalArgumentException::class.java) { adaptiveNewWords(20,Double.NaN,10) }
        Unit
    }
    @Test fun courseRoundTripRetainsRetryMessagesAndCursor() {
        val c=TeacherCourse(language="ko",goal="交流",level="初级",minutes=20,questions=listOf(q),attempts=1,answer="回答",weakIds=listOf("w"),messages=listOf(TeacherMessage("student","回答")),revision=3)
        assertEquals(c,TeacherCourse.from(c.entity()))
    }
    @Test fun atomicSaveIsIdempotentAndRemovalRollsBack()= runBlocking {
        val db=Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(),LearningDatabase::class.java).build()
        val repo=LearningRepository(db,object:ContentSource { override suspend fun words(languageCode:String)=emptyList<WordEntity>() },IntervalReviewScheduler())
        try {
            db.dao().insertWords(listOf(WordEntity("w","ko","basic","학교","学校","","","","")))
            val c=TeacherCourse(language="ko",goal="交流",level="初级",minutes=20,questions=listOf(q))
            repo.saveTeacherCourse(c.entity(),null)
            val next=c.copy(index=1,planned=true,revision=1)
            repo.saveTeacherCourse(next.entity(),0,"w",Recall.AGAIN,8,"wrong","grammar")
            assertEquals(1,repo.reviews.first().size)
            assertTrue(db.dao().word("w")!!.unresolved)
            try { repo.saveTeacherCourse(next.entity(),0,"w",Recall.AGAIN,8);fail("Duplicate turn accepted") } catch(expected:IllegalArgumentException) { }
            assertEquals(1,repo.reviews.first().size)
            repo.setInLearning("w",false)
            try { repo.saveTeacherCourse(next.copy(revision=2).entity(),1,"w",Recall.KNOW,8);fail("Removed word accepted") } catch(expected:IllegalArgumentException) { }
            assertEquals(1,repo.teacherCourse("ko")!!.revision)
            assertEquals(1,repo.reviews.first().size)
        } finally { db.close() }
    }
    @Test fun v4MigrationKeepsHistoryAndCourseSurvivesReopen()= runBlocking {
        val context=RuntimeEnvironment.getApplication();val name="teacher-v4.db"
        val schema=JSONObject(javaClass.getResource("/com.lingua.app.data.LearningDatabase/4.json")!!.readText()).getJSONObject("database")
        context.openOrCreateDatabase(name,0,null).use { sql ->
            val entities=schema.getJSONArray("entities")
            for(i in 0 until entities.length()) {
                val e=entities.getJSONObject(i)
                sql.execSQL(e.getString("createSql").replace("\${TABLE_NAME}",e.getString("tableName")))
                val indexes=e.optJSONArray("indices") ?: org.json.JSONArray()
                for(j in 0 until indexes.length()) sql.execSQL(indexes.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}",e.getString("tableName")))
            }
            sql.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            sql.execSQL("INSERT INTO room_master_table VALUES (42,'${schema.getString("identityHash") }')")
            sql.execSQL("INSERT INTO words(id,languageCode,lessonId,text,meaning,pronunciation,example,translation,collocations,stage,streak,studied,correct,errors,lastReview,nextReview,unresolved,partOfSpeech,similarWords,antonyms,favorite,inLearning) VALUES ('w','ko','basic','학교','学校','','','','',2,1,3,1,2,1,900,1,'','','',1,0)")
            sql.execSQL("INSERT INTO reviews VALUES(1,'w','ko','AGAIN',100,9,'learn','','')")
            sql.version=4
        }
        var db=Room.databaseBuilder(context,LearningDatabase::class.java,name).addMigrations(MIGRATION_4_5).build()
        try {
            assertEquals(900L,db.dao().word("w")!!.nextReview);assertFalse(db.dao().word("w")!!.inLearning)
            assertEquals(1,db.dao().observeReviews().first().size)
            val c=TeacherCourse(language="ko",goal="交流",level="初级",minutes=20,questions=listOf(q),attempts=1)
            db.dao().putTeacherCourse(c.entity());db.close()
            db=Room.databaseBuilder(context,LearningDatabase::class.java,name).build()
            assertEquals(c,TeacherCourse.from(db.dao().teacherCourse("ko")!!))
        } finally { db.close();context.deleteDatabase(name) }
    }
    @Test fun lessonUsesSeparateSentencesAndModuleGrammar() {
        val words=listOf(WordEntity("w","ko","basic","학교","学校","","","",""),WordEntity("f","ko","basic","친구","朋友","","","",""))
        val examples=listOf(ExampleEntity("s","ko","w","학교에 가요.","我去学校。"),ExampleEntity("t","ko","f","친구를 만나요.","我见朋友。"))
        val questions=teacherQuestions(words,examples,KoreanModule())
        assertTrue(questions.any { it.kind=="中译目标语言" });assertTrue(questions.any { it.kind=="目标语言译中" })
        assertTrue(questions.any { it.reference=="는" });assertTrue(questions.any { it.reference=="를" })
        val english=object:LanguageModule by KoreanModule() { override val language=Language("en","英语",true);override val grammar=object:GrammarProvider { override fun introductoryGrammar()=emptyList<Grammar>() } }
        assertEquals(1,teacherQuestions(listOf(words[0].copy(languageCode="en",text="school")),emptyList(),english).size)
    }
}
