package com.bbioon.plantdisease.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.bbioon.plantdisease.R
import com.bbioon.plantdisease.data.local.PreferencesManager
import com.bbioon.plantdisease.data.remote.GoogleAIService
import com.bbioon.plantdisease.ui.screens.detail.ScanDetailScreen
import com.bbioon.plantdisease.ui.screens.history.HistoryScreen
import com.bbioon.plantdisease.ui.screens.onboarding.OnboardingScreen
import com.bbioon.plantdisease.ui.screens.scanner.ScannerScreen
import com.bbioon.plantdisease.ui.screens.settings.SettingsScreen
import com.bbioon.plantdisease.ui.theme.*

sealed class Screen(val route: String) {
    data object Onboarding : Screen("onboarding")
    data object Scanner : Screen("scanner")
    data object History : Screen("history")
    data object Settings : Screen("settings")
    data object ScanDetail : Screen("scan/{id}") {
        fun withId(id: Long) = "scan/$id"
    }
}

@Composable
fun AppNavigation() {
    val context = LocalContext.current
    val prefs = remember { PreferencesManager(context) }
    val apiService = remember { GoogleAIService() }
    val navController = rememberNavController()

    var isFirstLaunch by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(Unit) { isFirstLaunch = prefs.isFirstLaunch() }

    if (isFirstLaunch == null) return

    val startDest = if (isFirstLaunch == true) Screen.Onboarding.route else Screen.Scanner.route

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showBottomBar = currentRoute in listOf(Screen.Scanner.route, Screen.History.route, Screen.Settings.route)

    val tabs = listOf(
        Triple(Screen.Scanner.route, Icons.Default.Eco, R.string.tab_scanner),
        Triple(Screen.History.route, Icons.Default.History, R.string.tab_history),
        Triple(Screen.Settings.route, Icons.Default.Settings, R.string.tab_settings),
    )

    Scaffold(
        containerColor = Background,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = TabBar,
                    tonalElevation = 0.dp,
                ) {
                    tabs.forEach { (route, icon, labelRes) ->
                        val selected = currentRoute == route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(icon, contentDescription = null) },
                            label = { Text(stringResource(labelRes), fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = TabActive,
                                selectedTextColor = TabActive,
                                unselectedIconColor = TabInactive,
                                unselectedTextColor = TabInactive,
                                indicatorColor = PrimaryMuted,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = startDest,
            modifier = Modifier.padding(padding),
        ) {
            composable(Screen.Onboarding.route) {
                OnboardingScreen(prefs = prefs) {
                    navController.navigate(Screen.Scanner.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            }
            composable(Screen.Scanner.route) {
                ScannerScreen(prefs = prefs, apiService = apiService)
            }
            composable(Screen.History.route) {
                HistoryScreen(onScanClick = { id ->
                    navController.navigate(Screen.ScanDetail.withId(id))
                })
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    prefs = prefs,
                    apiService = apiService,
                    onSetupGuide = {
                        navController.navigate(Screen.Onboarding.route)
                    },
                )
            }
            composable(
                route = Screen.ScanDetail.route,
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
            ) { backStackEntry ->
                val scanId = backStackEntry.arguments?.getLong("id") ?: return@composable
                ScanDetailScreen(scanId = scanId, onBack = { navController.popBackStack() })
            }
        }
    }
}
