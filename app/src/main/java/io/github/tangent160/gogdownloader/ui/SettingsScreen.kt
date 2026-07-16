package io.github.tangent160.gogdownloader.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings as AndroidSettings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.tangent160.gogdownloader.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onFullResync: () -> Unit,
    onIncrementalSync: () -> Unit,
    onSearchSync: (String) -> Unit,
    viewModel: SettingsViewModel = viewModel(),
) {
    val context = LocalContext.current
    val savedDir by viewModel.downloadDir.collectAsState(initial = null)
    var editedDir by remember { mutableStateOf<String?>(null) }
    var storageGranted by remember { mutableStateOf(hasStorageAccess(context)) }

    // Re-check when returning from the system settings screen.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) storageGranted = hasStorageAccess(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(savedDir) {
        if (editedDir == null && savedDir != null) editedDir = savedDir
    }

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        uri?.let { treeUriToPath(it) }?.let { path ->
            editedDir = path
            viewModel.setDownloadDir(path)
        }
    }

    val legacyPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { storageGranted = it }

    val showToast = { messageRes: Int ->
        Toast.makeText(context, messageRes, Toast.LENGTH_LONG).show()
    }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    val exportPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        uri?.let { viewModel.exportDatabase(it, showToast) }
    }
    val importPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) pendingImportUri = uri
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_download_dir),
                style = MaterialTheme.typography.titleMedium,
            )
            OutlinedTextField(
                value = editedDir ?: "",
                onValueChange = { editedDir = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.settings_download_dir_hint)) },
            )
            OutlinedButton(
                onClick = { folderPicker.launch(null) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.settings_pick_folder))
            }
            Button(
                onClick = { editedDir?.takeIf { it.isNotBlank() }?.let { viewModel.setDownloadDir(it) } },
                modifier = Modifier.fillMaxWidth(),
                enabled = editedDir?.isNotBlank() == true && editedDir != savedDir,
            ) {
                Text(stringResource(R.string.save))
            }

            Text(
                text = stringResource(R.string.settings_storage_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = if (storageGranted) {
                    stringResource(R.string.settings_storage_granted)
                } else {
                    stringResource(R.string.settings_storage_explanation)
                },
                style = MaterialTheme.typography.bodySmall,
            )
            if (!storageGranted) {
                OutlinedButton(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            context.startActivity(
                                Intent(
                                    AndroidSettings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                    Uri.parse("package:${context.packageName}"),
                                ),
                            )
                        } else {
                            legacyPermission.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.settings_storage_grant))
                }
            }

            Text(
                text = stringResource(R.string.settings_library_title),
                style = MaterialTheme.typography.titleMedium,
            )
            var searchQuery by remember { mutableStateOf("") }
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text(stringResource(R.string.sync_choice_search_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedButton(
                onClick = { onSearchSync(searchQuery.trim()) },
                enabled = searchQuery.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.sync_choice_search))
            }
            Text(
                text = stringResource(R.string.settings_incremental_explanation),
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(
                onClick = onIncrementalSync,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.settings_incremental_sync))
            }
            Text(
                text = stringResource(R.string.settings_full_resync_explanation),
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(
                onClick = onFullResync,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.settings_full_resync))
            }

            Text(
                text = stringResource(R.string.settings_backup_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.settings_backup_explanation),
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(
                onClick = {
                    val stamp = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
                    exportPicker.launch("gog-downloader-backup-$stamp.db")
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.settings_backup_export))
            }
            OutlinedButton(
                onClick = { importPicker.launch(arrayOf("*/*")) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.settings_backup_import))
            }
        }
    }

    pendingImportUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingImportUri = null },
            title = { Text(stringResource(R.string.settings_backup_import_confirm_title)) },
            text = { Text(stringResource(R.string.settings_backup_import_confirm_text)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingImportUri = null
                    viewModel.importDatabase(uri, showToast)
                }) {
                    Text(stringResource(R.string.settings_backup_import_confirm_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingImportUri = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

}

/**
 * Best-effort conversion of a SAF tree URI on primary storage to a filesystem
 * path (the native downloader can only use real paths).
 */
private fun treeUriToPath(uri: Uri): String? {
    val docId = runCatching { android.provider.DocumentsContract.getTreeDocumentId(uri) }
        .getOrNull() ?: return null
    val parts = docId.split(":", limit = 2)
    if (parts.size != 2) return null
    return when (parts[0]) {
        "primary" -> "${Environment.getExternalStorageDirectory().absolutePath}/${parts[1]}"
        else -> "/storage/${parts[0]}/${parts[1]}"
    }
}
