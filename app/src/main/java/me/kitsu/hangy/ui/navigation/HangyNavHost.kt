package me.kitsu.hangy.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import me.kitsu.hangy.R

/** Top-level tabs shown in the bottom navigation bar. */
enum class TopLevel(val route: String, val labelRes: Int, val icon: ImageVector) {
    Measure("measure", R.string.nav_measure, Icons.Filled.MonitorWeight),
    Routines("routines", R.string.nav_routines, Icons.Filled.FitnessCenter),
    Settings("settings", R.string.nav_settings, Icons.Filled.Settings),
}

object Routes {
    const val ROUTINE_DETAIL = "routine/{routineId}"
    const val ROUTINE_CREATE = "routine_create?cloneFrom={cloneFrom}"

    fun routineDetail(id: Long) = "routine/$id"
    fun routineCreate(cloneFrom: Long? = null) = "routine_create?cloneFrom=${cloneFrom ?: -1L}"
}

@Composable
fun HangyBottomBar(navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    NavigationBar {
        TopLevel.entries.forEach { tab ->
            val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
            NavigationBarItem(
                selected = selected,
                onClick = {
                    navController.navigate(tab.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(tab.icon, contentDescription = null) },
                label = { Text(stringResource(tab.labelRes)) },
            )
        }
    }
}

/** True for the three top-level tab routes, where the bottom bar should be visible. */
fun isTopLevelRoute(route: String?): Boolean = TopLevel.entries.any { it.route == route }
