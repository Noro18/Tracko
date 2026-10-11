package com.example.tracko.ui.habitdetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Detail shell: shows its habit id. Calendar + stats arrive in `habit-detail`. */
@Composable
fun DetailScreen(viewModel: DetailViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Detail ${uiState.habitId}")
        // Shell-stage back path (a real top-bar arrow arrives in `habit-detail`).
        Button(onClick = onBack) { Text("Back") }
    }
}
