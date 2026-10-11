package com.example.tracko.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.tracko.ui.edithabit.EditScreen
import com.example.tracko.ui.edithabit.EditViewModel
import com.example.tracko.ui.edithabit.EditViewModelFactory
import com.example.tracko.ui.habitdetail.DetailScreen
import com.example.tracko.ui.habitdetail.DetailViewModel
import com.example.tracko.ui.habitdetail.DetailViewModelFactory
import com.example.tracko.ui.home.HomeScreen
import com.example.tracko.ui.home.HomeViewModel
import com.example.tracko.ui.home.HomeViewModelFactory
import com.example.tracko.ui.milestones.MilestonesScreen
import com.example.tracko.ui.milestones.MilestonesViewModel
import com.example.tracko.ui.milestones.MilestonesViewModelFactory
import com.example.tracko.ui.theme.TrackoGreen
import com.example.tracko.ui.theme.TrackoGrey

/**
 * Classic string routes, one table. Call sites use the builders (never raw
 * strings) so a route change is a one-line fix.
 */
object Routes {
    const val HOME = "home"
    const val DETAIL = "detail/{habitId}"
    const val EDIT = "edit?habitId={habitId}"
    const val MILESTONES = "milestones"

    /** Card tap → habit detail (per-habit stats live in `habit-detail`). */
    fun detail(habitId: Long) = "detail/$habitId"

    /** Center + → null (create new); card menu → id (edit existing). */
    fun edit(habitId: Long?) = if (habitId == null) "edit" else "edit?habitId=$habitId"

    /**
     * Sentinel: `NavType.LongType` is a primitive `long` and rejects null, so a
     * missing arg decodes to this — never a real row id (Room ids start at 1).
     */
    const val NO_ID = -1L
}

/**
 * THE navigation file: the only place that changes screens. The `NavController`
 * owns the backstack (classic Navigation 2 — no manual lists). Screens only
 * expose tap lambdas; this file turns them into `navigate()` calls. Factories
 * arrive from `MainActivity` (manual DI); ViewModels are built per destination.
 */
@Composable
fun TrackoNavGraph(
    homeFactory: HomeViewModelFactory,
    detailFactoryFor: (Long) -> DetailViewModelFactory,
    editFactoryFor: (Long?) -> EditViewModelFactory,
    milestonesFactory: MilestonesViewModelFactory,
) {
    val navController = rememberNavController()
    // Tabs are peers; Edit/Detail are full-screen layers pushed on top of a
    // tab — the bar only exists on the peer screens, so + can only be pressed
    // from Home or Milestones and stacking can't happen.
    val entry by navController.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val showBottomBar = route == Routes.HOME || route == Routes.MILESTONES
    Scaffold(
        bottomBar = { if (showBottomBar) TrackoBottomBar(navController, route) },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        ) {
            composable(Routes.HOME) {
                val vm: HomeViewModel = viewModel(factory = homeFactory)
                HomeScreen(
                    viewModel = vm,
                    onHabitClick = { navController.navigate(Routes.detail(it)) },
                    // SingleTop: rapid/add-repeat taps reuse the fresh Edit instead
                    // of stacking duplicates that poison save/restore (2026-10-11).
                    onAddClick = { navController.navigate(Routes.edit(null)) { launchSingleTop = true } },
                )
            }
            composable(
                Routes.DETAIL,
                arguments = listOf(navArgument("habitId") { type = NavType.LongType }),
            ) { entry ->
                val habitId = entry.arguments?.getLong("habitId") ?: return@composable
                val vm: DetailViewModel = viewModel(factory = detailFactoryFor(habitId))
                DetailScreen(viewModel = vm, onBack = { navController.popBackStack() })
            }
            composable(
                Routes.EDIT,
                arguments = listOf(
                    navArgument("habitId") {
                        type = NavType.LongType
                        defaultValue = Routes.NO_ID
                    },
                ),
            ) { entry ->
                val raw = entry.arguments?.getLong("habitId") ?: Routes.NO_ID
                val habitId = raw.takeIf { it != Routes.NO_ID }
                val vm: EditViewModel = viewModel(factory = editFactoryFor(habitId))
                EditScreen(viewModel = vm, onDone = { navController.popBackStack() })
            }
            composable(Routes.MILESTONES) {
                val vm: MilestonesViewModel = viewModel(factory = milestonesFactory)
                MilestonesScreen(viewModel = vm)
            }
        }
    }
}

/**
 * Standard Material3 bar: Home tab (habit list) + Milestones tab (whole-app
 * statistics, decided 2026-10-11), with an empty center slot. The green +
 * floats OVER the seam from the overlay above (never inside the bar — the bar
 * slot clips anything sticking out). Bell/settings icons arrive with later modules.
 */
@Composable
private fun TrackoBottomBar(navController: NavHostController, route: String?) {
    NavigationBar {
        NavigationBarItem(
            selected = route == Routes.HOME,
            onClick = { navController.navigateTab(Routes.HOME) },
            icon = { Icon(Icons.Filled.Home, contentDescription = "Home") },
            label = { Text("Home") },
        )
        // Center + : green squircle with its OWN tap area. A NavigationBarItem
        // spreads its ripple across the whole slot — the clickable lives on the
        // 52dp box itself, so the ripple stays inside the squircle.
        // SingleTop: rapid taps reuse the fresh Edit instead of stacking
        // duplicates that poison tab save/restore (2026-10-11).
        Box(contentAlignment = Alignment.Center, modifier = Modifier.weight(1f)) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(TrackoGreen)
                    .clickable { navController.navigate(Routes.edit(null)) { launchSingleTop = true } },
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add habit", tint = Color.White, modifier = Modifier.size(28.dp))
            }
        }
        NavigationBarItem(
            selected = route == Routes.MILESTONES,
            onClick = { navController.navigateTab(Routes.MILESTONES) },
            icon = { Icon(Icons.Filled.Star, contentDescription = "Milestones") },
            label = { Text("Milestone") },
        )
    }
}

/**
 * Standard classic tab pattern: re-tapping a tab returns to its saved state
 * instead of stacking a duplicate. Sub-screens (detail/edit) sit above the tabs.
 */
private fun NavHostController.navigateTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}
