package com.lingua.core
import kotlin.test.*
class ExerciseTest {
    private val word=Vocabulary("ko-a","ko","basic","약속","约定","yaksok",ExampleSentence("내일 약속이 있어요.","明天有约。"),"약속 시간")
    @Test fun `all exercise modes have answers and preserve word identity`() {
        ExerciseType.entries.forEach { type -> val e=StandardExerciseGenerator().create(word,listOf(word),type); assertEquals(word.id,e.vocabularyId); assertTrue(e.expected.isNotBlank()); assertTrue(e.prompt.isNotBlank()) }
    }
    @Test fun `cloze hides target word`() { val e=StandardExerciseGenerator().create(word,listOf(word),ExerciseType.CLOZE); assertFalse(e.prompt.contains("약속")); assertTrue(e.prompt.contains("____")) }
    @Test fun `choices contain answer exactly once`() {
        val e=StandardExerciseGenerator().create(word,listOf(word,word.copy(id="b",meaning="朋友")),ExerciseType.CHOICE)
        assertEquals(1,e.choices.count { it==e.expected })
    }
    @Test fun `registered languages share platform contracts`() { val registry=LanguageRegistry(); assertEquals("ko",registry.module("ko").language.code); assertFalse(registry.languages.first { it.code=="en" }.available) }
    @Test fun `missing sentence falls back to valid word question`() {
        val exercise=StandardExerciseGenerator().create(word.copy(example=ExampleSentence("","")),listOf(word),ExerciseType.SENTENCE)
        assertEquals(ExerciseType.TO_TARGET,exercise.type)
        assertEquals(word.text,exercise.expected)
    }
    @Test fun `empty collocations still provide a cloze and choices are unique`() {
        val generator=StandardExerciseGenerator()
        assertTrue(generator.create(word.copy(collocations=""),listOf(word),ExerciseType.CLOZE).prompt.contains("____"))
        val choices=generator.create(word,listOf(word,word.copy(id="b")),ExerciseType.CHOICE).choices
        assertEquals(choices.distinct(),choices)
    }
}
