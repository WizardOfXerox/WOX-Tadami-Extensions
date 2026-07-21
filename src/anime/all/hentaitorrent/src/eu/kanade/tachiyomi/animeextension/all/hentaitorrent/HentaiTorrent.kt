package eu.kanade.tachiyomi.animeextension.all.hentaitorrent

import android.annotation.SuppressLint
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.ParsedAnimeHttpSource
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.torrentutils.TorrentUtils
import eu.kanade.tachiyomi.util.asJsoup
import android.app.Application
import android.content.SharedPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.SocketTimeoutException
import java.net.URLEncoder
import java.util.Locale

class HentaiTorrent :
    ParsedAnimeHttpSource(),
    ConfigurableAnimeSource {

    override val name = "Hentai Torrent (Torrent)"

    override val baseUrl = "https://www.hentaitorrents.com"

    override val lang = "all"

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_\$id", 0)
    }

    override val supportsLatest = true

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
        .add("Referer", "$baseUrl/")

    // ============================== Popular ===============================
    override fun popularAnimeRequest(page: Int): Request = GET("$baseUrl/catalog/Main%20Subsection%20Hentai/page/$page", headers)

    override fun popularAnimeSelector(): String = "div.image-container div.image-wrapper"

    override fun popularAnimeFromElement(element: Element): SAnime {
        val anime = SAnime.create()
        anime.setUrlWithoutDomain(element.select("a.overlay").attr("href"))
        anime.title = element.select("a.overlay").text().trim()
        anime.thumbnail_url = element.select("img").attr("src")
        return anime
    }

    override fun popularAnimeNextPageSelector(): String = "div.pagination a:contains(Next)"

    // ============================== Latest ===============================
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/page/$page", headers)

    override fun latestUpdatesSelector(): String = popularAnimeSelector()

    override fun latestUpdatesFromElement(element: Element): SAnime = popularAnimeFromElement(element)

    override fun latestUpdatesNextPageSelector(): String = popularAnimeNextPageSelector()

    // ============================== Search ===============================
    override suspend fun getSearchAnime(page: Int, query: String, filters: AnimeFilterList): AnimesPage {
        if (query.startsWith("https://")) {
            val url = query.toHttpUrl()
            if (url.host != baseUrl.toHttpUrl().host) {
                throw Exception("Unsupported url")
            }
            val id = url.pathSegments.getOrNull(1)
                ?: throw Exception("Unsupported url")
            return getSearchAnime(page, "${PREFIX_SEARCH}$id", filters)
        }
        if (query.startsWith(PREFIX_SEARCH)) {
            val id = query.removePrefix(PREFIX_SEARCH)
            return client.newCall(GET("$baseUrl/anime/$id", headers))
                .awaitSuccess()
                .use(::searchAnimeByIdParse)
        }
        return super.getSearchAnime(page, query, filters)
    }

    private fun searchAnimeByIdParse(response: Response): AnimesPage {
        val details = animeDetailsParse(response.use { it.asJsoup() })
        return AnimesPage(listOf(details), false)
    }

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        if (query.isNotEmpty()) {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            return GET("$baseUrl/s.php?search=$encodedQuery&page=$page", headers)
        }

        var cat = ""
        filters.forEach { filter ->
            if (filter is CategoryFilter) {
                cat = filter.toUriPart()
            }
        }

        return if (cat.isNotEmpty()) {
            GET("$baseUrl/catalog/$cat/page/$page", headers)
        } else {
            GET("$baseUrl/page/$page", headers)
        }
    }

    override fun searchAnimeSelector() = popularAnimeSelector()

    override fun searchAnimeFromElement(element: Element) = popularAnimeFromElement(element)

    override fun searchAnimeNextPageSelector() = popularAnimeNextPageSelector()

    // =========================== Anime Details ============================
    override fun animeDetailsParse(document: Document): SAnime {
        val anime = SAnime.create()
        anime.description = document.select("div.article-content").html()
            .replace(Regex("<(?!br\\s*/?)[^>]+>"), "")
            .replace("<br>", "\n")
            .replace("<br/>", "\n")
        return anime
    }

    // ============================== Episodes ==============================
    override fun episodeListSelector() = "a[href*=/dl.php], a:contains(Download Torrent)"

    override fun episodeListParse(response: Response): List<SEpisode> {
        val document = response.asJsoup()
        var downloadButtonUrl = document.select("a[href*=/dl.php]").attr("href")
        if (downloadButtonUrl.isBlank()) {
            downloadButtonUrl = document.select("a:contains(Download Torrent)").attr("href")
        }

        if (downloadButtonUrl.isBlank()) throw Exception("No Download Link Found on Page!")

        val dlPageUrl = if (downloadButtonUrl.startsWith("http")) downloadButtonUrl else "$baseUrl$downloadButtonUrl"
        val downloadPage = client.newCall(GET(dlPageUrl, headers)).execute().asJsoup()
        var torrentFileUrl = downloadPage.select("a[href*=.torrent], a.download-button").attr("abs:href")

        if (torrentFileUrl.isBlank()) {
            torrentFileUrl = downloadPage.select("a.download-button").attr("href")
            if (torrentFileUrl.isNotBlank() && !torrentFileUrl.startsWith("http")) {
                torrentFileUrl = "$baseUrl$torrentFileUrl"
            }
        }

        if (torrentFileUrl.isBlank()) throw Exception("No Torrent File Found on Download Page!")

        return try {
            val torrent = TorrentUtils.getTorrentInfo(torrentFileUrl, "torrent")
            val torrentMagnetLink = "magnet:?xt=urn:btih:${torrent.hash}&dn=${torrent.hash}"
            var torrentTrackers = fetchTrackers().split("\n").filter { it.isNotBlank() }.joinToString("&tr=", "&tr=")
            torrentTrackers += torrent.trackers.filter { it.isNotBlank() }.joinToString("&tr=", "&tr=")
            var episodeNumber = 1F
            torrent.files
                .filter { it.path.substringAfterLast('.').lowercase(Locale.ROOT) in validVideoExtensions }
                .map { file ->
                    SEpisode.create().apply {
                        name = if (preferences.getBoolean(IS_FILENAME_KEY, IS_FILENAME_DEFAULT)) {
                            file.path.split('/').last().trim()
                        } else {
                            file.path.trim().replace("[", "(").replace("]", ")").replace("/", "\uD83D\uDCC2 ")
                        }
                        url = "$torrentMagnetLink$torrentTrackers&index=${file.indexFile}"
                        episode_number = episodeNumber++
                        scanlator = convertBytesToReadable(file.size)
                    }
                }.reversed()
        } catch (_: SocketTimeoutException) {
            throw Exception("Dead Torrent \uD83D\uDE35")
        }
    }

    private val validVideoExtensions = setOf("mp4", "mov", "avi", "wmv", "mkv", "flv", "webm", "mpeg", "mpg", "mts", "vob", "ts")

    @SuppressLint("DefaultLocale")
    private fun convertBytesToReadable(bytes: Long): String {
        val kilobytes = bytes / 1024.0
        val megabytes = kilobytes / 1024.0
        val gigabytes = megabytes / 1024.0

        return when {
            gigabytes >= 1 -> String.format("%.2f GB", gigabytes)
            megabytes >= 1 -> String.format("%.2f MB", megabytes)
            else -> String.format("%.2f KB", kilobytes)
        }
    }

    private fun fetchTrackers(): String {
        val request = Request.Builder()
            .url("https://raw.githubusercontent.com/ngosang/trackerslist/refs/heads/master/trackers_all_http.txt")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Unexpected code $response")
            return response.body.string().trim()
        }
    }

    override fun episodeFromElement(element: Element) = throw Exception("Not used")

    // ============================ Video Links =============================

    override suspend fun getVideoList(episode: SEpisode): List<Video> = listOf(Video(episode.url, episode.name, episode.url))

    override fun videoListSelector() = throw Exception("Not used")

    override fun videoFromElement(element: Element) = throw Exception("Not used")

    override fun videoUrlParse(document: Document) = throw Exception("Not used")

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = IS_FILENAME_KEY
            title = "Only display filename"
            setDefaultValue(IS_FILENAME_DEFAULT)
            setOnPreferenceChangeListener { _, newValue ->
                preferences.edit().putBoolean(key, newValue as Boolean).commit()
            }
            summary = "Will note display full path of episode."
        }.also(screen::addPreference)

        SwitchPreferenceCompat(screen.context).apply {
            key = IS_IMG_KEY
            title = "Display Images in episode list."
            setDefaultValue(IS_IMG_DEFAULT)
            setOnPreferenceChangeListener { _, newValue ->
                preferences.edit().putBoolean(key, newValue as Boolean).commit()
            }
            summary = "Its an experimental option."
        }.also(screen::addPreference)

        SwitchPreferenceCompat(screen.context).apply {
            key = IS_AUDIO_KEY
            title = "Display Audio in episode list."
            setDefaultValue(IS_AUDIO_DEFAULT)
            setOnPreferenceChangeListener { _, newValue ->
                preferences.edit().putBoolean(key, newValue as Boolean).commit()
            }
            summary = "Its an experimental option."
        }.also(screen::addPreference)
    }

    // ============================ ROBUST FILTERS =============================

    override fun getFilterList(): AnimeFilterList = AnimeFilterList(
        CategoryFilter()
    )

    private open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) :
        AnimeFilter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }

    private class CategoryFilter : UriPartFilter("Category", arrayOf(
        Pair("All / Latest", ""),
        Pair("Main Subsection Hentai", "Main%20Subsection%20Hentai"),
        Pair("2D Video Hentai", "2D%20video%20Hentai"),
        Pair("3D Video Hentai", "3D%20video%20Hentai"),
        Pair("Hentai DVD HD", "Hentai%20DVD%20HD"),
        Pair("Cartoons", "Cartoons"),
        Pair("Manga Hentai", "Manga%20Hentai"),
        Pair("Artwork HCG Hentai", "Artwork%20HCG%20Hentai"),
        Pair("Comics Artwork", "Comics%20Artwork"),
        Pair("Games Main Subsection", "Games%20main%20subsection"),
        Pair("Visual Novels Games", "Visual%20Novels%20Games"),
        Pair("Games Role-playing", "Games%20Role-playing"),
        Pair("In Progress and Demo Games", "In%20Progress%20and%20Demo%20Games")
    ))

    companion object {
        const val PREFIX_SEARCH = "id:"

        private const val IS_FILENAME_KEY = "filename"
        private const val IS_FILENAME_DEFAULT = false

        private const val IS_IMG_KEY = "img"
        private const val IS_IMG_DEFAULT = false

        private const val IS_AUDIO_KEY = "audio"
        private const val IS_AUDIO_DEFAULT = false
    }
}
