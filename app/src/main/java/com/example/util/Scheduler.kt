package com.example.util

import com.example.data.HabitTask
import java.time.LocalDate

object Scheduler {

    /**
     * Checks if a habit task is scheduled for a given LocalDate.
     * Rule:
     * - Current Date >= Task Start Date
     * - Current Date <= Task End Date (if End Date exists)
     * - Current Day matches one of the selected weekdays
     */
    fun isTaskScheduled(task: HabitTask, date: LocalDate): Boolean {
        val targetDateStr = date.toString() // "YYYY-MM-DD"

        // 1. Current Date >= Task Start Date
        if (targetDateStr < task.startDate) return false

        // 2. Current Date <= Task End Date
        if (task.endDate != null && task.endDate.isNotBlank() && targetDateStr > task.endDate) {
            return false
        }

        // 3. Current Day matches selected weekdays
        val currentDayString = date.dayOfWeek.name // MONDAY, TUESDAY, etc.
        val selectedWeekdays = task.activeWeekdays
            .split(",")
            .map { it.trim().uppercase() }

        return selectedWeekdays.contains(currentDayString)
    }
}
