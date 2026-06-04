package com.example

import com.example.data.HabitLog
import com.example.data.HabitSet
import com.example.data.HabitSetWithTasks
import com.example.data.HabitTask
import com.example.util.HabitAnalyticsService
import com.example.util.Scheduler
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class HabitAnalyticsServiceTest {

    @Test
    fun testIsTaskScheduled() {
        // Task scheduled on Mondays, Tuesdays and Wednesdays
        val task = HabitTask(
            id = 1,
            habitSetId = 1,
            name = "Morning Stretching",
            startDate = "2026-06-01", // a Monday
            endDate = "2026-06-10",
            activeWeekdays = "Monday,Tuesday,Wednesday"
        )

        // 2026-06-01 is Monday -> Should be scheduled
        assertTrue(Scheduler.isTaskScheduled(task, LocalDate.parse("2026-06-01")))

        // 2026-06-04 is Thursday -> Should not be scheduled
        assertFalse(Scheduler.isTaskScheduled(task, LocalDate.parse("2026-06-04")))

        // 2026-05-31 is Sunday (before startDate) -> Should not be scheduled
        assertFalse(Scheduler.isTaskScheduled(task, LocalDate.parse("2026-05-31")))

        // 2026-06-11 is Thursday (after endDate) -> Should not be scheduled
        assertFalse(Scheduler.isTaskScheduled(task, LocalDate.parse("2026-06-11")))
    }

    @Test
    fun testIsPerfectDayForHabitSet() {
        val habitSet = HabitSet(id = 1, name = "Daily Health", startDate = "2026-06-01")
        val task1 = HabitTask(
            id = 101,
            habitSetId = 1,
            name = "Drink Water",
            startDate = "2026-06-01",
            activeWeekdays = "Monday,Tuesday,Wednesday,Thursday,Friday,Saturday,Sunday"
        )
        val task2 = HabitTask(
            id = 102,
            habitSetId = 1,
            name = "Pushups",
            startDate = "2026-06-01",
            activeWeekdays = "Monday,Tuesday,Wednesday,Thursday,Friday,Saturday,Sunday"
        )
        val setWithTasks = HabitSetWithTasks(habitSet, listOf(task1, task2))

        val date = LocalDate.parse("2026-06-01")

        // No logs -> All untouched (not perfect)
        var logs = emptyList<HabitLog>()
        assertFalse(HabitAnalyticsService.isPerfectDayForHabitSet(setWithTasks, logs, date)!!)

        // Only one task completed -> Not perfect
        logs = listOf(
            HabitLog(taskId = 101, date = "2026-06-01", status = "Complete")
        )
        assertFalse(HabitAnalyticsService.isPerfectDayForHabitSet(setWithTasks, logs, date)!!)

        // One completed, one partial -> Not perfect
        logs = listOf(
            HabitLog(taskId = 101, date = "2026-06-01", status = "Complete"),
            HabitLog(taskId = 102, date = "2026-06-01", status = "Partial")
        )
        assertFalse(HabitAnalyticsService.isPerfectDayForHabitSet(setWithTasks, logs, date)!!)

        // Both completed -> Perfect!
        logs = listOf(
            HabitLog(taskId = 101, date = "2026-06-01", status = "Complete"),
            HabitLog(taskId = 102, date = "2026-06-01", status = "Completed") // check if Completed behaves correctly
        )
        assertTrue(HabitAnalyticsService.isPerfectDayForHabitSet(setWithTasks, logs, date)!!)
    }

    @Test
    fun testCurrentStreakWithRestDays() {
        val habitSet = HabitSet(id = 1, name = "Gym Routine", startDate = "2026-06-01")
        // Scheduled on Monday and Wednesday only
        val task = HabitTask(
            id = 1,
            habitSetId = 1,
            name = "Lift Weights",
            startDate = "2026-06-01", // Mon
            activeWeekdays = "Monday,Wednesday"
        )
        val setWithTasks = HabitSetWithTasks(habitSet, listOf(task))

        // Dates:
        // 2026-06-01, Monday (Active Day) -> Completed
        // 2026-06-02, Tuesday (Rest Day) -> Ignore (No log needed)
        // 2026-06-03, Wednesday (Active Day) -> Completed
        val logs = listOf(
            HabitLog(taskId = 1, date = "2026-06-01", status = "Complete"),
            HabitLog(taskId = 1, date = "2026-06-03", status = "Complete")
        )

        // As of Wednesday, June 3 (Wednesday being reference date and completed)
        val streak = HabitAnalyticsService.calculateCurrentStreak(
            setWithTasks,
            logs,
            referenceDate = LocalDate.parse("2026-06-03")
        )
        // Monday + Wednesday = 2 active perfect days. Rest day (Tuesday) skipped. Streak should be 2!
        assertEquals(2, streak)
    }

    @Test
    fun testStreakWithTodayInProgress() {
        val habitSet = HabitSet(id = 1, name = "Reading Routine", startDate = "2026-06-01")
        val task = HabitTask(
            id = 1,
            habitSetId = 1,
            name = "Read 10 pages",
            startDate = "2026-06-01", // Mon
            activeWeekdays = "Monday,Tuesday,Wednesday"
        )
        val setWithTasks = HabitSetWithTasks(habitSet, listOf(task))

        // Logs: Mon (completed), Tue (completed), Wed (today - untouched / no logs yet)
        val logs = listOf(
            HabitLog(taskId = 1, date = "2026-06-01", status = "Complete"),
            HabitLog(taskId = 1, date = "2026-06-02", status = "Complete")
        )

        val referenceDate = LocalDate.parse("2026-06-03") // Wed

        val streak = HabitAnalyticsService.calculateCurrentStreak(
            setWithTasks,
            logs,
            referenceDate = referenceDate
        )
        // Wednesday is active but untouched. It should not break the streak.
        // It skips to Tuesday. Tuesday & Monday are perfect. Streak should be 2!
        assertEquals(2, streak)
    }

    @Test
    fun testStreakWithTodayFailed() {
        val habitSet = HabitSet(id = 1, name = "Reading Routine", startDate = "2026-06-01")
        val task = HabitTask(
            id = 1,
            habitSetId = 1,
            name = "Read 10 pages",
            startDate = "2026-06-01",
            activeWeekdays = "Monday,Tuesday,Wednesday"
        )
        val setWithTasks = HabitSetWithTasks(habitSet, listOf(task))

        // Logs: Mon (completed), Tue (completed), Wed (today - marked as Missed)
        val logs = listOf(
            HabitLog(taskId = 1, date = "2026-06-01", status = "Complete"),
            HabitLog(taskId = 1, date = "2026-06-02", status = "Complete"),
            HabitLog(taskId = 1, date = "2026-06-03", status = "Missed")
        )

        val referenceDate = LocalDate.parse("2026-06-03") // Wed

        val streak = HabitAnalyticsService.calculateCurrentStreak(
            setWithTasks,
            logs,
            referenceDate = referenceDate
        )
        // Today is failed, so streak is broken immediately -> 0
        assertEquals(0, streak)
    }

    @Test
    fun testBestStreakCalculation() {
        val habitSet = HabitSet(id = 1, name = "Gym Routine", startDate = "2026-06-01")
        val task = HabitTask(
            id = 1,
            habitSetId = 1,
            name = "Lift Weights",
            startDate = "2026-06-01",
            activeWeekdays = "Monday,Tuesday,Wednesday,Thursday,Friday"
        )
        val setWithTasks = HabitSetWithTasks(habitSet, listOf(task))

        // Logs:
        // Mon (01) -> Complete
        // Tue (02) -> Complete
        // Wed (03) -> Missed (Reset!)
        // Thu (04) -> Complete
        // Fri (05) -> Complete
        // Sat/Sun are Rest Days
        // Mon (08) -> Complete
        // Tue (09) -> Complete
        val logs = listOf(
            HabitLog(taskId = 1, date = "2026-06-01", status = "Complete"),
            HabitLog(taskId = 1, date = "2026-06-02", status = "Complete"),
            HabitLog(taskId = 1, date = "2026-06-03", status = "Missed"),
            HabitLog(taskId = 1, date = "2026-06-04", status = "Complete"),
            HabitLog(taskId = 1, date = "2026-06-05", status = "Complete"),
            HabitLog(taskId = 1, date = "2026-06-08", status = "Complete"),
            HabitLog(taskId = 1, date = "2026-06-09", status = "Complete")
        )

        val bestStreak = HabitAnalyticsService.calculateBestStreak(
            setWithTasks,
            logs,
            referenceDate = LocalDate.parse("2026-06-09")
        )

        // Best streak is Thu, Fri, Mon, Tue = 4! (Since Sat/Sun are rest days, they don't reset or count).
        assertEquals(4, bestStreak)
    }

    @Test
    fun testProductivityScoreCalculation() {
        val habitSet = HabitSet(id = 1, name = "Hybrid Routine", startDate = "2026-06-01")
        val task = HabitTask(
            id = 1,
            habitSetId = 1,
            name = "Task 1",
            startDate = "2026-06-01",
            activeWeekdays = "Monday,Tuesday,Wednesday,Thursday"
        )
        val setWithTasks = HabitSetWithTasks(habitSet, listOf(task))

        // Logs:
        // Mon 01 -> Complete (1.0 points)
        // Tue 02 -> Partial (0.5 points)
        // Wed 03 -> Missed (0.0 points)
        // Thu 04 -> Complete (1.0 points)
        // Total points earned: 1.0 + 0.5 + 0.0 + 1.0 = 2.5
        // Total scheduled: 4
        // Formula: (2.5 / 4) * 100 = 62.5% -> round to 63
        val logs = listOf(
            HabitLog(taskId = 1, date = "2026-06-01", status = "Complete"),
            HabitLog(taskId = 1, date = "2026-06-02", status = "Partial"),
            HabitLog(taskId = 1, date = "2026-06-03", status = "Missed"),
            HabitLog(taskId = 1, date = "2026-06-04", status = "Complete")
        )

        val score = HabitAnalyticsService.calculateHabitSetProductivity(
            setWithTasks,
            logs,
            startDate = LocalDate.parse("2026-06-01"),
            endDate = LocalDate.parse("2026-06-04")
        )

        assertEquals(63, score)
    }
}
