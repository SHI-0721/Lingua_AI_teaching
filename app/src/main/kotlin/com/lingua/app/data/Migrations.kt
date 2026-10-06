package com.lingua.app.data
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
val MIGRATION_4_5=object: Migration(4,5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS teacher_courses (id TEXT NOT NULL PRIMARY KEY,languageCode TEXT NOT NULL,payload TEXT NOT NULL,revision INTEGER NOT NULL,updatedAt INTEGER NOT NULL)")
    }
}
val MIGRATION_3_4=object: Migration(3,4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE words ADD COLUMN inLearning INTEGER NOT NULL DEFAULT 0")
        // Keep existing learners enrolled; do not enqueue thousands of untouched dictionary senses.
        db.execSQL("UPDATE words SET inLearning=1 WHERE studied>0 OR lessonId NOT LIKE '%-cloud'")
        db.execSQL("CREATE TABLE IF NOT EXISTS difficulty_tags (vocabularyId TEXT NOT NULL,framework TEXT NOT NULL,level TEXT NOT NULL,source TEXT NOT NULL,confidence REAL NOT NULL,version TEXT NOT NULL,PRIMARY KEY(vocabularyId,framework))")
    }
}
val MIGRATION_2_3=object: Migration(2,3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS content_sources (vocabularyId TEXT NOT NULL PRIMARY KEY, sourceName TEXT NOT NULL, sourceUrl TEXT NOT NULL, entryId TEXT NOT NULL, senseId TEXT NOT NULL, license TEXT NOT NULL, attribution TEXT NOT NULL)")
    }
}
val MIGRATION_1_2=object: Migration(1,2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE words ADD COLUMN partOfSpeech TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE words ADD COLUMN similarWords TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE words ADD COLUMN antonyms TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE words ADD COLUMN favorite INTEGER NOT NULL DEFAULT 0")
        db.execSQL("CREATE TABLE IF NOT EXISTS examples (id TEXT NOT NULL PRIMARY KEY, languageCode TEXT NOT NULL, vocabularyId TEXT NOT NULL, sentence TEXT NOT NULL, translation TEXT NOT NULL, difficulty INTEGER NOT NULL, audioSource TEXT, keywords TEXT NOT NULL, grammarNotes TEXT NOT NULL)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_examples_vocabularyId ON examples (vocabularyId)")
        db.execSQL("INSERT INTO examples SELECT id || '-legacy', languageCode, id, example, translation, 1, NULL, '', '' FROM words WHERE example != ''")
        db.execSQL("CREATE TABLE IF NOT EXISTS daily (date TEXT NOT NULL, languageCode TEXT NOT NULL, studyMinutes REAL NOT NULL, newWords INTEGER NOT NULL, reviewCount INTEGER NOT NULL, correctCount INTEGER NOT NULL, wrongCount INTEGER NOT NULL, completed INTEGER NOT NULL, learnedWords INTEGER NOT NULL, targetWords INTEGER NOT NULL, seconds INTEGER NOT NULL, testCorrect INTEGER NOT NULL, testWrong INTEGER NOT NULL, PRIMARY KEY(date, languageCode))")
        db.execSQL("CREATE TABLE IF NOT EXISTS daily_words (date TEXT NOT NULL, languageCode TEXT NOT NULL, wordId TEXT NOT NULL, PRIMARY KEY(date, languageCode, wordId))")
    }
}
