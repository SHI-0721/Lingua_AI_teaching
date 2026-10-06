package com.lingua.app
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import com.lingua.app.data.*
import com.lingua.core.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
@RunWith(AndroidJUnit4::class)
class DatabasePersistenceTest {
    @Test fun recallTransactionSurvivesDatabaseReopen() = runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val name="lingua-persistence-test.db"
        context.deleteDatabase(name)
        try {
            val source=object: ContentSource { override suspend fun words(languageCode: String)=listOf(WordEntity("test-word",languageCode,"test","약속","约定","yaksok","약속이 있어요.","有约。","약속 시간")) }
            var db=Room.databaseBuilder(context,LearningDatabase::class.java,name).build()
            val repo=LearningRepository(db,source,IntervalReviewScheduler())
            repo.seed("ko")
            val due=repo.record("test-word",Recall.AGAIN,8,"test","wrong","SPELLING")
            db.close()
            db=Room.databaseBuilder(context,LearningDatabase::class.java,name).build()
            val word=db.dao().word("test-word")!!
            assertEquals(1,word.errors)
            assertTrue(word.unresolved)
            assertEquals(due,word.nextReview)
            assertEquals(1,db.dao().observeReviews().first().size)
            assertEquals(8L,db.dao().observeSessions().first().single().seconds)
            db.close()
        } finally { context.deleteDatabase(name) }
    }
}
