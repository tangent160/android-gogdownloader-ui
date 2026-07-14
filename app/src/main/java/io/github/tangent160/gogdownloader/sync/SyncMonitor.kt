package io.github.tangent160.gogdownloader.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface SyncState {
    data object Idle : SyncState

    data class Running(
        val lastLine: String = "",
        /** 0..1 when the CLI's "current/total" progress bar is available. */
        val progress: Float? = null,
        val current: Int? = null,
        val total: Int? = null,
    ) : SyncState

    data class Error(val message: String) : SyncState
    data object Done : SyncState
}

/** Shared state between [SyncService] and the UI. */
object SyncMonitor {

    private val stateFlow = MutableStateFlow<SyncState>(SyncState.Idle)
    val state: StateFlow<SyncState> = stateFlow.asStateFlow()

    val isRunning: Boolean get() = stateFlow.value is SyncState.Running

    fun update(state: SyncState) {
        stateFlow.value = state
    }
}
