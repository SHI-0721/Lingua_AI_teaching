package com.lingua.core

interface Tokenizer { fun normalize(text: String): String }
interface PronunciationProvider { val localeTag: String }
interface VocabularyProvider { val assetPath: String; val dictionaryAssetPath: String? get() = null }
interface GrammarProvider { fun introductoryGrammar(): List<Grammar> }
interface ExerciseGenerator { fun create(word: Vocabulary, pool: List<Vocabulary>, type: ExerciseType): Exercise }
interface LanguageModule {
    val language: Language
    val tokenizer: Tokenizer
    val pronunciation: PronunciationProvider
    val vocabulary: VocabularyProvider
    val grammar: GrammarProvider
    val exercises: ExerciseGenerator
}
enum class ExerciseType { TO_MEANING, TO_TARGET, CHOICE, CLOZE, SPELLING, SENTENCE }
data class Exercise(val vocabularyId: String, val type: ExerciseType, val prompt: String, val expected: String, val choices: List<String> = emptyList())
class StandardTokenizer : Tokenizer { override fun normalize(text: String) = java.text.Normalizer.normalize(text.trim(), java.text.Normalizer.Form.NFKC).lowercase().replace(Regex("[\\s\\p{P}]+"), "") }
class StandardExerciseGenerator : ExerciseGenerator {
    override fun create(word: Vocabulary, pool: List<Vocabulary>, type: ExerciseType): Exercise = when(type) {
        ExerciseType.TO_MEANING -> Exercise(word.id, type, "请写出中文意思：${word.text}", word.meaning)
        ExerciseType.TO_TARGET -> Exercise(word.id, type, "请翻译：${word.meaning}", word.text)
        ExerciseType.CHOICE -> Exercise(word.id, type, "选择 ${word.text} 的中文意思", word.meaning, (pool.map { it.meaning }.distinct().filter { it != word.meaning }.shuffled().take(3) + word.meaning).shuffled())
        ExerciseType.CLOZE -> Exercise(word.id, type, "填空：${if(word.collocations.contains(word.text)) word.collocations.replace(word.text, "____") else "____"}\n提示：${word.meaning}", word.text)
        ExerciseType.SPELLING -> Exercise(word.id, type, "拼写：${word.meaning}\n读音：${word.pronunciation}", word.text)
        ExerciseType.SENTENCE -> if(word.example.text.isNotBlank() && word.example.translation.isNotBlank()) Exercise(word.id, type, "翻译句子：${word.example.translation}", word.example.text) else create(word,pool,ExerciseType.TO_TARGET)
    }
}
class KoreanModule : LanguageModule {
    override val language = Language("ko", "韩语", true)
    override val tokenizer = StandardTokenizer()
    override val pronunciation = object : PronunciationProvider { override val localeTag = "ko-KR" }
    override val vocabulary = object : VocabularyProvider { override val assetPath = "content/ko.json"; override val dictionaryAssetPath = "content/ko-dictionary.json" }
    override val grammar = object : GrammarProvider { override fun introductoryGrammar() = listOf(
        Grammar("ko-basic", "ko", "韩语通常采用主语—宾语—谓语的顺序。"),
        Grammar("ko-topic", "ko", "저 后用 는 标记话题；口语可以省略一些助词，但本题练习完整形式。", "填助词：저___ 학생이에요.", "는", "학교"),
        Grammar("ko-object", "ko", "한국어 以元音结尾，用 를 标记宾语。省略助词不一定不自然，练习完整形式时应补全。", "填助词：한국어___ 공부해요.", "를", "친구"),
        Grammar("ko-destination", "ko", "에 标记移动的目的地，가요 是 가다 的礼貌现在时形式。", "造句：我去学校。", "저는 학교에 가요.", "학교", listOf("학교에 가요.","제가 학교에 가요.","나는 학교에 가요."))
    ) }
    override val exercises = StandardExerciseGenerator()
}
class LanguageRegistry(modules: List<LanguageModule> = listOf(KoreanModule())) {
    private val entries = modules.associateBy { it.language.code }
    val languages get() = entries.values.map { it.language } + listOf(Language("en", "英语 · 即将开放", false), Language("ja", "日语 · 即将开放", false)).filter { it.code !in entries }
    fun module(code: String) = requireNotNull(entries[code]) { "语言模块尚未安装：$code" }
}
interface AIService {
    suspend fun generateExample(languageCode: String, word: String): String
    suspend fun explainGrammar(languageCode: String, sentence: String): String
    suspend fun generateExercises(languageCode: String, mistakes: List<String>): List<Exercise>
    suspend fun correctSentence(languageCode: String, sentence: String): String
    suspend fun chat(languageCode: String, messages: List<String>): String
}
