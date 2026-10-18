package com.example.tracko.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.tracko.domain.HeatCell
import com.example.tracko.domain.HeatmapBuilder
import com.example.tracko.domain.model.DayStatus
import com.example.tracko.domain.model.HabitType
import com.example.tracko.domain.model.LogEntry
import com.example.tracko.ui.theme.TrackoGreen
import com.example.tracko.ui.theme.TrackoGrey
import com.example.tracko.ui.theme.TrackoTheme
import java.time.LocalDate

/** One habit on Home: name, streak + freeze labels, heatmap. No logic, just draws [row]. */
@Composable
fun HabitCard(row: HabitRowUi, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(row.name, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("🔥 ${row.currentStreak} day streak", style = MaterialTheme.typography.bodyMedium)
                Text("❄️ ${row.freezeCount}/3 freezes", style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Heatmap(row.heatmap)
                Spacer(Modifier.weight(1f))
                // TODO(Task 3): pass the real onCheckTap — position + look only for now.
                HabitCheckButton(type = row.type, enabled = row.checkEnabled, onClick = {})
            }
        }
    }
}

/** Left-aligned week-columns of small squares; a fixed slot right of it holds the check button. */
@Composable
private fun Heatmap(weeks: List<List<HeatCell>>) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        weeks.forEach { week ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                week.forEach { cell -> HeatSquare(cell) }
            }
        }
    }
}

@Composable
private fun HeatSquare(cell: HeatCell) {
    val color = if (cell.isFuture) Color.Transparent else cell.status.heatColor()
    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(color),
    )
}

/** FR-14: frozen days look different from completed ones. */
private fun DayStatus?.heatColor(): Color = when (this) {
    DayStatus.DONE, DayStatus.CLEAN, DayStatus.BONUS -> TrackoGreen
    DayStatus.FROZEN -> Color(0xFF64B5F6)
    DayStatus.MISSED, DayStatus.SLIPPED -> Color(0xFFE57373)
    DayStatus.NOT_ACTIVE, DayStatus.PAUSED, null -> TrackoGrey.copy(alpha = 0.25f)
}

/**
 * The card's action button. Icon follows the habit type (✓ positive, ✕
 * negative); greyed when no legal tap exists today. It never decides rules —
 * `enabled` and `onClick` arrive from outside (ViewModel in Task 3).
 */
@Composable
fun HabitCheckButton(
    type: HabitType,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = if (enabled) TrackoGreen else TrackoGrey.copy(alpha = 0.35f)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(container)
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Icon(
            imageVector = if (type == HabitType.POSITIVE) Icons.Filled.Check else Icons.Filled.Close,
            contentDescription = if (type == HabitType.POSITIVE) "Mark done" else "Mark no slip",
            tint = Color.White,
            modifier = Modifier.size(26.dp),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun HabitCardPreview() {
    val today = LocalDate.parse("2026-10-11")
    val logs = (0..40).map {
        LogEntry(
            habitId = 1L,
            date = today.minusDays(it.toLong()),
            status = when {
                it % 9 == 8 -> DayStatus.MISSED
                it % 9 == 4 -> DayStatus.FROZEN
                else -> DayStatus.DONE
            },
            loggedAt = 1L,
        )
    }
    TrackoTheme {
        HabitCard(
            row = HabitRowUi(1L, "Run", HabitType.POSITIVE, currentStreak = 12, freezeCount = 2, heatmap = HeatmapBuilder().build(logs, today, weeks = 12), checkEnabled = true),
            onClick = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}
