package com.example.wallpaper

import android.content.Context
import android.content.SharedPreferences
import android.graphics.*
import android.service.wallpaper.WallpaperService
import android.os.Handler
import android.os.Looper
import android.view.SurfaceHolder
import com.example.data.AppDatabase
import com.example.data.HabitLog
import com.example.data.HabitSetWithTasks
import com.example.util.HabitAnalyticsService
import com.example.util.Scheduler
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import java.time.LocalDate

class HabitWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine {
        return HabitWallpaperEngine()
    }

    private inner class HabitWallpaperEngine : Engine(), SharedPreferences.OnSharedPreferenceChangeListener {
        private val prefs = getSharedPreferences("habitgrid_settings", Context.MODE_PRIVATE)
        private val db = AppDatabase.getDatabase(this@HabitWallpaperService)
        
        private val handler = Handler(Looper.getMainLooper())
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        
        private var habitData: List<HabitSetWithTasks> = emptyList()
        private var allLogs: List<HabitLog> = emptyList()
        private var isVisibleState = false

        init {
            prefs.registerOnSharedPreferenceChangeListener(this)
            
            // Collect data dynamically
            scope.launch {
                db.habitDao().getHabitSetsWithTasks().collectLatest { sets ->
                    habitData = sets
                    if (isVisibleState) drawFrame()
                }
            }
            scope.launch {
                db.habitDao().getAllLogs().collectLatest { logs ->
                    allLogs = logs
                    if (isVisibleState) drawFrame()
                }
            }
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            isVisibleState = visible
            if (visible) {
                drawFrame()
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            drawFrame()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            super.onSurfaceDestroyed(holder)
            isVisibleState = false
        }

        override fun onDestroy() {
            super.onDestroy()
            prefs.unregisterOnSharedPreferenceChangeListener(this)
            scope.cancel()
        }

        override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
            if (key?.startsWith("settings_wallpaper_") == true) {
                drawFrame()
            }
        }

        private fun drawFrame() {
            val holder = surfaceHolder
            var canvas: Canvas? = null
            try {
                canvas = holder.lockCanvas()
                if (canvas != null) {
                    drawWallpaper(canvas)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                if (canvas != null) {
                    try {
                        holder.unlockCanvasAndPost(canvas)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }

        private fun drawWallpaper(canvas: Canvas) {
            val width = canvas.width.toFloat()
            val height = canvas.height.toFloat()

            // Fetch Settings
            val theme = prefs.getString("settings_wallpaper_theme", "Warm HabitGrid") ?: "Warm HabitGrid"
            val showCurrentStreak = prefs.getBoolean("settings_wallpaper_show_current_streak", false)
            val showBestStreak = prefs.getBoolean("settings_wallpaper_show_best_streak", false)
            val showProductivity = prefs.getBoolean("settings_wallpaper_show_productivity", false)
            val showProgressBar = prefs.getBoolean("settings_wallpaper_show_progress_bar", false)
            val showTasks = prefs.getBoolean("settings_wallpaper_show_tasks", true)
            val showTotalDaysLeft = prefs.getBoolean("settings_wallpaper_show_total_days_left", true)
            val showDateLabels = prefs.getBoolean("settings_wallpaper_show_date_labels", false)
            val statsPosition = prefs.getString("settings_wallpaper_stats_position", "Below Grid") ?: "Below Grid"
            
            val dataSource = prefs.getString("settings_wallpaper_data_source", "Overall Habits") ?: "Overall Habits"
            val selectedHabitSetName = prefs.getString("settings_wallpaper_selected_habit_set", "") ?: ""

            val bgPaint = Paint().apply {
                color = when (theme) {
                    "Dark" -> Color.rgb(20, 20, 20)
                    "Light" -> Color.rgb(245, 245, 245)
                    else -> Color.rgb(238, 226, 211) // Warm HabitGrid
                }
            }
            canvas.drawRect(0f, 0f, width, height, bgPaint)

            val primaryColor = when (theme) {
                "Dark" -> Color.rgb(100, 150, 255)
                "Light" -> Color.rgb(33, 150, 243)
                else -> Color.rgb(184, 134, 88)
            }
            
            val textColor = when (theme) {
                "Dark" -> Color.WHITE
                "Light" -> Color.BLACK
                else -> Color.rgb(80, 50, 30)
            }
            
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = textColor
                textSize = 50f
                typeface = Typeface.DEFAULT_BOLD
            }

            // Determine Target Sets
            val targetSets = if (dataSource == "Selected Habit Set") {
                habitData.filter { it.habitSet.name.equals(selectedHabitSetName, ignoreCase = true) }
            } else {
                habitData.filter { it.habitSet.status == "Active" }
            }

            if (habitData.isEmpty() || targetSets.isEmpty()) {
                textPaint.textAlign = Paint.Align.CENTER
                textPaint.textSize = 40f
                val message = if (habitData.isEmpty()) "Create at least one Habit Set to use the Live Wallpaper feature." else "No active habit set selected."
                
                // Text wrapping for empty message (simple split)
                val words = message.split(" ")
                var currentLine = ""
                var yOffset = height / 2
                for (word in words) {
                    if (textPaint.measureText(currentLine + word) > width - 80f) {
                        canvas.drawText(currentLine, width / 2, yOffset, textPaint)
                        yOffset += 60f
                        currentLine = "$word "
                    } else {
                        currentLine += "$word "
                    }
                }
                canvas.drawText(currentLine, width / 2, yOffset, textPaint)
                return
            }

            var minDate: LocalDate? = null
            var maxDate: LocalDate? = null

            for (set in targetSets) {
                val setStart = try { LocalDate.parse(set.habitSet.startDate) } catch(e: Exception) { LocalDate.now() }
                if (minDate == null || setStart.isBefore(minDate)) minDate = setStart
                
                val setEnd = set.habitSet.endDate?.let { 
                    try { LocalDate.parse(it) } catch(e: Exception) { null } 
                }
                
                if (setEnd == null) {
                    if (maxDate == null || maxDate!!.isBefore(LocalDate.now())) {
                        maxDate = LocalDate.now()
                    }
                } else {
                    if (maxDate == null || setEnd.isAfter(maxDate)) {
                        maxDate = setEnd
                    }
                }
            }

            val startDate = minDate ?: LocalDate.now()
            val endDate = maxDate ?: LocalDate.now()
            
            val logMapByDate = allLogs.groupBy { it.date }

            // Metrics
            var currentStreak = 0
            var bestStreak = 0
            var completedToday = 0
            val todayStr = LocalDate.now().toString()
            
            // Re-calculate streak and today metrics based on targetSets
            if (targetSets.isNotEmpty()) {
                currentStreak = targetSets.maxOf { HabitAnalyticsService.calculateCurrentStreak(it, allLogs, LocalDate.now()) }
                bestStreak = targetSets.maxOf { HabitAnalyticsService.calculateBestStreak(it, allLogs, LocalDate.now()) }
            }
            
            val logsForToday = logMapByDate[todayStr]?.associateBy { it.taskId } ?: emptyMap()
            var todayTotalScheduled = 0
            var todayTotalPoints = 0.0
            
            for (set in targetSets) {
                val scheduledTasks = set.tasks.filter { Scheduler.isTaskScheduled(it, LocalDate.now()) }
                for (task in scheduledTasks) {
                    todayTotalScheduled++
                    val logStatus = logsForToday[task.id]?.status
                    if (HabitAnalyticsService.isCompletedStatus(logStatus)) {
                        todayTotalPoints += 1.0
                        completedToday++ 
                    } else if (HabitAnalyticsService.isPartialStatus(logStatus)) {
                        todayTotalPoints += 0.5
                    }
                }
            }
            val todayProductivityPercent = if (todayTotalScheduled == 0) 0f else (todayTotalPoints / todayTotalScheduled).toFloat()

            canvas.save()
            
            // Grid Dimensions
            val daysBetween = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate).toInt() + 1
            val totalCells = daysBetween
            
            // Dynamic Grid Calculation to maximize cell size while fitting the design limits
            val MIN_COLS = 7
            val maxAllowedCols = totalCells.coerceAtLeast(MIN_COLS)
            
            var bestCols = MIN_COLS
            var maxBoxSize = 0f
            
            for (c in MIN_COLS..maxAllowedCols) {
                val r = Math.ceil(totalCells / c.toDouble()).toInt().coerceAtLeast(1)
                
                val currentMaxWBox = (width - 80f) / (c + (c - 1) * 0.2f).coerceAtLeast(1f)
                val currentMaxHBox = (height * 0.45f) / (r + (r - 1) * 0.2f).coerceAtLeast(1f)
                val currentBoxSize = Math.min(currentMaxHBox, currentMaxWBox)
                
                if (currentBoxSize > maxBoxSize) {
                    maxBoxSize = currentBoxSize
                    bestCols = c
                }
            }
            
            val cols = bestCols
            val rows = Math.ceil(totalCells / cols.toDouble()).toInt().coerceAtLeast(1)

            val boxSize = maxBoxSize.coerceAtLeast(4f)
            val gap = boxSize * 0.2f

            val totalW = (cols * boxSize) + ((cols - 1) * gap)
            val totalH = (rows * boxSize) + ((rows - 1) * gap)
                
            val startX = 40f
            val startY = (height - totalH) / 2f
            
            val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                style = Paint.Style.STROKE
                strokeWidth = Math.max(1f, boxSize * 0.05f)
            }
            val dateTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = boxSize * 0.35f
                textAlign = Paint.Align.CENTER
                typeface = Typeface.DEFAULT_BOLD
            }
            
            var cellIndex = 0
            for (r in 0 until rows) {
                for (c in 0 until cols) {
                    if (cellIndex >= totalCells) break
                        
                    val bx = startX + c * (boxSize + gap)
                    val by = startY + r * (boxSize + gap)
                        
                    val targetDate = startDate.plusDays(cellIndex.toLong())
                    val targetDateStr = targetDate.toString()
                        
                    val isToday = targetDate == LocalDate.now()
                    val isFuture = targetDate.isAfter(LocalDate.now())
                        
                    var totalScheduled = 0
                    var totalPoints = 0.0
                        
                    if (!isFuture) {
                        val logsForTarget = logMapByDate[targetDateStr]?.associateBy { it.taskId } ?: emptyMap()
                        for (set in targetSets) {
                            val targetSetTasks = set.tasks.filter { Scheduler.isTaskScheduled(it, targetDate) }
                            for (task in targetSetTasks) {
                                totalScheduled++
                                val logStatus = logsForTarget[task.id]?.status
                                if (HabitAnalyticsService.isCompletedStatus(logStatus)) {
                                    totalPoints += 1.0
                                } else if (HabitAnalyticsService.isPartialStatus(logStatus)) {
                                    totalPoints += 0.5
                                }
                            }
                        }
                    }
                        
                    val dailyScore = if (totalScheduled == 0) 0 else Math.round((totalPoints / totalScheduled) * 100).toInt()
                        
                    boxPaint.color = when {
                        isFuture -> Color.rgb(170, 170, 170) // Grey (Future Day)
                        totalScheduled == 0 -> Color.rgb(245, 245, 220) // Beige (Rest Day)
                        dailyScore == 100 -> 0xFF2E7D32.toInt() // Green (Perfect Day)
                        dailyScore in 1..99 -> 0xFFE65100.toInt() // Orange (Partial Day)
                        else -> 0xFFEF5350.toInt() // Red (Missed Day)
                    }
                        
                    canvas.drawRoundRect(bx, by, bx + boxSize, by + boxSize, boxSize*0.2f, boxSize*0.2f, boxPaint)
                        
                    if (isToday) {
                        canvas.drawRoundRect(bx, by, bx + boxSize, by + boxSize, boxSize*0.2f, boxSize*0.2f, strokePaint)
                    }

                    if (showDateLabels) {
                        dateTextPaint.color = if (boxPaint.color == Color.rgb(245, 245, 220) || boxPaint.color == Color.rgb(170, 170, 170)) Color.DKGRAY else Color.WHITE
                        val dateText = targetDate.dayOfMonth.toString()
                        canvas.drawText(
                            dateText, 
                            bx + boxSize/2f, 
                            by + boxSize/2f - ((dateTextPaint.descent() + dateTextPaint.ascent()) / 2f), 
                            dateTextPaint
                        )
                    }
                    cellIndex++
                }
            }
            
            canvas.restore()
            
            // Draw Stats
            if (statsPosition == "Above Grid" || statsPosition == "Below Grid") {
                val statsLines = mutableListOf<String>()
                if (showCurrentStreak) statsLines.add("🔥 $currentStreak Days")
                if (showBestStreak) statsLines.add("🏆 $bestStreak Days")
                if (showProductivity) statsLines.add("Productivity: ${(todayProductivityPercent * 100).toInt()}%")
                if (showTasks) statsLines.add("Task: $completedToday/$todayTotalScheduled")
                if (showTotalDaysLeft) {
                    val daysLeft = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), endDate).toInt().coerceAtLeast(0)
                    statsLines.add("Days Left: $daysLeft")
                }
                
                textPaint.textSize = Math.min(45f, width * 0.05f)
                textPaint.textAlign = Paint.Align.CENTER
                
                // Calculate total height of stats to center block properly
                val lineHeight = textPaint.descent() - textPaint.ascent() + 20f
                var statsBlockH = statsLines.size * lineHeight
                if (showProgressBar) statsBlockH += 60f
                
                var sy = if (statsPosition == "Above Grid") {
                    startY - Math.max(40f, statsBlockH + 20f)
                } else {
                    startY + totalH + 80f
                }
                
                for (line in statsLines) {
                    canvas.drawText(line, width / 2f, sy, textPaint)
                    sy += lineHeight
                }
                
                if (showProgressBar) {
                    val barWidth = width * 0.8f
                    val barHeight = 20f
                    val bx = (width - barWidth) / 2f
                    sy += 20f
                    
                    val bgBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.rgb(200, 200, 200)
                    }
                    val fgBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = primaryColor
                    }
                    
                    canvas.drawRoundRect(bx, sy, bx + barWidth, sy + barHeight, barHeight/2, barHeight/2, bgBarPaint)
                    if (todayProductivityPercent > 0) {
                        canvas.drawRoundRect(bx, sy, bx + (barWidth * todayProductivityPercent), sy + barHeight, barHeight/2, barHeight/2, fgBarPaint)
                    }
                }
            }
        }
    }
}

