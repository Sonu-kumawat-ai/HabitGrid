package com.example.data

import kotlinx.coroutines.flow.Flow

class HabitRepository(private val habitDao: HabitDao) {

    val allHabitSetsWithTasks: Flow<List<HabitSetWithTasks>> = habitDao.getHabitSetsWithTasks()

    fun getHabitSetWithTasksById(id: Int): Flow<HabitSetWithTasks?> {
        return habitDao.getHabitSetWithTasksById(id)
    }

    suspend fun insertHabitSet(habitSet: HabitSet): Long {
        return habitDao.insertHabitSet(habitSet)
    }

    suspend fun updateHabitSet(habitSet: HabitSet) {
        habitDao.updateHabitSet(habitSet)
    }

    suspend fun deleteHabitSet(habitSet: HabitSet) {
        habitDao.deleteHabitSet(habitSet)
    }

    suspend fun insertHabitTask(habitTask: HabitTask): Long {
        return habitDao.insertHabitTask(habitTask)
    }

    suspend fun updateHabitTask(habitTask: HabitTask) {
        habitDao.updateHabitTask(habitTask)
    }

    suspend fun deleteHabitTask(habitTask: HabitTask) {
        habitDao.deleteHabitTask(habitTask)
    }

    suspend fun deleteHabitTaskById(id: Int) {
        habitDao.deleteHabitTaskById(id)
    }

    fun getAllLogs(): Flow<List<HabitLog>> {
        return habitDao.getAllLogs()
    }

    suspend fun getAllLogsList(): List<HabitLog> {
        return habitDao.getAllLogsList()
    }

    fun getLogsForDate(date: String): Flow<List<HabitLog>> {
        return habitDao.getLogsForDate(date)
    }

    suspend fun insertOrUpdateLog(log: HabitLog) {
        habitDao.insertOrUpdateLog(log)
    }

    suspend fun deleteLog(taskId: Int, date: String) {
        habitDao.deleteLog(taskId, date)
    }
}
