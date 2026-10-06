package com.lingua.app.data

import android.content.Context
import org.json.JSONArray
import com.lingua.core.LanguageRegistry

/** Additional CSV, server and AI adapters implement this contract without changing screens. */
interface ContentSource {
    suspend fun dictionary(languageCode: String): RemoteContent = RemoteContent(emptyList(),emptyList())
    suspend fun words(languageCode: String): List<WordEntity>
    suspend fun examples(languageCode: String): List<ExampleEntity> = words(languageCode).filter { it.example.isNotBlank() }.map { ExampleEntity(it.id+"-legacy",languageCode,it.id,it.example,it.translation) }
}
class JsonAssetContentSource(private val context: Context, private val registry: LanguageRegistry) : ContentSource {
    override suspend fun dictionary(languageCode: String): RemoteContent {
        val path=registry.module(languageCode).vocabulary.dictionaryAssetPath ?: return RemoteContent(emptyList(),emptyList())
        val words=JSONArray(context.assets.open(path).bufferedReader().use { it.readText() })
        return RemoteContentSource.parse(languageCode,words,JSONArray())
    }
    override suspend fun words(languageCode: String): List<WordEntity> {
        val json = JSONArray(context.assets.open(registry.module(languageCode).vocabulary.assetPath).bufferedReader().use { it.readText() })
        return (0 until json.length()).map { index -> json.getJSONObject(index).let { o -> WordEntity(o.getString("id"), languageCode, o.getString("lessonId"),o.getString("text"),o.getString("meaning"),o.getString("pronunciation"),"","",o.getString("collocations"),partOfSpeech=o.optString("partOfSpeech"),similarWords=o.optString("similarWords"),antonyms=o.optString("antonyms")) } }
    }
    override suspend fun examples(languageCode: String): List<ExampleEntity> {
        val path=registry.module(languageCode).vocabulary.assetPath.replace(".json","-examples.json")
        val array=JSONArray(context.assets.open(path).bufferedReader().use { it.readText() })
        return (0 until array.length()).map { index -> array.getJSONObject(index).let { o -> ExampleEntity(o.getString("id"),languageCode,o.getString("vocabularyId"),o.getString("sentence"),o.getString("translation"),o.optInt("difficulty",1),keywords=o.optString("keywords"),grammarNotes=o.optString("grammarNotes")) } }
    }
}
