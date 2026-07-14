package io.github.tangent160.gogdownloader.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.tangent160.gogdownloader.R

/**
 * Asks the user how to populate the game database after login: a full update
 * (slow, entire library) or a search for specific games.
 */
@Composable
fun SyncChoiceScreen(
    onFullUpdate: () -> Unit,
    onSearchUpdate: (String) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text(
            text = stringResource(R.string.sync_choice_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = stringResource(R.string.sync_choice_search_explanation),
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text(stringResource(R.string.sync_choice_search_hint)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Button(
            onClick = { onSearchUpdate(query.trim()) },
            enabled = query.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.sync_choice_search))
        }
        HorizontalDivider()
        Text(
            text = stringResource(R.string.sync_choice_full_explanation),
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedButton(
            onClick = onFullUpdate,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.sync_choice_full))
        }
    }
}
