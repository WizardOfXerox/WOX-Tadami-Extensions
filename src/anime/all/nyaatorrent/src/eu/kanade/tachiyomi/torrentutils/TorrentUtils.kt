package eu.kanade.tachiyomi.torrentutils

class TorrentInfo(
    val hash: String,
    val trackers: List<String>,
    val files: List<TorrentFile>
)

class TorrentFile(
    val path: String,
    val indexFile: Int,
    val size: Long
)

object TorrentUtils {
    fun getTorrentInfo(url: String, type: String): TorrentInfo {
        throw UnsupportedOperationException("Runtime stub")
    }
}
