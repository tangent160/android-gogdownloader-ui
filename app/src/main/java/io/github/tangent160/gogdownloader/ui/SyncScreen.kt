package io.github.tangent160.gogdownloader.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.tangent160.gogdownloader.R
import io.github.tangent160.gogdownloader.core.SyncMode
import io.github.tangent160.gogdownloader.sync.SyncState

@Composable
fun SyncScreen(
    onDone: () -> Unit,
    mode: SyncMode = SyncMode.Incremental,
    viewModel: SyncViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    var started by androidx.compose.runtime.saveable.rememberSaveable {
        androidx.compose.runtime.mutableStateOf(false)
    }

    LaunchedEffect(Unit) {
        if (!started) {
            // Consume a leftover Done from a previous sync before starting.
            viewModel.acknowledgeDone()
            viewModel.sync(mode)
            started = true
        }
    }

    if (started && state is SyncState.Done) {
        LaunchedEffect(state) {
            viewModel.acknowledgeDone()
            onDone()
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        when (val s = state) {
            is SyncState.Running -> {
                Text(
                    text = stringResource(R.string.sync_running),
                    style = MaterialTheme.typography.titleMedium,
                )
                val progress = s.progress
                if (progress != null) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = stringResource(R.string.sync_progress, s.current ?: 0, s.total ?: 0),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    CircularProgressIndicator()
                }
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
                Button(onClick = { viewModel.sync(mode) }) {
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
