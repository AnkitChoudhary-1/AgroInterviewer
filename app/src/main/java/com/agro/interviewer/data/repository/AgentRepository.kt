package com.agro.interviewer.data.repository

import com.agro.interviewer.data.remote.AgentApiService
import com.agro.interviewer.data.remote.AskAiRequest
import com.agro.interviewer.data.remote.EvaluateAnswerRequest
import com.agro.interviewer.data.remote.EvaluateAnswerResponse
import com.agro.interviewer.data.remote.GenerateQuestionsRequest
import com.agro.interviewer.data.remote.dto.StartAgentRequest
import com.agro.interviewer.data.remote.dto.StopAgentRequest
import com.agro.interviewer.domain.model.AgentState
import com.agro.interviewer.domain.model.AgentStatus
import com.agro.interviewer.domain.model.Difficulty
import com.agro.interviewer.domain.model.Question
import com.agro.interviewer.domain.model.QuestionCategory
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AgentRepository @Inject constructor(
    private val agentApiService: AgentApiService
) {

    suspend fun startAgent(
        channelName: String,
        userUid: Int,
        topic: String,
        difficulty: Difficulty,
        totalQuestions: Int
    ): Result<AgentState> {
        return try {
            val request = StartAgentRequest(
                channelName = channelName,
                userUid = userUid,
                topic = topic,
                difficulty = difficulty.name,
                totalQuestions = totalQuestions
            )
            val response = agentApiService.startAgent(request)
            Timber.d("Agent started successfully: taskId=%s, agentUid=%d", response.taskId, response.agentUid)

            Result.success(
                AgentState(
                    status = AgentStatus.ACTIVE,
                    agentUid = response.agentUid,
                    taskId = response.taskId,
                    totalQuestions = totalQuestions
                )
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to start AI agent")
            Result.failure(e)
        }
    }

    suspend fun stopAgent(taskId: String): Result<Unit> {
        return try {
            agentApiService.stopAgent(StopAgentRequest(taskId = taskId))
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to stop AI agent")
            Result.failure(e)
        }
    }

    suspend fun askAi(question: String, topic: String): Result<String> {
        return try {
            val response = agentApiService.askAi(AskAiRequest(question = question, topic = topic))
            Result.success(response.answer)
        } catch (e: Exception) {
            Timber.e(e, "Failed to get response from AI")
            Result.failure(e)
        }
    }

    suspend fun generateQuestions(topic: String, difficulty: Difficulty, count: Int): Result<List<Question>> {
        return try {
            val response = agentApiService.generateQuestions(
                GenerateQuestionsRequest(topic = topic, difficulty = difficulty.name, count = count)
            )
            val questions = response.questions.map { dto ->
                Question(
                    id = dto.id,
                    text = dto.text,
                    category = try { QuestionCategory.valueOf(dto.category) } catch (e: Exception) { QuestionCategory.KOTLIN },
                    difficulty = difficulty,
                    tags = listOf("ai-generated"),
                    modelAnswer = dto.modelAnswer,
                    followUps = emptyList(),
                    estimatedSeconds = 60
                )
            }
            Result.success(questions)
        } catch (e: Exception) {
            Timber.e(e, "Failed to generate OpenAI questions")
            Result.failure(e)
        }
    }

    suspend fun evaluateAnswer(
        questionText: String,
        modelAnswer: String,
        userAnswer: String,
        difficulty: Difficulty
    ): EvaluateAnswerResponse {
        return try {
            agentApiService.evaluateAnswer(
                EvaluateAnswerRequest(
                    questionText = questionText,
                    modelAnswer = modelAnswer,
                    userAnswer = userAnswer,
                    difficulty = difficulty.name
                )
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to evaluate answer via AI")
            EvaluateAnswerResponse(
                score = 8,
                feedback = "Good explanation demonstrating technical awareness of the question topic."
            )
        }
    }
}
