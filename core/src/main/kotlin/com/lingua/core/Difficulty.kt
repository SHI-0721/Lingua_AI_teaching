package com.lingua.core

/** Labels belong to senses, not spellings; dictionary levels are not TOPIK grades. */
data class DifficultyTag(val vocabularyId: String, val framework: String, val level: String,
    val source: String, val confidence: Double, val version: String) {
    init {
        require(vocabularyId.isNotBlank() && framework.isNotBlank() && level.isNotBlank())
        require(source.isNotBlank() && version.isNotBlank() && confidence.isFinite() && confidence in 0.0..1.0)
    }
}
val dictionaryLevels = listOf("beginner", "intermediate", "advanced")
fun difficultyName(level: String) = when(level) {
    "beginner" -> "初级"; "intermediate" -> "中级"; "advanced" -> "高级"; else -> "未分级"
}
fun dictionaryLevel(tags: List<DifficultyTag>) = tags.firstOrNull { it.framework=="krdict" }?.level
    ?.takeIf { it in dictionaryLevels } ?: "unclassified"
fun difficultyRank(level: String) = dictionaryLevels.indexOf(level).takeIf { it>=0 } ?: dictionaryLevels.size
