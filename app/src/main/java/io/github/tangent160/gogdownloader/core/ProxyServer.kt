package io.github.tangent160.gogdownloader.core

import android.util.Log
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors

private const val TAG = "GogProxy"

/**
 * Minimal local HTTP CONNECT proxy for the bundled gog-downloader process.
 *
 * The static (musl) PHP binary cannot resolve DNS on Android: musl reads
 * /etc/resolv.conf, which doesn't exist there. Routing its traffic through
 * this in-app proxy makes the JVM do the name resolution instead — curl and
 * PHP pick the proxy up from the http(s)_proxy environment variables.
 */
class ProxyServer {

    private val executor = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "gog-proxy").apply { isDaemon = true }
    }
    private var serverSocket: ServerSocket? = null

    /** Starts the proxy (idempotent) and returns its port on 127.0.0.1. */
    @Synchronized
    fun start(): Int {
        serverSocket?.takeIf { !it.isClosed }?.let { return it.localPort }
        val socket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        serverSocket = socket
        Log.i(TAG, "listening on 127.0.0.1:${socket.localPort}")
        executor.execute {
            while (!socket.isClosed) {
                val client = try {
                    socket.accept()
                } catch (e: Exception) {
                    Log.w(TAG, "accept loop ended: $e")
                    break
                }
                executor.execute { handleClient(client) }
            }
            Log.i(TAG, "server socket closed")
        }
        return socket.localPort
    }

    /**
     * Self-test: opens a CONNECT tunnel to [host]:[port] through the proxy
     * from the JVM side and reports whether the proxy answered 200.
     */
    fun selfTest(host: String, port: Int = 443): Boolean = runCatching {
        Socket("127.0.0.1", start()).use { socket ->
            socket.soTimeout = 10_000
            socket.getOutputStream().write("CONNECT $host:$port HTTP/1.1\r\n\r\n".toByteArray())
            val response = readLine(socket.getInputStream())
            Log.i(TAG, "self-test response: $response")
            response?.contains(" 200 ") == true
        }
    }.getOrElse {
        Log.w(TAG, "self-test failed: $it")
        false
    }

    private fun handleClient(client: Socket) {
        try {
            client.use {
                val input = client.getInputStream()
                val requestLine = readLine(input) ?: return
                val parts = requestLine.split(" ")
                if (parts.size < 2 || !parts[0].equals("CONNECT", ignoreCase = true)) {
                    // Only https (CONNECT) is supported; all GOG endpoints use it.
                    client.getOutputStream().write(
                        "HTTP/1.1 405 Method Not Allowed\r\n\r\n".toByteArray(),
                    )
                    return
                }
                // Drain the remaining request headers.
                while (true) {
                    val line = readLine(input) ?: return
                    if (line.isEmpty()) break
                }
                val (host, port) = parts[1].split(":", limit = 2).let {
                    it[0] to (it.getOrNull(1)?.toIntOrNull() ?: 443)
                }
                Log.d(TAG, "CONNECT $host:$port")
                val upstream = try {
                    Socket(host, port)
                } catch (e: Exception) {
                    Log.w(TAG, "upstream connect to $host:$port failed: $e")
                    client.getOutputStream().write(
                        "HTTP/1.1 502 Bad Gateway\r\n\r\n".toByteArray(),
                    )
                    return
                }
                upstream.use {
                    client.getOutputStream()
                        .write("HTTP/1.1 200 Connection established\r\n\r\n".toByteArray())
                    val downstreamPump = executor.submit {
                        pump(upstream.getInputStream(), client.getOutputStream())
                        client.shutdownOutputSafely()
                    }
                    pump(input, upstream.getOutputStream())
                    upstream.shutdownOutputSafely()
                    downstreamPump.get()
                }
            }
        } catch (_: Exception) {
            // Connection-level failures just end the tunnel.
        }
    }

    private fun readLine(input: InputStream): String? {
        val buffer = StringBuilder()
        while (true) {
            val byte = input.read()
            if (byte == -1) return if (buffer.isEmpty()) null else buffer.toString()
            if (byte == '\n'.code) break
            if (byte != '\r'.code) buffer.append(byte.toChar())
        }
        return buffer.toString()
    }

    private fun pump(from: InputStream, to: OutputStream) {
        try {
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val read = from.read(buffer)
                if (read == -1) break
                to.write(buffer, 0, read)
                to.flush()
            }
        } catch (_: Exception) {
            // Peer closed; tunnel ends.
        }
    }

    private fun Socket.shutdownOutputSafely() {
        try {
            if (!isClosed) shutdownOutput()
        } catch (_: Exception) {
        }
    }
}
