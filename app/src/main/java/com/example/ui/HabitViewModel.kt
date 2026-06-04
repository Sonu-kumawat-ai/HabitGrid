package com.example.ui

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class HabitViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: HabitRepository

    val searchQuery = MutableStateFlow("")
    val sortOrder = MutableStateFlow("Name") // "Name", "Streak", "Tasks count"
    val statusFilter = MutableStateFlow("All") // "All", "Active", "Completed", "Archived"

    // UI-level navigation state or selected detail set
    val selectedHabitSetId = MutableStateFlow<Int?>(null)
    val isCreatingHabitSetGlobal = MutableStateFlow(false)
    val isCreatingTaskQuickly = MutableStateFlow(false)
    val selectedTab = MutableStateFlow("Home")

    val sharedPrefs: SharedPreferences = application.getSharedPreferences("habitgrid_settings", Context.MODE_PRIVATE)

    // Notification Settings
    val notificationsEnabled = MutableStateFlow(sharedPrefs.getBoolean("settings_notifications_enabled", false))
    val dailyRemindersEnabled = MutableStateFlow(sharedPrefs.getBoolean("settings_daily_reminders_enabled", false))
    val dailyRemindersTime = MutableStateFlow(sharedPrefs.getString("settings_daily_reminders_time", "09:00 AM") ?: "09:00 AM")
    val missedRemindersEnabled = MutableStateFlow(sharedPrefs.getBoolean("settings_missed_reminders_enabled", false))
    val missedRemindersFrequency = MutableStateFlow(sharedPrefs.getString("settings_missed_reminders_frequency", "Once Every 2 Hours") ?: "Once Every 2 Hours")
    val dailySummaryEnabled = MutableStateFlow(sharedPrefs.getBoolean("settings_daily_summary_enabled", false))
    val dailySummaryTime = MutableStateFlow(sharedPrefs.getString("settings_daily_summary_time", "10:00 PM") ?: "10:00 PM")

    // Reminder Preferences
    val reminderFrequency = MutableStateFlow(sharedPrefs.getString("settings_reminder_frequency", "Once Per Day") ?: "Once Per Day")
    val reminderStartTime = MutableStateFlow(sharedPrefs.getString("settings_reminder_start_time", "09:00 AM") ?: "09:00 AM")
    val reminderEndTime = MutableStateFlow(sharedPrefs.getString("settings_reminder_end_time", "09:00 PM") ?: "09:00 PM")

    // Live Wallpaper Settings
    val wallpaperActive = MutableStateFlow(sharedPrefs.getBoolean("settings_wallpaper_active", false))
    val wallpaperShowCurrentStreak = MutableStateFlow(sharedPrefs.getBoolean("settings_wallpaper_show_current_streak", false))
    val wallpaperShowBestStreak = MutableStateFlow(sharedPrefs.getBoolean("settings_wallpaper_show_best_streak", false))
    val wallpaperShowProductivity = MutableStateFlow(sharedPrefs.getBoolean("settings_wallpaper_show_productivity", false))
    val wallpaperShowProgressBar = MutableStateFlow(sharedPrefs.getBoolean("settings_wallpaper_show_progress_bar", false))
    val wallpaperShowTasks = MutableStateFlow(sharedPrefs.getBoolean("settings_wallpaper_show_tasks", true))
    val wallpaperShowTotalDaysLeft = MutableStateFlow(sharedPrefs.getBoolean("settings_wallpaper_show_total_days_left", true))
    val wallpaperShowDateLabels = MutableStateFlow(sharedPrefs.getBoolean("settings_wallpaper_show_date_labels", false))
    
    val wallpaperDataSource = MutableStateFlow(sharedPrefs.getString("settings_wallpaper_data_source", "Overall Habits") ?: "Overall Habits")
    val wallpaperSelectedHabitSet = MutableStateFlow(sharedPrefs.getString("settings_wallpaper_selected_habit_set", "") ?: "")
    
    val wallpaperTheme = MutableStateFlow(sharedPrefs.getString("settings_wallpaper_theme", "Warm HabitGrid") ?: "Warm HabitGrid")
    val wallpaperStatsPosition = MutableStateFlow(sharedPrefs.getString("settings_wallpaper_stats_position", "Below Grid") ?: "Below Grid")

    // Appearance & Theme Settings
    val themeMode = MutableStateFlow(sharedPrefs.getString("settings_theme_mode", "System Default") ?: "System Default")

    // Basic Settings
    val autoMarkMissed = MutableStateFlow(sharedPrefs.getBoolean("settings_auto_mark_missed", true))
    val deleteConfirmation = MutableStateFlow(sharedPrefs.getBoolean("settings_delete_confirmation", true))

    // Home Task Sorting preference
    val homeTaskSortOrder = MutableStateFlow(sharedPrefs.getString("settings_home_task_sort_order", "Status") ?: "Status")

    fun saveSetting(key: String, value: Any) {
        sharedPrefs.edit().apply {
            when (value) {
                is Boolean -> putBoolean(key, value)
                is String -> putString(key, value)
                is Int -> putInt(key, value)
                is Float -> putFloat(key, value)
                is Long -> putLong(key, value)
            }
            apply()
        }
        
        // Update flows
        when (key) {
            "settings_notifications_enabled" -> notificationsEnabled.value = value as Boolean
            "settings_daily_reminders_enabled" -> dailyRemindersEnabled.value = value as Boolean
            "settings_daily_reminders_time" -> dailyRemindersTime.value = value as String
            "settings_missed_reminders_enabled" -> missedRemindersEnabled.value = value as Boolean
            "settings_missed_reminders_frequency" -> missedRemindersFrequency.value = value as String
            "settings_daily_summary_enabled" -> dailySummaryEnabled.value = value as Boolean
            "settings_daily_summary_time" -> dailySummaryTime.value = value as String
            
            "settings_reminder_frequency" -> reminderFrequency.value = value as String
            "settings_reminder_start_time" -> reminderStartTime.value = value as String
            "settings_reminder_end_time" -> reminderEndTime.value = value as String
            
            "settings_wallpaper_active" -> wallpaperActive.value = value as Boolean
            "settings_wallpaper_show_current_streak" -> wallpaperShowCurrentStreak.value = value as Boolean
            "settings_wallpaper_show_best_streak" -> wallpaperShowBestStreak.value = value as Boolean
            "settings_wallpaper_show_productivity" -> wallpaperShowProductivity.value = value as Boolean
            "settings_wallpaper_show_progress_bar" -> wallpaperShowProgressBar.value = value as Boolean
            "settings_wallpaper_show_tasks" -> wallpaperShowTasks.value = value as Boolean
            "settings_wallpaper_show_total_days_left" -> wallpaperShowTotalDaysLeft.value = value as Boolean
            "settings_wallpaper_show_date_labels" -> wallpaperShowDateLabels.value = value as Boolean
            "settings_wallpaper_data_source" -> wallpaperDataSource.value = value as String
            "settings_wallpaper_selected_habit_set" -> wallpaperSelectedHabitSet.value = value as String
            "settings_wallpaper_theme" -> wallpaperTheme.value = value as String
            "settings_wallpaper_stats_position" -> wallpaperStatsPosition.value = value as String
            
            "settings_theme_mode" -> themeMode.value = value as String
            "settings_auto_mark_missed" -> autoMarkMissed.value = value as Boolean
            "settings_delete_confirmation" -> deleteConfirmation.value = value as Boolean
            "settings_home_task_sort_order" -> homeTaskSortOrder.value = value as String
        }
        com.example.notification.NotificationHelper.scheduleAllEnabledNotifications(getApplication())
    }

    fun resetSettings() {
        sharedPrefs.edit().clear().apply()
        
        // Notify all StateFlows with default values
        notificationsEnabled.value = false
        dailyRemindersEnabled.value = false
        dailyRemindersTime.value = "09:00 AM"
        missedRemindersEnabled.value = false
        missedRemindersFrequency.value = "Once Per Day"
        dailySummaryEnabled.value = false
        
        reminderFrequency.value = "Once Per Day"
        reminderStartTime.value = "09:00 AM"
        reminderEndTime.value = "09:00 PM"
        
        wallpaperActive.value = false
        wallpaperShowCurrentStreak.value = false
        wallpaperShowBestStreak.value = false
        wallpaperShowProductivity.value = false
        wallpaperShowProgressBar.value = false
        wallpaperShowTasks.value = true
        wallpaperShowTotalDaysLeft.value = true
        wallpaperShowDateLabels.value = false
        
        wallpaperDataSource.value = "Overall Habits"
        wallpaperSelectedHabitSet.value = ""
        wallpaperTheme.value = "Warm HabitGrid"
        wallpaperStatsPosition.value = "Below Grid"
        
        themeMode.value = "System Default"
        autoMarkMissed.value = true
        deleteConfirmation.value = true
        homeTaskSortOrder.value = "Status"
        
        // Ensure default properties are newly saved
        sharedPrefs.edit().apply {
            putBoolean("settings_notifications_enabled", false)
            putBoolean("settings_daily_reminders_enabled", false)
            putString("settings_daily_reminders_time", "09:00 AM")
            putBoolean("settings_missed_reminders_enabled", false)
            putString("settings_missed_reminders_frequency", "Once Every 2 Hours")
            putBoolean("settings_daily_summary_enabled", false)
            putString("settings_daily_summary_time", "10:00 PM")
            putString("settings_reminder_frequency", "Once Per Day")
            putString("settings_reminder_start_time", "09:00 AM")
            putString("settings_reminder_end_time", "09:00 PM")
            putBoolean("settings_wallpaper_active", false)
            putBoolean("settings_wallpaper_show_current_streak", false)
            putBoolean("settings_wallpaper_show_best_streak", false)
            putBoolean("settings_wallpaper_show_productivity", false)
            putBoolean("settings_wallpaper_show_progress_bar", false)
            putBoolean("settings_wallpaper_show_tasks", true)
            putBoolean("settings_wallpaper_show_total_days_left", true)
            putBoolean("settings_wallpaper_show_date_labels", false)
            putString("settings_wallpaper_data_source", "Overall Habits")
            putString("settings_wallpaper_selected_habit_set", "")
            putString("settings_wallpaper_theme", "Warm HabitGrid")
            putString("settings_wallpaper_stats_position", "Below Grid")
            putString("settings_theme_mode", "System Default")
            putBoolean("settings_auto_mark_missed", true)
            putBoolean("settings_delete_confirmation", true)
            putString("settings_home_task_sort_order", "Status")
            apply()
        }
        com.example.notification.NotificationHelper.scheduleAllEnabledNotifications(getApplication())
    }

    init {
        val database = AppDatabase.getDatabase(application)
        repository = HabitRepository(database.habitDao())
        com.example.notification.NotificationHelper.createNotificationChannel(application)
        com.example.notification.NotificationHelper.scheduleAllEnabledNotifications(application)
        viewModelScope.launch {
            detectAndMarkMissed()
        }
    }

    // Combine all list, search filter and sort
    val filteredHabitSets: StateFlow<List<HabitSetWithTasks>> = combine(
        repository.allHabitSetsWithTasks,
        searchQuery,
        sortOrder,
        statusFilter
    ) { sets, query, sort, status ->
        var list = sets

        // 1. Search filter
        if (query.isNotBlank()) {
            list = list.filter {
                it.habitSet.name.contains(query, ignoreCase = true) ||
                it.habitSet.description.contains(query, ignoreCase = true)
            }
        }

        // 2. Status filter
        if (status != "All") {
            list = list.filter { it.habitSet.status.equals(status, ignoreCase = true) }
        }

        // 3. Sort
        when (sort) {
            "Name" -> list.sortedBy { it.habitSet.name.lowercase() }
            "Streak" -> list.sortedByDescending { it.habitSet.currentStreak }
            "Tasks count" -> list.sortedByDescending { it.tasks.size }
            else -> list
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Active details set
    val activeHabitSetWithTasks: StateFlow<HabitSetWithTasks?> = selectedHabitSetId
        .flatMapLatest { id ->
            if (id == null) flowOf(null)
            else repository.getHabitSetWithTasksById(id)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    // Mutations - HabitSet
    fun createHabitSet(name: String, description: String, startDate: String, endDate: String?, priority: Int? = null) {
        viewModelScope.launch {
            repository.insertHabitSet(
                HabitSet(
                    name = name,
                    description = description,
                    startDate = startDate,
                    endDate = endDate,
                    status = "Active",
                    currentStreak = 0,
                    priority = priority
                )
            )
        }
    }

    fun updateHabitSet(habitSet: HabitSet) {
        viewModelScope.launch {
            repository.updateHabitSet(habitSet)
        }
    }

    fun deleteHabitSet(habitSet: HabitSet) {
        viewModelScope.launch {
            repository.deleteHabitSet(habitSet)
            if (selectedHabitSetId.value == habitSet.id) {
                selectedHabitSetId.value = null
            }
        }
    }

    fun archiveHabitSet(habitSet: HabitSet) {
        viewModelScope.launch {
            repository.updateHabitSet(habitSet.copy(status = "Archived"))
        }
    }

    fun activateHabitSet(habitSet: HabitSet) {
        viewModelScope.launch {
            repository.updateHabitSet(habitSet.copy(status = "Active"))
        }
    }

    // Mutations - Tasks
    fun createTask(
        habitSetId: Int,
        name: String,
        description: String,
        startDate: String,
        endDate: String?,
        activeWeekdays: List<String>
    ) {
        viewModelScope.launch {
            repository.insertHabitTask(
                HabitTask(
                    habitSetId = habitSetId,
                    name = name,
                    description = description,
                    startDate = startDate,
                    endDate = endDate,
                    activeWeekdays = activeWeekdays.joinToString(",")
                )
            )
        }
    }

    fun updateTask(
        task: HabitTask,
        name: String,
        description: String,
        startDate: String,
        endDate: String?,
        activeWeekdays: List<String>
    ) {
        viewModelScope.launch {
            repository.updateHabitTask(
                task.copy(
                    name = name,
                    description = description,
                    startDate = startDate,
                    endDate = endDate,
                    activeWeekdays = activeWeekdays.joinToString(",")
                )
            )
        }
    }

    fun deleteTask(task: HabitTask) {
        viewModelScope.launch {
            repository.deleteHabitTask(task)
        }
    }

    fun duplicateTask(task: HabitTask) {
        viewModelScope.launch {
            repository.insertHabitTask(
                HabitTask(
                    habitSetId = task.habitSetId,
                    name = "${task.name} (Copy)",
                    description = task.description,
                    startDate = task.startDate,
                    endDate = task.endDate,
                    activeWeekdays = task.activeWeekdays
                )
            )
        }
    }

    // Today's Date String for persistent logs
    val todayDateStr = java.time.LocalDate.now().toString()

    // Flow of logs for today
    val todayLogs: StateFlow<List<HabitLog>> = repository.getLogsForDate(todayDateStr)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Flow of all logs for analytics calculations
    val allLogs: StateFlow<List<HabitLog>> = repository.getAllLogs()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Flow of all unfiltered habit sets with tasks for global dashboard calculations
    val unfilteredHabitSets: StateFlow<List<HabitSetWithTasks>> = repository.allHabitSetsWithTasks
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun updateTaskStatus(taskId: Int, status: String) {
        viewModelScope.launch {
            repository.insertOrUpdateLog(HabitLog(taskId = taskId, date = todayDateStr, status = status))
            updateStreakForTaskSet(taskId)
        }
    }

    fun updateTaskStatusForDate(taskId: Int, date: String, status: String) {
        if (date != todayDateStr) {
            // Locking System: historical records are read-only
            return
        }
        viewModelScope.launch {
            if (status == "None") {
                repository.deleteLog(taskId = taskId, date = date)
            } else {
                repository.insertOrUpdateLog(HabitLog(taskId = taskId, date = date, status = status))
            }
            updateStreakForTaskSet(taskId)
        }
    }

    private fun updateStreakForTaskSet(taskId: Int) {
        viewModelScope.launch {
            try {
                val setsWithTasks = repository.allHabitSetsWithTasks.first()
                val targetTask = setsWithTasks.flatMap { it.tasks }.find { it.id == taskId }
                if (targetTask != null) {
                    val matchedSet = setsWithTasks.find { it.habitSet.id == targetTask.habitSetId }
                    if (matchedSet != null) {
                        val logs = repository.getAllLogsList()
                        val newStreak = com.example.util.HabitAnalyticsService.calculateCurrentStreak(matchedSet, logs, java.time.LocalDate.now())
                        repository.updateHabitSet(matchedSet.habitSet.copy(currentStreak = newStreak))
                    }
                }
            } catch (e: Exception) {
                // Fail-safe protection inside async scope
            }
        }
    }

    suspend fun detectAndMarkMissed() {
        if (!autoMarkMissed.value) {
            return
        }
        val setsWithTasks = repository.allHabitSetsWithTasks.first()
        val allLogs = repository.getAllLogsList()
        val logMap = allLogs.associateBy { it.taskId to it.date }
        val yesterday = java.time.LocalDate.now().minusDays(1)

        for (setWithTasks in setsWithTasks) {
            if (setWithTasks.habitSet.status != "Active") continue

            for (task in setWithTasks.tasks) {
                val startDate = try {
                    java.time.LocalDate.parse(task.startDate)
                } catch (e: Exception) {
                    java.time.LocalDate.now()
                }

                // Cap checkback to last 30 days to keep database operations fast & smooth
                val limit = java.time.LocalDate.now().minusDays(30)
                val checkStart = if (startDate.isBefore(limit)) limit else startDate

                var date = checkStart
                while (!date.isAfter(yesterday)) {
                    val dateStr = date.toString()
                    if (com.example.util.Scheduler.isTaskScheduled(task, date)) {
                        val existingLog = logMap[task.id to dateStr]
                        if (existingLog == null) {
                            repository.insertOrUpdateLog(
                                HabitLog(
                                    taskId = task.id,
                                    date = dateStr,
                                    status = "Missed"
                                )
                            )
                        }
                    }
                    date = date.plusDays(1)
                }
            }
        }
    }
}
