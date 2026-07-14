package io.github.tangent160.gogdownloader.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.tangent160.gogdownloader.R
import io.github.tangent160.gogdownloader.download.DownloadQueue
import io.github.tangent160.gogdownloader.download.JobState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(onBack: () -> Unit) {
    val statuses by DownloadQueue.statuses.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.queue_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { DownloadQueue.clearFinished() }) {
                        Icon(Icons.Filled.ClearAll, contentDescription = stringResource(R.string.queue_clear))
                    }
                },
            )
        },
    ) { padding ->
        if (statuses.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.queue_empty))
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(statuses, key = { it.job.id }) { status ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = status.job.gameTitle,
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text = when (status.state) {
                                JobState.QUEUED -> stringResource(R.string.queue_state_queued)
                                JobState.RUNNING -> status.lastLine.ifEmpty {
                                    stringResource(R.string.queue_state_running)
                                }
                                JobState.DONE -> stringResource(R.string.queue_state_done)
                                JobState.FAILED -> stringResource(R.string.queue_state_failed)
                                JobState.CANCELLED -> stringResource(R.string.queue_state_cancelled)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (status.state == JobState.FAILED) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }
        }
    }
}
