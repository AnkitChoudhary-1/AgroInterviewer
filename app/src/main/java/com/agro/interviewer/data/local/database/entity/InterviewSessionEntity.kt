package com.agro.interviewer.data.local.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "interview_sessions")
data class InterviewSessionEntity(
    @PrimaryKey val id: String,
    val startedAt: Long,
    val endedAt: Long?,
    val topic: String,
    val category: String,
    val difficulty: String,
    val totalQuestions: Int,
    val channelName: String,
    val agentTaskId: String,
    val status: String,
    val overallScore: Float,
    val durationSeconds: Int
)
