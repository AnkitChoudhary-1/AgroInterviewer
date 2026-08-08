package com.agro.interviewer.domain.model

import java.util.UUID

data class InterviewSession(
    val id: String = UUID.randomUUID().toString(),
    val startedAt: Long = System.currentTimeMillis(),
    val endedAt: Long? = null,
    val topic: String,
    val category: QuestionCategory,
    val difficulty: Difficulty,
    val totalQuestions: Int,
    val channelName: String,
    val agentTaskId: String = "",
    val status: SessionStatus = SessionStatus.IN_PROGRESS,
    val questionAnswers: List<QuestionAnswer> = emptyList()
)

data class QuestionAnswer(
    val id: String = UUID.randomUUID().toString(),
    val sessionId: String,
    val questionId: String,
    val questionText: String,
    val userAnswer: String,
    val aiFeedback: String,
    val score: Int,
    val timestamp: Long = System.currentTimeMillis()
)

enum class SessionStatus {
    IN_PROGRESS,
    COMPLETED,
    ABANDONED
}
