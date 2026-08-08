package com.agro.interviewer.data.parser

import com.agro.interviewer.domain.model.MessageSender
import com.agro.interviewer.domain.model.TranscriptMessage
import org.json.JSONObject
import timber.log.Timber

object TranscriptParser {

    fun parse(uid: Int, data: ByteArray, agentUid: Int): TranscriptMessage? {
        return try {
            val jsonString = String(data, Charsets.UTF_8).trim()
            if (!jsonString.startsWith("{")) return null

            val json = JSONObject(jsonString)

            val turnId = json.optInt("turn_id", json.optInt("turnId", 0))
            val isFinal = json.optBoolean("is_final", json.optBoolean("isFinal", true))

            var role = json.optString("role", "")
            var content = ""

            val targetJson = if (json.has("payload")) {
                json.optJSONObject("payload") ?: json
            } else if (json.has("data")) {
                json.optJSONObject("data") ?: json
            } else {
                json
            }

            if (targetJson.has("choices")) {
                val choices = targetJson.optJSONArray("choices")
                val firstChoice = choices?.optJSONObject(0)
                val delta = firstChoice?.optJSONObject("delta") ?: firstChoice?.optJSONObject("message")
                if (delta != null) {
                    role = delta.optString("role", role)
                    content = delta.optString("content", "")
                }
            }
            
            if (content.isBlank() && targetJson.has("transcript")) {
                content = targetJson.optString("transcript", "")
            }
            if (content.isBlank() && targetJson.has("text")) {
                content = targetJson.optString("text", "")
            }
            if (content.isBlank() && targetJson.has("content")) {
                content = targetJson.optString("content", "")
            }

            if (content.isBlank()) return null

            val sender = when {
                uid == agentUid || role == "assistant" || role == "agent" -> MessageSender.AGENT
                else -> MessageSender.USER
            }

            TranscriptMessage(
                id = "msg_${System.currentTimeMillis()}_${(0..999).random()}",
                sender = sender,
                text = content,
                isFinal = isFinal,
                turnId = turnId
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse transcript stream packet")
            null
        }
    }
}
