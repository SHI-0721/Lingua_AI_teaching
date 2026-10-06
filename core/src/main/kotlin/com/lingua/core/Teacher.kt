package com.lingua.core

enum class TeachingGrade { CORRECT, PARTIAL, INCORRECT, UNGRADED }
data class TeachingQuestion(val id: String, val vocabularyId: String, val kind: String, val prompt: String,
    val reference: String, val speech: String, val explanation: String, val aliases: List<String> = emptyList())
data class TeachingFeedback(val questionId: String, val grade: TeachingGrade, val feedback: String, val hint: String)
interface TeachingService { suspend fun evaluate(language: String, question: TeachingQuestion, answer: String): TeachingFeedback }

/** Unmatched free text is unknown, not automatically wrong. AI never controls scheduling. */
class OfflineTeachingService : TeachingService {
    override suspend fun evaluate(language: String, question: TeachingQuestion, answer: String): TeachingFeedback {
        val normalize=StandardTokenizer()
        val matched=(question.aliases+question.reference).any { normalize.normalize(it)==normalize.normalize(answer) }
        return TeachingFeedback(question.id,if(matched) TeachingGrade.CORRECT else TeachingGrade.UNGRADED,
            if(matched) "与已验证参考表达一致。" else "离线规则无法判断这个表达是否合理，不计为答错。请先尝试提示，再对照参考答案自评。",
            "想一想关键词、语序和词尾；这道题考查${question.kind}。")
    }
}

fun validatedFeedback(result: TeachingFeedback, question: TeachingQuestion): TeachingFeedback {
    require(result.questionId==question.id && result.feedback.isNotBlank() && result.feedback.length<=2000 && result.hint.length<=500)
    return result
}

fun adaptiveNewWords(minutes: Int, accuracy: Double, goal: Int): Int {
    require(minutes in 5..120 && accuracy.isFinite() && accuracy in 0.0..1.0)
    return (minutes/3 * if(accuracy<0.6) 0.4 else if(accuracy<0.85) 0.7 else 1.0).toInt().coerceIn(1,goal.coerceAtLeast(1))
}
