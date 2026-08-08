package com.agro.interviewer.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.agro.interviewer.domain.model.Difficulty
import com.agro.interviewer.domain.model.QuestionCategory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

@Singleton
class UserPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val LAST_CATEGORY = stringPreferencesKey("last_category")
        val LAST_DIFFICULTY = stringPreferencesKey("last_difficulty")
        val LAST_QUESTION_COUNT = intPreferencesKey("last_question_count")
        val IS_FIRST_LAUNCH = booleanPreferencesKey("is_first_launch")
        val TOTAL_SESSIONS_COMPLETED = intPreferencesKey("total_sessions_completed")
    }

    val lastCategory: Flow<QuestionCategory> = context.dataStore.data.map { prefs ->
        val name = prefs[Keys.LAST_CATEGORY] ?: QuestionCategory.KOTLIN.name
        try { QuestionCategory.valueOf(name) } catch (e: Exception) { QuestionCategory.KOTLIN }
    }

    val lastDifficulty: Flow<Difficulty> = context.dataStore.data.map { prefs ->
        val name = prefs[Keys.LAST_DIFFICULTY] ?: Difficulty.MID.name
        try { Difficulty.valueOf(name) } catch (e: Exception) { Difficulty.MID }
    }

    val lastQuestionCount: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[Keys.LAST_QUESTION_COUNT] ?: 5
    }

    val isFirstLaunch: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.IS_FIRST_LAUNCH] ?: true
    }

    suspend fun saveLastCategory(category: QuestionCategory) {
        context.dataStore.edit { it[Keys.LAST_CATEGORY] = category.name }
    }

    suspend fun saveLastDifficulty(difficulty: Difficulty) {
        context.dataStore.edit { it[Keys.LAST_DIFFICULTY] = difficulty.name }
    }

    suspend fun saveLastQuestionCount(count: Int) {
        context.dataStore.edit { it[Keys.LAST_QUESTION_COUNT] = count }
    }

    suspend fun setFirstLaunchComplete() {
        context.dataStore.edit { it[Keys.IS_FIRST_LAUNCH] = false }
    }

    suspend fun incrementCompletedSessions() {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.TOTAL_SESSIONS_COMPLETED] ?: 0
            prefs[Keys.TOTAL_SESSIONS_COMPLETED] = current + 1
        }
    }
}
