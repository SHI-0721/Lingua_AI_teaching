package com.lingua.app
import android.app.Application
import androidx.room.Room
import com.lingua.app.data.*
import com.lingua.core.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class)
class MigrationAndDailyTest {
    @Test fun v1UpgradePreservesProgressAndMovesLegacySentence() = runBlocking {
        val context=RuntimeEnvironment.getApplication()
        val name="migration-test.db"
        val schema=JSONObject(javaClass.getResource("/com.lingua.app.data.LearningDatabase/1.json")!!.readText()).getJSONObject("database")
        context.openOrCreateDatabase(name,0,null).use { sql ->
            val entities=schema.getJSONArray("entities")
            (0 until entities.length()).forEach { i -> val e=entities.getJSONObject(i); sql.execSQL(e.getString("createSql").replace("\${TABLE_NAME}",e.getString("tableName"))) }
            sql.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            sql.execSQL("INSERT INTO room_master_table VALUES (42, '${schema.getString("identityHash")}')")
            sql.execSQL("INSERT INTO words VALUES ('old','ko','basic','약속','约定','yaksok','내일 약속이 있어요.','明天有约。','약속 시간',2,1,3,1,2,1,900,1)")
            sql.version=1
        }
        val db=Room.databaseBuilder(context,LearningDatabase::class.java,name).addMigrations(MIGRATION_1_2,MIGRATION_2_3,MIGRATION_3_4,MIGRATION_4_5).build()
        try {
            val word=db.dao().word("old")!!
            assertEquals(3,word.studied); assertEquals(2,word.errors); assertEquals(900L,word.nextReview); assertFalse(word.favorite)
            assertEquals("old",db.dao().observeExamples().first().single().vocabularyId)
        } finally { db.close(); context.deleteDatabase(name) }
    }
    @Test fun repeatedWordDoesNotCompleteDailyGoalAndRecordSurvivesReopen() = runBlocking {
        val context=RuntimeEnvironment.getApplication()
        val name="daily-test.db"
        var db=Room.databaseBuilder(context,LearningDatabase::class.java,name).build()
        val source=object: ContentSource { override suspend fun words(languageCode: String)=listOf("a","b").map { WordEntity(it,languageCode,"basic",it,it,it,"sentence","translation","phrase") } }
        try {
            val repo=LearningRepository(db,source,IntervalReviewScheduler()); repo.seed("ko")
            repo.record("a",Recall.KNOW,30,"learn",dailyGoal=2)
            repo.record("a",Recall.AGAIN,30,"test",dailyGoal=1)
            var record=repo.daily.first().single()
            assertEquals(1,record.learnedWords); assertEquals(1,record.newWords); assertFalse(record.completed); assertEquals(2,record.targetWords); assertEquals(1.0,record.studyMinutes,0.001); assertEquals(1,record.testWrong)
            repo.record("b",Recall.KNOW,60,"review",dailyGoal=2)
            record=repo.daily.first().single()
            assertTrue(record.completed); assertEquals(2,record.learnedWords); assertEquals(1,record.reviewCount)
            db.close(); db=Room.databaseBuilder(context,LearningDatabase::class.java,name).build()
            assertTrue(db.dao().observeDaily().first().single().completed)
        } finally { db.close(); context.deleteDatabase(name) }
    }
    @Test fun evaluatorReportsReferenceDifferenceWithoutClaimingGrammarAccuracy() = runBlocking {
        val evaluator=RuleAnswerEvaluator()
        assertTrue(evaluator.evaluate(EvaluationRequest("학교에 가요!","학교에 가요.","ko")).correct)
        val result=evaluator.evaluate(EvaluationRequest("학교 가요","학교에 가요.","ko",features=listOf("에：目的地助词")))
        assertFalse(result.correct); assertTrue(result.details.last().second.contains("目的地"))
    }
}
