package com.mempharma.app.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mempharma.app.R
import com.mempharma.app.ui.edit.AddEditMedicationScreen
import com.mempharma.app.ui.history.HistoryScreen
import com.mempharma.app.ui.home.HomeScreen
import com.mempharma.app.ui.meds.MedicationListScreen
import com.mempharma.app.ui.settings.SettingsScreen

/** Destination routes. */
object MemRoutes {
    const val HOME = "home"
    const val MEDS = "meds"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val EDIT = "edit/{medId}"
    fun edit(medId: Long) = "edit/$medId"
}

private val topLevelRoutes = setOf(MemRoutes.HOME, MemRoutes.MEDS, MemRoutes.HISTORY, MemRoutes.SETTINGS)

private data class BottomDestination(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector
)

private val bottomDestinations = listOf(
    BottomDestination(MemRoutes.HOME, R.string.nav_today, Icons.Filled.Home),
    BottomDestination(MemRoutes.MEDS, R.string.nav_medicines, Icons.Filled.DateRange),
    BottomDestination(MemRoutes.HISTORY, R.string.nav_history, Icons.Filled.List),
    BottomDestination(MemRoutes.SETTINGS, R.string.nav_settings, Icons.Filled.Settings)
)

/** Top-level navigation scaffold with the big, always-labelled bottom bar. */
@Composable
fun MemPharmaApp(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    Scaffold(
        modifier = modifier,
        bottomBar = {
            if (currentRoute in topLevelRoutes) {
                NavigationBar {
                    bottomDestinations.forEach { dest ->
                        NavigationBarItem(
                            selected = currentRoute == dest.route,
                            onClick = {
                                if (currentRoute != dest.route) {
                                    navController.navigate(dest.route) {
                                        popUpTo(MemRoutes.HOME) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = { Icon(dest.icon, contentDescription = null) },
                            label = { Text(stringResource(dest.labelRes)) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = MemRoutes.HOME,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(MemRoutes.HOME) {
                HomeScreen(
                    onAdd = { navController.navigate(MemRoutes.edit(0L)) },
                    onEdit = { navController.navigate(MemRoutes.edit(it)) }
                )
            }
            composable(MemRoutes.MEDS) {
                MedicationListScreen(
                    onAdd = { navController.navigate(MemRoutes.edit(0L)) },
                    onEdit = { navController.navigate(MemRoutes.edit(it)) }
                )
            }
            composable(
                route = MemRoutes.EDIT,
                arguments = listOf(navArgument("medId") { type = NavType.LongType; defaultValue = 0L })
            ) { entry ->
                val medId = entry.arguments?.getLong("medId") ?: 0L
                AddEditMedicationScreen(
                    medId = medId,
                    onDone = { navController.popBackStack() }
                )
            }
            composable(MemRoutes.HISTORY) { HistoryScreen() }
            composable(MemRoutes.SETTINGS) { SettingsScreen() }
        }
    }
}
