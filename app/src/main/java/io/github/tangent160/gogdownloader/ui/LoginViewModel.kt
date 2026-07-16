package io.github.tangent160.gogdownloader.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.tangent160.gogdownloader.GogApp
import io.github.tangent160.gogdownloader.R
import io.github.tangent160.gogdownloader.core.DatabaseBackup
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface LoginState {
    data object Idle : LoginState
    data object Working : LoginState
    data class Error(val message: String) : LoginState
    data object Success : LoginState

    /** A database backup was imported and contains a valid login. */
    data object Imported : LoginState
}

class LoginViewModel(application: Application) : AndroidViewModel(application) {

    private val cli = getApplication<GogApp>().gogCli

    private val stateFlow = MutableStateFlow<LoginState>(LoginState.Idle)
    val state: StateFlow<LoginState> = stateFlow.asStateFlow()

    fun login(codeOrUrl: String) {
        if (stateFlow.value is LoginState.Working) return
        stateFlow.value = LoginState.Working
        viewModelScope.launch {
            val result = runCatching { cli.codeLogin(codeOrUrl) }.getOrNull()
            stateFlow.value = when {
                result == null -> LoginState.Error("Failed to run gog-downloader")
                result.success -> LoginState.Success
                else -> LoginState.Error(result.errorMessage)
            }
        }
    }

    /** Restores a database backup instead of logging in. */
    fun importBackup(uri: Uri) {
        if (stateFlow.value is LoginState.Working) return
        stateFlow.value = LoginState.Working
        viewModelScope.launch {
            val app = getApplication<GogApp>()
            val message = DatabaseBackup.import(app, uri)
            val loggedIn = app.gameDatabase.isLoggedIn()
            stateFlow.value = when {
                message != R.string.settings_backup_import_done ->
                    LoginState.Error(app.getString(message))
                // Imported, but the backup has no saved login: keep the
                // library and let the user log in on top of it.
                !loggedIn -> LoginState.Error(app.getString(R.string.settings_backup_no_auth))
                else -> LoginState.Imported
            }
        }
    }
}
