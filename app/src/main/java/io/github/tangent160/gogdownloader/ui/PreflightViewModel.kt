package io.github.tangent160.gogdownloader.ui

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.tangent160.gogdownloader.GogApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PreflightState(
    val internetGranted: Boolean? = null,
    val notificationsGranted: Boolean? = null,
    val storageGranted: Boolean? = null,
    val binaryRuns: Boolean? = null,
    val proxyWorks: Boolean? = null,
    val networkWorks: Boolean? = null,
    val error: String? = null,
)

class PreflightViewModel(application: Application) : AndroidViewModel(application) {

    private val cli = getApplication<GogApp>().gogCli

    private val stateFlow = MutableStateFlow(PreflightState())
    val state: StateFlow<PreflightState> = stateFlow.asStateFlow()

    fun refreshChecks(context: Context) {
        stateFlow.update {
            it.copy(
                internetGranted = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.INTERNET,
                ) == PackageManager.PERMISSION_GRANTED,
                notificationsGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    ContextCompat.checkSelfPermission(
                        context, Manifest.permission.POST_NOTIFICATIONS,
                    ) == PackageManager.PERMISSION_GRANTED
                } else {
                    true
                },
                storageGranted = hasStorageAccess(context),
            )
        }
    }

    fun runSelfTest(context: Context) {
        refreshChecks(context)
        stateFlow.update {
            it.copy(binaryRuns = null, proxyWorks = null, networkWorks = null, error = null)
        }
        viewModelScope.launch {
            // 1. Does the bundled binary execute at all?
            val version = runCatching { cli.run("--version") }.getOrNull()
            if (version?.success != true) {
                stateFlow.update {
                    it.copy(
                        binaryRuns = false,
                        proxyWorks = false,
                        networkWorks = false,
                        error = "The bundled gog-downloader binary failed to run.",
                    )
                }
                return@launch
            }
            stateFlow.update { it.copy(binaryRuns = true) }

            // 2. Is the in-app DNS proxy reachable and able to tunnel to GOG?
            // This runs entirely on the JVM side, isolating proxy problems
            // from problems in the PHP process.
            val proxyOk = runCatching { cli.proxySelfTest() }.getOrDefault(false)
            stateFlow.update { it.copy(proxyWorks = proxyOk) }
            if (!proxyOk) {
                stateFlow.update {
                    it.copy(
                        networkWorks = false,
                        error = "The in-app proxy could not tunnel to GOG. " +
                            "Check the device's internet connection.",
                    )
                }
                return@launch
            }

            // 3. Can the PHP process reach GOG through the in-app proxy? A code-login with a
            // dummy code is the only unauthenticated networked command; GOG
            // rejecting the code ("Failed to log in using the code") proves the
            // request made the round trip, while DNS/connection problems
            // surface as transport errors.
            val network = runCatching { cli.run("code-login", "preflight-self-test") }.getOrNull()
            val reachedGog = network != null &&
                (network.success || network.errorMessage.contains("Failed to log in using the code"))
            stateFlow.update {
                it.copy(
                    networkWorks = reachedGog,
                    error = if (reachedGog) null else network?.errorMessage,
                )
            }
        }
    }
}
