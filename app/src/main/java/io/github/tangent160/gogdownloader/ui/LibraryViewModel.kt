package io.github.tangent160.gogdownloader.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.tangent160.gogdownloader.GogApp
import io.github.tangent160.gogdownloader.core.Game
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface LibraryState {
    data object Loading : LibraryState
    data class Loaded(val games: List<Game>) : LibraryState
}

class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val app = getApplication<GogApp>()

    private val stateFlow = MutableStateFlow<LibraryState>(LibraryState.Loading)
    val state: StateFlow<LibraryState> = stateFlow.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            stateFlow.value = LibraryState.Loaded(app.gameDatabase.games())
        }
    }

    suspend fun coverUrl(gogId: Long): String? = app.coverRepository.coverUrl(gogId)
}
