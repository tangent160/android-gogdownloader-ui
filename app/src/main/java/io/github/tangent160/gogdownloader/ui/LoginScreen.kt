package io.github.tangent160.gogdownloader.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.tangent160.gogdownloader.R

/** The URL gog-downloader tells users to visit for code login. */
private const val AUTH_URL =
    "https://auth.gog.com/auth?client_id=46899977096215655" +
        "&redirect_uri=https%3A%2F%2Fembed.gog.com%2Fon_login_success%3Forigin%3Dclient" +
        "&response_type=code&layout=client2"

@Composable
fun LoginScreen(
    onLoggedIn: () -> Unit,
    onImported: () -> Unit,
    viewModel: LoginViewModel = viewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    var pastedUrl by rememberSaveable { mutableStateOf("") }

    val importPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let { viewModel.importBackup(it) }
    }

    if (state is LoginState.Success) {
        onLoggedIn()
        return
    }
    if (state is LoginState.Imported) {
        onImported()
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
        Text(
            text = stringResource(R.string.login_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = stringResource(R.string.login_instructions),
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedButton(
            onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, AUTH_URL.toUri())) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.login_open_browser))
        }
        OutlinedTextField(
            value = pastedUrl,
            onValueChange = { pastedUrl = it },
            label = { Text(stringResource(R.string.login_paste_hint)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = state !is LoginState.Working,
        )
        when (val s = state) {
            is LoginState.Working -> CircularProgressIndicator()
            is LoginState.Error -> Text(
                text = s.message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
            else -> {}
        }
        Button(
            onClick = { viewModel.login(pastedUrl.trim()) },
            modifier = Modifier.fillMaxWidth(),
            enabled = pastedUrl.isNotBlank() && state !is LoginState.Working,
        ) {
            Text(stringResource(R.string.login_submit))
        }
        Text(
            text = stringResource(R.string.login_import_explanation),
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedButton(
            onClick = { importPicker.launch(arrayOf("*/*")) },
            modifier = Modifier.fillMaxWidth(),
            enabled = state !is LoginState.Working,
        ) {
            Text(stringResource(R.string.login_import_button))
        }
    }
}
