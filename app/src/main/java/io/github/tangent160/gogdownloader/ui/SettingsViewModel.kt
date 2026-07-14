package io.github.tangent160.gogdownloader.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.tangent160.gogdownloader.GogApp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val settings = getApplication<GogApp>().settings

    val downloadDir: Flow<String> = settings.downloadDir

    fun setDownloadDir(path: String) {
        viewModelScope.launch { settings.setDownloadDir(path) }
    }
}
