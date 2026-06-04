package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.ExperimentalFoundationApi
import com.example.ui.HabitViewModel
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val viewModel: HabitViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val themeMode by viewModel.themeMode.collectAsState()
            val useDarkTheme = when (themeMode) {
                "Light Theme" -> false
                "Dark Theme" -> true
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }

            MyApplicationTheme(darkTheme = useDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainAppScaffold(viewModel)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun MainAppScaffold(viewModel: HabitViewModel) {
    val context = LocalContext.current
    val selectedTab by viewModel.selectedTab.collectAsState()
    var isFabExpanded by remember { mutableStateOf(false) }

    // Dialog state for "Add Daily Note"
    var showDailyNoteDialog by remember { mutableStateOf(false) }
    var dailyNoteText by remember { mutableStateOf("") }
    
    LaunchedEffect(showDailyNoteDialog) {
        if (showDailyNoteDialog) {
            val prefs = context.getSharedPreferences("habitgrid_settings", android.content.Context.MODE_PRIVATE)
            val savedNote = prefs.getString("global_daily_note_${java.time.LocalDate.now()}", null)
            if (savedNote != null) {
                dailyNoteText = savedNote
            } else {
                dailyNoteText = ""
            }
        }
    }
    
    var showSelectHabitSetForTaskDialog by remember { mutableStateOf(false) }
    val habitSetsWithTasks by viewModel.unfilteredHabitSets.collectAsState()
    
    val backStack = remember { androidx.compose.runtime.mutableStateListOf<String>("Home") }
    var backPressedOnce by remember { mutableStateOf(false) }

    LaunchedEffect(selectedTab) {
        if (backStack.lastOrNull() != selectedTab) {
            // Check if we are doing a back navigation; if so, top of backStack already matches.
            // Wait, if it doesn't match, we add it. 
            backStack.add(selectedTab)
        }
    }

    androidx.activity.compose.BackHandler {
        if (backStack.size > 1) {
            backStack.removeLast()
            viewModel.selectedTab.value = backStack.last()
            backPressedOnce = false
        } else if (selectedTab != "Home") {
            viewModel.selectedTab.value = "Home"
            backPressedOnce = false
        } else {
            if (backPressedOnce) {
                (context as? android.app.Activity)?.finish()
            } else {
                backPressedOnce = true
                Toast.makeText(context, "Press back again to exit", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar(
                modifier = Modifier.testTag("app_bottom_bar"),
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                val tabs = listOf(
                    Triple("Home", Icons.Default.Home, "home_tab"),
                    Triple("Analytics", Icons.Default.BarChart, "analytics_tab"),
                    Triple("Habit Sets", Icons.Default.Layers, "habit_sets_tab"),
                    Triple("Calendar", Icons.Default.CalendarMonth, "calendar_tab"),
                    Triple("Profile", Icons.Default.Person, "profile_tab")
                )

                tabs.forEach { (name, icon, tag) ->
                    val isSelected = selectedTab == name
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = {
                            viewModel.selectedTab.value = name
                            isFabExpanded = false // Collapse FAB on navigation
                        },
                        icon = {
                            Icon(icon, contentDescription = name)
                        },
                        label = {
                            Text(name, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                        },
                        modifier = Modifier.testTag(tag)
                    )
                }
            }
        },
        floatingActionButton = {
            // Only show FAB on Home and Habit Sets pages as per user request!
            if (selectedTab == "Home" || selectedTab == "Habit Sets") {
                Box(
                    contentAlignment = Alignment.BottomEnd,
                    modifier = Modifier.padding(bottom = 16.dp, end = 8.dp)
                ) {
                    val rotState by animateFloatAsState(
                        targetValue = if (isFabExpanded) 135f else 0f,
                        animationSpec = spring(stiffness = Spring.StiffnessMedium),
                        label = "fab_rotation"
                    )

                    // Main Primary speed-dial FAB triggers bottom sheet
                    FloatingActionButton(
                        onClick = { isFabExpanded = !isFabExpanded },
                        containerColor = if (isFabExpanded) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primary,
                        contentColor = if (isFabExpanded) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .testTag("flow_speed_dial_fab")
                            .shadow(if (isFabExpanded) 0.dp else 8.dp, CircleShape)
                            .size(60.dp),
                        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 0.dp, pressedElevation = 0.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Trigger Options",
                            modifier = Modifier
                                .size(28.dp)
                                .graphicsLayer { rotationZ = rotState }
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (selectedTab) {
                "Home" -> HomeScreen(viewModel = viewModel)
                "Analytics" -> AnalyticsScreen(viewModel = viewModel)
                "Habit Sets" -> HabitSetsScreen(viewModel = viewModel)
                "Calendar" -> CalendarScreen(viewModel = viewModel)
                "Profile" -> ProfileScreen(viewModel = viewModel)
                "Settings" -> SettingsScreen(
                    viewModel = viewModel,
                    onBack = { viewModel.selectedTab.value = "Home" }
                )
            }
        }
    }

    // Add Daily Note Dialog Component
    if (showDailyNoteDialog) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { 
                if (dailyNoteText.isNotBlank()) {
                    val prefs = context.getSharedPreferences("habitgrid_settings", android.content.Context.MODE_PRIVATE)
                    prefs.edit().putString("global_daily_note_${java.time.LocalDate.now()}", dailyNoteText).apply()
                    Toast.makeText(context, "Draft Auto-Saved", Toast.LENGTH_SHORT).show()
                }
                showDailyNoteDialog = false 
            },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                        .testTag("global_daily_note_form_panel"),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    // Custom App Bar / Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Add Daily Note",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            fontSize = 24.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        IconButton(onClick = { 
                            if (dailyNoteText.isNotBlank()) {
                                val prefs = context.getSharedPreferences("habitgrid_settings", android.content.Context.MODE_PRIVATE)
                                prefs.edit().putString("global_daily_note_${java.time.LocalDate.now()}", dailyNoteText).apply()
                            }
                            showDailyNoteDialog = false 
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }

                    Divider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))

                    Text(
                        text = "Document your focus levels and flow state during habit schedules.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                    )

                    // Note input
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Note Description *",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        OutlinedTextField(
                            value = dailyNoteText,
                            onValueChange = { 
                                dailyNoteText = it 
                                val prefs = context.getSharedPreferences("habitgrid_settings", android.content.Context.MODE_PRIVATE)
                                prefs.edit().putString("global_daily_note_${java.time.LocalDate.now()}", it).apply()
                            },
                            placeholder = { Text("Write your daily note here or reflect on your day...") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(0.6f)
                                .testTag("daily_note_text_input"),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    // Form Actions
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { 
                                if (dailyNoteText.isNotBlank()) {
                                    val prefs = context.getSharedPreferences("habitgrid_settings", android.content.Context.MODE_PRIVATE)
                                    prefs.edit().putString("global_daily_note_${java.time.LocalDate.now()}", dailyNoteText).apply()
                                }
                                showDailyNoteDialog = false 
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(vertical = 14.dp)
                        ) {
                            Text("Cancel", fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                if (dailyNoteText.isNotBlank()) {
                                    val prefs = context.getSharedPreferences("habitgrid_settings", android.content.Context.MODE_PRIVATE)
                                    prefs.edit().putString("global_daily_note_${java.time.LocalDate.now()}", dailyNoteText).apply()
                                    Toast.makeText(context, "Note Saved Successfully", Toast.LENGTH_SHORT).show()
                                    showDailyNoteDialog = false
                                } else {
                                    Toast.makeText(context, "Please write a note description.", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f).testTag("confirm_save_daily_note"),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(vertical = 14.dp)
                        ) {
                            Text("Save Note", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // Modal Bottom Sheet for Quick Actions
    if (isFabExpanded) {
        ModalBottomSheet(
            onDismissRequest = { isFabExpanded = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = null,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            modifier = Modifier.testTag("quick_actions_bottom_sheet")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(top = 16.dp, bottom = 24.dp)
            ) {
                // Centered Custom Header with close button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(modifier = Modifier.width(48.dp))
                    Text(
                        text = "Quick Actions",
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(
                        onClick = { isFabExpanded = false },
                        modifier = Modifier
                            .size(36.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))

                // Item 1: Add Task
                QuickSheetItem(
                    icon = {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .shadow(2.dp, RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add Task",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    },
                    title = "Add Task",
                    description = "Create a new behavior attached to an existing habit set."
                ) {
                    isFabExpanded = false
                    showSelectHabitSetForTaskDialog = true
                }

                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))

                // Item 2: Create Habit Set
                QuickSheetItem(
                    icon = {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .shadow(2.dp, RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Layers,
                                contentDescription = "Habit Set",
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    },
                    title = "Create Habit Set",
                    description = "An activity with recurring patterns, featuring in-depth monitoring and analytical data."
                ) {
                    isFabExpanded = false
                    viewModel.selectedTab.value = "Habit Sets"
                    viewModel.selectedHabitSetId.value = null
                    viewModel.isCreatingHabitSetGlobal.value = true
                }

                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))

                // Item 3: Add Today's Note
                QuickSheetItem(
                    icon = {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .shadow(2.dp, RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.tertiaryContainer, RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.EditNote,
                                contentDescription = "Daily Note",
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    },
                    title = "Add Today's Note",
                    description = "Document your focus levels, emotional state, and achievements during today's flow."
                ) {
                    isFabExpanded = false
                    showDailyNoteDialog = true
                }

                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
            }
        }
    }

    if (showSelectHabitSetForTaskDialog) {
        val activeSets = habitSetsWithTasks.filter { it.habitSet.status == "Active" }
        AlertDialog(
            onDismissRequest = { showSelectHabitSetForTaskDialog = false },
            title = {
                Text(
                    text = "Select Habit Set",
                    fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                if (activeSets.isEmpty()) {
                    Text(
                        text = "You do not have any active Habit Sets. Create a Habit Set first before adding a task.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(activeSets) { setWithTasks ->
                            Card(
                                onClick = {
                                    viewModel.selectedHabitSetId.value = setWithTasks.habitSet.id
                                    viewModel.selectedTab.value = "Habit Sets"
                                    viewModel.isCreatingTaskQuickly.value = true
                                    showSelectHabitSetForTaskDialog = false
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Layers,
                                        contentDescription = "Set",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = setWithTasks.habitSet.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSelectHabitSetForTaskDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun QuickSheetItem(
    icon: @Composable () -> Unit,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp, horizontal = 20.dp)
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        icon()
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                lineHeight = 16.sp
            )
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = "Navigate",
            tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
            modifier = Modifier.size(22.dp)
        )
    }
}
