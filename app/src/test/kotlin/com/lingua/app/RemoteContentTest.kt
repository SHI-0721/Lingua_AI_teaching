package com.lingua.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.lingua.app.data.*
import com.lingua.core.IntervalReviewScheduler
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class RemoteContentTest {
    private val words = """[{"id":"ko-03","language_code":"ko","word":"학교","meaning":"学校（更新）","pronunciation":"hak-kkyo","collocations":["학교 생활"]}]"""
    private val examples = """[{"id":"ko-03-a","language_code":"ko","vocabulary_id":"ko-03","sentence":"학교에 가요.","translation":"我去学校。","keywords":{"학교":"学校"}}]"""
    @Test fun syncPreservesProgressAndIsIdempotent() = runBlocking {
        val db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(),LearningDatabase::class.java).allowMainThreadQueries().build()
        try {
            val source=object:ContentSource { override suspend fun words(languageCode:String)=emptyList<WordEntity>() }
            val repo=LearningRepository(db,source,IntervalReviewScheduler())
            db.dao().insertWords(listOf(WordEntity("ko-03","ko","old","학교","学校","","","","",studied=8,correct=5,errors=3,nextReview=1234,favorite=true)))
            val content=RemoteContentSource.parse("ko",JSONArray(words),JSONArray(examples))
            repo.applyRemoteContent(content); repo.applyRemoteContent(content)
            val word=requireNotNull(db.dao().word("ko-03"))
            assertEquals("学校（更新）",word.meaning)
            assertEquals(8,word.studied); assertEquals(3,word.errors)
            assertEquals(1234L,word.nextReview); assertTrue(word.favorite)
            assertEquals(1,db.dao().wordCount("ko"))
            assertEquals(1,db.dao().observeExamples().first().size)
        } finally { db.close() }
    }
    @Test fun rejectsWrongLanguageAndOrphanExamples() {
        assertThrows(IllegalArgumentException::class.java) { RemoteContentSource.parse("en",JSONArray(words),JSONArray(examples)) }
        assertThrows(IllegalArgumentException::class.java) { RemoteContentSource.parse("ko",JSONArray("[]"),JSONArray(examples)) }
    }
}
