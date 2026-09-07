package com.macroresearch.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {
    @Query("SELECT * FROM cached_event WHERE eventTime >= :from ORDER BY eventTime, importance DESC")
    fun observeUpcoming(from: String): Flow<List<CachedEventEntity>>

    @Query("SELECT * FROM cached_event WHERE id = :id")
    fun observeEvent(id: Long): Flow<CachedEventEntity?>

    @Upsert
    suspend fun upsert(events: List<CachedEventEntity>)

    @Query("DELETE FROM cached_event WHERE eventTime < :before")
    suspend fun deleteOlderThan(before: String)

    @Query("SELECT EXISTS(SELECT 1 FROM followed_event WHERE eventId = :eventId)")
    fun observeFollowed(eventId: Long): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM followed_event WHERE eventId = :eventId)")
    suspend fun isFollowed(eventId: Long): Boolean

    @Upsert
    suspend fun follow(event: FollowedEventEntity)

    @Query("DELETE FROM followed_event WHERE eventId = :eventId")
    suspend fun unfollow(eventId: Long)
}
