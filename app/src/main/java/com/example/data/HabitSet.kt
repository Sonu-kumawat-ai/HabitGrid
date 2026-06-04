package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "habit_sets")
data class HabitSet(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val description: String = "",
    val startDate: String, // String representation format "YYYY-MM-DD"
    val endDate: String? = null, // null means indefinite
    val status: String = "Active", // "Active", "Completed", "Archived"
    val currentStreak: Int = 0,
    val priority: Int? = null
)
