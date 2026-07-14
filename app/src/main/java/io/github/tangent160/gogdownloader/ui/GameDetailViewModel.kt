package io.github.tangent160.gogdownloader.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.tangent160.gogdownloader.GogApp
import io.github.tangent160.gogdownloader.core.DownloadFile
import io.github.tangent160.gogdownloader.core.ExtraFile
import io.github.tangent160.gogdownloader.core.Game
import io.github.tangent160.gogdownloader.download.DownloadQueue
import io.github.tangent160.gogdownloader.download.DownloadService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface GameDetailState {
    data object Loading : GameDetailState
    data class Loaded(
        val game: Game,
        val downloads: List<DownloadFile>,
        val extras: List<ExtraFile>,
        val selected: Set<String> = emptySet(),
        val includeExtras: Boolean = false,
    ) : GameDetailState
}

class GameDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val app = getApplication<GogApp>()

    private val stateFlow = MutableStateFlow<GameDetailState>(GameDetailState.Loading)
    val state: StateFlow<GameDetailState> = stateFlow.asStateFlow()

    fun load(rowId: Long) {
        if (stateFlow.value is GameDetailState.Loaded) return
        viewModelScope.launch {
            val game = app.gameDatabase.game(rowId) ?: return@launch
            stateFlow.value = GameDetailState.Loaded(
                game = game,
                downloads = app.gameDatabase.downloads(rowId),
                extras = app.gameDatabase.extras(rowId),
            )
        }
    }

    fun toggle(name: String) {
        stateFlow.update { state ->
            if (state !is GameDetailState.Loaded) return@update state
            state.copy(
                selected = if (name in state.selected) state.selected - name else state.selected + name,
            )
        }
    }

    fun toggleExtras() {
        stateFlow.update { state ->
            if (state !is GameDetailState.Loaded) return@update state
            state.copy(includeExtras = !state.includeExtras)
        }
    }

    fun enqueueSelected() {
        val state = stateFlow.value as? GameDetailState.Loaded ?: return
        if (state.selected.isEmpty() && !state.includeExtras) return
        viewModelScope.launch {
            DownloadQueue.enqueue(
                gameTitle = state.game.title,
                skippedNames = state.downloads.map { it.name }.filter { it !in state.selected },
                includeInstallers = state.selected.isNotEmpty(),
                includeExtras = state.includeExtras,
                targetDir = app.settings.currentDownloadDir(),
            )
            DownloadService.start(app)
        }
    }
}
