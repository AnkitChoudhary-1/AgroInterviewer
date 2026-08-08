package com.agro.interviewer.domain.model

data class AgentState(
    val status: AgentStatus = AgentStatus.IDLE,
    val agentUid: Int = 0,
    val taskId: String = "",
    val isSpeaking: Boolean = false,
    val isListening: Boolean = false,
    val currentQuestion: Int = 0,
    val totalQuestions: Int = 5,
    val errorMessage: String? = null
)

enum class AgentStatus {
    IDLE,
    STARTING,
    ACTIVE,
    STOPPING,
    STOPPED,
    FAILED
}
