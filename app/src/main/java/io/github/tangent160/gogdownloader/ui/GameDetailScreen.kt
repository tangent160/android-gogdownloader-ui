package io.github.tangent160.gogdownloader.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.tangent160.gogdownloader.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameDetailScreen(
    gameRowId: Long,
    onBack: () -> Unit,
    onQueued: () -> Unit,
    viewModel: GameDetailViewModel = viewModel(key = "game-$gameRowId"),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { viewModel.enqueueSelected(); onQueued() }

    fun startDownload() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.enqueueSelected()
            onQueued()
        }
    }

    androidx.compose.runtime.LaunchedEffect(gameRowId) { viewModel.load(gameRowId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text((state as? GameDetailState.Loaded)?.game?.title ?: "")
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        when (val s = state) {
            is GameDetailState.Loading ->
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }

            is GameDetailState.Loaded -> Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    if (s.downloads.isNotEmpty()) {
                        item {
                            SectionHeader(stringResource(R.string.detail_installers))
                        }
                        items(s.downloads) { file ->
                            val key = file.name
                            SelectableRow(
                                title = file.name,
                                subtitle = listOfNotNull(file.platform, file.language, formatSize(file.sizeBytes))
                                    .joinToString(" · "),
                                checked = key in s.selected,
                                onToggle = { viewModel.toggle(key) },
                            )
                        }
                    }
                    if (s.extras.isNotEmpty()) {
                        item {
                            SelectableRow(
                                title = stringResource(R.string.detail_include_extras),
                                subtitle = stringResource(R.string.detail_extras_count, s.extras.size),
                                checked = s.includeExtras,
                                onToggle = { viewModel.toggleExtras() },
                            )
                        }
                    }
                    if (s.downloads.isEmpty() && s.extras.isEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.detail_no_files),
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                }
                Button(
                    onClick = { startDownload() },
                    enabled = s.selected.isNotEmpty() || s.includeExtras,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    Text(stringResource(R.string.detail_download_selected, s.selected.size))
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun SelectableRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Column(modifier = Modifier.padding(start = 8.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1L shl 30 -> "%.1f GB".format(bytes / (1024.0 * 1024 * 1024))
    bytes >= 1L shl 20 -> "%.1f MB".format(bytes / (1024.0 * 1024))
    bytes >= 1L shl 10 -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}
