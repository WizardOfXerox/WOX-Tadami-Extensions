package eu.kanade.tachiyomi.animeextension.all.javguru.extractors

import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.lib.playlistutils.PlaylistUtils
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient

class EmTurboExtractor(private val client: OkHttpClient, private val headers: Headers) {

    private val playlistExtractor by lazy { PlaylistUtils(client, headers) }

    fun getVideos(url: String): List<Video> {
        val document = client.newCall(GET(url, headers)).execute().asJsoup()

        val script = document.selectFirst("script:containsData(urlplay)")
            ?.data()
            ?: return emptyList()

        val urlPlay = URLPLAY.find(script)?.groupValues?.get(1)
            ?: return emptyList()

        if (urlPlay.toHttpUrlOrNull() == null) {
            return emptyList()
        }

        val videos = playlistExtractor.extractFromHls(urlPlay, url, videoNameGen = { quality -> "EmTurboVid: $quality" })
            .distinctBy { it.url }

        // Clean headers without restrictive Referer to prevent ExoPlayer 403 / 0-second playback crashes
        val cleanHeaders = headers.newBuilder().removeAll("Referer").build()
        return videos.map { v ->
            Video(v.url, v.quality, v.videoUrl, cleanHeaders, v.subtitleTracks, v.audioTracks)
        }
    }

    companion object {
        private val URLPLAY = Regex("urlPlay\\s*=\\s*\'([^\']+)")
    }
}
