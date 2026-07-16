package io.github.tangent160.gogdownloader.core

import android.content.Context
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Wrapper around the bundled gog-downloader binary.
 *
 * The binary is packaged as a native library so Android installs it with the
 * exec bit set; user-writable locations may not be exec'd on modern Android.
 */
class GogCli(context: Context) {

    private val binary = File(context.applicationInfo.nativeLibraryDir, "libgogdownloader.so")
    private val proxy = ProxyServer()
    val configDir: File = File(context.filesDir, "gog").apply { mkdirs() }
    private val homeDir: File = context.filesDir
    private val tmpDir: File = context.cacheDir

    val databaseFile: File
        get() = File(configDir, "gog-downloader.db")

    class Result(val exitCode: Int, val output: String) {
        val success: Boolean get() = exitCode == 0

        /**
         * The actual error from the CLI output. On failure Symfony prints the
         * exception message, a boxed copy of it, and finally the command
         * usage synopsis — so "the last line" is useless as an error.
         */
        val errorMessage: String
            get() {
                // [critical] ... Message: "Failed to log in..."
                Regex("Message: \"(.+?)\"").find(output)?.let { return it.groupValues[1] }
                // Fallback: first non-blank line after "In SomeFile.php line 12:"
                val lines = output.lines()
                val marker = lines.indexOfFirst { it.trim().matches(Regex("In .+ line \\d+:")) }
                if (marker >= 0) {
                    lines.drop(marker + 1).firstOrNull { it.isNotBlank() }?.let { return it.trim() }
                }
                return lines.lastOrNull { it.isNotBlank() }?.trim() ?: "gog-downloader failed (exit $exitCode)"
            }
    }

    /**
     * Runs `gog-downloader <args>`, streaming each output line (stdout and
     * stderr merged) to [onLine]. Returns the process result.
     */
    suspend fun run(
        vararg args: String,
        onLine: (String) -> Unit = {},
        onProcessStarted: (Process) -> Unit = {},
    ): Result = withContext(Dispatchers.IO) {
        // The static musl binary can't do DNS on Android; route it through the
        // in-app proxy (see ProxyServer).
        val proxyUrl = "http://127.0.0.1:${proxy.start()}"
        val process = ProcessBuilder(binary.absolutePath, *args, "--no-interaction")
            .redirectErrorStream(true)
            .apply {
                environment()["CONFIG_DIRECTORY"] = configDir.absolutePath
                environment()["HOME"] = homeDir.absolutePath
                environment()["TMPDIR"] = tmpDir.absolutePath
                environment()["http_proxy"] = proxyUrl
                environment()["https_proxy"] = proxyUrl
                environment()["HTTP_PROXY"] = proxyUrl
                environment()["HTTPS_PROXY"] = proxyUrl
                // OpenSSL can't read Android's trust store; give it a PEM
                // export of the device's trusted CAs (see CaStore).
                environment()["SSL_CERT_FILE"] = CaStore.pemFile(configDir).absolutePath
            }
            .start()
        onProcessStarted(process)
        // Split on \r as well as \n: the CLI redraws progress bars with bare
        // carriage returns, which would otherwise only surface at process exit.
        val output = StringBuilder()
        process.inputStream.bufferedReader().use { reader ->
            val line = StringBuilder()
            while (true) {
                val c = reader.read()
                if (c == -1) break
                when (c.toChar()) {
                    '\n', '\r' -> {
                        if (line.isNotEmpty()) {
                            output.appendLine(line)
                            onLine(line.toString())
                            line.setLength(0)
                        }
                    }
                    else -> line.append(c.toChar())
                }
            }
            if (line.isNotEmpty()) {
                output.appendLine(line)
                onLine(line.toString())
            }
        }
        val exit = process.waitFor()
        Result(exit, output.toString())
    }

    /** Verifies the in-app proxy can tunnel to GOG (JVM side only). */
    suspend fun proxySelfTest(): Boolean = withContext(Dispatchers.IO) {
        proxy.selfTest("api.gog.com")
    }

    suspend fun codeLogin(codeOrUrl: String): Result = run("code-login", codeOrUrl)

    /**
     * Updates the games database. A [SyncMode.Full] sync makes one API request
     * per owned game and can take a long time on large libraries;
     * [SyncMode.Incremental] (--updated-only) also matches games not yet in
     * the local database.
     */
    suspend fun updateDatabase(mode: SyncMode, includeHidden: Boolean, onLine: (String) -> Unit): Result {
        val args = buildList {
            add("update-database")
            when (mode) {
                is SyncMode.Full -> {}
                is SyncMode.Incremental -> add("--updated-only")
                is SyncMode.Search -> add("--search=${mode.query}")
            }
            if (includeHidden) add("--include-hidden")
        }
        return run(*args.toTypedArray(), onLine = onLine)
    }
}
