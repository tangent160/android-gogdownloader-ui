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
    val configDir: File = File(context.filesDir, "gog").apply { mkdirs() }
    private val homeDir: File = context.filesDir
    private val tmpDir: File = context.cacheDir

    val databaseFile: File
        get() = File(configDir, "gog-downloader.db")

    class Result(val exitCode: Int, val output: String) {
        val success: Boolean get() = exitCode == 0
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
        val process = ProcessBuilder(binary.absolutePath, *args, "--no-interaction")
            .redirectErrorStream(true)
            .apply {
                environment()["CONFIG_DIRECTORY"] = configDir.absolutePath
                environment()["HOME"] = homeDir.absolutePath
                environment()["TMPDIR"] = tmpDir.absolutePath
            }
            .start()
        onProcessStarted(process)
        val output = StringBuilder()
        process.inputStream.bufferedReader().useLines { lines ->
            lines.forEach { line ->
                output.appendLine(line)
                onLine(line)
            }
        }
        val exit = process.waitFor()
        Result(exit, output.toString())
    }

    suspend fun codeLogin(codeOrUrl: String): Result = run("code-login", codeOrUrl)

    suspend fun updateDatabase(onLine: (String) -> Unit): Result =
        run("update-database", onLine = onLine)
}
