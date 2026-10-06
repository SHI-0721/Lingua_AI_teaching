package com.lingua.app.data
import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map
private val Context.learningSettings by preferencesDataStore("learning_settings")
data class Settings(val language: String="ko", val theme: String="system", val dailyGoal: Int=10)
class SettingsStore(private val context: Context) {
    private val language = stringPreferencesKey("language")
    private val theme = stringPreferencesKey("theme")
    private val goal = intPreferencesKey("goal")
    val settings = context.learningSettings.data.map { Settings(it[language] ?: "ko",it[theme] ?: "system",it[goal] ?: 10) }
    suspend fun setLanguage(value: String) { context.learningSettings.edit { it[language]=value } }
    suspend fun setTheme(value: String) { context.learningSettings.edit { it[theme]=value } }
    suspend fun setGoal(value: Int) { context.learningSettings.edit { it[goal]=value.coerceIn(1,100) } }
}
