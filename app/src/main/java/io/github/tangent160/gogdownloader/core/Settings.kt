package io.github.tangent160.gogdownloader.core

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class Settings(private val context: Context) {

    private val downloadDirKey = stringPreferencesKey("download_dir")

    /** Default: app-specific external storage, writable without any permission. */
    val defaultDownloadDir: String
        get() = File(context.getExternalFilesDir(null), "GOG").absolutePath

    val downloadDir: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[downloadDirKey] ?: defaultDownloadDir
    }

    suspend fun currentDownloadDir(): String = downloadDir.first()

    suspend fun setDownloadDir(path: String) {
        context.dataStore.edit { prefs -> prefs[downloadDirKey] = path }
    }
}
