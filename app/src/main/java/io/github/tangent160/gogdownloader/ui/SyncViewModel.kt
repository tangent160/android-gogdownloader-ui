package io.github.tangent160.gogdownloader.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import io.github.tangent160.gogdownloader.core.SyncMode
import io.github.tangent160.gogdownloader.sync.SyncMonitor
import io.github.tangent160.gogdownloader.sync.SyncService
import io.github.tangent160.gogdownloader.sync.SyncState
import kotlinx.coroutines.flow.StateFlow

class SyncViewModel(application: Application) : AndroidViewModel(application) {

    val state: StateFlow<SyncState> = SyncMonitor.state

    /** Starts (or re-attaches to) a sync running in the foreground service. */
    fun sync(mode: SyncMode) {
        SyncService.start(getApplication(), mode)
    }

    /** Resets the shared Done state so the next visit starts a fresh sync. */
    fun acknowledgeDone() {
        if (SyncMonitor.state.value is SyncState.Done) {
            SyncMonitor.update(SyncState.Idle)
        }
    }
}
