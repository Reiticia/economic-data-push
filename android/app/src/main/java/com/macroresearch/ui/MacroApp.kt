package com.macroresearch.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.macroresearch.data.MacroRepository
import com.macroresearch.ui.analysis.AnalysisScreen
import com.macroresearch.ui.calendar.CalendarScreen
import com.macroresearch.ui.event.EventDetailScreen
import com.macroresearch.ui.history.HistoryScreen
import com.macroresearch.ui.home.HomeScreen
import com.macroresearch.ui.market.MarketScreen
import com.macroresearch.ui.settings.SettingsScreen

private data class TopDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val destinations = listOf(
    TopDestination("home", "首页", Icons.Outlined.Home),
    TopDestination("calendar", "日历", Icons.Outlined.CalendarMonth),
    TopDestination("market", "市场", Icons.Outlined.QueryStats),
    TopDestination("history", "历史", Icons.Outlined.Insights),
    TopDestination("settings", "设置", Icons.Outlined.Settings),
)

@Composable
fun MacroApp(repository: MacroRepository) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val showBottomBar = destinations.any { destination ->
        currentDestination?.hierarchy?.any { it.route == destination.route } == true
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    destinations.forEach { destination ->
                        val selected = currentDestination?.hierarchy?.any {
                            it.route == destination.route
                        } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(destination.icon, destination.label) },
                            label = { Text(destination.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(navController, startDestination = "home") {
            composable("home") {
                HomeScreen(repository, padding) { navController.navigate("event/$it") }
            }
            composable("calendar") {
                CalendarScreen(repository, padding) { navController.navigate("event/$it") }
            }
            composable("market") { MarketScreen(padding) }
            composable("history") {
                HistoryScreen(repository, padding) { navController.navigate("event/$it") }
            }
            composable("settings") { SettingsScreen(padding) }
            composable("event/{eventId}") { entry ->
                val id = entry.arguments?.getString("eventId")?.toLongOrNull() ?: return@composable
                EventDetailScreen(
                    id = id,
                    repository = repository,
                    onBack = navController::popBackStack,
                    onAnalysis = { navController.navigate("analysis/$id") },
                    onHistory = {
                        navController.navigate("history") {
                            popUpTo(navController.graph.findStartDestination().id)
                        }
                    },
                )
            }
            composable("analysis/{eventId}") { entry ->
                val id = entry.arguments?.getString("eventId")?.toLongOrNull() ?: return@composable
                AnalysisScreen(id, repository, navController::popBackStack)
            }
        }
    }
}

