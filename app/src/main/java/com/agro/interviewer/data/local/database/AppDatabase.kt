package com.agro.interviewer.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.agro.interviewer.data.local.database.dao.InterviewSessionDao
import com.agro.interviewer.data.local.database.dao.QuestionAnswerDao
import com.agro.interviewer.data.local.database.entity.InterviewSessionEntity
import com.agro.interviewer.data.local.database.entity.QuestionAnswerEntity

@Database(
    entities = [
        InterviewSessionEntity::class,
        QuestionAnswerEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun interviewSessionDao(): InterviewSessionDao
    abstract fun questionAnswerDao(): QuestionAnswerDao

    companion object {
        const val DATABASE_NAME = "interview_coach.db"
    }
}
