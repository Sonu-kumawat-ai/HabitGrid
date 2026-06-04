package com.example.notification

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.time.LocalTime
import java.util.*

object NotificationHelper {
    private const val TAG = "NotificationHelper"
    const val CHANNEL_ID = "habitgrid_reminders"
    const val CHANNEL_NAME = "HabitGrid Reminders"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = "Channels for HabitGrid daily check-ins, remaining habits, and daily summary report"
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun scheduleAllEnabledNotifications(context: Context) {
        val sharedPrefs = context.getSharedPreferences("habitgrid_settings", Context.MODE_PRIVATE)
        val notificationsEnabled = sharedPrefs.getBoolean("settings_notifications_enabled", false)

        Log.d(TAG, "Scheduling enabled notifications. Master status: $notificationsEnabled")

        // If master is disabled, clean up everything and exit
        if (!notificationsEnabled) {
            cancelAllScheduledNotifications(context)
            return
        }

        // 1. Daily check-in reminder
        val dailyRemindersEnabled = sharedPrefs.getBoolean("settings_daily_reminders_enabled", false)
        if (dailyRemindersEnabled) {
            val dailyRemindersTime = sharedPrefs.getString("settings_daily_reminders_time", "09:00 AM") ?: "09:00 AM"
            scheduleDailyReminder(context, dailyRemindersTime)
        } else {
            cancelNotification(context, 101, "com.example.notification.TRIGGER_DAILY_REMINDER")
        }

        // 2. Missed task reminders
        val missedRemindersEnabled = sharedPrefs.getBoolean("settings_missed_reminders_enabled", false)
        if (missedRemindersEnabled) {
            val missedRemindersFrequency = sharedPrefs.getString("settings_missed_reminders_frequency", "Once Per Day") ?: "Once Per Day"
            scheduleMissedReminders(context, missedRemindersFrequency)
        } else {
            cancelNotification(context, 102, "com.example.notification.TRIGGER_MISSED_REMINDER")
        }

        // 3. Daily summary report (9:30 PM - 21:30)
        val dailySummaryEnabled = sharedPrefs.getBoolean("settings_daily_summary_enabled", false)
        if (dailySummaryEnabled) {
            scheduleDailySummary(context)
        } else {
            cancelNotification(context, 103, "com.example.notification.TRIGGER_DAILY_SUMMARY")
        }
    }

    fun cancelAllScheduledNotifications(context: Context) {
        Log.d(TAG, "Canceling all scheduled alarms...")
        cancelNotification(context, 101, "com.example.notification.TRIGGER_DAILY_REMINDER")
        cancelNotification(context, 102, "com.example.notification.TRIGGER_MISSED_REMINDER")
        cancelNotification(context, 103, "com.example.notification.TRIGGER_DAILY_SUMMARY")
    }

    private fun cancelNotification(context: Context, requestCode: Int, action: String) {
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            this.action = action
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent)
    }

    private fun scheduleDailyReminder(context: Context, timeStr: String) {
        try {
            val time = parseTimeStr(timeStr)
            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, time.hour)
                set(Calendar.MINUTE, time.minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (before(Calendar.getInstance())) {
                    add(Calendar.DATE, 1)
                }
            }

            setAlarm(context, calendar.timeInMillis, 101, "com.example.notification.TRIGGER_DAILY_REMINDER")
            Log.d(TAG, "Scheduled Daily Reminder at: $timeStr (${calendar.time})")
        } catch (e: Exception) {
            Log.e(TAG, "Error scheduling daily reminder: ${e.message}", e)
        }
    }

    private fun scheduleDailySummary(context: Context) {
        val sharedPrefs = context.getSharedPreferences("habitgrid_settings", Context.MODE_PRIVATE)
        val timeStr = sharedPrefs.getString("settings_daily_summary_time", "10:00 PM") ?: "10:00 PM"
        var hour = 21
        var minute = 30
        try {
            val parts = timeStr.split(":", " ")
            if (parts.size >= 2) {
                var h = parts[0].toInt()
                val m = parts[1].toInt()
                if (parts.size == 3 && parts[2].uppercase() == "PM" && h < 12) h += 12
                if (parts.size == 3 && parts[2].uppercase() == "AM" && h == 12) h = 0
                hour = h
                minute = m
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing daily summary time: $timeStr", e)
        }

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(Calendar.getInstance())) {
                add(Calendar.DATE, 1)
            }
        }
        setAlarm(context, calendar.timeInMillis, 103, "com.example.notification.TRIGGER_DAILY_SUMMARY")
        Log.d(TAG, "Scheduled Daily Summary at $timeStr (${calendar.time})")
    }

    private fun scheduleMissedReminders(context: Context, frequency: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            action = "com.example.notification.TRIGGER_MISSED_REMINDER"
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            102,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Cancel previous first
        alarmManager.cancel(pendingIntent)

        val intervalMillis: Long = when (frequency) {
            "Once Every Hour" -> 1 * 60 * 60 * 1000L
            "Once Every 2 Hours" -> 2 * 60 * 60 * 1000L
            "Custom" -> {
                val sharedPrefs = context.getSharedPreferences("habitgrid_settings", Context.MODE_PRIVATE)
                val hoursGapStr = sharedPrefs.getString("settings_custom_hourly_hours_gap", "3") ?: "3"
                val hours = hoursGapStr.toIntOrNull()?.coerceIn(1, 24) ?: 3
                hours * 60 * 60 * 1000L
            }
            else -> 1 * 60 * 60 * 1000L
        }

        val firstTrigger = System.currentTimeMillis() + intervalMillis
        alarmManager.setRepeating(
            AlarmManager.RTC_WAKEUP,
            firstTrigger,
            intervalMillis,
            pendingIntent
        )
        Log.d(TAG, "Scheduled Missed Reminders starting at ${Date(firstTrigger)} with interval of $intervalMillis")
    }

    private fun setAlarm(context: Context, triggerAtMillis: Long, requestCode: Int, action: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            this.action = action
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    private fun parseTimeStr(timeStr: String): LocalTime {
        val parts = timeStr.trim().split(" ")
        val timeParts = parts[0].split(":")
        var hour = timeParts[0].toInt()
        val minute = timeParts[1].toInt()
        val amPm = parts[1].uppercase()

        if (amPm == "PM" && hour < 12) {
            hour += 12
        } else if (amPm == "AM" && hour == 12) {
            hour = 0
        }
        return LocalTime.of(hour, minute)
    }
}
