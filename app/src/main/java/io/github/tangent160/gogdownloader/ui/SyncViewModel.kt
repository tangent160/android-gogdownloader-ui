package io.github.tangent160.gogdownloader.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.tangent160.gogdownloader.GogApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface SyncState {
    data object Idle : SyncState
    data class Running(val lastLine: String = "") : SyncState
    data class Error(val message: String) : SyncState
    data object Done : SyncState
}

class SyncViewModel(application: Application) : AndroidViewModel(application) {

    private val cli = getApplication<GogApp>().gogCli

    private val stateFlow = MutableStateFlow<SyncState>(SyncState.Idle)
    val state: StateFlow<SyncState> = stateFlow.asStateFlow()

    fun sync() {
        if (stateFlow.value is SyncState.Running) return
        stateFlow.value = SyncState.Running()
        viewModelScope.launch {
            val result = runCatching {
                cli.updateDatabase { line ->
                    if (line.isNotBlank()) {
                        stateFlow.update { current ->
                            if (current is SyncState.Running) SyncState.Running(line.trim()) else current
                        }
                    }
                }
            }.getOrNull()
            stateFlow.value = when {
                result == null -> SyncState.Error("Failed to run gog-downloader")
                result.success -> SyncState.Done
                else -> SyncState.Error(result.errorMessage)
            }
        }
    }
}
