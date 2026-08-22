package com.neuroflow.app.presentation.common

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.neuroflow.app.presentation.analytics.AnalyticsScreen
import com.neuroflow.app.presentation.focus.FocusScreen
import com.neuroflow.app.presentation.history.HistoryScreen
import com.neuroflow.app.presentation.matrix.MatrixScreen
import com.neuroflow.app.presentation.matrix.QuadrantDetailScreen
import com.neuroflow.app.presentation.onboarding.AppGuideScreen
import com.neuroflow.app.presentation.schedule.ScheduleScreen
import com.neuroflow.app.presentation.settings.SettingsScreen
import com.neuroflow.app.presentation.settings.PriorityWeightsScreen

sealed class Screen(val route: String, val title: String, val icon: ImageVector?) {
    data object Matrix : Screen("matrix", "Matrix", Icons.Filled.GridView)
    data object Schedule : Screen("schedule", "Schedule", Icons.Filled.CalendarMonth)
    data object History : Screen("history", "History", Icons.Filled.History)
    data object Analytics : Screen("analytics", "Analytics", Icons.Filled.Analytics)
    data object QuadrantDetail : Screen("quadrant/{quadrantName}", "Quadrant", null)
    data object Focus : Screen("focus/{taskId}?skipped={skipped}", "Focus", null)
    data object Settings : Screen("settings", "Settings", null)
    data object PriorityWeights : Screen("priority_weights", "Priority Weights", null)
    data object AppGuide : Screen("app_guide", "App Guide", null)
}

val bottomNavItems = listOf(
    Screen.Matrix,
    Screen.Schedule,
    Screen.History,
    Screen.Analytics,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NeuroFlowApp() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val showBottomBar = bottomNavItems.any { screen ->
        currentDestination?.hierarchy?.any { it.route == screen.route } == true
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(modifier = Modifier.width(280.dp)) {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    "proFlow",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = androidx.compose.ui.Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                bottomNavItems.forEach { screen ->
                    NavigationDrawerItem(
                        icon = { Icon(screen.icon!!, contentDescription = screen.title) },
                        label = { Text(screen.title) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            scope.launch { drawerState.close() }
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                NavigationDrawerItem(
                    icon = { Icon(Icons.Filled.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") },
                    selected = currentDestination?.route == Screen.Settings.route,
                    onClick = {
                        scope.launch { drawerState.close() }
                        navController.navigate(Screen.Settings.route)
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = "How it works") },
                    label = { Text("How it works") },
                    selected = currentDestination?.route == Screen.AppGuide.route,
                    onClick = {
                        scope.launch { drawerState.close() }
                        navController.navigate(Screen.AppGuide.route)
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
            }
        }
    ) {
        Scaffold(
            bottomBar = {
                if (showBottomBar) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ) {
                        bottomNavItems.forEach { screen ->
                            NavigationBarItem(
                                icon = { Icon(screen.icon!!, contentDescription = screen.title) },
                                label = { Text(screen.title) },
                                selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                                onClick = {
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = Screen.Matrix.route,
                modifier = Modifier.padding(innerPadding)
            ) {
                composable(Screen.Matrix.route) {
                    MatrixScreen(
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        onNavigateToQuadrant = { quadrantName ->
                            navController.navigate("quadrant/$quadrantName")
                        },
                        onNavigateToFocus = { taskId ->
                            navController.navigate("focus/$taskId")
                        },
                        onNavigateToSettings = {
                            navController.navigate(Screen.Settings.route)
                        }
                    )
                }
                composable(Screen.Schedule.route) {
                    ScheduleScreen(
                        onNavigateToFocus = { taskId ->
                            navController.navigate("focus/$taskId")
                        }
                    )
                }
                composable(Screen.History.route) {
                    HistoryScreen()
                }
                composable(Screen.Analytics.route) {
                    AnalyticsScreen()
                }
                composable(
                    route = Screen.QuadrantDetail.route,
                    arguments = listOf(navArgument("quadrantName") { type = NavType.StringType })
                ) { backStackEntry ->
                    val quadrantName = backStackEntry.arguments?.getString("quadrantName") ?: "DO_FIRST"
                    QuadrantDetailScreen(
                        quadrantName = quadrantName,
                        onNavigateBack = { navController.popBackStack() },
                        onNavigateToFocus = { taskId ->
                            navController.navigate("focus/$taskId")
                        }
                    )
                }
                composable(
                    route = "focus/{taskId}?skipped={skipped}",
                    arguments = listOf(
                        navArgument("taskId") { type = NavType.StringType },
                        navArgument("skipped") { type = NavType.StringType; defaultValue = "" }
                    )
                ) { backStackEntry ->
                    val taskId = backStackEntry.arguments?.getString("taskId") ?: ""
                    val skipped = backStackEntry.arguments?.getString("skipped") ?: ""
                    FocusScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onNavigateToNextTask = { nextTaskId, newSkipped ->
                            navController.navigate("focus/$nextTaskId?skipped=$newSkipped") {
                                popUpTo("focus/$taskId") { inclusive = true }
                            }
                        }
                    )
                }
                composable(Screen.Settings.route) {
                    SettingsScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onNavigateToPriorityWeights = {
                            navController.navigate(Screen.PriorityWeights.route)
                        }
                    )
                }
                composable(Screen.PriorityWeights.route) {
                    PriorityWeightsScreen(
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
                composable(Screen.AppGuide.route) {
                    AppGuideScreen(
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}
