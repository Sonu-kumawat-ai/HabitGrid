package com.example.notification

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.AppDatabase
import com.example.data.HabitLog
import com.example.data.HabitSetWithTasks
import com.example.util.HabitAnalyticsService
import com.example.util.Scheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

class NotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        val action = intent.action ?: return
        Log.d("NotificationReceiver", "Received broadcast action: $action")

        val sharedPrefs = context.getSharedPreferences("habitgrid_settings", Context.MODE_PRIVATE)
        val notificationsEnabled = sharedPrefs.getBoolean("settings_notifications_enabled", false)

        // If master switch is OFF, do not post any notification at all!
        if (!notificationsEnabled && action != Intent.ACTION_BOOT_COMPLETED) {
            Log.d("NotificationReceiver", "Notifications are disabled globally. Ignoring.")
            return
        }

        when (action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                // On boot, reschedule all notifications
                NotificationHelper.createNotificationChannel(context)
                NotificationHelper.scheduleAllEnabledNotifications(context)
            }
            "com.example.notification.TRIGGER_DAILY_REMINDER" -> {
                val dailyEnabled = sharedPrefs.getBoolean("settings_daily_reminders_enabled", false)
                if (dailyEnabled) {
                    val title = sharedPrefs.getString("settings_custom_daily_reminder_title", "Daily Reminder! ☀️") ?: "Daily Reminder! ☀️"
                    val body = sharedPrefs.getString("settings_custom_daily_reminder_body", "Don't forget today's habits.") ?: "Don't forget today's habits."
                    showNotification(context, 1001, title, body)
                    // Reschedule for tomorrow
                    NotificationHelper.scheduleAllEnabledNotifications(context)
                }
            }
            "com.example.notification.TRIGGER_MISSED_REMINDER" -> {
                val missedEnabled = sharedPrefs.getBoolean("settings_missed_reminders_enabled", false)
                if (missedEnabled) {
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val db = AppDatabase.getDatabase(context)
                            val dao = db.habitDao()
                            val allSets = dao.getHabitSetsWithTasks().first()
                            val allLogsList = dao.getAllLogsList()

                            val todayStr = LocalDate.now().toString()
                            val todayLogs = allLogsList.filter { it.date == todayStr }.associateBy { it.taskId }

                            val activeSets = allSets.filter { it.habitSet.status == "Active" }
                            val scheduledToday = activeSets.flatMap { set ->
                                set.tasks.filter { Scheduler.isTaskScheduled(it, LocalDate.now()) }
                            }

                            val remainingCount = scheduledToday.count { task ->
                                val log = todayLogs[task.id]
                                log == null || log.status != "Complete"
                            }

                            if (remainingCount > 0) {
                                val templateTitle = sharedPrefs.getString("settings_custom_missed_reminder_title", "Remaining Tasks! ⚠️") ?: "Remaining Tasks! ⚠️"
                                val templateBody = sharedPrefs.getString("settings_custom_missed_reminder_body", "You still have {remaining} tasks remaining today.") ?: "You still have {remaining} tasks remaining today."

                                val finalTitle = templateTitle.replace("{remaining}", remainingCount.toString())
                                val finalBody = templateBody.replace("{remaining}", remainingCount.toString())

                                showNotification(context, 1002, finalTitle, finalBody)
                            }
                        } catch (e: Exception) {
                            Log.e("NotificationReceiver", "Error sending missed tasks reminder: ${e.message}", e)
                        }
                    }
                }
            }
            "com.example.notification.TRIGGER_DAILY_SUMMARY" -> {
                val summaryEnabled = sharedPrefs.getBoolean("settings_daily_summary_enabled", false)
                if (summaryEnabled) {
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val db = AppDatabase.getDatabase(context)
                            val dao = db.habitDao()
                            val allSets = dao.getHabitSetsWithTasks().first()
                            val allLogsList = dao.getAllLogsList()

                            val today = LocalDate.now()
                            val activeSets = allSets.filter { it.habitSet.status == "Active" }

                            val todayStr = LocalDate.now().toString()
                            val todayLogs = allLogsList.filter { it.date == todayStr }.associateBy { it.taskId }

                            var totalScheduled = 0
                            var totalCompleted = 0
                            var totalPoints = 0.0

                            for (set in activeSets) {
                                val scheduledTasks = set.tasks.filter { Scheduler.isTaskScheduled(it, today) }
                                for (task in scheduledTasks) {
                                    totalScheduled++
                                    val status = todayLogs[task.id]?.status
                                    if (HabitAnalyticsService.isCompletedStatus(status)) {
                                        totalPoints += 1.0
                                        totalCompleted++
                                    } else if (HabitAnalyticsService.isPartialStatus(status)) {
                                        totalPoints += 0.5
                                    }
                                }
                            }

                            val todayProductivity = if (totalScheduled > 0) ((totalPoints / totalScheduled) * 100).toInt() else 0
                            val remaining = totalScheduled - totalCompleted

                            val finalTitle = "Daily Progress Snapshot"
                            val finalBody = "Productivity: $todayProductivity% | Tasks: $totalCompleted completed, $remaining remaining"

                            showNotification(context, 1003, finalTitle, finalBody)
                            // Reschedule for next day
                            NotificationHelper.scheduleAllEnabledNotifications(context)
                        } catch (e: Exception) {
                            Log.e("NotificationReceiver", "Error sending daily summary: ${e.message}", e)
                        }
                    }
                }
            }
        }
    }

    private fun showNotification(context: Context, id: Int, title: String, body: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, NotificationHelper.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm) // Using safe standard built-in android icon to avoid resource missing crashes
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(id, notification)
    }
}
