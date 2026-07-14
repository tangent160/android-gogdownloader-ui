package io.github.tangent160.gogdownloader.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.tangent160.gogdownloader.R

@Composable
fun SyncScreen(
    onDone: () -> Unit,
    viewModel: SyncViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) { viewModel.sync() }

    if (state is SyncState.Done) {
        onDone()
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        when (val s = state) {
            is SyncState.Running -> {
                CircularProgressIndicator()
                Text(
                    text = stringResource(R.string.sync_running),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = s.lastLine,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                )
            }

            is SyncState.Error -> {
                Text(
                    text = stringResource(R.string.sync_failed),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                Text(text = s.message, style = MaterialTheme.typography.bodySmall)
                Button(onClick = { viewModel.sync() }) {
                    Text(stringResource(R.string.retry))
                }
                TextButton(onClick = onDone) {
                    Text(stringResource(R.string.sync_skip))
                }
            }

            else -> {}
        }
    }
}
