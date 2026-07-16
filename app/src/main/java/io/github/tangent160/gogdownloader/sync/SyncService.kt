package io.github.tangent160.gogdownloader.sync

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import io.github.tangent160.gogdownloader.GogApp
import io.github.tangent160.gogdownloader.MainActivity
import io.github.tangent160.gogdownloader.R
import io.github.tangent160.gogdownloader.core.Settings
import io.github.tangent160.gogdownloader.core.SyncMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service running `update-database`, so the sync survives the
 * screen sleeping or the user switching apps.
 */
class SyncService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var worker: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.sync_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification(getString(R.string.sync_running))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        if (worker?.isActive != true) {
            val mode = when (intent?.getStringExtra(EXTRA_MODE)) {
                MODE_FULL -> SyncMode.Full
                MODE_SEARCH -> SyncMode.Search(intent.getStringExtra(EXTRA_QUERY).orEmpty())
                else -> SyncMode.Incremental
            }
            worker = scope.launch { runSync(mode) }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun runSync(mode: SyncMode) {
        val app = application as GogApp
        val wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "gogdownloader:sync")
        wakeLock.acquire(WAKE_LOCK_TIMEOUT_MS)
        try {
            SyncMonitor.update(SyncState.Running())
            // Remember how the library is populated; automatic refreshes are
            // disabled in search mode (see Settings.librarySyncMode).
            when (mode) {
                is SyncMode.Full -> app.settings.setLibrarySyncMode(Settings.SYNC_MODE_FULL)
                is SyncMode.Search -> app.settings.setLibrarySyncMode(Settings.SYNC_MODE_SEARCH)
                is SyncMode.Incremental -> {}
            }
            val includeHidden = app.settings.currentIncludeHidden()
            val result = runCatching {
                app.gogCli.updateDatabase(mode, includeHidden) { line ->
                    parseProgress(line)?.let { SyncMonitor.update(it) }
                }
            }.getOrNull()
            SyncMonitor.update(
                when {
                    result == null -> SyncState.Error("Failed to run gog-downloader")
                    result.success -> SyncState.Done
                    else -> SyncState.Error(result.errorMessage)
                },
            )
        } finally {
            if (wakeLock.isHeld) wakeLock.release()
            stopSelf()
        }
    }

    /**
     * Turns a CLI output line into a Running state. The CLI renders progress
     * for every update mode as ` 12/300 [===>------]   4% - Game Title`.
     */
    private fun parseProgress(rawLine: String): SyncState.Running? {
        val line = rawLine.trim()
        if (line.isEmpty()) return null
        val match = PROGRESS_PATTERN.find(line)
            ?: return SyncState.Running(lastLine = line)
        val current = match.groupValues[1].toInt()
        val total = match.groupValues[2].toInt()
        return SyncState.Running(
            lastLine = match.groupValues[3].trim().removePrefix("-").trim(),
            progress = if (total > 0) current.toFloat() / total else null,
            current = current,
            total = total,
        )
    }

    private fun buildNotification(text: String): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        private val PROGRESS_PATTERN = Regex("""^(\d+)/(\d+)\s*\[.*]\s*\d+%(.*)$""")
        private const val CHANNEL_ID = "sync"
        private const val NOTIFICATION_ID = 2
        private const val EXTRA_MODE = "mode"
        private const val EXTRA_QUERY = "query"
        const val MODE_INCREMENTAL = "incremental"
        const val MODE_FULL = "full"
        const val MODE_SEARCH = "search"
        private const val WAKE_LOCK_TIMEOUT_MS = 4 * 60 * 60 * 1000L

        fun start(context: Context, mode: SyncMode) {
            if (SyncMonitor.isRunning) return
            SyncMonitor.update(SyncState.Running())
            val intent = Intent(context, SyncService::class.java).apply {
                when (mode) {
                    is SyncMode.Full -> putExtra(EXTRA_MODE, MODE_FULL)
                    is SyncMode.Incremental -> putExtra(EXTRA_MODE, MODE_INCREMENTAL)
                    is SyncMode.Search -> {
                        putExtra(EXTRA_MODE, MODE_SEARCH)
                        putExtra(EXTRA_QUERY, mode.query)
                    }
                }
            }
            context.startForegroundService(intent)
        }
    }
}
