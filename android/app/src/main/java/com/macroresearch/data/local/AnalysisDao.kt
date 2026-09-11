package com.macroresearch.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AnalysisDao {
    @Query("SELECT * FROM ai_analysis WHERE eventId = :eventId")
    fun observe(eventId: Long): Flow<AiAnalysisEntity?>

    @Query("SELECT * FROM ai_analysis WHERE eventId = :eventId")
    suspend fun analysis(eventId: Long): AiAnalysisEntity?

    @Upsert
    suspend fun upsert(analysis: AiAnalysisEntity)
}
