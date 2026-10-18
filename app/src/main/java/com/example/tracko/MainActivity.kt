package com.example.tracko

import android.content.pm.ApplicationInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.example.tracko.data.local.DebugSeeder
import com.example.tracko.data.local.TrackoDatabase
import com.example.tracko.data.repository.AppStateRepository
import com.example.tracko.data.repository.HabitLogRepository
import com.example.tracko.data.repository.HabitRepository
import com.example.tracko.ui.edithabit.EditViewModelFactory
import com.example.tracko.ui.habitdetail.DetailViewModelFactory
import com.example.tracko.ui.home.HomeViewModelFactory
import com.example.tracko.ui.milestones.MilestonesViewModelFactory
import com.example.tracko.ui.navigation.TrackoNavGraph
import com.example.tracko.ui.theme.TrackoTheme
import java.time.LocalDate
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    /**
     * Manual DI (architecture assumption 5): one DB singleton → three repos →
     * per-screen factories → [TrackoNavGraph]. Screens never call `getInstance`
     * themselves; the graph never builds a ViewModel without a factory.
     */
    private val database by lazy { TrackoDatabase.getInstance(this) }
    private val habitRepository by lazy {
        HabitRepository(database.habitDao(), database.habitLogDao(), database.appStateDao())
    }
    private val habitLogRepository by lazy { HabitLogRepository(database.habitLogDao()) }
    private val appStateRepository by lazy { AppStateRepository(database.appStateDao()) }

    private val homeFactory by lazy {
        HomeViewModelFactory(habitRepository, habitLogRepository, appStateRepository, LocalDate.now())
    }
    private val milestonesFactory by lazy {
        MilestonesViewModelFactory(habitRepository, appStateRepository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            lifecycleScope.launch {
                // TEMPORARY demo data while no creation UI exists (debug builds only).
                DebugSeeder.seedIfEmpty(habitRepository, database.habitLogDao(), LocalDate.now())
            }
        }
        setContent {
            TrackoTheme {
                TrackoNavGraph(
                    homeFactory = homeFactory,
                    detailFactoryFor = { id -> DetailViewModelFactory(id, habitRepository, habitLogRepository) },
                    editFactoryFor = { id -> EditViewModelFactory(id, habitRepository, habitLogRepository) },
                    milestonesFactory = milestonesFactory,
                )
            }
        }
    }
}
