package io.github.tangent160.gogdownloader.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.tangent160.gogdownloader.GogApp
import io.github.tangent160.gogdownloader.core.DownloadFile
import io.github.tangent160.gogdownloader.core.ExtraFile
import io.github.tangent160.gogdownloader.core.Game
import io.github.tangent160.gogdownloader.core.GogCliFilters
import io.github.tangent160.gogdownloader.download.DownloadQueue
import io.github.tangent160.gogdownloader.download.DownloadService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * All platform/language variants sharing one installer name. GOG reuses the
 * name across variants and the CLI's --skip-download matches on it, so a
 * group is the smallest unit that can be selected or skipped.
 */
data class DownloadGroup(
    val name: String,
    val files: List<DownloadFile>,
) {
    val totalBytes: Long get() = files.sumOf { it.sizeBytes }
}

sealed interface GameDetailState {
    data object Loading : GameDetailState
    data class Loaded(
        val game: Game,
        val groups: List<DownloadGroup>,
        val extras: List<ExtraFile>,
        val selected: Set<String> = emptySet(),
        val includeExtras: Boolean = false,
        val platformFilter: Set<String> = emptySet(),
        val languageFilter: Set<String> = emptySet(),
    ) : GameDetailState {
        val availablePlatforms: List<String> =
            groups.flatMap { it.files }.mapNotNull { it.platform }.distinct()
        val availableLanguages: List<String> =
            groups.flatMap { it.files }.mapNotNull { it.language }.distinct()

        private fun DownloadFile.matchesFilters(): Boolean =
            (platformFilter.isEmpty() || platform in platformFilter) &&
                (languageFilter.isEmpty() || language in languageFilter)

        /** Groups as they will download: filters applied to both rows and their variants. */
        val visibleGroups: List<DownloadGroup> = groups
            .map { group -> group.copy(files = group.files.filter { it.matchesFilters() }) }
            .filter { it.files.isNotEmpty() }
    }
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
                groups = app.gameDatabase.downloads(rowId)
                    .groupBy { it.name }
                    .map { (name, files) -> DownloadGroup(name, files) },
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

    fun togglePlatform(platform: String) {
        updateFilters { state ->
            state.copy(platformFilter = state.platformFilter.toggled(platform))
        }
    }

    fun toggleLanguage(language: String) {
        updateFilters { state ->
            state.copy(languageFilter = state.languageFilter.toggled(language))
        }
    }

    private fun Set<String>.toggled(value: String): Set<String> =
        if (value in this) this - value else this + value

    private fun updateFilters(transform: (GameDetailState.Loaded) -> GameDetailState.Loaded) {
        stateFlow.update { state ->
            if (state !is GameDetailState.Loaded) return@update state
            val updated = transform(state)
            // Drop selections whose group is no longer visible under the new filters.
            val visibleNames = updated.visibleGroups.map { it.name }.toSet()
            updated.copy(selected = updated.selected intersect visibleNames)
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
        val includeInstallers = state.selected.isNotEmpty()
        viewModelScope.launch {
            DownloadQueue.enqueue(
                gameTitle = state.game.title,
                skippedNames = state.visibleGroups.map { it.name }.filter { it !in state.selected },
                includeInstallers = includeInstallers,
                includeExtras = state.includeExtras,
                // Filtering an extras-only job by OS/language would drop the whole
                // game (the CLI discards games with no matching installers).
                platforms = if (includeInstallers) cliFilterArgs(state.platformFilter, GogCliFilters::osArgOrNull) else emptyList(),
                languages = if (includeInstallers) cliFilterArgs(state.languageFilter, GogCliFilters::languageArgOrNull) else emptyList(),
                targetDir = app.settings.currentDownloadDir(),
            )
            DownloadService.start(app)
        }
    }

    /**
     * If any active filter value can't be expressed as a CLI argument, drop
     * the whole filter dimension: passing only the mappable values would
     * exclude files the user selected under the unmappable one.
     */
    private fun cliFilterArgs(filter: Set<String>, map: (String) -> String?): List<String> {
        val mapped = filter.map { map(it) ?: return emptyList() }
        return mapped
    }
}
