package com.example.tracko

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.tracko.data.local.TrackoDatabase
import com.example.tracko.data.repository.AppStateRepository
import com.example.tracko.data.repository.HabitLogRepository
import com.example.tracko.data.repository.HabitRepository
import com.example.tracko.ui.theme.TrackoTheme

class MainActivity : ComponentActivity() {
    /**
     * Manual DI (architecture assumption 5): one DB singleton → three repos.
     * Per-feature `XxxViewModelFactory(repo)` instances are built here in the
     * `home` / `habit-edit` modules and passed to `viewModel(factory = ...)`.
     * Screens never call `getInstance` themselves.
     */
    private val database by lazy { TrackoDatabase.getInstance(this) }
    private val habitRepository by lazy {
        HabitRepository(database.habitDao(), database.habitLogDao(), database.appStateDao())
    }
    private val habitLogRepository by lazy { HabitLogRepository(database.habitLogDao()) }
    private val appStateRepository by lazy { AppStateRepository(database.appStateDao()) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TrackoTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        name = "Android",
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    TrackoTheme {
        Greeting("Android")
    }
}