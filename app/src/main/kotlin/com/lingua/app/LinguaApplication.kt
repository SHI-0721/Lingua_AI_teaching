package com.lingua.app
import android.app.Application
import androidx.room.Room
import com.lingua.app.data.*
import com.lingua.core.*
import com.lingua.app.speech.*
import android.content.Context
class LinguaApplication : Application() {
    val registry = LanguageRegistry()
    var answerEvaluator: AnswerEvaluator = RuleAnswerEvaluator()
    var pronunciationFactory: (Context)->PronunciationService = { AndroidPronunciationService(it) }
    val settings by lazy { SettingsStore(this) }
    val repository by lazy {
        val database = Room.databaseBuilder(this,LearningDatabase::class.java,"lingua.db").addMigrations(MIGRATION_1_2,MIGRATION_2_3,MIGRATION_3_4,MIGRATION_4_5).build()
        LearningRepository(database,JsonAssetContentSource(this,registry),IntervalReviewScheduler())
    }
}
