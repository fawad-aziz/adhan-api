package com.adhan.prayertimes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PrayerTimesScreen(
    state: PrayerTimesUiState,
    onBaseUrlChange: (String) -> Unit,
    onZipChange: (String) -> Unit,
    onCountryChange: (String) -> Unit,
    onDateChange: (String) -> Unit,
    onMethodChange: (String) -> Unit,
    onFetch: () -> Unit,
    onDetectLocation: () -> Unit,
) {
    val scroll = rememberScrollState()
    Column(
        modifier = Modifier
            .padding(16.dp)
            .verticalScroll(scroll)
            .fillMaxWidth(),
    ) {
        Text("Prayer Times", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            "Enter ZIP / postal code to fetch times from your Azure Function.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = state.baseUrl,
            onValueChange = onBaseUrlChange,
            label = { Text("Function base URL") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = state.zip,
                onValueChange = onZipChange,
                label = { Text("ZIP / Postal") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            OutlinedTextField(
                value = state.country,
                onValueChange = onCountryChange,
                label = { Text("Country") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onDetectLocation,
                enabled = !state.locationLoading,
            ) {
                Text(if (state.locationLoading) "Detecting…" else "Use my location")
            }
            Text(
                "Fills ZIP and country from device location",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        state.locationError?.let { err ->
            Spacer(Modifier.height(4.dp))
            Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = state.date,
                onValueChange = onDateChange,
                label = { Text("Date (yyyy-MM-dd)") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("Optional") },
            )
            OutlinedTextField(
                value = state.method,
                onValueChange = onMethodChange,
                label = { Text("Method") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
        }
        Text(
            "Leave date empty for today (UTC). Method defaults to 2 on server.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onFetch,
            enabled = !state.loading,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
            ),
        ) {
            Text(if (state.loading) "Loading…" else "Get prayer times")
        }

        state.error?.let { err ->
            Spacer(Modifier.height(12.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
            ) {
                Text(
                    err,
                    modifier = Modifier.padding(12.dp),
                    color = Color(0xFFB91C1C),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        state.panel?.let { panel ->
            Spacer(Modifier.height(20.dp))
            PrayerPanel(panel)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PrayerPanel(panel: PrayerPanelUi) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF0F172A), Color(0xFF1F2937), Color(0xFF111827)),
                ),
            )
            .padding(20.dp),
    ) {
        Column {
            Text(
                "Today's Prayer Times",
                color = Color(0xFFE5E7EB),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
            val sub = buildString {
                append(panel.readableDate)
                if (panel.hijriDate.isNotBlank()) {
                    append(" · ")
                    append(panel.hijriDate)
                }
            }
            if (sub.isNotBlank()) {
                Text(
                    sub,
                    color = Color(0xFF9CA3AF),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                panel.cards.forEach { card ->
                    val isNext = panel.nextKey == card.key
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isNext) Color(0xFFF97316) else Color(0xE60F172A),
                        ),
                    ) {
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                            Text(
                                card.label,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = if (isNext) Color(0xFF111827) else Color(0xFFE5E7EB),
                            )
                            Text(
                                card.time,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = if (isNext) Color(0xFF111827) else Color(0xFFE5E7EB),
                                modifier = Modifier.padding(top = 4.dp),
                            )
                            if (isNext) {
                                Text(
                                    "UPCOMING",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 0.8.sp,
                                    color = Color(0xFF111827),
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
