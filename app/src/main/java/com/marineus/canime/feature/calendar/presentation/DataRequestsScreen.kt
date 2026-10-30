package com.marineus.canime.feature.calendar.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.marineus.canime.ui.theme.PastelBlue

@Immutable
data class DataRequestItem(
    val id: String,
    val title: String,
    val description: String,
    val inputLabel: String,
    val input: String,
    val actionLabel: String,
    val isRunning: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    val onInputChange: (String) -> Unit,
    val onRun: () -> Unit,
)

@Composable
fun DataRequestsScreen(
    requests: List<DataRequestItem>,
    isDarkTheme: Boolean,
    onThemeToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(Modifier.padding(top = 12.dp, bottom = 4.dp)) {
                Text("Veri kaynakları", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Uygulamanın kullandığı dış verileri buradan alabilir ve güncelleyebilirsiniz.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(requests, key = { it.id }) { request -> DataRequestCard(request) }
        item {
            ThemeSettingsCard(
                isDarkTheme = isDarkTheme,
                onThemeToggle = onThemeToggle,
            )
        }
        item {
            Text(
                text = "Karakter verileri: AniList · Eksik doğum günleri: mikai.me",
                modifier = Modifier
                    .padding(start = 4.dp, end = 4.dp, bottom = 16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DataRequestCard(request: DataRequestItem) {
    var isExpanded by rememberSaveable(request.id) { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FilledTonalButton(
                onClick = { isExpanded = !isExpanded },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(request.title)
            }
            Text(request.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
            AnimatedVisibility(visible = isExpanded) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = request.input,
                        onValueChange = request.onInputChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(request.inputLabel) },
                        singleLine = true,
                        enabled = !request.isRunning,
                    )
                    FilledTonalButton(
                        onClick = request.onRun,
                        enabled = !request.isRunning,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (request.isRunning) CircularProgressIndicator(strokeWidth = 2.dp, color = PastelBlue)
                        else Text(request.actionLabel)
                    }
                }
            }
            request.error?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            request.message?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun ThemeSettingsCard(isDarkTheme: Boolean, onThemeToggle: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (isDarkTheme) "☾ Koyu tema" else "☀ Koyu tema",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    if (isDarkTheme) "Açık" else "Kapalı",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = isDarkTheme,
                onCheckedChange = { onThemeToggle() },
                modifier = Modifier.semantics {
                    contentDescription = if (isDarkTheme) "Açık temaya geç" else "Koyu temaya geç"
                },
            )
        }
    }
}
