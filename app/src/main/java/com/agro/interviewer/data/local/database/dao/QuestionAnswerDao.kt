package com.agro.interviewer.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.agro.interviewer.data.local.database.entity.QuestionAnswerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface QuestionAnswerDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnswer(answer: QuestionAnswerEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnswers(answers: List<QuestionAnswerEntity>)

    @Query("SELECT * FROM question_answers WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getAnswersForSession(sessionId: String): List<QuestionAnswerEntity>

    @Query("SELECT * FROM question_answers WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getAnswersForSessionFlow(sessionId: String): Flow<List<QuestionAnswerEntity>>

    @Query("DELETE FROM question_answers WHERE sessionId = :sessionId")
    suspend fun deleteAnswersForSession(sessionId: String)
}
