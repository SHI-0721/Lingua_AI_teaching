package com.lingua.app
import androidx.room.Room
import android.content.Context
import com.lingua.app.data.*
import com.lingua.core.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=android.app.Application::class)
class RepositoryTest {
    @Test fun databaseReopenRetainsReviewScheduleAndErrorHistory() = runBlocking {
        val context: Context=RuntimeEnvironment.getApplication()
        val name="reopen-test.db"
        context.deleteDatabase(name)
        var db=Room.databaseBuilder(context,LearningDatabase::class.java,name).build()
        try {
            db.dao().insertWords(listOf(WordEntity("persist","ko","basic","약속","约定","yaksok","약속이 있어요.","有约。","약속 시간")))
            val source=object: ContentSource { override suspend fun words(languageCode: String)=emptyList<WordEntity>() }
            val due=LearningRepository(db,source,IntervalReviewScheduler()).record("persist",Recall.AGAIN,9,"test","wrong","SPELLING")
            db.close()
            db=Room.databaseBuilder(context,LearningDatabase::class.java,name).build()
            assertEquals(due,db.dao().word("persist")!!.nextReview)
            assertTrue(db.dao().word("persist")!!.unresolved)
            assertEquals("SPELLING",db.dao().observeReviews().first().single().errorKind)
            assertEquals(9L,db.dao().observeSessions().first().single().seconds)
        } finally { db.close(); context.deleteDatabase(name) }
    }
    @Test fun failedRecallPersistsAndCorrectRecallResolvesMistake() = runBlocking {
        val context: Context=RuntimeEnvironment.getApplication()
        val db=Room.inMemoryDatabaseBuilder(context,LearningDatabase::class.java).build()
        try {
            val source=object: ContentSource { override suspend fun words(languageCode: String)=listOf(WordEntity("test",languageCode,"basic","약속","约定","yaksok","약속이 있어요.","有约。","약속 시간")) }
            val repo=LearningRepository(db,source,IntervalReviewScheduler())
            repo.seed("ko"); repo.seed("ko")
            assertEquals(1,repo.words.first().size)
            repo.record("test",Recall.AGAIN,5,"test","wrong","SPELLING")
            assertTrue(db.dao().word("test")!!.unresolved)
            assertEquals(1,db.dao().word("test")!!.errors)
            repo.record("test",Recall.KNOW,7,"mistakes")
            val word=db.dao().word("test")!!
            assertFalse(word.unresolved)
            assertEquals(1,word.errors)
            assertEquals(2,word.studied)
            assertEquals(2,repo.reviews.first().size)
            assertEquals(12L,repo.sessions.first().sumOf { it.seconds })
        } finally { db.close() }
    }
}
