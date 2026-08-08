package com.agro.interviewer.domain.model

data class SessionResult(
    val session: InterviewSession,
    val overallScore: Float,
    val scoreBreakdown: ScoreBreakdown,
    val strengths: List<String>,
    val improvements: List<String>,
    val durationSeconds: Int,
    val questionsAttempted: Int,
    val grade: PerformanceGrade
)

data class ScoreBreakdown(
    val correctness: Float,
    val completeness: Float,
    val clarity: Float,
    val confidence: Float
)

enum class PerformanceGrade(
    val label: String,
    val colorHex: Long
) {
    EXCELLENT("Excellent", 0xFF4CAF50),
    GOOD("Good", 0xFF8BC34A),
    AVERAGE("Average", 0xFFFF9800),
    NEEDS_WORK("Needs Work", 0xFFF44336)
}

fun Float.toGrade(): PerformanceGrade = when {
    this >= 8.5f -> PerformanceGrade.EXCELLENT
    this >= 7.0f -> PerformanceGrade.GOOD
    this >= 5.0f -> PerformanceGrade.AVERAGE
    else -> PerformanceGrade.NEEDS_WORK
}
