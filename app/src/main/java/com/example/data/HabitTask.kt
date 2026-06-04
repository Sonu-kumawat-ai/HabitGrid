package com.example.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "habit_tasks",
    foreignKeys = [
        ForeignKey(
            entity = HabitSet::class,
            parentColumns = ["id"],
            childColumns = ["habitSetId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class HabitTask(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val habitSetId: Int,
    val name: String,
    val description: String = "",
    val startDate: String, // String representation format "YYYY-MM-DD"
    val endDate: String? = null,
    val activeWeekdays: String // Comma separated list, e.g. "Monday,Wednesday,Friday"
)
