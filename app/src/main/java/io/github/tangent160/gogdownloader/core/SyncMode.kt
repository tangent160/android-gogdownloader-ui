package io.github.tangent160.gogdownloader.core

/** What `update-database` should fetch. */
sealed interface SyncMode {
    /** Only new and updated games (`--updated-only`). Cheap; used for routine refreshes. */
    data object Incremental : SyncMode
    /** Every game in the library. Slow; only when the user explicitly asks. */
    data object Full : SyncMode
    /** Only games matching a search term (`--search`). */
    data class Search(val query: String) : SyncMode
}
