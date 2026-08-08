package com.agro.interviewer.domain.model

data class Question(
    val id: String,
    val text: String,
    val category: QuestionCategory,
    val difficulty: Difficulty,
    val tags: List<String> = emptyList(),
    val modelAnswer: String,
    val followUps: List<String> = emptyList(),
    val estimatedSeconds: Int = 60
)

enum class QuestionCategory(
    val displayName: String
) {
    KOTLIN("Kotlin"),
    ANDROID_FUNDAMENTALS("Android Fundamentals"),
    JETPACK_COMPOSE("Jetpack Compose"),
    ARCHITECTURE("Architecture"),
    COROUTINES("Coroutines & Flow"),
    TESTING("Testing"),
    PERFORMANCE("Performance"),
    SYSTEM_DESIGN("System Design")
}

