package io.github.tangent160.gogdownloader.core

import android.database.sqlite.SQLiteDatabase
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class Game(
    val rowId: Long,
    val gogId: Long,
    val title: String,
    val slug: String?,
)

data class DownloadFile(
    val language: String?,
    val platform: String?,
    val name: String,
    val sizeBytes: Long,
    val md5: String?,
)

data class ExtraFile(
    val name: String,
    val sizeBytes: Long,
)

/**
 * Read-only view of gog-downloader's own SQLite database
 * (populated by the `update-database` command).
 */
class GameDatabase(private val databaseFile: File) {

    private fun <T> query(block: (SQLiteDatabase) -> T): T? {
        if (!databaseFile.exists()) return null
        return SQLiteDatabase.openDatabase(
            databaseFile.absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY,
        ).use(block)
    }

    suspend fun isLoggedIn(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            query { db ->
                db.rawQuery(
                    "select count(*) from auth where token is not null and refreshToken is not null",
                    null,
                ).use { it.moveToFirst() && it.getLong(0) > 0 }
            } == true
        }.getOrDefault(false)
    }

    suspend fun games(): List<Game> = withContext(Dispatchers.IO) {
        query { db ->
            db.rawQuery(
                "select id, game_id, title, slug from games order by title collate nocase",
                null,
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(
                            Game(
                                rowId = cursor.getLong(0),
                                gogId = cursor.getLong(1),
                                title = cursor.getString(2),
                                slug = if (cursor.isNull(3)) null else cursor.getString(3),
                            )
                        )
                    }
                }
            }
        } ?: emptyList()
    }

    suspend fun game(rowId: Long): Game? = withContext(Dispatchers.IO) {
        query { db ->
            db.rawQuery(
                "select id, game_id, title, slug from games where id = ?",
                arrayOf(rowId.toString()),
            ).use { cursor ->
                if (cursor.moveToFirst()) {
                    Game(
                        rowId = cursor.getLong(0),
                        gogId = cursor.getLong(1),
                        title = cursor.getString(2),
                        slug = if (cursor.isNull(3)) null else cursor.getString(3),
                    )
                } else {
                    null
                }
            }
        }
    }

    suspend fun downloads(gameRowId: Long): List<DownloadFile> = withContext(Dispatchers.IO) {
        query { db ->
            db.rawQuery(
                "select language, platform, name, size, md5 from downloads where game_id = ? order by platform, language, name",
                arrayOf(gameRowId.toString()),
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(
                            DownloadFile(
                                language = cursor.getString(0),
                                platform = cursor.getString(1),
                                name = cursor.getString(2),
                                sizeBytes = cursor.getDouble(3).toLong(),
                                md5 = cursor.getString(4),
                            )
                        )
                    }
                }
            }
        } ?: emptyList()
    }

    suspend fun extras(gameRowId: Long): List<ExtraFile> = withContext(Dispatchers.IO) {
        runCatching {
            query { db ->
                db.rawQuery(
                    "select name, size from game_extras where game_id = ? order by name",
                    arrayOf(gameRowId.toString()),
                ).use { cursor ->
                    buildList {
                        while (cursor.moveToNext()) {
                            add(ExtraFile(name = cursor.getString(0), sizeBytes = cursor.getLong(1)))
                        }
                    }
                }
            }
        }.getOrNull() ?: emptyList()
    }
}
