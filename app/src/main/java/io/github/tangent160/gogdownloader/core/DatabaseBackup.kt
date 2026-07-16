package io.github.tangent160.gogdownloader.core

import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import io.github.tangent160.gogdownloader.GogApp
import io.github.tangent160.gogdownloader.R
import io.github.tangent160.gogdownloader.download.DownloadQueue
import io.github.tangent160.gogdownloader.download.JobState
import io.github.tangent160.gogdownloader.sync.SyncMonitor
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Export/import of the CLI's gog-downloader.db (auth + library in one file). */
object DatabaseBackup {

    /** Copies the CLI's database out to a user-chosen document. Returns a message res id. */
    suspend fun export(app: GogApp, uri: Uri): Int = withContext(Dispatchers.IO) {
        val db = app.gogCli.databaseFile
        if (!db.isFile) return@withContext R.string.settings_backup_nothing
        runCatching {
            app.contentResolver.openOutputStream(uri, "wt")!!
                .use { out -> db.inputStream().use { it.copyTo(out) } }
        }.fold({ R.string.settings_backup_export_done }, { R.string.settings_backup_failed })
    }

    /**
     * Replaces the CLI's database with a user-chosen backup file. Returns a
     * message res id; [R.string.settings_backup_import_done] means success.
     */
    suspend fun import(app: GogApp, uri: Uri): Int = withContext(Dispatchers.IO) {
        val busy = SyncMonitor.isRunning || DownloadQueue.statuses.value.any {
            it.state == JobState.QUEUED || it.state == JobState.RUNNING
        }
        if (busy) return@withContext R.string.settings_backup_busy

        val temp = File(app.gogCli.configDir, "import.db.tmp")
        try {
            app.contentResolver.openInputStream(uri)!!
                .use { input -> temp.outputStream().use { input.copyTo(it) } }
            if (!isValidDatabase(temp)) return@withContext R.string.settings_backup_invalid
            if (!temp.renameTo(app.gogCli.databaseFile)) {
                return@withContext R.string.settings_backup_failed
            }
            R.string.settings_backup_import_done
        } catch (e: Exception) {
            R.string.settings_backup_failed
        } finally {
            temp.delete()
        }
    }

    private fun isValidDatabase(file: File): Boolean {
        val magic = ByteArray(16)
        val read = file.inputStream().use { it.read(magic) }
        if (read != 16 || !magic.decodeToString().startsWith("SQLite format 3")) return false
        return runCatching {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                db.rawQuery(
                    "select count(*) from sqlite_master where type = 'table' and name in ('games', 'auth')",
                    null,
                ).use { it.moveToFirst() && it.getInt(0) == 2 }
            }
        }.getOrDefault(false)
    }
}
