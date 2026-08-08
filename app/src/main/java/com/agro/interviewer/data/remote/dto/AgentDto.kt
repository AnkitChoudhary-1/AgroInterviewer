package com.agro.interviewer.data.remote.dto

data class StartAgentRequest(
    val channelName: String,
    val userUid: Int,
    val topic: String,
    val difficulty: String,
    val totalQuestions: Int
)

data class StartAgentResponse(
    val taskId: String,
    val agentUid: Int,
    val status: String
)

data class StopAgentRequest(
    val taskId: String
)

data class StopAgentResponse(
    val taskId: String,
    val status: String
)
