package eu.kanade.tachiyomi.animeextension.en.hentaigasm

import android.content.SharedPreferences
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.animeextension.en.hentaigasm.HentaigasmFilters.GENRES
import eu.kanade.tachiyomi.animeextension.en.hentaigasm.HentaigasmFilters.GenreFilter
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.ParsedAnimeHttpSource
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.getPreferencesLazy
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URLEncoder

class Hentaigasm :
    ParsedAnimeHttpSource(),
    ConfigurableAnimeSource {

    override val name = "Hentaigasm"

    override val baseUrl = "https://hentaigasm.com"

    override val lang = "en"

    override val supportsLatest = true

    private val preferences by getPreferencesLazy()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    override val client: OkHttpClient = network.client.newBuilder()
        .build()

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // ============================== Popular ===============================

    override fun popularAnimeRequest(page: Int): Request {
        val url = if (page == 1) "$baseUrl/" else "$baseUrl/page/$page/"
        return GET(url, headers)
    }

    override fun popularAnimeSelector(): String = "div.item-post, div.item"

    override fun popularAnimeFromElement(element: Element): SAnime {
        val anime = SAnime.create()
        val linkEl = element.selectFirst("a.clip-link, h2.title a, .thumb a")
        anime.setUrlWithoutDomain(linkEl?.attr("href") ?: "")
        anime.title = linkEl?.attr("title")?.takeIf { it.isNotBlank() }
            ?: element.selectFirst("h2.title a, .title a")?.text()
            ?: "Unknown"

        val img = element.selectFirst("div.thumb img, img")
        anime.thumbnail_url = img?.let {
            val src = it.attr("abs:src")
            if (src.isNotBlank()) src else it.attr("abs:data-src")
        }
        return anime
    }

    override fun popularAnimeNextPageSelector(): String = "div.wp-pagenavi a.nextpostslink, div.wp-pagenavi a.larger"

    // =============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request = popularAnimeRequest(page)

    override fun latestUpdatesSelector(): String = popularAnimeSelector()

    override fun latestUpdatesFromElement(element: Element): SAnime = popularAnimeFromElement(element)

    override fun latestUpdatesNextPageSelector(): String = popularAnimeNextPageSelector()

    // =============================== Search ===============================

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        if (query.isNotBlank()) {
            val url = "$baseUrl/wp-json/wp/v2/posts".toHttpUrl().newBuilder()
                .addQueryParameter("search", query)
                .addQueryParameter("page", page.toString())
                .addQueryParameter("per_page", "12")
                .build()
            return GET(url, headers)
        }

        val genre = filters.filterIsInstance<GenreFilter>().firstOrNull()?.selected
        return if (!genre.isNullOrBlank()) {
            val url = if (page == 1) "$baseUrl/genre/$genre/" else "$baseUrl/genre/$genre/page/$page/"
            GET(url, headers)
        } else {
            popularAnimeRequest(page)
        }
    }

    override fun searchAnimeSelector(): String = popularAnimeSelector()

    override fun searchAnimeFromElement(element: Element): SAnime = popularAnimeFromElement(element)

    override fun searchAnimeNextPageSelector(): String = popularAnimeNextPageSelector()

    override fun searchAnimeParse(response: Response): AnimesPage {
        if (response.request.url.encodedPath.contains("wp-json")) {
            val jsonString = response.body.string()
            val posts = json.decodeFromString<List<WpPost>>(jsonString)
            val totalPages = response.header("X-WP-TotalPages")?.toIntOrNull() ?: 1
            val page = response.request.url.queryParameter("page")?.toIntOrNull() ?: 1
            val hasNextPage = page < totalPages

            val animes = posts.map { post ->
                val title = Jsoup.parse(post.title.rendered).text()
                SAnime.create().apply {
                    setUrlWithoutDomain(post.link)
                    this.title = title
                    this.thumbnail_url = extractThumbnail(post.content.rendered)
                        ?: runCatching {
                            "https://hgasm1.com/preview/${URLEncoder.encode(title, "UTF-8")}.webp"
                        }.getOrNull()
                }
            }
            return AnimesPage(animes, hasNextPage)
        }
        return super.searchAnimeParse(response)
    }

    override fun getFilterList(): AnimeFilterList = AnimeFilterList(
        GenreFilter("Genre", GENRES),
    )

    // ============================== Details ===============================

    override fun animeDetailsParse(document: Document): SAnime {
        val anime = SAnime.create()
        anime.title = document.selectFirst("h1#title, h1.entry-title")?.text()
            ?: document.selectFirst("meta[property=og:title]")?.attr("content")
            ?: document.selectFirst("title")?.text().orEmpty()

        anime.thumbnail_url = document.selectFirst("meta[property=og:image]")?.attr("content")
            ?: document.selectFirst("div.thumb img")?.attr("abs:src")
            ?: JW_IMAGE_REGEX.find(document.html())?.groupValues?.get(1)

        val descEl = document.selectFirst("div.entry-content")
        val descText = descEl?.let { el ->
            val clone = el.clone()
            clone.select("script, style, a.btn").remove()
            clone.text()
        }
        anime.description = descText?.ifBlank { null }

        anime.genre = document.select("div#extras a[href*=/genre/], a[href*=/category/], a[href*=/hentai/]")
            .map { it.text().trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString()

        anime.status = SAnime.COMPLETED
        return anime
    }

    // ============================== Episodes ==============================

    override fun episodeListParse(response: Response): List<SEpisode> {
        val document = response.asJsoup()
        val title = document.selectFirst("h1#title, h1.entry-title")?.text()
            ?: document.selectFirst("meta[property=og:title]")?.attr("content")
            ?: "Episode 1"

        val episode = SEpisode.create().apply {
            setUrlWithoutDomain(response.request.url.encodedPath)
            name = title
            episode_number = runCatching {
                EP_REGEX.find(title)?.groupValues?.get(1)?.toFloat()
            }.getOrNull() ?: 1F
            date_upload = 0L
        }
        return listOf(episode)
    }

    override fun episodeListSelector(): String = throw UnsupportedOperationException("Not used")

    override fun episodeFromElement(element: Element): SEpisode = throw UnsupportedOperationException("Not used")

    // =============================== Videos ===============================

    override fun videoListParse(response: Response): List<Video> {
        val html = response.body.string()
        val videoHeaders = headers.newBuilder()
            .set("Referer", "$baseUrl/")
            .build()

        val videos = mutableListOf<Video>()

        // 1. JWPlayer setup file: "https://hgasm2.com/...mp4"
        JW_FILE_REGEX.findAll(html).forEach { match ->
            val fileUrl = match.groupValues[1]
            val host = fileUrl.toHttpUrlOrNull()?.host ?: "JWPlayer"
            videos.add(Video(fileUrl, "Stream ($host)", fileUrl, videoHeaders))
        }

        // 2. Download / direct mp4 links
        MP4_LINK_REGEX.findAll(html).forEach { match ->
            val fileUrl = match.groupValues[1]
            if (videos.none { it.videoUrl == fileUrl }) {
                val host = fileUrl.toHttpUrlOrNull()?.host ?: "Direct"
                videos.add(Video(fileUrl, "Download ($host)", fileUrl, videoHeaders))
            }
        }

        // 3. Fallback to any iframes
        val doc = Jsoup.parse(html, response.request.url.toString())
        doc.select("iframe[src]").forEach { iframe ->
            val src = iframe.attr("abs:src")
            if (src.isNotBlank() && !src.contains("ads", ignoreCase = true) && !src.contains("magsrv", ignoreCase = true)) {
                val host = src.toHttpUrlOrNull()?.host ?: "Embed"
                videos.add(Video(src, "Embed ($host)", src, videoHeaders))
            }
        }

        val prefServer = preferences.getString(PREF_SERVER_KEY, PREF_SERVER_DEFAULT)
        return videos.sortedWith(
            compareByDescending { it.quality.startsWith(prefServer.orEmpty()) },
        )
    }

    override fun videoListSelector(): String = throw UnsupportedOperationException("Not used")

    override fun videoFromElement(element: Element): Video = throw UnsupportedOperationException("Not used")

    override fun videoUrlParse(document: Document): String = throw UnsupportedOperationException("Not used")

    // ============================ Preferences =============================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val serverPref = ListPreference(screen.context).apply {
            key = PREF_SERVER_KEY
            title = "Preferred Server"
            entries = arrayOf("Stream", "Download")
            entryValues = arrayOf("Stream", "Download")
            setDefaultValue(PREF_SERVER_DEFAULT)
            summary = "%s"
            setOnPreferenceChangeListener { _, newValue ->
                preferences.edit().putString(key, newValue.toString()).commit()
            }
        }
        screen.addPreference(serverPref)
    }

    // ============================== Helpers ===============================

    private fun extractThumbnail(renderedContent: String): String? {
        return JW_IMAGE_REGEX.find(renderedContent)?.groupValues?.get(1)
            ?: IMG_SRC_REGEX.find(renderedContent)?.groupValues?.get(1)
    }

    @Serializable
    private data class WpPost(
        val id: Long = 0L,
        val link: String = "",
        val title: WpRendered = WpRendered(),
        val content: WpRendered = WpRendered(),
    )

    @Serializable
    private data class WpRendered(
        val rendered: String = "",
    )

    companion object {
        private val JW_FILE_REGEX = Regex("""(?i)file\s*:\s*["'](https?://[^"']+\.mp4[^"']*)["']""")
        private val JW_IMAGE_REGEX = Regex("""(?i)image\s*:\s*["'](https?://[^"']+\.(?:jpg|jpeg|png|webp)[^"']*)["']""")
        private val MP4_LINK_REGEX = Regex("""(?i)href\s*=\s*["'](https?://[^"']+\.mp4[^"']*)["']""")
        private val IMG_SRC_REGEX = Regex("""(?i)src\s*=\s*["'](https?://[^"']+\.(?:jpg|jpeg|png|webp)[^"']*)["']""")
        private val EP_REGEX = Regex("""(?i)(?:episode|\bep\b|\bpart\b)?\s*(\d+(?:\.\d+)?)\s*(?:subbed|raw|dubbed|$)""")

        private const val PREF_SERVER_KEY = "pref_server"
        private const val PREF_SERVER_DEFAULT = "Stream"
    }
}
