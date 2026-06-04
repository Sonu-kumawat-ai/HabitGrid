package com.example.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "habit_logs",
    primaryKeys = ["taskId", "date"],
    foreignKeys = [
        ForeignKey(
            entity = HabitTask::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class HabitLog(
    val taskId: Int,
    val date: String, // String representation format "YYYY-MM-DD"
    val status: String, // "Complete", "Partial", "Missed"
    val updatedAt: Long = System.currentTimeMillis()
)
