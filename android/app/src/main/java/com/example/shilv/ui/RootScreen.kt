package com.example.shilv.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.shilv.ui.discovery.DiscoveryScreen
import com.example.shilv.ui.discovery.PhotoPermissionScreen
import com.example.shilv.ui.discovery.TripDiscoveryScreen
import com.example.shilv.ui.event.EventDetailScreen
import com.example.shilv.ui.map.TripMapScreen
import com.example.shilv.ui.memory.MemoryCardScreen
import com.example.shilv.ui.settings.SettingsScreen
import com.example.shilv.ui.timeline.AllTripsTimelineScreen
import com.example.shilv.ui.trip.DayTimelineScreen
import com.example.shilv.ui.trip.TripOverviewScreen

object Routes {
    const val Discovery = "discovery"
    const val Timeline = "timeline"
    const val Settings = "settings"
    const val TripDiscovery = "trip_discovery/{tripId}"
    const val TripOverview = "trip_overview/{tripId}"
    const val DayTimeline = "day_timeline/{tripId}/{dayId}"
    const val EventDetail = "event_detail/{tripId}/{dayId}/{eventId}"
    const val TripMap = "trip_map/{tripId}"
    const val MemoryCard = "memory_card/{tripId}"

    fun tripDiscovery(tripId: String) = "trip_discovery/$tripId"
    fun tripOverview(tripId: String) = "trip_overview/$tripId"
    fun dayTimeline(tripId: String, dayId: String) = "day_timeline/$tripId/$dayId"
    fun eventDetail(tripId: String, dayId: String, eventId: String) = "event_detail/$tripId/$dayId/$eventId"
    fun tripMap(tripId: String) = "trip_map/$tripId"
    fun memoryCard(tripId: String) = "memory_card/$tripId"
}

private data class TabItem(val route: String, val label: String, val icon: ImageVector)

private val topTabs = listOf(
    TabItem(Routes.Discovery, "回忆", Icons.Filled.PhotoLibrary),
    TabItem(Routes.Timeline, "时间线", Icons.Filled.CalendarMonth),
    TabItem(Routes.Settings, "我", Icons.Filled.Person),
)

@Composable
fun RootScreen(model: AppModel) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute == Routes.Discovery || currentRoute == Routes.Timeline || currentRoute == Routes.Settings

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    topTabs.forEach { tab ->
                        val selected = currentRoute == tab.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.Discovery,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Routes.Discovery) {
                if (model.accessState.value == PhotoAccessState.Full) {
                    DiscoveryScreen(model, navController)
                } else {
                    PhotoPermissionScreen(model)
                }
            }
            composable(Routes.Timeline) { AllTripsTimelineScreen(model, navController) }
            composable(Routes.Settings) { SettingsScreen(model) }
            composable(
                Routes.TripDiscovery,
                arguments = listOf(androidx.navigation.navArgument("tripId") { type = androidx.navigation.NavType.StringType }),
            ) { entry ->
                val tripId = entry.arguments?.getString("tripId") ?: ""
                TripDiscoveryScreen(model, navController, tripId)
            }
            composable(
                Routes.TripOverview,
                arguments = listOf(androidx.navigation.navArgument("tripId") { type = androidx.navigation.NavType.StringType }),
            ) { entry ->
                val tripId = entry.arguments?.getString("tripId") ?: ""
                TripOverviewScreen(model, navController, tripId)
            }
            composable(
                Routes.DayTimeline,
                arguments = listOf(
                    androidx.navigation.navArgument("tripId") { type = androidx.navigation.NavType.StringType },
                    androidx.navigation.navArgument("dayId") { type = androidx.navigation.NavType.StringType },
                ),
            ) { entry ->
                DayTimelineScreen(
                    model, navController,
                    entry.arguments?.getString("tripId") ?: "",
                    entry.arguments?.getString("dayId") ?: "",
                )
            }
            composable(
                Routes.EventDetail,
                arguments = listOf(
                    androidx.navigation.navArgument("tripId") { type = androidx.navigation.NavType.StringType },
                    androidx.navigation.navArgument("dayId") { type = androidx.navigation.NavType.StringType },
                    androidx.navigation.navArgument("eventId") { type = androidx.navigation.NavType.StringType },
                ),
            ) { entry ->
                EventDetailScreen(
                    model, navController,
                    entry.arguments?.getString("tripId") ?: "",
                    entry.arguments?.getString("dayId") ?: "",
                    entry.arguments?.getString("eventId") ?: "",
                )
            }
            composable(
                Routes.TripMap,
                arguments = listOf(androidx.navigation.navArgument("tripId") { type = androidx.navigation.NavType.StringType }),
            ) { entry ->
                TripMapScreen(model, navController, entry.arguments?.getString("tripId") ?: "")
            }
            composable(
                Routes.MemoryCard,
                arguments = listOf(androidx.navigation.navArgument("tripId") { type = androidx.navigation.NavType.StringType }),
            ) { entry ->
                MemoryCardScreen(model, entry.arguments?.getString("tripId") ?: "")
            }
        }
    }
}