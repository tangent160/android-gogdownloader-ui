package io.github.tangent160.gogdownloader.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.tangent160.gogdownloader.GogApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface LoginState {
    data object Idle : LoginState
    data object Working : LoginState
    data class Error(val message: String) : LoginState
    data object Success : LoginState
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
                else -> LoginState.Error(
                    result.output.lines().lastOrNull { it.isNotBlank() } ?: "Login failed",
                )
            }
        }
    }
}
