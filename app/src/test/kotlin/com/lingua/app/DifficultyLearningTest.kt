package com.lingua.app

import android.app.Application
import androidx.room.Room
import com.lingua.app.data.*
import com.lingua.app.ui.*
import com.lingua.core.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class)
class DifficultyLearningTest {
    @Test fun actualCloudSnapshotAndPublicDownloadKeepPersonalHistory() = runBlocking {
        val folder=System.getenv("LINGUA_CLOUD_VERIFICATION")
        org.junit.Assume.assumeTrue(folder != null)
        val root=java.io.File(folder!!)
        val config=java.util.Properties().apply { java.io.File(root,"../../supabase.local.properties").inputStream().use { load(it) } }
        val publicContent=RemoteContentSource(config.getProperty("SUPABASE_URL"),config.getProperty("SUPABASE_PUBLISHABLE_KEY")).fetch("ko")
        assertEquals(8,publicContent.words.size)
        assertTrue(publicContent.difficulties.isEmpty()) // Official senses remain unpublished.
        val rows=JSONObject(java.io.File(root,"after.json").readText()).getJSONArray("rows")
        val snapshot=RemoteContentSource.parse("ko",rows,JSONArray())
        assertEquals(6003,snapshot.difficulties.size)
        assertTrue(snapshot.difficulties.all { it.framework=="krdict" && it.level=="beginner" })
        val db=Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(),LearningDatabase::class.java).build()
        try {
            val repo=LearningRepository(db,object:ContentSource { override suspend fun words(languageCode:String)=emptyList<WordEntity>() },IntervalReviewScheduler())
            val original=snapshot.words.first { it.id==snapshot.difficulties.first().vocabularyId }.copy(inLearning=false,studied=9,correct=6,errors=3,nextReview=123456,favorite=true)
            db.dao().insertWords(listOf(original))
            repo.applyRemoteContent(snapshot); repo.applyRemoteContent(snapshot); repo.applyRemoteContent(publicContent)
            assertEquals(original,db.dao().word(original.id))
            assertEquals(6003,repo.difficulties.first().size)
        } finally { db.close() }
    }
    private fun word(id: String)=WordEntity(id,"ko","basic",id,id,"","","","")
    private fun tag(id: String,level: String)=DifficultyTag(id,"krdict",level,"official",1.0,"v1")

    @Test fun queueOrdersNewSensesButNeverPostponesUrgentReviews() {
        val words=listOf(word("advanced"),word("unknown"),word("beginner"),word("middle"),
            word("removed").copy(inLearning=false),word("due").copy(studied=2,nextReview=10),
            word("mistake").copy(studied=1,unresolved=true,errors=3,nextReview=10000))
        val tags=listOf(tag("advanced","advanced"),tag("beginner","beginner"),tag("middle","intermediate"),
            DifficultyTag("unknown","topik","1","estimate",0.5,"v1"))
        val queue=learningQueue(words,tags,"learn",100,10).map { it.id }
        assertEquals(listOf("due","mistake","beginner","middle","advanced","unknown"),queue)
        assertEquals(listOf("due","mistake"),learningQueue(words,tags,"today",100,0,setOf("due")).map { it.id })
        assertEquals(listOf("due"),learningQueue(words,tags,"review",100,10).map { it.id })
        assertEquals(listOf("middle"),searchVocabulary(words,"",false,false,"intermediate",tags).map { it.id })
        assertFalse(searchVocabulary(words,"",false,true).any { it.id=="removed" })
        assertEquals("unclassified",dictionaryLevel(tags.filter { it.vocabularyId=="unknown" }))
    }

    @Test fun enrollmentRemovalRejoinAndRemoteSyncKeepHistory() = runBlocking {
        val db=Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(),LearningDatabase::class.java).build()
        val source=object: ContentSource { override suspend fun words(languageCode:String)=emptyList<WordEntity>() }
        val repo=LearningRepository(db,source,IntervalReviewScheduler())
        try {
            db.dao().insertWords(listOf(word("a")))
            val due=repo.record("a",Recall.AGAIN,12,"learn")
            repo.setInLearning("a",false)
            val content=RemoteContentSource.parse("ko",JSONArray("""[{"id":"a","language_code":"ko","word":"학교","meaning":"学校","difficulty_tags":[{"framework":"krdict","level":"intermediate","source":"official","confidence":1.0,"version":"v1"}]}]"""),JSONArray())
            repo.applyRemoteContent(content)
            val removed=db.dao().word("a")!!
            assertFalse(removed.inLearning); assertEquals(due,removed.nextReview); assertEquals(1,removed.errors)
            try { repo.record("a",Recall.KNOW,2,"learn"); fail("Removed word must not record an answer") }
            catch(expected: IllegalArgumentException) { assertTrue(expected.message!!.contains("移出学习")) }
            assertEquals(1,repo.reviews.first().size); assertEquals(12L,repo.sessions.first().single().seconds)
            assertEquals("intermediate",repo.difficulties.first().single().level)
            repo.setInLearning("a",true)
            assertEquals(removed.copy(inLearning=true),db.dao().word("a"))
            repo.applyRemoteContent(content.copy(words=listOf(word("new").copy(inLearning=false)),difficulties=emptyList()))
            assertFalse(db.dao().word("new")!!.inLearning)
        } finally { db.close() }
    }

    @Test fun migrationV3KeepsLearnedWordsButDoesNotEnrollUntouchedDictionary() = runBlocking {
        val context=RuntimeEnvironment.getApplication()
        val name="difficulty-v3.db"
        val schema=JSONObject(javaClass.getResource("/com.lingua.app.data.LearningDatabase/3.json")!!.readText()).getJSONObject("database")
        context.openOrCreateDatabase(name,0,null).use { sql ->
            val entities=schema.getJSONArray("entities")
            for(i in 0 until entities.length()) {
                val e=entities.getJSONObject(i)
                sql.execSQL(e.getString("createSql").replace("\${TABLE_NAME}",e.getString("tableName")))
                val indexes=e.optJSONArray("indices") ?: JSONArray()
                for(j in 0 until indexes.length()) sql.execSQL(indexes.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}",e.getString("tableName")))
            }
            sql.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            sql.execSQL("INSERT INTO room_master_table VALUES (42,'${schema.getString("identityHash")}')")
            for((id,studied) in listOf("learned" to 3,"untouched" to 0)) sql.execSQL(
                "INSERT INTO words VALUES ('$id','ko','ko-cloud','학교','学校','','','','',2,1,$studied,1,2,1,900,1,'','','',1)")
            sql.execSQL("INSERT INTO reviews VALUES (1,'learned','ko','AGAIN',100,9,'learn','','')")
            sql.execSQL("INSERT INTO daily VALUES ('2026-10-01','ko',2.0,1,2,2,1,0,1,10,120,1,1)")
            sql.version=3
        }
        val db=Room.databaseBuilder(context,LearningDatabase::class.java,name).addMigrations(MIGRATION_3_4,MIGRATION_4_5).build()
        try {
            val learned=db.dao().word("learned")!!
            assertTrue(learned.inLearning); assertTrue(learned.favorite)
            assertEquals(3,learned.studied); assertEquals(900L,learned.nextReview)
            assertFalse(db.dao().word("untouched")!!.inLearning)
            assertEquals(1,db.dao().observeReviews().first().size); assertEquals(120L,db.dao().observeDaily().first().single().seconds)
            assertTrue(db.dao().observeDifficulties().first().isEmpty())
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun rejectsInvalidConfidenceAndDoesNotInventTagsForOldServer() {
        val row="""{"id":"a","language_code":"ko","word":"학교","meaning":"学校"}"""
        assertTrue(RemoteContentSource.parse("ko",JSONArray("[$row]"),JSONArray()).difficulties.isEmpty())
        val invalid=JSONObject(row).put("difficulty_tags",JSONArray("""[{"framework":"krdict","level":"beginner","source":"official","confidence":2,"version":"v1"}]"""))
        assertThrows(IllegalArgumentException::class.java) {
            RemoteContentSource.parse("ko",JSONArray().put(invalid),JSONArray())
        }
    }
}
