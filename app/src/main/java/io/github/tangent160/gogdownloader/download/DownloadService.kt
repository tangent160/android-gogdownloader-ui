package io.github.tangent160.gogdownloader.download

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
import androidx.core.app.NotificationCompat
import io.github.tangent160.gogdownloader.GogApp
import io.github.tangent160.gogdownloader.MainActivity
import io.github.tangent160.gogdownloader.R
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service that drains [DownloadQueue] by running the bundled
 * gog-downloader binary, one game at a time.
 */
class DownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var worker: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInForeground(getString(R.string.download_preparing))
        if (worker?.isActive != true) {
            worker = scope.launch { drainQueue() }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun drainQueue() {
        val cli = (application as GogApp).gogCli
        val wakeLock = getSystemService(android.os.PowerManager::class.java)
            .newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "gogdownloader:download")
        wakeLock.acquire(12 * 60 * 60 * 1000L)
        try {
            drainQueueLocked(cli)
        } finally {
            if (wakeLock.isHeld) wakeLock.release()
            stopSelf()
        }
    }

    private suspend fun drainQueueLocked(cli: io.github.tangent160.gogdownloader.core.GogCli) {
        while (true) {
            val job = DownloadQueue.nextQueued() ?: break
            DownloadQueue.update(job.id, state = JobState.RUNNING)
            updateNotification(getString(R.string.download_running, job.gameTitle))
            File(job.targetDir).mkdirs()

            val args = buildList {
                add("download")
                add(job.targetDir)
                add("--only=${job.gameTitle}")
                if (job.includeExtras) {
                    add("--extras")
                    add("--skip-existing-extras")
                }
                if (!job.includeInstallers) add("--no-games")
                job.platforms.forEach { add("--os=$it") }
                job.languages.forEach { add("--language=$it") }
                job.skippedNames.forEach { add("--skip-download=$it") }
            }

            val result = runCatching {
                cli.run(
                    *args.toTypedArray(),
                    onLine = { line ->
                        val trimmed = line.trim()
                        if (trimmed.isNotEmpty()) {
                            DownloadQueue.update(job.id, lastLine = trimmed)
                        }
                    },
                )
            }.getOrNull()

            DownloadQueue.update(
                job.id,
                state = if (result?.success == true) JobState.DONE else JobState.FAILED,
            )
        }
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.download_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .build()
    }

    private fun startInForeground(text: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                buildNotification(text),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(NOTIFICATION_ID, buildNotification(text))
        }
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(text))
    }

    companion object {
        private const val CHANNEL_ID = "downloads"
        private const val NOTIFICATION_ID = 1

        fun start(context: Context) {
            context.startForegroundService(Intent(context, DownloadService::class.java))
        }
    }
}
