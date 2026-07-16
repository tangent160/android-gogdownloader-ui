package io.github.tangent160.gogdownloader.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.tangent160.gogdownloader.GogApp
import io.github.tangent160.gogdownloader.core.DatabaseBackup
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val settings = getApplication<GogApp>().settings

    val downloadDir: Flow<String> = settings.downloadDir

    fun setDownloadDir(path: String) {
        viewModelScope.launch { settings.setDownloadDir(path) }
    }

    /** Copies the CLI's database out to a user-chosen document. */
    fun exportDatabase(uri: Uri, onResult: (Int) -> Unit) {
        viewModelScope.launch { onResult(DatabaseBackup.export(getApplication(), uri)) }
    }

    /** Replaces the CLI's database with a user-chosen backup file. */
    fun importDatabase(uri: Uri, onResult: (Int) -> Unit) {
        viewModelScope.launch { onResult(DatabaseBackup.import(getApplication(), uri)) }
    }
}
