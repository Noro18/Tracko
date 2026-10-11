package com.example.tracko.ui.milestones

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Milestones shell: whole-app stats title. Celebrations arrive in `milestones`. */
@Composable
fun MilestonesScreen(viewModel: MilestonesViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Milestones (${uiState.overallStreak}-day)")
    }
}
