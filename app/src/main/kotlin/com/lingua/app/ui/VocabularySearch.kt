package com.lingua.app.ui

import com.lingua.app.data.WordEntity
import com.lingua.core.StandardTokenizer
import com.lingua.core.DifficultyTag
import com.lingua.core.dictionaryLevel

/** Shared search policy; independent of UI and of the selected language. */
fun searchVocabulary(words: List<WordEntity>, query: String, favoritesOnly: Boolean,
    learningOnly: Boolean=false, level: String="all", tags: List<DifficultyTag> = emptyList()): List<WordEntity> {
    val tokenizer=StandardTokenizer()
    val needle=tokenizer.normalize(query)
    val levels=tags.groupBy { it.vocabularyId }.mapValues { dictionaryLevel(it.value) }
    return words.filter { (!favoritesOnly || it.favorite) && (!learningOnly || it.inLearning) &&
        (level=="all" || (levels[it.id] ?: "unclassified")==level) &&
        (needle.isEmpty() || tokenizer.normalize(it.text).contains(needle) || tokenizer.normalize(it.meaning).contains(needle)) }
        .sortedWith(compareByDescending<WordEntity> { it.favorite }.thenBy { it.lessonId.endsWith("-cloud") }.thenBy { it.text }.thenBy { it.id })
}
