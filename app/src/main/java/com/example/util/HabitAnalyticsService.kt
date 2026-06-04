package com.example.util

import com.example.data.HabitLog
import com.example.data.HabitSetWithTasks
import com.example.data.HabitTask
import java.time.LocalDate

object HabitAnalyticsService {

    /**
     * Checks if a status represents completion.
     */
    fun isCompletedStatus(status: String?): Boolean {
        if (status == null) return false
        val s = status.lowercase()
        return s == "complete" || s == "completed"
    }

    /**
     * Checks if a status represents partial completion.
     */
    fun isPartialStatus(status: String?): Boolean {
        if (status == null) return false
        val s = status.lowercase()
        return s == "partial"
    }

    /**
     * Checks if a status represents being missed.
     */
    fun isMissedStatus(status: String?): Boolean {
        if (status == null) return false
        val s = status.lowercase()
        return s == "missed"
    }

    /**
     * Active Day Logic:
     * A day is considered an Active Day for a Habit Set only if at least one task inside the set is scheduled on that date.
     */
    fun isActiveDayForHabitSet(
        setWithTasks: HabitSetWithTasks,
        date: LocalDate
    ): Boolean {
        return setWithTasks.tasks.any { Scheduler.isTaskScheduled(it, date) }
    }

    /**
     * Perfect Day (Strike) Logic:
     * A Perfect Day occurs only when 100% of scheduled tasks for that Habit Set are marked Completed.
     * Rest days (days with 0 tasks scheduled) are not considered active and return null.
     */
    fun isPerfectDayForHabitSet(
        setWithTasks: HabitSetWithTasks,
        logs: List<HabitLog>,
        date: LocalDate
    ): Boolean? {
        val scheduledTasks = setWithTasks.tasks.filter { Scheduler.isTaskScheduled(it, date) }
        if (scheduledTasks.isEmpty()) return null // Rest Day

        val logMapForDay = logs.filter { it.date == date.toString() }.associateBy { it.taskId }
        return scheduledTasks.all { task ->
            val log = logMapForDay[task.id]
            isCompletedStatus(log?.status)
        }
    }

    /**
     * Current Streak Calculation for a Habit Set scanning backward from today/referenceDate.
     * Rules:
     * - Perfect Day = +1 streak
     * - Rest Day = Skip (rest days do not break streak)
     * - Failed Active Day = Stop calculation immediately
     */
    fun calculateCurrentStreak(
        habitSetWithTasks: HabitSetWithTasks,
        logs: List<HabitLog>,
        referenceDate: LocalDate = LocalDate.now()
    ): Int {
        val tasks = habitSetWithTasks.tasks
        if (tasks.isEmpty()) return 0

        val setStartDate = try {
            LocalDate.parse(habitSetWithTasks.habitSet.startDate)
        } catch (e: Exception) {
            LocalDate.now()
        }
        val tasksStartDate = tasks.mapNotNull {
            try { LocalDate.parse(it.startDate) } catch (e: Exception) { null }
        }.minOrNull() ?: setStartDate
        val earliestDate = if (setStartDate.isBefore(tasksStartDate)) setStartDate else tasksStartDate

        val logMap = logs.associateBy { it.taskId to it.date }

        var current = referenceDate
        var streak = 0
        var safetyCounter = 0

        while (!current.isBefore(earliestDate) && safetyCounter < 10000) {
            safetyCounter++
            val scheduledTasks = tasks.filter { Scheduler.isTaskScheduled(it, current) }
            if (scheduledTasks.isEmpty()) {
                // Rest Day: Skip (ignore in streak calculation)
                current = current.minusDays(1)
                continue
            }

            // Active Day: Check if Perfect
            val allComplete = scheduledTasks.all { task ->
                val log = logMap[task.id to current.toString()]
                isCompletedStatus(log?.status)
            }

            if (allComplete) {
                streak++
                current = current.minusDays(1)
            } else {
                // It is not a Perfect Day. Let's see if we should stop.
                if (current == referenceDate) {
                    // Today is in progress. If there are explicit failures (Partial or Missed), break the streak.
                    // Otherwise, skip today's increment but stay active (yesterday's streak remains intact).
                    val hasFailures = scheduledTasks.any { task ->
                        val log = logMap[task.id to current.toString()]
                        val status = log?.status
                        isPartialStatus(status) || isMissedStatus(status)
                    }
                    if (hasFailures) {
                        break
                    } else {
                        current = current.minusDays(1)
                    }
                } else {
                    // Past active days must be perfect; otherwise, the streak stops.
                    break
                }
            }
        }
        return streak
    }

    /**
     * Current Streak Calculation for an Individual Task scanning backward from today/referenceDate.
     */
    fun calculateTaskCurrentStreak(
        task: HabitTask,
        logs: List<HabitLog>,
        referenceDate: LocalDate = LocalDate.now()
    ): Int {
        val taskStartDate = try {
            LocalDate.parse(task.startDate)
        } catch (e: Exception) {
            LocalDate.now()
        }

        val logMap = logs.filter { it.taskId == task.id }.associateBy { it.date }

        var current = referenceDate
        var streak = 0
        var safetyCounter = 0

        while (!current.isBefore(taskStartDate) && safetyCounter < 10000) {
            safetyCounter++
            if (!Scheduler.isTaskScheduled(task, current)) {
                // Rest Day: Skip
                current = current.minusDays(1)
                continue
            }

            val status = logMap[current.toString()]?.status
            if (isCompletedStatus(status)) {
                streak++
                current = current.minusDays(1)
            } else {
                if (current == referenceDate) {
                    if (isPartialStatus(status) || isMissedStatus(status)) {
                        break
                    } else {
                        current = current.minusDays(1)
                    }
                } else {
                    break
                }
            }
        }
        return streak
    }

    /**
     * Best Streak Calculation for a Habit Set scanning forward from start to referenceDate.
     * Rules:
     * - Perfect Day = Increment
     * - Failed Day = Reset
     * - Rest Day = Ignore
     */
    fun calculateBestStreak(
        habitSetWithTasks: HabitSetWithTasks,
        logs: List<HabitLog>,
        referenceDate: LocalDate = LocalDate.now()
    ): Int {
        val tasks = habitSetWithTasks.tasks
        if (tasks.isEmpty()) return 0

        val setStartDate = try {
            LocalDate.parse(habitSetWithTasks.habitSet.startDate)
        } catch (e: Exception) {
            LocalDate.now()
        }
        val tasksStartDate = tasks.mapNotNull {
            try { LocalDate.parse(it.startDate) } catch (e: Exception) { null }
        }.minOrNull() ?: setStartDate
        val earliestDate = if (setStartDate.isBefore(tasksStartDate)) setStartDate else tasksStartDate

        val logMap = logs.associateBy { it.taskId to it.date }

        var current = earliestDate
        var tempStreak = 0
        var bestStreak = 0
        var safetyCounter = 0

        while (!current.isAfter(referenceDate) && safetyCounter < 10000) {
            safetyCounter++
            val scheduledTasks = tasks.filter { Scheduler.isTaskScheduled(it, current) }
            if (scheduledTasks.isEmpty()) {
                // Rest Day: Ignore (keep streak intact, do not increment or reset)
                current = current.plusDays(1)
                continue
            }

            // Active Day
            val allComplete = scheduledTasks.all { task ->
                val log = logMap[task.id to current.toString()]
                isCompletedStatus(log?.status)
            }

            if (allComplete) {
                tempStreak++
                if (tempStreak > bestStreak) {
                    bestStreak = tempStreak
                }
            } else {
                if (current == referenceDate) {
                    // Today is in progress. Reset temp streak only if hard failure exists.
                    val hasFailures = scheduledTasks.any { task ->
                        val log = logMap[task.id to current.toString()]
                        val status = log?.status
                        isPartialStatus(status) || isMissedStatus(status)
                    }
                    if (hasFailures) {
                        tempStreak = 0
                    }
                } else {
                    // Failed active day in the past resets streak
                    tempStreak = 0
                }
            }
            current = current.plusDays(1)
        }
        return bestStreak
    }

    /**
     * Best Streak Calculation for an Individual Task scanning forward from start to referenceDate.
     */
    fun calculateTaskBestStreak(
        task: HabitTask,
        logs: List<HabitLog>,
        referenceDate: LocalDate = LocalDate.now()
    ): Int {
        val taskStartDate = try {
            LocalDate.parse(task.startDate)
        } catch (e: Exception) {
            LocalDate.now()
        }

        val logMap = logs.filter { it.taskId == task.id }.associateBy { it.date }

        var current = taskStartDate
        var tempStreak = 0
        var bestStreak = 0
        var safetyCounter = 0

        while (!current.isAfter(referenceDate) && safetyCounter < 10000) {
            safetyCounter++
            if (!Scheduler.isTaskScheduled(task, current)) {
                // Rest Day
                current = current.plusDays(1)
                continue
            }

            val status = logMap[current.toString()]?.status
            if (isCompletedStatus(status)) {
                tempStreak++
                if (tempStreak > bestStreak) {
                    bestStreak = tempStreak
                }
            } else {
                if (current == referenceDate) {
                    if (isPartialStatus(status) || isMissedStatus(status)) {
                        tempStreak = 0
                    }
                } else {
                    tempStreak = 0
                }
            }
            current = current.plusDays(1)
        }
        return bestStreak
    }

    /**
     * Formula:
     * Productivity Score = (Total Points Earned / Total Scheduled Tasks) * 100
     * Scoring:
     * - Completed = 1.0 (status matches Complete/Completed)
     * - Partial = 0.5 (status matches Partial)
     * - Incomplete = 0 (Missed or untouched)
     */
    
    /**
     * Calculates Overall Productivity for all active Habit Sets over a specific date range.
     */
    fun calculateOverallProductivity(
        habitSetsWithTasks: List<HabitSetWithTasks>,
        logs: List<HabitLog>,
        startDate: LocalDate,
        endDate: LocalDate
    ): Int {
        var totalPoints = 0.0
        var totalScheduled = 0

        val logMap = logs.associateBy { it.taskId to it.date }
        val activeSets = habitSetsWithTasks.filter { it.habitSet.status == "Active" }

        var current = startDate
        while (!current.isAfter(endDate)) {
            for (setWithTasks in activeSets) {
                val scheduledTasks = setWithTasks.tasks.filter { Scheduler.isTaskScheduled(it, current) }
                for (task in scheduledTasks) {
                    totalScheduled++
                    val log = logMap[task.id to current.toString()]
                    totalPoints += when {
                        isCompletedStatus(log?.status) -> 1.0
                        isPartialStatus(log?.status) -> 0.5
                        else -> 0.0
                    }
                }
            }
            current = current.plusDays(1)
        }

        if (totalScheduled == 0) return 0
        return Math.round((totalPoints / totalScheduled) * 100).toInt()
    }

    /**
     * Calculates Habit Set Productivity for a specific Habit Set over a date range.
     */
    fun calculateHabitSetProductivity(
        habitSetWithTasks: HabitSetWithTasks,
        logs: List<HabitLog>,
        startDate: LocalDate,
        endDate: LocalDate
    ): Int {
        var totalPoints = 0.0
        var totalScheduled = 0

        val logMap = logs.associateBy { it.taskId to it.date }
        val tasks = habitSetWithTasks.tasks

        var current = startDate
        while (!current.isAfter(endDate)) {
            val scheduledTasks = tasks.filter { Scheduler.isTaskScheduled(it, current) }
            for (task in scheduledTasks) {
                totalScheduled++
                val log = logMap[task.id to current.toString()]
                totalPoints += when {
                    isCompletedStatus(log?.status) -> 1.0
                    isPartialStatus(log?.status) -> 0.5
                    else -> 0.0
                }
            }
            current = current.plusDays(1)
        }

        if (totalScheduled == 0) return 0
        return Math.round((totalPoints / totalScheduled) * 100).toInt()
    }

    /**
     * Calculates Task Productivity for a specific task over 1 or more dates.
     */
    fun calculateTaskProductivity(
        task: HabitTask,
        logs: List<HabitLog>,
        startDate: LocalDate,
        endDate: LocalDate
    ): Int {
        var totalPoints = 0.0
        var totalScheduled = 0

        val logMap = logs.filter { it.taskId == task.id }.associateBy { it.date }

        var current = startDate
        while (!current.isAfter(endDate)) {
            if (Scheduler.isTaskScheduled(task, current)) {
                totalScheduled++
                val log = logMap[current.toString()]
                totalPoints += when {
                    isCompletedStatus(log?.status) -> 1.0
                    isPartialStatus(log?.status) -> 0.5
                    else -> 0.0
                }
            }
            current = current.plusDays(1)
        }

        if (totalScheduled == 0) return 0
        return Math.round((totalPoints / totalScheduled) * 100).toInt()
    }

    /**
     * Calculates Daily Productivity across all active habit sets on a specific date.
     */
    fun calculateDailyProductivity(
        habitSetsWithTasks: List<HabitSetWithTasks>,
        logs: List<HabitLog>,
        date: LocalDate
    ): Int {
        var totalPoints = 0.0
        var totalScheduled = 0

        val logMap = logs.associateBy { it.taskId to it.date }
        val activeSets = habitSetsWithTasks.filter { it.habitSet.status == "Active" }

        for (setWithTasks in activeSets) {
            val scheduledTasks = setWithTasks.tasks.filter { Scheduler.isTaskScheduled(it, date) }
            for (task in scheduledTasks) {
                totalScheduled++
                val log = logMap[task.id to date.toString()]
                totalPoints += when {
                    isCompletedStatus(log?.status) -> 1.0
                    isPartialStatus(log?.status) -> 0.5
                    else -> 0.0
                }
            }
        }

        if (totalScheduled == 0) return 0
        return Math.round((totalPoints / totalScheduled) * 100).toInt()
    }
}
