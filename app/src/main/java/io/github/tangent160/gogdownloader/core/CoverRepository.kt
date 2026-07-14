package io.github.tangent160.gogdownloader.core

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Resolves game cover URLs from the public GOG products API
 * (the local database doesn't store artwork). Resolved URLs are cached on
 * disk; the images themselves are cached by Coil.
 */
class CoverRepository(context: Context) {

    private val cacheFile = File(context.cacheDir, "covers.json")
    private val cache = ConcurrentHashMap<Long, String>()
    private val diskLock = Mutex()

    init {
        runCatching {
            if (cacheFile.exists()) {
                val json = JSONObject(cacheFile.readText())
                json.keys().forEach { key -> cache[key.toLong()] = json.getString(key) }
            }
        }
    }

    suspend fun coverUrl(gogId: Long): String? {
        cache[gogId]?.let { return it.ifEmpty { null } }
        return withContext(Dispatchers.IO) {
            val resolved = fetchCoverUrl(gogId) ?: ""
            cache[gogId] = resolved
            persist()
            resolved.ifEmpty { null }
        }
    }

    private fun fetchCoverUrl(gogId: Long): String? = runCatching {
        val connection = URL("https://api.gog.com/products/$gogId")
            .openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        try {
            if (connection.responseCode != 200) return@runCatching null
            val body = connection.inputStream.bufferedReader().readText()
            val images = JSONObject(body).optJSONObject("images") ?: return@runCatching null
            val logo = images.optString("logo2x").ifEmpty { images.optString("logo") }
            if (logo.isEmpty()) null else if (logo.startsWith("//")) "https:$logo" else logo
        } finally {
            connection.disconnect()
        }
    }.getOrNull()

    private suspend fun persist() {
        diskLock.withLock {
            runCatching {
                val json = JSONObject()
                cache.forEach { (id, url) -> json.put(id.toString(), url) }
                cacheFile.writeText(json.toString())
            }
        }
    }
}
