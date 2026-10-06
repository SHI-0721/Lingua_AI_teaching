package com.lingua.app

import android.app.Application
import androidx.room.Room
import com.lingua.app.data.*
import com.lingua.app.ui.searchVocabulary
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
class DictionaryAcceptanceTest {
    @Test fun officialAssetSeedsIdempotentlyWithAttributionAndWithoutForgedTranslations() = runBlocking {
        val context=RuntimeEnvironment.getApplication()
        val source=JsonAssetContentSource(context,LanguageRegistry())
        val dictionary=source.dictionary("ko")
        assertEquals(6003,dictionary.words.size)
        assertEquals(6003,dictionary.sources.size)
        assertTrue(dictionary.examples.isEmpty())
        assertEquals(6003,dictionary.difficulties.size)
        assertTrue(dictionary.words.none { it.inLearning })
        assertTrue(dictionary.difficulties.all { it.framework=="krdict" && it.level=="beginner" })
        assertTrue(dictionary.sources.all { it.entryId.isNotBlank() && it.senseId.isNotBlank() && it.license=="CC BY-SA 2.0 KR" })
        val db=Room.inMemoryDatabaseBuilder(context,LearningDatabase::class.java).build()
        try {
            val repo=LearningRepository(db,source,IntervalReviewScheduler())
            repo.seed("ko")
            val id=dictionary.words.first().id
            repo.setInLearning(id,true)
            repo.record(id,Recall.AGAIN,7,"learn")
            repo.setInLearning(id,false)
            val due=db.dao().word(id)!!.nextReview
            repo.seed("ko")
            assertEquals(6011,repo.words.first().size)
            assertEquals(15,repo.examples.first().size)
            assertEquals(6003,repo.sources.first().size)
            assertEquals(6003,repo.difficulties.first().size)
            assertEquals(8,repo.words.first().count { it.inLearning })
            assertEquals(due,db.dao().word(id)!!.nextReview)
            assertEquals(1,db.dao().word(id)!!.errors)
        } finally { db.close() }
    }

    @Test fun searchFindsKoreanChineseAndFavorites() {
        val word=WordEntity("a","ko","basic","약속","约定","","","","",favorite=true)
        val other=word.copy(id="b",text="친구",meaning="朋友",favorite=false)
        assertEquals(listOf(word),searchVocabulary(listOf(other,word)," 약 속 ",false))
        assertEquals(listOf(word),searchVocabulary(listOf(other,word),"约定",false))
        assertEquals(listOf(word),searchVocabulary(listOf(other,word),"",true))
        assertTrue(searchVocabulary(listOf(word),"不存在",false).isEmpty())
    }

    @Test fun v2UpgradeRetainsFavoriteReviewAndDailyRecords() = runBlocking {
        val context=RuntimeEnvironment.getApplication()
        val name="dictionary-v2-upgrade.db"
        context.deleteDatabase(name)
        val schema=JSONObject(javaClass.getResource("/com.lingua.app.data.LearningDatabase/2.json")!!.readText()).getJSONObject("database")
        context.openOrCreateDatabase(name,0,null).use { sql ->
            val entities=schema.getJSONArray("entities")
            for(i in 0 until entities.length()) {
                val e=entities.getJSONObject(i)
                sql.execSQL(e.getString("createSql").replace("\${TABLE_NAME}",e.getString("tableName")))
                val indices=e.optJSONArray("indices") ?: org.json.JSONArray()
                for(j in 0 until indices.length()) sql.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}",e.getString("tableName")))
            }
            sql.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            sql.execSQL("INSERT INTO room_master_table VALUES (42,'${schema.getString("identityHash")}')")
            sql.execSQL("INSERT INTO words (id,languageCode,lessonId,text,meaning,pronunciation,example,translation,collocations,stage,streak,studied,correct,errors,lastReview,nextReview,unresolved,partOfSpeech,similarWords,antonyms,favorite) VALUES ('old','ko','basic','약속','约定','','','','',2,1,3,1,2,1,900,1,'','','',1)")
            sql.execSQL("INSERT INTO daily VALUES ('2026-10-01','ko',2.0,1,2,2,1,0,1,10,120,1,1)")
            sql.version=2
        }
        val db=Room.databaseBuilder(context,LearningDatabase::class.java,name).addMigrations(MIGRATION_2_3,MIGRATION_3_4,MIGRATION_4_5).build()
        try {
            val word=db.dao().word("old")!!
            assertTrue(word.favorite); assertEquals(3,word.studied); assertEquals(900L,word.nextReview)
            assertEquals(120L,db.dao().observeDaily().first().single().seconds)
            assertTrue(db.dao().observeSources().first().isEmpty())
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
