package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitDao {

    @Transaction
    @Query("SELECT * FROM habit_sets ORDER BY id DESC")
    fun getHabitSetsWithTasks(): Flow<List<HabitSetWithTasks>>

    @Transaction
    @Query("SELECT * FROM habit_sets WHERE id = :id LIMIT 1")
    fun getHabitSetWithTasksById(id: Int): Flow<HabitSetWithTasks?>

    @Query("SELECT * FROM habit_sets WHERE id = :id LIMIT 1")
    suspend fun getHabitSetById(id: Int): HabitSet?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHabitSet(habitSet: HabitSet): Long

    @Update
    suspend fun updateHabitSet(habitSet: HabitSet)

    @Delete
    suspend fun deleteHabitSet(habitSet: HabitSet)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHabitTask(habitTask: HabitTask): Long

    @Update
    suspend fun updateHabitTask(habitTask: HabitTask)

    @Delete
    suspend fun deleteHabitTask(habitTask: HabitTask)

    @Query("DELETE FROM habit_tasks WHERE id = :id")
    suspend fun deleteHabitTaskById(id: Int)

    @Query("SELECT * FROM habit_logs")
    fun getAllLogs(): Flow<List<HabitLog>>

    @Query("SELECT * FROM habit_logs")
    suspend fun getAllLogsList(): List<HabitLog>

    @Query("SELECT * FROM habit_logs WHERE date = :date")
    fun getLogsForDate(date: String): Flow<List<HabitLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateLog(log: HabitLog)

    @Query("DELETE FROM habit_logs WHERE taskId = :taskId AND date = :date")
    suspend fun deleteLog(taskId: Int, date: String)
}
