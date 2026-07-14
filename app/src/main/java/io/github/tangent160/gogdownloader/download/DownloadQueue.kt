package io.github.tangent160.gogdownloader.download

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** One download request: the selected files of a single game. */
data class DownloadJob(
    val id: Long,
    val gameTitle: String,
    /** Installer file names the user did NOT select (passed as --skip-download). */
    val skippedNames: List<String>,
    /** Whether any installers were selected at all. */
    val includeInstallers: Boolean,
    val includeExtras: Boolean,
    val targetDir: String,
)

enum class JobState { QUEUED, RUNNING, DONE, FAILED, CANCELLED }

data class JobStatus(
    val job: DownloadJob,
    val state: JobState = JobState.QUEUED,
    val lastLine: String = "",
)

/** In-memory queue shared between [DownloadService] and the UI. */
object DownloadQueue {

    private var nextId = 1L
    private val statusesFlow = MutableStateFlow<List<JobStatus>>(emptyList())
    val statuses: StateFlow<List<JobStatus>> = statusesFlow.asStateFlow()

    @Synchronized
    fun enqueue(
        gameTitle: String,
        skippedNames: List<String>,
        includeInstallers: Boolean,
        includeExtras: Boolean,
        targetDir: String,
    ): DownloadJob {
        val job = DownloadJob(
            id = nextId++,
            gameTitle = gameTitle,
            skippedNames = skippedNames,
            includeInstallers = includeInstallers,
            includeExtras = includeExtras,
            targetDir = targetDir,
        )
        statusesFlow.update { it + JobStatus(job) }
        return job
    }

    fun nextQueued(): DownloadJob? =
        statusesFlow.value.firstOrNull { it.state == JobState.QUEUED }?.job

    fun update(jobId: Long, state: JobState? = null, lastLine: String? = null) {
        statusesFlow.update { list ->
            list.map { status ->
                if (status.job.id == jobId) {
                    status.copy(
                        state = state ?: status.state,
                        lastLine = lastLine ?: status.lastLine,
                    )
                } else {
                    status
                }
            }
        }
    }

    fun clearFinished() {
        statusesFlow.update { list ->
            list.filter { it.state == JobState.QUEUED || it.state == JobState.RUNNING }
        }
    }
}
