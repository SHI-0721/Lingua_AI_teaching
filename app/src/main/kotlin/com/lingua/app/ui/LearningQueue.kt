package com.lingua.app.ui

import com.lingua.app.data.WordEntity
import com.lingua.core.*

/** Difficulty orders NEW content only; it must not postpone due reviews or mistakes. */
fun learningQueue(words: List<WordEntity>, tags: List<DifficultyTag>, mode: String, now: Long,
    limit: Int, studiedToday: Set<String> = emptySet()): List<WordEntity> {
    val active=words.filter { it.inLearning }
    val levels=tags.groupBy { it.vocabularyId }.mapValues { dictionaryLevel(it.value) }
    val priority=compareByDescending<WordEntity> { it.unresolved }.thenByDescending { it.errors }.thenBy { it.nextReview }.thenBy { it.id }
    val due=active.filter { it.studied>0 && it.nextReview<=now }.sortedWith(priority)
    val mistakes=active.filter { it.unresolved }.sortedWith(priority)
    val fresh=active.filter { it.studied==0 }.sortedWith(compareBy<WordEntity> { difficultyRank(levels[it.id] ?: "unclassified") }.thenBy { it.id })
    return when(mode) {
        "review" -> due
        "mistakes" -> mistakes
        "test" -> (mistakes.shuffled().take(3)+fresh.take(3)+active.filter { it.studied>0 }.shuffled().take(4)+active.shuffled()).distinctBy { it.id }.take(10).shuffled()
        "today" -> {
            val urgent=(due+mistakes).distinctBy { it.id }
            val urgentIds=urgent.map { it.id }.toSet()
            // Keep urgent retries even when today's goal is complete or this word was already answered.
            urgent + fresh.filter { it.id !in studiedToday && it.id !in urgentIds }.take((limit-urgent.size).coerceAtLeast(0))
        }
        else -> (due+mistakes+fresh+active.filter { it.studied>0 }.shuffled()).distinctBy { it.id }.take(limit)
    }
}
