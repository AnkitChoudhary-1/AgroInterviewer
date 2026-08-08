package com.agro.interviewer.domain.model

import java.util.UUID

data class TranscriptMessage(
    val id: String = UUID.randomUUID().toString(),
    val sender: MessageSender,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isFinal: Boolean = true,
    val turnId: Int = 0
)

enum class MessageSender {
    USER,
    AGENT
}
