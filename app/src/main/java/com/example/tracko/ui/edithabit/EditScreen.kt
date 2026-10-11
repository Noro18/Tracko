package com.example.tracko.ui.edithabit

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

/** Edit shell: "new" vs "edit #id". Form arrives in `habit-edit`. */
@Composable
fun EditScreen(viewModel: EditViewModel, onDone: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(if (uiState.habitId == null) "Edit (new)" else "Edit ${uiState.habitId}")
        // Shell-stage leave path: save-success and the top-bar arrow will call
        // this in `habit-edit`; a Back button proves it today.
        Button(onClick = onDone) { Text("Back") }
    }
}
