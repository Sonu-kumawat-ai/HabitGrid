@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.HabitSet
import com.example.data.HabitTask
import com.example.data.HabitSetWithTasks
import com.example.ui.HabitViewModel
import java.time.LocalDate
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DatePicker

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitSetsScreen(
    viewModel: HabitViewModel,
    modifier: Modifier = Modifier
) {
    val habitSetsWithTasks by viewModel.filteredHabitSets.collectAsState()
    val selectedId by viewModel.selectedHabitSetId.collectAsState()
    val activeDetail by viewModel.activeHabitSetWithTasks.collectAsState()

    // Form flow control states
    var createSetFormActive by remember { mutableStateOf(false) }
    var editSetFormTarget by remember { mutableStateOf<HabitSet?>(null) }
    var createTaskFormActive by remember { mutableStateOf(false) }
    var editTaskFormTarget by remember { mutableStateOf<HabitTask?>(null) }

    // Deletion confirmation states
    var setForDeleteConfirmation by remember { mutableStateOf<HabitSet?>(null) }
    var taskForDeleteConfirmation by remember { mutableStateOf<HabitTask?>(null) }

    // FAB global trigger listener
    val isCreatingGlobal by viewModel.isCreatingHabitSetGlobal.collectAsState()
    LaunchedEffect(isCreatingGlobal) {
        if (isCreatingGlobal) {
            createSetFormActive = true
            viewModel.isCreatingHabitSetGlobal.value = false
        }
    }

    val isCreatingTaskQuickly by viewModel.isCreatingTaskQuickly.collectAsState()
    LaunchedEffect(isCreatingTaskQuickly) {
        if (isCreatingTaskQuickly) {
            createTaskFormActive = true
            viewModel.isCreatingTaskQuickly.value = false
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (createSetFormActive) {
            // HABIT SET CREATION FORM
            HabitSetForm(
                title = "Create Habit Set",
                onSubmit = { name, desc, start, end, priority ->
                    viewModel.createHabitSet(name, desc, start, end, priority)
                    createSetFormActive = false
                },
                onCancel = { createSetFormActive = false }
            )
        } else if (editSetFormTarget != null) {
            // HABIT SET EDITION FORM
            val target = editSetFormTarget!!
            HabitSetForm(
                title = "Edit Habit Set",
                initialName = target.name,
                initialDescription = target.description,
                initialStartDate = target.startDate,
                initialEndDate = target.endDate,
                initialPriority = target.priority,
                onSubmit = { name, desc, start, end, priority ->
                    viewModel.updateHabitSet(
                        target.copy(
                            name = name,
                            description = desc,
                            startDate = start,
                            endDate = end,
                            priority = priority
                        )
                    )
                    editSetFormTarget = null
                },
                onCancel = { editSetFormTarget = null }
            )
        } else if (createTaskFormActive && activeDetail != null) {
            // TASK CREATION FORM
            HabitTaskForm(
                title = "Create Workday Task",
                parentStartDate = activeDetail!!.habitSet.startDate,
                parentEndDate = activeDetail!!.habitSet.endDate,
                onSubmit = { name, desc, start, end, weekdays ->
                    viewModel.createTask(activeDetail!!.habitSet.id, name, desc, start, end, weekdays)
                    createTaskFormActive = false
                },
                onCancel = { createTaskFormActive = false }
            )
        } else if (editTaskFormTarget != null && activeDetail != null) {
            // TASK EDITION FORM
            val target = editTaskFormTarget!!
            HabitTaskForm(
                title = "Edit Workday Task",
                initialName = target.name,
                initialDescription = target.description,
                initialStartDate = target.startDate,
                initialEndDate = target.endDate,
                initialWeekdays = target.activeWeekdays.split(",").map { it.trim() },
                parentStartDate = activeDetail!!.habitSet.startDate,
                parentEndDate = activeDetail!!.habitSet.endDate,
                onSubmit = { name, desc, start, end, weekdays ->
                    viewModel.updateTask(target, name, desc, start, end, weekdays)
                    editTaskFormTarget = null
                },
                onCancel = { editTaskFormTarget = null }
            )
        } else if (selectedId != null && activeDetail != null) {
            // HABIT SET DETAIL VIEW
            val detail = activeDetail!!
            HabitSetDetailsView(
                detail = detail,
                viewModel = viewModel,
                onBack = { viewModel.selectedHabitSetId.value = null },
                onAddTask = { createTaskFormActive = true },
                onEditTask = { editTaskFormTarget = it },
                onDeleteTask = { task ->
                    if (viewModel.deleteConfirmation.value) {
                        taskForDeleteConfirmation = task
                    } else {
                        viewModel.deleteTask(task)
                    }
                },
                onDuplicateTask = { viewModel.duplicateTask(it) },
                onViewDashboard = { viewModel.selectedTab.value = "Analytics" },
                onEditSet = { editSetFormTarget = detail.habitSet },
                onArchiveSet = { 
                    viewModel.updateHabitSet(detail.habitSet.copy(status = if (detail.habitSet.status == "Archived") "Active" else "Archived")) 
                },
                onDeleteSet = {
                    if (viewModel.deleteConfirmation.value) {
                        setForDeleteConfirmation = detail.habitSet
                    } else {
                        viewModel.deleteHabitSet(detail.habitSet)
                        viewModel.selectedHabitSetId.value = null
                    }
                }
            )
        } else {
            // HABIT SET LIST VIEW
            HabitSetsListView(
                habitSetsWithTasks = habitSetsWithTasks,
                viewModel = viewModel,
                onSelectSet = { viewModel.selectedHabitSetId.value = it.id },
                onEditSet = { editSetFormTarget = it },
                onDeleteSet = { set ->
                    if (viewModel.deleteConfirmation.value) {
                        setForDeleteConfirmation = set
                    } else {
                        viewModel.deleteHabitSet(set)
                    }
                },
                onCreateSet = { createSetFormActive = true }
            )
        }

        // --- GLOBAL DIALOGS ---

        // Deletion Confirmation for Habit Set
        setForDeleteConfirmation?.let { set ->
            AlertDialog(
                onDismissRequest = { setForDeleteConfirmation = null },
                title = { Text("Delete Habit Set?", fontWeight = FontWeight.Bold) },
                text = { Text("This will permanently delete \"${set.name}\" along with all associated tasks. This action cannot be undone.") },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.deleteHabitSet(set)
                            setForDeleteConfirmation = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.testTag("confirm_delete_set")
                    ) {
                        Text("Delete", color = MaterialTheme.colorScheme.onError)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { setForDeleteConfirmation = null }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Deletion Confirmation for Task
        taskForDeleteConfirmation?.let { task ->
            AlertDialog(
                onDismissRequest = { taskForDeleteConfirmation = null },
                title = { Text("Delete Task?", fontWeight = FontWeight.Bold) },
                text = { Text("Delete the task \"${task.name}\"? This will clean it from scheduled trackers completely.") },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.deleteTask(task)
                            taskForDeleteConfirmation = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.testTag("confirm_delete_task")
                    ) {
                        Text("Delete", color = MaterialTheme.colorScheme.onError)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { taskForDeleteConfirmation = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

// ==========================================
// SUB-SCREEN: HABIT SETS LIST VIEW
// ==========================================
@Composable
fun HabitSetsListView(
    habitSetsWithTasks: List<HabitSetWithTasks>,
    viewModel: HabitViewModel,
    onSelectSet: (HabitSet) -> Unit,
    onEditSet: (HabitSet) -> Unit,
    onDeleteSet: (HabitSet) -> Unit,
    onCreateSet: () -> Unit
) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()
    val statusFilter by viewModel.statusFilter.collectAsState()
    val allLogs by viewModel.allLogs.collectAsState()

    var showFilterSheet by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Upper title & Summary counts
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Habit Sets",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    fontSize = 26.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Grouped workflows & structures",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            TextButton(
                onClick = onCreateSet,
                modifier = Modifier.testTag("create_set_text_btn"),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary,
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                )
            ) {
                Icon(Icons.Default.Add, contentDescription = "New", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Create new", fontWeight = FontWeight.Bold)
            }
        }

        // Search Habit Sets text input with Filter Dialog trigger
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.searchQuery.value = it },
                placeholder = { Text("Search sets (e.g., Morning routine)") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                trailingIcon = if (searchQuery.isNotEmpty()) {
                    {
                        IconButton(onClick = { viewModel.searchQuery.value = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                } else null,
                modifier = Modifier
                    .weight(1f)
                    .testTag("search_habit_sets_field"),
                shape = RoundedCornerShape(16.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                )
            )

            FilledTonalIconButton(
                onClick = { showFilterSheet = true },
                modifier = Modifier.size(52.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = "Filter & Sort Options"
                )
            }
        }

        // REDESIGNED M3 FILTER & SORT DIALOG (ACTS AS BOTTOM SHEET SELECTOR)
        if (showFilterSheet) {
            AlertDialog(
                onDismissRequest = { showFilterSheet = false },
                confirmButton = {
                    TextButton(onClick = { showFilterSheet = false }) {
                        Text("Apply Filters", fontWeight = FontWeight.Bold)
                    }
                },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Filter & Sort", fontWeight = FontWeight.Black)
                    }
                },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // STATUS FILTER
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "STATUS DISPLAY",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                val filters = listOf("All", "Active", "Archived")
                                filters.forEach { f ->
                                    val isSelected = statusFilter == f
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { viewModel.statusFilter.value = f },
                                        label = { Text(f) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                        ),
                                        modifier = Modifier.testTag("filter_chip_$f")
                                    )
                                }
                            }
                        }

                        Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

                        // SORT ALGORITHM
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "SORT ORDER",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                val sorts = listOf("Name", "Streak", "Tasks count")
                                sorts.forEach { s ->
                                    val isSelected = sortOrder == s
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { viewModel.sortOrder.value = s }
                                            .padding(vertical = 8.dp)
                                    ) {
                                        RadioButton(
                                            selected = isSelected,
                                            onClick = { viewModel.sortOrder.value = s },
                                            modifier = Modifier.testTag("sort_chip_$s")
                                        )
                                        Text(
                                            text = s,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            )
        }

        Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

        // LIST OF SYSTEM HABIT SETS
        if (habitSetsWithTasks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.GridOff,
                        contentDescription = "Empty",
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(56.dp)
                    )
                    Text(
                        text = "No Habit Sets found.",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Try clearing search or click \"Create new\" above.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(habitSetsWithTasks, key = { it.habitSet.id }) { item ->
                    HabitSetCard(
                        item = item,
                        allLogs = allLogs,
                        onClick = { onSelectSet(item.habitSet) },
                        onEdit = { onEditSet(item.habitSet) },
                        onDelete = { onDeleteSet(item.habitSet) },
                        onArchiveToggle = {
                            if (item.habitSet.status == "Archived") {
                                viewModel.activateHabitSet(item.habitSet)
                            } else {
                                viewModel.archiveHabitSet(item.habitSet)
                            }
                        },
                        onQuickAddTask = {
                            viewModel.selectedHabitSetId.value = item.habitSet.id
                            viewModel.isCreatingTaskQuickly.value = true
                        }
                    )
                }
            }
        }
    }
}

// ==========================================
// CARD: INDIVIDUAL HABIT SET DISPLAY
// ==========================================
@Composable
fun HabitSetCard(
    item: HabitSetWithTasks,
    allLogs: List<com.example.data.HabitLog>,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onArchiveToggle: () -> Unit,
    onQuickAddTask: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hSet = item.habitSet
    val tasksCount = item.tasks.size
    var expandedDropdown by remember { mutableStateOf(false) }

    val setStartDate = remember(hSet.startDate) {
        try { LocalDate.parse(hSet.startDate) } catch (e: Exception) { LocalDate.now() }
    }
    val progressPercent = remember(item, allLogs, setStartDate) {
        com.example.util.HabitAnalyticsService.calculateHabitSetProductivity(item, allLogs, setStartDate, LocalDate.now())
    }

    val visualInfo = remember(hSet.name) {
        getHabitSetVisualInfo(hSet.name)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("habit_set_card_${hSet.id}"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Large styled category icon matching selected category naming
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(visualInfo.bg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = visualInfo.icon,
                        contentDescription = "Icon representing ${hSet.name}",
                        tint = visualInfo.tint,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Middle area containing Title, description, and status tags
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = hSet.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        // Status Badge pill
                        val badgeColor = when (hSet.status) {
                            "Active" -> Color(0xFF2E7D32)
                            "Archived" -> Color(0xFF757575)
                            else -> MaterialTheme.colorScheme.primary
                        }

                        Box(
                            modifier = Modifier
                                .background(badgeColor.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = hSet.status,
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 9.sp,
                                color = badgeColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (hSet.description.isNotBlank()) {
                        Text(
                            text = hSet.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Stat badges: Tasks & Streak (Image 1 style)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.EventNote,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.8f),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "$tasksCount Task${if (tasksCount != 1) "s" else ""}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocalFireDepartment,
                                contentDescription = null,
                                tint = Color(0xFFE65100),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "${hSet.currentStreak} Day Streak",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }

                // 3-dots Menu action trigger mapping Image 1
                Box {
                    IconButton(
                        onClick = { expandedDropdown = true },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("set_more_options_${hSet.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    DropdownMenu(
                        expanded = expandedDropdown,
                        onDismissRequest = { expandedDropdown = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Edit Set") },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            onClick = {
                                expandedDropdown = false
                                onEdit()
                            },
                            modifier = Modifier.testTag("edit_set_dropdown_${hSet.id}")
                        )
                        DropdownMenuItem(
                            text = { Text(if (hSet.status == "Archived") "Unarchive" else "Archive") },
                            leadingIcon = { 
                                Icon(
                                    imageVector = if (hSet.status == "Archived") Icons.Default.Unarchive else Icons.Default.Archive,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            onClick = {
                                expandedDropdown = false
                                onArchiveToggle()
                            },
                            modifier = Modifier.testTag("archive_set_dropdown_${hSet.id}")
                        )
                        DropdownMenuItem(
                            text = { Text("Quick Add Task") },
                            leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            onClick = {
                                expandedDropdown = false
                                onQuickAddTask()
                            },
                            modifier = Modifier.testTag("quick_add_task_dropdown_${hSet.id}")
                        )
                        Divider(modifier = Modifier.padding(vertical = 4.dp))
                        DropdownMenuItem(
                            text = { Text("Delete Set", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp)) },
                            onClick = {
                                expandedDropdown = false
                                onDelete()
                            },
                            modifier = Modifier.testTag("delete_set_dropdown_${hSet.id}")
                        )
                    }
                }
            }

            // Beautiful thin overall progress indicator
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Set Progress",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = "$progressPercent%",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                LinearProgressIndicator(
                    progress = progressPercent / 100f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                )
            }
        }
    }
}

// ==========================================
// SUB-SCREEN: HABIT SET DETAILS VIEW
// ==========================================
@Composable
fun HabitSetDetailsView(
    detail: HabitSetWithTasks,
    viewModel: HabitViewModel,
    onBack: () -> Unit,
    onAddTask: () -> Unit,
    onEditTask: (HabitTask) -> Unit,
    onDeleteTask: (HabitTask) -> Unit,
    onDuplicateTask: (HabitTask) -> Unit,
    onViewDashboard: () -> Unit,
    onEditSet: () -> Unit,
    onArchiveSet: () -> Unit,
    onDeleteSet: () -> Unit
) {
    val hSet = detail.habitSet
    val tasks = detail.tasks

    // Fetch live progress states for today's tasks inside this specific Habit Set
    val todayLogs by viewModel.todayLogs.collectAsState()
    val today = remember { LocalDate.now() }
    
    val logMap = remember(todayLogs) {
        todayLogs.associate { it.taskId to it.status }
    }
    
    val scheduledToday = remember(tasks, today) {
        tasks.filter { com.example.util.Scheduler.isTaskScheduled(it, today) }
    }
    
    val completedCount = scheduledToday.count { logMap[it.id] == "Complete" }
    val partialCount = scheduledToday.count { logMap[it.id] == "Partial" }
    val remainingCount = scheduledToday.size - (completedCount + partialCount)

    val todayProgressPercent = if (scheduledToday.isNotEmpty()) {
        ((completedCount.toFloat() + partialCount.toFloat() * 0.5f) / scheduledToday.size.toFloat() * 100f).toInt()
    } else {
        0
    }

    val visualInfo = remember(hSet.name) {
        getHabitSetVisualInfo(hSet.name)
    }

    var expandedDropdown by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("set_details_screener"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Toolbar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .testTag("detail_back_btn")
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        CircleShape
                    )
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Go back")
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = hSet.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Habit Set Detailed Card",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            IconButton(
                onClick = onViewDashboard,
                modifier = Modifier
                    .testTag("detail_view_dashboard_btn")
                    .background(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                        CircleShape
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.Timeline,
                    contentDescription = "View Task-Set Dashboard",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Box {
                IconButton(
                    onClick = { expandedDropdown = true },
                    modifier = Modifier
                        .testTag("detail_more_options_btn")
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More Options"
                    )
                }
                DropdownMenu(
                    expanded = expandedDropdown,
                    onDismissRequest = { expandedDropdown = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Edit Habit Set") },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        onClick = {
                            expandedDropdown = false
                            onEditSet()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(if (hSet.status == "Archived") "Unarchive Habit Set" else "Archive Habit Set") },
                        leadingIcon = { 
                            Icon(
                                imageVector = if (hSet.status == "Archived") Icons.Default.Unarchive else Icons.Default.Archive,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        onClick = {
                            expandedDropdown = false
                            onArchiveSet()
                        }
                    )
                    Divider(modifier = Modifier.padding(vertical = 4.dp))
                    DropdownMenuItem(
                        text = { Text("Delete Habit Set", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp)) },
                        onClick = {
                            expandedDropdown = false
                            onDeleteSet()
                        }
                    )
                }
            }
        }

        // Parent Information Card (Image 1 stylings)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header of Parent: Category Icon with Title and Description
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(visualInfo.bg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = visualInfo.icon,
                            contentDescription = null,
                            tint = visualInfo.tint,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = hSet.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (hSet.description.isNotBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = hSet.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }

                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.06f))

                // Stats Rows columns (Image 1 style)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarToday,
                                contentDescription = null,
                                tint = Color(0xFF2E7D32),
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "START DATE",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        Text(
                            text = hSet.startDate,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarToday,
                                contentDescription = null,
                                tint = Color(0xFF2E7D32),
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "END DATE",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        Text(
                            text = if (hSet.endDate.isNullOrBlank()) "No end date" else hSet.endDate,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.EventNote,
                                contentDescription = null,
                                tint = Color(0xFF2E7D32),
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "TOTAL TASKS",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        Text(
                            text = "${tasks.size}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // Today's Progress Card (Image 1 style)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Today's Progress",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "$todayProgressPercent%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFFE08244) // terracotta accent
                    )
                }

                LinearProgressIndicator(
                    progress = todayProgressPercent / 100f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp)),
                    color = Color(0xFFE08244),
                    trackColor = Color(0xFFFFF0E4)
                )

                Text(
                    text = if (scheduledToday.isNotEmpty()) {
                        "$completedCount Completed  •  $partialCount Partial  •  $remainingCount Remaining"
                    } else {
                        "No tasks scheduled for today."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Subtasks checklist area header (Image 1 style)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Tasks in this Habit Set",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Button(
                onClick = onAddTask,
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFF0E4), contentColor = Color(0xFFE08244)),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                modifier = Modifier.testTag("add_subtask_btn_trigger")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Task", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add Task", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }

        if (tasks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Empty tasks",
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = "This Habit Set contains 0 tasks.",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Add tasks inside to trigger scheduling rules.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(tasks, key = { it.id }) { task ->
                    TaskCard(
                        task = task,
                        currentStatus = logMap[task.id] ?: "None",
                        habitSetVisualInfo = visualInfo,
                        onEdit = { onEditTask(task) },
                        onDelete = { onDeleteTask(task) },
                        onDuplicate = { onDuplicateTask(task) }
                    )
                }
            }
        }
    }
}

// ==========================================
// CARD: INDIVIDUAL SUBTASK CARD inside Detail
// ==========================================
@Composable
fun TaskCard(
    task: HabitTask,
    currentStatus: String,
    habitSetVisualInfo: HabitSetVisualInfo,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDuplicate: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expandedDropdown by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("task_card_${task.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Task name and active weekdays block
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = task.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (task.description.isNotBlank()) {
                        Text(
                            text = task.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                // Weekday chips selector circles (M T W T F S S) aligned with Image 1
                val dayList = listOf(
                    "MONDAY" to "M",
                    "TUESDAY" to "T",
                    "WEDNESDAY" to "W",
                    "THURSDAY" to "T",
                    "FRIDAY" to "F",
                    "SATURDAY" to "S",
                    "SUNDAY" to "S"
                )
                val activeList = remember(task.activeWeekdays) {
                    task.activeWeekdays.split(",").map { it.trim().uppercase() }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    dayList.forEach { (fullDay, initial) ->
                        val isActive = activeList.contains(fullDay)
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isActive) habitSetVisualInfo.bg
                                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = initial,
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isActive) habitSetVisualInfo.tint else MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }

            // Task execution checklist status today (checkmark / diamond / circular ring contour)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when (currentStatus) {
                    "Complete" -> {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Completed Today",
                            tint = Color(0xFF2E7D32),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    "Partial" -> {
                        Icon(
                            imageVector = Icons.Default.Stars,
                            contentDescription = "Partially Completed Today",
                            tint = Color(0xFFE08244),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    else -> {
                        // Unmarked / Missed / Remaining circle outline contour
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .border(1.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), CircleShape)
                        )
                    }
                }

                // 3-dots actions menu
                Box {
                    IconButton(
                        onClick = { expandedDropdown = true },
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("task_more_options_${task.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Task options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    DropdownMenu(
                        expanded = expandedDropdown,
                        onDismissRequest = { expandedDropdown = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Edit Task") },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            onClick = {
                                expandedDropdown = false
                                onEdit()
                            },
                            modifier = Modifier.testTag("edit_subtask_dropdown_${task.id}")
                        )
                        DropdownMenuItem(
                            text = { Text("Duplicate Task") },
                            leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            onClick = {
                                expandedDropdown = false
                                onDuplicate()
                            },
                            modifier = Modifier.testTag("duplicate_subtask_dropdown_${task.id}")
                        )
                        Divider(modifier = Modifier.padding(vertical = 4.dp))
                        DropdownMenuItem(
                            text = { Text("Delete Task", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp)) },
                            onClick = {
                                expandedDropdown = false
                                onDelete()
                            },
                            modifier = Modifier.testTag("delete_subtask_dropdown_${task.id}")
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// FORM COMPONENT: HABIT SET CREATE / EDIT
// ==========================================
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun HabitSetForm(
    title: String,
    initialName: String = "",
    initialDescription: String = "",
    initialStartDate: String = LocalDate.now().toString(),
    initialEndDate: String? = null,
    initialPriority: Int? = null,
    onSubmit: (name: String, description: String, startDate: String, endDate: String?, priority: Int?) -> Unit,
    onCancel: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var description by remember { mutableStateOf(initialDescription) }
    var startDate by remember { mutableStateOf(initialStartDate) }
    var endDate by remember { mutableStateOf(initialEndDate ?: "") }
    var priorityStr by remember { mutableStateOf(initialPriority?.toString() ?: "5") }

    // Validation state
    var nameError by remember { mutableStateOf<String?>(null) }
    var datesError by remember { mutableStateOf<String?>(null) }
    var priorityError by remember { mutableStateOf<String?>(null) }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(24.dp)
            .testTag("habit_set_form_panel"),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // Form Title
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
            fontSize = 24.sp,
            color = MaterialTheme.colorScheme.primary
        )

        Divider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))

        // Habit Set Name Field
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Habit Set Name *",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    nameError = if (it.isBlank()) "Habit Set title is required" else null
                },
                placeholder = { Text("e.g. Morning Routine, Fitness Plan") },
                isError = nameError != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("form_set_name"),
                shape = RoundedCornerShape(12.dp)
            )
            nameError?.let {
                Text(text = it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
            }
        }

        // Description Field
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Description (Optional)",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                placeholder = { Text("What is the main goal or vision of this Habit Set?") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .testTag("form_set_description"),
                shape = RoundedCornerShape(12.dp)
            )
        }

        // Start Date Field
        StandardDatePickerField(
            label = "Start Date *",
            value = startDate,
            onValueChange = { startDate = it },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("form_set_start_date")
        )

        // End Date Field
        StandardDatePickerField(
            label = "End Date * (Mandatory)",
            value = endDate,
            placeholder = "Select End Date",
            onValueChange = { endDate = it },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("form_set_end_date"),
            isError = datesError != null
        )

        // Priority Field (1-10)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Priority (1-10)",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            var expanded by remember { mutableStateOf(false) }
            androidx.compose.material3.ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                OutlinedTextField(
                    readOnly = true,
                    value = priorityStr,
                    onValueChange = { },
                    label = { Text("Priority") },
                    trailingIcon = {
                        androidx.compose.material3.ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                        .testTag("form_set_priority"),
                    shape = RoundedCornerShape(12.dp)
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    (1..10).forEach { priorityLevel ->
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text(priorityLevel.toString()) },
                            onClick = {
                                priorityStr = priorityLevel.toString()
                                expanded = false
                            }
                        )
                    }
                }
            }
        }

        datesError?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.testTag("form_date_error_msg")
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Actions Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = onCancel,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("form_set_cancel_btn")
            ) {
                Text("Cancel", fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = {
                    // Manual verification
                    var valid = true
                    if (name.isBlank()) {
                        nameError = "Habit Set title is required"
                        valid = false
                    }

                    if (endDate.isBlank()) {
                        datesError = "End Date is required."
                        valid = false
                    } else if (startDate.isNotBlank() && endDate < startDate) {
                        datesError = "End Date cannot occur before Start Date."
                        valid = false
                    } else {
                        datesError = null
                    }

                    var finalPriority: Int? = null
                    if (priorityStr.isNotBlank()) {
                        val v = priorityStr.toIntOrNull()
                        if (v == null || v !in 1..10) {
                            priorityError = "Priority must be an integer between 1 and 10"
                            valid = false
                        } else {
                            priorityError = null
                            finalPriority = v
                        }
                    } else {
                        priorityError = null
                    }

                    if (valid) {
                        onSubmit(
                            name.trim(),
                            description.trim(),
                            startDate.trim(),
                            endDate.trim(),
                            finalPriority
                        )
                    }
                },
                modifier = Modifier
                    .weight(1.5f)
                    .height(48.dp)
                    .testTag("form_set_submit_btn"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Save Habit Set", fontWeight = FontWeight.Black)
            }
        }
    }
}

// ==========================================
// FORM COMPONENT: HABIT TASK CREATE / EDIT
// ==========================================
@Composable
fun HabitTaskForm(
    title: String,
    initialName: String = "",
    initialDescription: String = "",
    initialStartDate: String = LocalDate.now().toString(),
    initialEndDate: String? = null,
    initialWeekdays: List<String> = listOf("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"),
    parentStartDate: String,
    parentEndDate: String?,
    onSubmit: (name: String, description: String, startDate: String, endDate: String?, weekdays: List<String>) -> Unit,
    onCancel: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var description by remember { mutableStateOf(initialDescription) }
    var startDate by remember { mutableStateOf(initialStartDate) }
    var endDate by remember { mutableStateOf(initialEndDate ?: "") }

    // Selected weekdays list
    val selectedWeekdays = remember { mutableStateListOf<String>().apply { addAll(initialWeekdays) } }

    // Validation state
    var nameError by remember { mutableStateOf<String?>(null) }
    var dateError by remember { mutableStateOf<String?>(null) }
    var weekdayError by remember { mutableStateOf<String?>(null) }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(24.dp)
            .testTag("task_form_panel"),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // Form Title
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
            fontSize = 24.sp,
            color = MaterialTheme.colorScheme.primary
        )

        Divider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))

        // Task Name
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Task Name *",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    nameError = if (it.isBlank()) "Task name is required" else null
                },
                placeholder = { Text("e.g. Drink Water, Exercise, Study") },
                isError = nameError != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("form_task_name"),
                shape = RoundedCornerShape(12.dp)
            )
            nameError?.let {
                Text(text = it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
            }
        }

        // Task Description (Optional)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Task Description (Optional)",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                placeholder = { Text("Describe exactly what this task means to ensure you follow through.") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .testTag("form_task_description"),
                shape = RoundedCornerShape(12.dp)
            )
        }

        // Active Weekdays (At least 1 must be selected)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Active Weekdays * (Support multiple)",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                // Quick selector buttons
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "All Days",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable {
                                selectedWeekdays.clear()
                                selectedWeekdays.addAll(
                                    listOf(
                                        "MONDAY",
                                        "TUESDAY",
                                        "WEDNESDAY",
                                        "THURSDAY",
                                        "FRIDAY",
                                        "SATURDAY",
                                        "SUNDAY"
                                    )
                                )
                                weekdayError = null
                            }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                    Text(
                        text = "Weekdays Only",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier
                            .clickable {
                                selectedWeekdays.clear()
                                selectedWeekdays.addAll(
                                    listOf("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY")
                                )
                                weekdayError = null
                            }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }

            val weekList = listOf(
                "MONDAY" to "Mon",
                "TUESDAY" to "Tue",
                "WEDNESDAY" to "Wed",
                "THURSDAY" to "Thu",
                "FRIDAY" to "Fri",
                "SATURDAY" to "Sat",
                "SUNDAY" to "Sun"
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                weekList.forEach { (weekday, abbrev) ->
                    val isSelected = selectedWeekdays.contains(weekday)
                    Card(
                        modifier = Modifier
                            .weight(1F)
                            .padding(horizontal = 2.dp)
                            .clickable {
                                if (isSelected) {
                                    selectedWeekdays.remove(weekday)
                                } else {
                                    selectedWeekdays.add(weekday)
                                }
                                weekdayError = if (selectedWeekdays.isEmpty()) {
                                    "Select at least one active day."
                                } else null
                            }
                            .testTag("form_weekday_$abbrev"),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = abbrev,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            weekdayError?.let {
                Text(text = it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
            }
        }

        // Start Date Field
        StandardDatePickerField(
            label = "Task Start Date *",
            value = startDate,
            onValueChange = { startDate = it },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("form_task_start_date")
        )

        // End Date Field
        StandardDatePickerField(
            label = "Task End Date (Optional)",
            value = endDate,
            placeholder = "Select End Date",
            onValueChange = { endDate = it },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("form_task_end_date")
        )

        dateError?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.testTag("form_task_date_error_msg")
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Actions Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = onCancel,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("form_task_cancel_btn")
            ) {
                Text("Cancel", fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = {
                    var isValid = true

                    if (name.isBlank()) {
                        nameError = "Task name is required."
                        isValid = false
                    }

                    if (selectedWeekdays.isEmpty()) {
                        weekdayError = "At least one workday MUST be selected."
                        isValid = false
                    }

                    // Enforce parent Habit Set's dates bounds
                    val pStart = parentStartDate
                    val pEnd = parentEndDate

                    if (startDate.isNotBlank()) {
                        if (startDate < pStart) {
                            dateError = "Task start date ($startDate) cannot be before parent Habit Set start date ($pStart)."
                            isValid = false
                        } else if (pEnd != null && startDate > pEnd) {
                            dateError = "Task start date ($startDate) cannot be after parent Habit Set end date ($pEnd)."
                            isValid = false
                        } else {
                            dateError = null
                        }
                    }

                    if (endDate.isNotBlank()) {
                        if (startDate.isNotBlank() && endDate < startDate) {
                            dateError = "Task end date cannot be before task start date."
                            isValid = false
                        } else if (pEnd != null && endDate > pEnd) {
                            dateError = "Task end date ($endDate) cannot be after parent Habit Set end date ($pEnd)."
                            isValid = false
                        } else if (endDate < pStart) {
                            dateError = "Task end date ($endDate) cannot be before parent Habit Set start date ($pStart)."
                            isValid = false
                        } else {
                            dateError = null
                        }
                    }

                    if (isValid) {
                        onSubmit(
                            name.trim(),
                            description.trim(),
                            startDate.trim(),
                            if (endDate.isBlank()) null else endDate.trim(),
                            selectedWeekdays.toList()
                        )
                    }
                },
                modifier = Modifier
                    .weight(1.5f)
                    .height(48.dp)
                    .testTag("form_task_submit_btn"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Save Task", fontWeight = FontWeight.Black)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerDialogPicker(
    initialDateStr: String,
    onDateSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = try {
            val localDate = LocalDate.parse(initialDateStr)
            localDate.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val selectedMillis = state.selectedDateMillis
                    if (selectedMillis != null) {
                        val instant = java.time.Instant.ofEpochMilli(selectedMillis)
                        val localDate = java.time.LocalDateTime.ofInstant(instant, java.time.ZoneOffset.UTC).toLocalDate()
                        onDateSelected(localDate.toString())
                    }
                    onDismiss()
                }
            ) {
                Text("Confirm", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    ) {
        DatePicker(state = state)
    }
}

@Composable
fun StandardDatePickerField(
    label: String,
    value: String,
    placeholder: String = "Select Date",
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false
) {
    var showDialog by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        // We use a custom styled OutlinedTextField set to read-only
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label, fontWeight = FontWeight.Bold) },
            placeholder = { Text(placeholder) },
            isError = isError,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            trailingIcon = {
                IconButton(onClick = { showDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.Event,
                        contentDescription = "Open Date Picker"
                    )
                }
            }
        )
        // Overlay to block and intercept clicks safely
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable { showDialog = true }
        )
    }

    if (showDialog) {
        DatePickerDialogPicker(
            initialDateStr = if (value.isBlank()) LocalDate.now().toString() else value,
            onDateSelected = onValueChange,
            onDismiss = { showDialog = false }
        )
    }
}

data class HabitSetVisualInfo(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val bg: Color,
    val tint: Color
)

fun getHabitSetVisualInfo(name: String): HabitSetVisualInfo {
    val lowercase = name.lowercase()
    return when {
        lowercase.contains("routine") || lowercase.contains("daily") || lowercase.contains("morning") || lowercase.contains("night") -> {
            HabitSetVisualInfo(
                icon = Icons.Default.WbSunny,
                bg = Color(0xFFFFF0E4), // Soft cream/terracotta peach
                tint = Color(0xFFE08244)  // Terracotta Warm orange
            )
        }
        lowercase.contains("study") || lowercase.contains("learn") || lowercase.contains("math") || lowercase.contains("homework") || lowercase.contains("coding") -> {
            HabitSetVisualInfo(
                icon = Icons.Default.MenuBook,
                bg = Color(0xFFE2F3E4), // Soft sage/cream green
                tint = Color(0xFF2E7D32)  // Rich foliage green
            )
        }
        lowercase.contains("fitness") || lowercase.contains("gym") || lowercase.contains("workout") || lowercase.contains("run") || lowercase.contains("sport") || lowercase.contains("walk") -> {
            HabitSetVisualInfo(
                icon = Icons.Default.FitnessCenter,
                bg = Color(0xFFF1E6F7), // Soft heather purple
                tint = Color(0xFF7B1FA2)  // Tyrian orchid purple
            )
        }
        lowercase.contains("mindfulness") || lowercase.contains("meditation") || lowercase.contains("mind") || lowercase.contains("spa") || lowercase.contains("yoga") -> {
            HabitSetVisualInfo(
                icon = Icons.Default.Spa,
                bg = Color(0xFFFFECEF), // Soft rose pink
                tint = Color(0xFFC2185B)  // Dark crimson rose
            )
        }
        lowercase.contains("reading") || lowercase.contains("read") || lowercase.contains("book") -> {
            HabitSetVisualInfo(
                icon = Icons.Default.AutoStories,
                bg = Color(0xFFF3EBE6), // Muted linen brown
                tint = Color(0xFF5D4037)  // Cacao bean brown
            )
        }
        else -> {
            HabitSetVisualInfo(
                icon = Icons.Default.Bookmark,
                bg = Color(0xFFE0F7FA), // Soft ice blue/teal
                tint = Color(0xFF006064)  // Deep ocean cyans
            )
        }
    }
}
