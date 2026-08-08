package com.agro.interviewer.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.agro.interviewer.data.local.database.entity.InterviewSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface InterviewSessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: InterviewSessionEntity)

    @Update
    suspend fun updateSession(session: InterviewSessionEntity)

    @Query("SELECT * FROM interview_sessions ORDER BY startedAt DESC")
    fun getAllSessionsFlow(): Flow<List<InterviewSessionEntity>>

    @Query("SELECT * FROM interview_sessions WHERE id = :sessionId")
    suspend fun getSessionById(sessionId: String): InterviewSessionEntity?

    @Query("SELECT * FROM interview_sessions WHERE status = 'COMPLETED' ORDER BY startedAt DESC LIMIT :limit")
    fun getRecentCompletedSessions(limit: Int = 10): Flow<List<InterviewSessionEntity>>

    @Query("SELECT AVG(overallScore) FROM interview_sessions WHERE status = 'COMPLETED'")
    suspend fun getAverageScore(): Float?

    @Query("DELETE FROM interview_sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: String)

    @Query("SELECT COUNT(*) FROM interview_sessions WHERE status = 'COMPLETED'")
    suspend fun getCompletedSessionCount(): Int
}
