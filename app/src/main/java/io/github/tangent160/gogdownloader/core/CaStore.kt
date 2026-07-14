package io.github.tangent160.gogdownloader.core

import android.util.Base64
import android.util.Log
import java.io.File
import java.security.KeyStore
import java.security.cert.X509Certificate

private const val TAG = "GogCaStore"

/**
 * Exports the device's trusted CA certificates as a PEM bundle.
 *
 * The static PHP binary verifies TLS with OpenSSL, which cannot read
 * Android's native trust store — but it honors the SSL_CERT_FILE environment
 * variable, so we hand it the same CAs the device itself trusts.
 */
object CaStore {

    @Volatile
    private var generatedFor: File? = null

    /** Writes (once per process) and returns the PEM bundle file. */
    @Synchronized
    fun pemFile(directory: File): File {
        val file = File(directory, "cacert.pem")
        if (generatedFor == file && file.length() > 0) return file
        runCatching {
            val keyStore = KeyStore.getInstance("AndroidCAStore").apply { load(null) }
            file.bufferedWriter().use { writer ->
                for (alias in keyStore.aliases()) {
                    val certificate = keyStore.getCertificate(alias) as? X509Certificate ?: continue
                    writer.write("-----BEGIN CERTIFICATE-----\n")
                    writer.write(
                        Base64.encodeToString(certificate.encoded, Base64.NO_WRAP)
                            .chunked(64)
                            .joinToString("\n"),
                    )
                    writer.write("\n-----END CERTIFICATE-----\n")
                }
            }
            generatedFor = file
            Log.i(TAG, "wrote CA bundle: ${file.length()} bytes")
        }.onFailure {
            Log.w(TAG, "failed to export CA bundle: $it")
        }
        return file
    }
}
