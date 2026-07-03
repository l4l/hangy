package me.kitsu.hangy.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import me.kitsu.hangy.ui.measure.MeasureScreen
import me.kitsu.hangy.ui.navigation.HangyBottomBar
import me.kitsu.hangy.ui.navigation.Routes
import me.kitsu.hangy.ui.navigation.TopLevel
import me.kitsu.hangy.ui.navigation.isTopLevelRoute
import me.kitsu.hangy.ui.routine.RoutineCreateScreen
import me.kitsu.hangy.ui.routine.RoutineDetailScreen
import me.kitsu.hangy.ui.routine.RoutineListScreen
import me.kitsu.hangy.ui.settings.SettingsScreen

@Composable
fun HangyApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            if (isTopLevelRoute(currentRoute)) HangyBottomBar(navController)
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = TopLevel.Measure.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(TopLevel.Measure.route) { MeasureScreen() }

            composable(TopLevel.Routines.route) {
                RoutineListScreen(
                    onOpen = { id -> navController.navigate(Routes.routineDetail(id)) },
                    onCreate = { navController.navigate(Routes.routineCreate()) },
                    onClone = { id -> navController.navigate(Routes.routineCreate(id)) },
                )
            }

            composable(TopLevel.Settings.route) { SettingsScreen() }

            composable(
                route = Routes.ROUTINE_DETAIL,
                arguments = listOf(navArgument("routineId") { type = NavType.LongType }),
            ) { entry ->
                val id = entry.arguments?.getLong("routineId") ?: return@composable
                RoutineDetailScreen(
                    routineId = id,
                    onClone = { cloneId -> navController.navigate(Routes.routineCreate(cloneId)) },
                    onBack = { navController.popBackStack() },
                    onDeleted = { navController.popBackStack() },
                )
            }

            composable(
                route = Routes.ROUTINE_CREATE,
                arguments = listOf(
                    navArgument("cloneFrom") {
                        type = NavType.LongType
                        defaultValue = -1L
                    },
                ),
            ) { entry ->
                val cloneFrom = entry.arguments?.getLong("cloneFrom")?.takeIf { it > 0 }
                RoutineCreateScreen(
                    cloneFrom = cloneFrom,
                    onSaved = { navController.popBackStack() },
                    onCancel = { navController.popBackStack() },
                )
            }
        }
    }
}
