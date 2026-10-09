package com.renotify.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RuleDao {

    @Query("SELECT * FROM rules ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<Rule>>

    @Query("SELECT * FROM rules")
    suspend fun getAll(): List<Rule>

    @Insert
    suspend fun insert(rule: Rule): Long

    @Update
    suspend fun update(rule: Rule)

    @Query("DELETE FROM rules WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE rules SET matchCount = matchCount + 1 WHERE id = :id")
    suspend fun incrementMatch(id: Long)

    @Query("UPDATE rules SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("UPDATE rules SET delivery = :delivery WHERE id = :id")
    suspend fun setDelivery(id: Long, delivery: Int)

    @Query("DELETE FROM rules WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)
}
