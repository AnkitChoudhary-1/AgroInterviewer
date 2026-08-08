package com.agro.interviewer.data.remote

import com.agro.interviewer.data.remote.dto.StartAgentRequest
import com.agro.interviewer.data.remote.dto.StartAgentResponse
import com.agro.interviewer.data.remote.dto.StopAgentRequest
import com.agro.interviewer.data.remote.dto.StopAgentResponse
import retrofit2.http.Body
import retrofit2.http.POST

data class AskAiRequest(
    val question: String,
    val topic: String = "Android Development"
)

data class AskAiResponse(
    val answer: String
)

data class GenerateQuestionsRequest(
    val topic: String,
    val difficulty: String,
    val count: Int = 5
)

data class GeneratedQuestionDto(
    val id: String,
    val text: String,
    val category: String,
    val difficulty: String,
    val modelAnswer: String
)

data class GenerateQuestionsResponse(
    val questions: List<GeneratedQuestionDto>
)

data class EvaluateAnswerRequest(
    val questionText: String,
    val modelAnswer: String,
    val userAnswer: String,
    val difficulty: String
)

data class EvaluateAnswerResponse(
    val score: Int,
    val feedback: String
)

interface AgentApiService {

    @POST("api/agent/start")
    suspend fun startAgent(
        @Body request: StartAgentRequest
    ): StartAgentResponse

    @POST("api/agent/stop")
    suspend fun stopAgent(
        @Body request: StopAgentRequest
    ): StopAgentResponse

    @POST("api/ai/ask")
    suspend fun askAi(
        @Body request: AskAiRequest
    ): AskAiResponse

    @POST("api/questions/generate")
    suspend fun generateQuestions(
        @Body request: GenerateQuestionsRequest
    ): GenerateQuestionsResponse

    @POST("api/answers/evaluate")
    suspend fun evaluateAnswer(
        @Body request: EvaluateAnswerRequest
    ): EvaluateAnswerResponse
}
