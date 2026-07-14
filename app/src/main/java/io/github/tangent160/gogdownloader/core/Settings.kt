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

    private val librarySyncModeKey = stringPreferencesKey("library_sync_mode")

    /**
     * How the library was populated: [SYNC_MODE_FULL] or [SYNC_MODE_SEARCH].
     * In search mode automatic refreshes must not run `--updated-only`,
     * because it would fetch every game missing from the local database —
     * effectively a full update the user never asked for.
     */
    val librarySyncMode: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[librarySyncModeKey] ?: SYNC_MODE_FULL
    }

    suspend fun currentLibrarySyncMode(): String = librarySyncMode.first()

    suspend fun setLibrarySyncMode(mode: String) {
        context.dataStore.edit { prefs -> prefs[librarySyncModeKey] = mode }
    }

    companion object {
        const val SYNC_MODE_FULL = "full"
        const val SYNC_MODE_SEARCH = "search"
    }
}
