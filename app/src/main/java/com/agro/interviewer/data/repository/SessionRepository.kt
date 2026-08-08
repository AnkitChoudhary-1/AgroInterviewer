package com.agro.interviewer.data.repository

import com.agro.interviewer.data.local.database.dao.InterviewSessionDao
import com.agro.interviewer.data.local.database.dao.QuestionAnswerDao
import com.agro.interviewer.data.local.database.entity.InterviewSessionEntity
import com.agro.interviewer.data.local.database.entity.QuestionAnswerEntity
import com.agro.interviewer.data.local.datastore.UserPreferences
import com.agro.interviewer.domain.model.Difficulty
import com.agro.interviewer.domain.model.InterviewSession
import com.agro.interviewer.domain.model.QuestionAnswer
import com.agro.interviewer.domain.model.QuestionCategory
import com.agro.interviewer.domain.model.ScoreBreakdown
import com.agro.interviewer.domain.model.SessionResult
import com.agro.interviewer.domain.model.SessionStatus
import com.agro.interviewer.domain.model.toGrade
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionRepository @Inject constructor(
    private val sessionDao: InterviewSessionDao,
    private val questionAnswerDao: QuestionAnswerDao,
    private val userPreferences: UserPreferences
) {

    suspend fun createSession(session: InterviewSession): String {
        sessionDao.insertSession(session.toEntity(overallScore = 0f, durationSeconds = 0))
        Timber.d("Created session %s", session.id)
        return session.id
    }

    suspend fun getSessionById(sessionId: String): InterviewSessionEntity? {
        return sessionDao.getSessionById(sessionId)
    }

    suspend fun saveQuestionAnswer(answer: QuestionAnswer) {
        questionAnswerDao.insertAnswer(answer.toEntity())
    }

    suspend fun completeSession(
        sessionId: String,
        endedAt: Long = System.currentTimeMillis()
    ): SessionResult? {
        val entity = sessionDao.getSessionById(sessionId) ?: return null
        val answerEntities = questionAnswerDao.getAnswersForSession(sessionId)

        val answers = answerEntities.map { it.toDomain() }
        val overallScore = if (answers.isNotEmpty()) answers.map { it.score.toFloat() }.average().toFloat() else 0.0f
        val durationSeconds = ((endedAt - entity.startedAt) / 1000).toInt()

        sessionDao.updateSession(
            entity.copy(
                endedAt = endedAt,
                status = SessionStatus.COMPLETED.name,
                overallScore = overallScore,
                durationSeconds = durationSeconds
            )
        )

        userPreferences.incrementCompletedSessions()
        return buildResult(entity.toDomain(answers), answers, overallScore, durationSeconds)
    }

    suspend fun abandonSession(sessionId: String) {
        val entity = sessionDao.getSessionById(sessionId) ?: return
        sessionDao.updateSession(
            entity.copy(
                endedAt = System.currentTimeMillis(),
                status = SessionStatus.ABANDONED.name
            )
        )
    }

    fun getRecentSessions(): Flow<List<InterviewSession>> {
        return sessionDao.getRecentCompletedSessions(limit = 20).map { entities ->
            entities.map { entity ->
                val answers = questionAnswerDao.getAnswersForSession(entity.id)
                entity.toDomain(answers.map { it.toDomain() })
            }
        }
    }

    suspend fun getSessionResult(sessionId: String): SessionResult? {
        val entity = sessionDao.getSessionById(sessionId) ?: return null
        val answers = questionAnswerDao.getAnswersForSession(sessionId).map { it.toDomain() }
        return buildResult(entity.toDomain(answers), answers, entity.overallScore, entity.durationSeconds)
    }

    suspend fun getAverageScore(): Float = sessionDao.getAverageScore() ?: 0f

    suspend fun getCompletedSessionCount(): Int = sessionDao.getCompletedSessionCount()

    private fun buildResult(
        session: InterviewSession,
        answers: List<QuestionAnswer>,
        overallScore: Float,
        durationSeconds: Int
    ): SessionResult {
        val breakdown = ScoreBreakdown(
            correctness = if (answers.isNotEmpty()) answers.map { it.score.toFloat() }.average().toFloat() else 0.0f,
            completeness = if (answers.isNotEmpty()) (overallScore * 0.9f).coerceIn(0f, 10f) else 0.0f,
            clarity = if (answers.isNotEmpty()) (overallScore * 0.85f).coerceIn(0f, 10f) else 0.0f,
            confidence = if (answers.isNotEmpty()) (overallScore * 0.8f).coerceIn(0f, 10f) else 0.0f
        )

        val strengths = if (answers.isNotEmpty()) {
            answers.filter { it.score >= 7 }.take(3).map { "Strong in: ${it.questionText}" }
                .ifEmpty { listOf("Demonstrated good technical baseline") }
        } else {
            listOf("No questions attempted during session")
        }

        val improvements = if (answers.isNotEmpty()) {
            answers.filter { it.score < 7 }.take(3).map { "Review: ${it.questionText}" }
                .ifEmpty { listOf("Continue practicing deep technical nuances") }
        } else {
            listOf("Attempt questions during session to receive feedback")
        }

        return SessionResult(
            session = session,
            overallScore = overallScore,
            scoreBreakdown = breakdown,
            strengths = strengths,
            improvements = improvements,
            durationSeconds = durationSeconds,
            questionsAttempted = maxOf(answers.size, 1),
            grade = overallScore.toGrade()
        )
    }

    private fun InterviewSession.toEntity(overallScore: Float, durationSeconds: Int) = InterviewSessionEntity(
        id = id,
        startedAt = startedAt,
        endedAt = endedAt,
        topic = topic,
        category = category.name,
        difficulty = difficulty.name,
        totalQuestions = totalQuestions,
        channelName = channelName,
        agentTaskId = agentTaskId,
        status = status.name,
        overallScore = overallScore,
        durationSeconds = durationSeconds
    )

    private fun InterviewSessionEntity.toDomain(answers: List<QuestionAnswer> = emptyList()) = InterviewSession(
        id = id,
        startedAt = startedAt,
        endedAt = endedAt,
        topic = topic,
        category = QuestionCategory.valueOf(category),
        difficulty = Difficulty.valueOf(difficulty),
        totalQuestions = totalQuestions,
        channelName = channelName,
        agentTaskId = agentTaskId,
        status = SessionStatus.valueOf(status),
        questionAnswers = answers
    )

    private fun QuestionAnswer.toEntity() = QuestionAnswerEntity(
        id = id,
        sessionId = sessionId,
        questionId = questionId,
        questionText = questionText,
        userAnswer = userAnswer,
        aiFeedback = aiFeedback,
        score = score,
        timestamp = timestamp
    )

    private fun QuestionAnswerEntity.toDomain() = QuestionAnswer(
        id = id,
        sessionId = sessionId,
        questionId = questionId,
        questionText = questionText,
        userAnswer = userAnswer,
        aiFeedback = aiFeedback,
        score = score,
        timestamp = timestamp
    )
}
