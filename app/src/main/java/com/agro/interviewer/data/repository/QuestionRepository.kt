package com.agro.interviewer.data.repository

import android.content.Context
import com.agro.interviewer.domain.model.Difficulty
import com.agro.interviewer.domain.model.Question
import com.agro.interviewer.domain.model.QuestionCategory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QuestionRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val cache = mutableMapOf<String, List<Question>>()

    suspend fun getQuestions(
        category: QuestionCategory,
        difficulty: Difficulty,
        count: Int
    ): List<Question> = withContext(Dispatchers.IO) {
        val allQuestions = loadQuestionsForCategory(category, difficulty)
        allQuestions.shuffled().take(count)
    }

    suspend fun getAllCategories(): List<QuestionCategory> {
        return QuestionCategory.entries.toList()
    }

    suspend fun buildQuestionContext(
        category: QuestionCategory,
        difficulty: Difficulty,
        count: Int
    ): String = withContext(Dispatchers.IO) {
        val questions = getQuestions(category, difficulty, count)
        val sb = StringBuilder()
        sb.appendLine("## Interview Questions for This Session")
        sb.appendLine("Topic: ${category.displayName}")
        sb.appendLine("Difficulty: ${difficulty.name}")
        sb.appendLine()
        sb.appendLine("Ask these questions IN ORDER. Do not skip any.")
        sb.appendLine()

        questions.forEachIndexed { index, question ->
            sb.appendLine("### Question ${index + 1}")
            sb.appendLine("Ask: \"${question.text}\"")
            sb.appendLine("Expected answer includes: ${question.modelAnswer}")
            if (question.followUps.isNotEmpty()) {
                sb.appendLine("Optional follow-ups: ${question.followUps.joinToString("; ")}")
            }
            sb.appendLine()
        }
        sb.toString()
    }

    private suspend fun loadQuestionsForCategory(
        category: QuestionCategory,
        difficulty: Difficulty
    ): List<Question> {
        val key = cacheKey(category, difficulty)
        cache[key]?.let { return it }

        val fileName = buildFileName(category, difficulty)

        return try {
            val json = context.assets
                .open("questions/$fileName")
                .bufferedReader()
                .use { it.readText() }

            val parsed = parseQuestions(json)
            cache[key] = parsed
            parsed
        } catch (e: Exception) {
            Timber.w("Could not load asset %s — %s", fileName, e.message)
            loadFallbackQuestions(category)
        }
    }

    private fun buildFileName(category: QuestionCategory, difficulty: Difficulty): String {
        val categorySlug = when (category) {
            QuestionCategory.KOTLIN -> "kotlin"
            QuestionCategory.ANDROID_FUNDAMENTALS -> "android"
            QuestionCategory.JETPACK_COMPOSE -> "compose"
            QuestionCategory.ARCHITECTURE -> "architecture"
            QuestionCategory.COROUTINES -> "coroutines"
            QuestionCategory.TESTING -> "testing"
            QuestionCategory.PERFORMANCE -> "performance"
            QuestionCategory.SYSTEM_DESIGN -> "system_design"
        }
        val difficultySlug = difficulty.name.lowercase()
        return "${categorySlug}_${difficultySlug}.json"
    }

    private fun parseQuestions(json: String): List<Question> {
        val array = JSONArray(json)
        val questions = mutableListOf<Question>()

        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)

            val followUpsArray = obj.optJSONArray("followUps")
            val followUps = mutableListOf<String>()
            if (followUpsArray != null) {
                for (j in 0 until followUpsArray.length()) {
                    followUps.add(followUpsArray.getString(j))
                }
            }

            val tagsArray = obj.optJSONArray("tags")
            val tags = mutableListOf<String>()
            if (tagsArray != null) {
                for (j in 0 until tagsArray.length()) {
                    tags.add(tagsArray.getString(j))
                }
            }

            questions.add(
                Question(
                    id = obj.getString("id"),
                    text = obj.getString("text"),
                    category = QuestionCategory.valueOf(obj.getString("category")),
                    difficulty = Difficulty.valueOf(obj.getString("difficulty")),
                    tags = tags,
                    modelAnswer = obj.getString("modelAnswer"),
                    followUps = followUps,
                    estimatedSeconds = obj.optInt("estimatedSeconds", 60)
                )
            )
        }
        return questions
    }

    private suspend fun loadFallbackQuestions(category: QuestionCategory): List<Question> {
        for (difficulty in Difficulty.entries) {
            val key = cacheKey(category, difficulty)
            cache[key]?.let { return it }
        }
        return emptyList()
    }

    private fun cacheKey(category: QuestionCategory, difficulty: Difficulty) =
        "${category.name}_${difficulty.name}"
}
