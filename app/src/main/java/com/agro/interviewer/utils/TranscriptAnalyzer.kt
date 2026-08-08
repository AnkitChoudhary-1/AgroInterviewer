package com.agro.interviewer.utils

import com.agro.interviewer.domain.model.MessageSender
import com.agro.interviewer.domain.model.QuestionAnswer
import com.agro.interviewer.domain.model.TranscriptMessage
import timber.log.Timber

/**
 * Parses the raw voice transcript to extract Q&A pairs and scores.
 * The AI is instructed to include scores like "Score: 7/10" in its feedback,
 * which we extract here. If not present, we estimate from sentiment.
 */
object TranscriptAnalyzer {

    private val SCORE_PATTERNS = listOf(
        Regex("""[Ss]core[:\s]+(\d+)\s*/\s*10"""),
        Regex("""(\d+)\s*out\s*of\s*10"""),
        Regex("""(\d+)/10"""),
        Regex("""[Rr]ating[:\s]+(\d+)""")
    )

    data class ExtractedQA(
        val questionText: String,
        val userAnswer: String,
        val aiFeedback: String,
        val score: Int,
        val turnId: Int
    )

    fun extractQAPairs(
        transcript: List<TranscriptMessage>,
        sessionId: String
    ): List<QuestionAnswer> {
        if (transcript.isEmpty()) return emptyList()

        val extracted = groupIntoQAPairs(transcript)

        return extracted.mapIndexed { index, qa ->
            QuestionAnswer(
                sessionId = sessionId,
                questionId = "q_${sessionId}_${index + 1}",
                questionText = qa.questionText,
                userAnswer = qa.userAnswer,
                aiFeedback = qa.aiFeedback,
                score = qa.score
            )
        }
    }

    fun extractScore(feedbackText: String): Int {
        for (pattern in SCORE_PATTERNS) {
            val match = pattern.find(feedbackText)
            if (match != null) {
                val score = match.groupValues[1].toIntOrNull()
                if (score != null && score in 0..10) {
                    Timber.d("TranscriptAnalyzer: score=$score from feedback")
                    return score
                }
            }
        }
        return estimateScoreFromSentiment(feedbackText)
    }

    private fun groupIntoQAPairs(transcript: List<TranscriptMessage>): List<ExtractedQA> {
        val pairs = mutableListOf<ExtractedQA>()
        val finalMessages = transcript.filter { it.isFinal }

        var currentQuestion: TranscriptMessage? = null
        var currentUserAnswer = StringBuilder()
        var currentFeedback = StringBuilder()
        var lastAgentTurnId = -1

        for (message in finalMessages) {
            when (message.sender) {
                MessageSender.AGENT -> {
                    val isNewTurn = message.turnId != lastAgentTurnId
                    if (isNewTurn) {
                        if (currentQuestion != null && currentUserAnswer.isNotBlank()) {
                            val feedbackText = currentFeedback.toString().trim()
                            pairs.add(
                                ExtractedQA(
                                    questionText = currentQuestion!!.text,
                                    userAnswer = currentUserAnswer.toString().trim(),
                                    aiFeedback = feedbackText,
                                    score = extractScore(feedbackText),
                                    turnId = currentQuestion!!.turnId
                                )
                            )
                        }

                        if (looksLikeQuestion(message.text)) {
                            currentQuestion = message
                            currentUserAnswer = StringBuilder()
                            currentFeedback = StringBuilder()
                        } else {
                            currentFeedback.append(message.text).append(" ")
                        }
                        lastAgentTurnId = message.turnId
                    } else {
                        currentFeedback.append(message.text).append(" ")
                    }
                }
                MessageSender.USER -> {
                    currentUserAnswer.append(message.text).append(" ")
                }
            }
        }

        if (currentQuestion != null && currentUserAnswer.isNotBlank()) {
            val feedbackText = currentFeedback.toString().trim()
            pairs.add(
                ExtractedQA(
                    questionText = currentQuestion!!.text,
                    userAnswer = currentUserAnswer.toString().trim(),
                    aiFeedback = feedbackText,
                    score = extractScore(feedbackText),
                    turnId = currentQuestion!!.turnId
                )
            )
        }

        Timber.d("TranscriptAnalyzer: ${pairs.size} Q&A pairs extracted")
        return pairs
    }

    private fun looksLikeQuestion(text: String): Boolean {
        val trimmed = text.trim()
        val questionWords = listOf(
            "what", "how", "why", "when", "where", "which",
            "explain", "describe", "tell me", "can you", "could you",
            "have you", "do you", "would you"
        )
        return trimmed.endsWith("?") || questionWords.any { trimmed.lowercase().startsWith(it) }
    }

    private fun estimateScoreFromSentiment(text: String): Int {
        val lower = text.lowercase()
        val positiveKeywords = listOf(
            "excellent", "perfect", "outstanding", "great", "good",
            "correct", "right", "well done", "spot on", "accurate",
            "comprehensive", "thorough", "clear"
        )
        val negativeKeywords = listOf(
            "incorrect", "wrong", "missing", "incomplete", "poor",
            "confused", "misunderstood", "not quite", "unfortunately",
            "lacking", "should have", "forgot"
        )
        val pos = positiveKeywords.count { lower.contains(it) }
        val neg = negativeKeywords.count { lower.contains(it) }

        return when {
            pos >= 3 && neg == 0 -> 9
            pos >= 2 && neg <= 1 -> 7
            pos >= 1 && neg <= 1 -> 6
            pos == 0 && neg == 0 -> 5
            neg >= 3 -> 2
            neg >= 2 -> 3
            else -> 4
        }
    }
}
