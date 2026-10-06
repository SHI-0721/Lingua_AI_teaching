package com.lingua.core

data class Language(val code: String, val name: String, val available: Boolean)
data class Course(val id: String, val languageCode: String, val title: String)
data class Lesson(val id: String, val courseId: String, val title: String)
data class Grammar(val id: String, val languageCode: String, val explanation: String, val prompt: String="", val reference: String="", val anchor: String="", val aliases: List<String> = emptyList())
data class ExampleSentence(val text: String, val translation: String, val id: String="", val languageCode: String="", val vocabularyId: String="", val difficulty: Int=1, val audioSource: String?=null, val keywords: List<Pair<String,String>> = emptyList())
data class Vocabulary(val id: String, val languageCode: String, val lessonId: String, val text: String, val meaning: String, val pronunciation: String, val example: ExampleSentence, val collocations: String)
enum class Recall { KNOW, FUZZY, AGAIN }
data class Progress(val stage: Int = 0, val streak: Int = 0, val studied: Int = 0, val correct: Int = 0, val errors: Int = 0, val lastReview: Long = 0, val nextReview: Long = 0)
data class ReviewRecord(val vocabularyId: String, val result: Recall, val timestamp: Long)
data class StudySession(val languageCode: String, val startedAt: Long, val durationSeconds: Long, val count: Int)
interface ReviewScheduler { fun schedule(previous: Progress, result: Recall, now: Long): Progress }

/** A failed recall returns to a one-minute retry; success advances through day intervals. */
class IntervalReviewScheduler : ReviewScheduler {
    private val days = listOf(0, 1, 3, 7, 14, 30)
    override fun schedule(previous: Progress, result: Recall, now: Long): Progress {
        val stage = when (result) { Recall.KNOW -> (previous.stage + 1).coerceAtMost(days.lastIndex); Recall.FUZZY -> (previous.stage - 1).coerceAtLeast(0); Recall.AGAIN -> 0 }
        val delay = when (result) { Recall.AGAIN -> 60_000L; Recall.FUZZY -> if (stage == 0) 600_000L else days[stage] * 86_400_000L; Recall.KNOW -> days[stage] * 86_400_000L }
        return previous.copy(stage = stage, streak = if (result == Recall.KNOW) previous.streak + 1 else 0, studied = previous.studied + 1, correct = previous.correct + if (result == Recall.KNOW) 1 else 0, errors = previous.errors + if (result == Recall.AGAIN) 1 else 0, lastReview = now, nextReview = now + delay)
    }
}
