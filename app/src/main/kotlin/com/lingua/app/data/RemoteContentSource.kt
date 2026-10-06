package com.lingua.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.URI
import java.net.URLEncoder
import javax.net.ssl.HttpsURLConnection

data class RemoteContent(val words: List<WordEntity>, val examples: List<ExampleEntity>, val sources: List<ContentAttributionEntity> = emptyList(), val difficulties: List<DifficultyTagEntity> = emptyList())

/** Public content only. Never supply a database password or server-side secret. */
class RemoteContentSource(private val baseUrl: String, private val key: String) {
    suspend fun fetch(language: String): RemoteContent = withContext(Dispatchers.IO) {
        require(key.startsWith("sb_publishable_") || key.startsWith("eyJ")) { "请配置客户端 Publishable / anon 密钥" }
        val uri = URI(baseUrl.trim().trimEnd('/'))
        require(uri.scheme == "https" && uri.host?.endsWith(".supabase.co") == true && uri.userInfo == null && uri.rawQuery == null && uri.rawFragment == null && uri.path.orEmpty().isEmpty()) { "请配置正确的 Supabase Project URL" }
        val wordsJson = download(uri.toString(), "vocabulary", language)
        val examplesJson = download(uri.toString(), "example_sentences", language)
        parse(language, wordsJson, examplesJson)
    }
    private fun download(base: String, table: String, language: String): JSONArray {
        val rows = JSONArray()
        var offset = 0
        while (true) {
            val query = "select=*&language_code=eq." + URLEncoder.encode(language,"UTF-8") +
                "&published=eq.true&deleted_at=is.null&order=id.asc&limit=500&offset=" + offset
            val connection = URI("$base/rest/v1/$table?$query").toURL().openConnection() as HttpsURLConnection
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 15000
                connection.readTimeout = 20000
                connection.setRequestProperty("apikey", key)
                connection.setRequestProperty("Accept", "application/json")
                val code = connection.responseCode
                check(code == 200) { if(code in listOf(401,403)) "云端权限验证失败，请检查客户端密钥与 RLS" else "词库请求失败（HTTP $code）" }
                val text = connection.inputStream.bufferedReader().use { it.readText() }
                check(text.length <= 5_000_000) { "词库单页过大" }
                val page = JSONArray(text)
                for (i in 0 until page.length()) rows.put(page.getJSONObject(i))
                if(page.length() == 0) break
                offset += page.length()
                check(offset <= 50000) { "词库过大，请分批同步" }
            } finally { connection.disconnect() }
        }
        return rows
    }
    companion object {
        fun parse(language: String, words: JSONArray, examples: JSONArray): RemoteContent {
            fun list(row: org.json.JSONObject, field: String): String =
                row.optJSONArray(field)?.let { a -> (0 until a.length()).joinToString("；") { a.getString(it) } }.orEmpty()
            val mappedWords = (0 until words.length()).map { i ->
                val w=words.getJSONObject(i)
                require(w.getString("language_code")==language && w.getString("id").isNotBlank() && w.getString("word").isNotBlank() && w.getString("meaning").isNotBlank())
                WordEntity(w.getString("id"),language,"$language-cloud",w.getString("word"),w.getString("meaning"),w.optString("pronunciation"),"","",list(w,"collocations"),partOfSpeech=w.optString("part_of_speech"),similarWords=list(w,"similar_words"),antonyms=list(w,"antonyms"),inLearning=false)
            }
            val ids=mappedWords.map { it.id }.toSet()
            require(ids.size==mappedWords.size) { "云端单词 ID 重复" }
            val mappedExamples=(0 until examples.length()).map { i ->
                val e=examples.getJSONObject(i)
                require(e.getString("language_code")==language && e.getString("vocabulary_id") in ids && e.getString("id").isNotBlank() && e.getString("sentence").isNotBlank() && e.getString("translation").isNotBlank())
                val keywords=e.optJSONObject("keywords")
                ExampleEntity(e.getString("id"),language,e.getString("vocabulary_id"),e.getString("sentence"),e.getString("translation"),e.optInt("difficulty",1).also { require(it in 1..5) },if(e.isNull("audio_source")) null else e.optString("audio_source"),keywords?.keys()?.asSequence()?.joinToString(";") { "$it=${keywords.getString(it)}" }.orEmpty(),e.optString("grammar_notes"))
            }
            require(mappedExamples.map { it.id }.distinct().size==mappedExamples.size)
            val sources=(0 until words.length()).mapNotNull { i ->
                val w=words.getJSONObject(i)
                if(w.isNull("source_name") || w.optString("source_name").isBlank()) null else
                    ContentAttributionEntity(w.getString("id"),w.getString("source_name"),w.optString("source_url"),w.optString("source_entry_id"),w.optString("source_sense_id"),w.optString("license"),w.optString("attribution"))
            }
            val difficulties=(0 until words.length()).flatMap { i ->
                val row=words.getJSONObject(i)
                val tags=if(row.has("difficulty_tags") && !row.isNull("difficulty_tags")) row.getJSONArray("difficulty_tags") else JSONArray()
                (0 until tags.length()).map { j ->
                    val t=tags.getJSONObject(j)
                    DifficultyTagEntity(row.getString("id"),t.getString("framework"),t.getString("level"),
                        t.getString("source"),t.getDouble("confidence"),t.getString("version")).also { it.domain() }
                }
            }
            require(difficulties.distinctBy { it.vocabularyId to it.framework }.size==difficulties.size) { "难度体系重复" }
            return RemoteContent(mappedWords,mappedExamples,sources,difficulties)
        }
    }
}
