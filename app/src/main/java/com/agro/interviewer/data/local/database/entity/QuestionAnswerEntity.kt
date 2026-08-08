package com.agro.interviewer.data.local.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "question_answers",
    foreignKeys = [
        ForeignKey(
            entity = InterviewSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sessionId")]
)
data class QuestionAnswerEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val questionId: String,
    val questionText: String,
    val userAnswer: String,
    val aiFeedback: String,
    val score: Int,
    val timestamp: Long
)
