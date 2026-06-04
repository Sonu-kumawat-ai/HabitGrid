package com.example.data

import androidx.room.Embedded
import androidx.room.Relation

data class HabitSetWithTasks(
    @Embedded val habitSet: HabitSet,
    @Relation(
        parentColumn = "id",
        entityColumn = "habitSetId"
    )
    val tasks: List<HabitTask>
)
