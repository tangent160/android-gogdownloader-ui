package io.github.tangent160.gogdownloader.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.tangent160.gogdownloader.GogApp
import io.github.tangent160.gogdownloader.core.Game
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class LibrarySort {
    TITLE_ASC,
    TITLE_DESC,
    RECENT,
    SIZE,
}

sealed interface LibraryState {
    data object Loading : LibraryState

    /** [isFiltered] tells an empty [games] list apart from a search with no matches. */
    data class Loaded(val games: List<Game>, val isFiltered: Boolean) : LibraryState
}

class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val app = getApplication<GogApp>()

    private val allGames = MutableStateFlow<List<Game>?>(null)

    private val searchQueryFlow = MutableStateFlow("")
    val searchQuery: StateFlow<String> = searchQueryFlow.asStateFlow()

    private val sortFlow = MutableStateFlow(LibrarySort.TITLE_ASC)
    val sort: StateFlow<LibrarySort> = sortFlow.asStateFlow()

    val state: StateFlow<LibraryState> =
        combine(allGames, searchQueryFlow, sortFlow) { games, query, sort ->
            if (games == null) return@combine LibraryState.Loading
            val filtered = if (query.isBlank()) {
                games
            } else {
                games.filter { it.title.contains(query.trim(), ignoreCase = true) }
            }
            val sorted = when (sort) {
                LibrarySort.TITLE_ASC -> filtered.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
                LibrarySort.TITLE_DESC -> filtered.sortedWith(compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.title })
                LibrarySort.RECENT -> filtered.sortedByDescending { it.rowId }
                LibrarySort.SIZE -> filtered.sortedByDescending { it.totalSizeBytes }
            }
            LibraryState.Loaded(sorted, isFiltered = query.isNotBlank())
        }.stateIn(viewModelScope, SharingStarted.Eagerly, LibraryState.Loading)

    init {
        viewModelScope.launch {
            sortFlow.value = runCatching {
                LibrarySort.valueOf(app.settings.currentLibrarySort())
            }.getOrDefault(LibrarySort.TITLE_ASC)
            refresh()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            allGames.value = app.gameDatabase.games()
        }
    }

    fun setSearchQuery(query: String) {
        searchQueryFlow.value = query
    }

    fun setSort(sort: LibrarySort) {
        sortFlow.value = sort
        viewModelScope.launch { app.settings.setLibrarySort(sort.name) }
    }

    suspend fun coverUrl(gogId: Long): String? = app.coverRepository.coverUrl(gogId)
}
