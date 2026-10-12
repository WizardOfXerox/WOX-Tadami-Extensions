package eu.kanade.tachiyomi.animeextension.en.hentaicity

import eu.kanade.tachiyomi.animeextension.en.hentaicity.HentaiCityFilters.CATEGORIES
import eu.kanade.tachiyomi.animeextension.en.hentaicity.HentaiCityFilters.CategoryFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.ParsedAnimeHttpSource
import eu.kanade.tachiyomi.lib.playlistutils.PlaylistUtils
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URLEncoder

class HentaiCity : ParsedAnimeHttpSource() {

    override val name = "HentaiCity"

    override val baseUrl = "https://www.hentaicity.com"

    override val lang = "en"

    override val supportsLatest = true

    override val client: OkHttpClient = network.client.newBuilder()
        .build()

    private val playlistUtils by lazy { PlaylistUtils(client, headers) }

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        .add("User-Agent", USER_AGENT)

    // ======================= Popular Anime =======================

    override fun popularAnimeRequest(page: Int): Request {
        val pageStr = if (page == 1) "" else "-$page"
        return GET("$baseUrl/videos/straight/all-popular$pageStr.html", headers)
    }

    override fun popularAnimeSelector(): String =
        "a[href*=\"/click/\"][href*=\"/video/\"]:has(img)"

    override fun popularAnimeFromElement(element: Element): SAnime = SAnime.create().apply {
        setUrlWithoutDomain(element.attr("abs:href"))
        val img = element.selectFirst("img")
        title = img?.attr("alt")?.ifBlank { null }
            ?: element.attr("title").ifBlank { null }
            ?: element.text()
        thumbnail_url = img?.attr("abs:src")
    }

    override fun popularAnimeNextPageSelector(): String =
        "a:contains(Next), a.next"

    // ======================= Latest Anime =======================

    override fun latestUpdatesRequest(page: Int): Request {
        val pageStr = if (page == 1) "" else "-$page"
        return GET("$baseUrl/videos/straight/all-recent$pageStr.html", headers)
    }

    override fun latestUpdatesSelector(): String =
        popularAnimeSelector()

    override fun latestUpdatesFromElement(element: Element): SAnime =
        popularAnimeFromElement(element)

    override fun latestUpdatesNextPageSelector(): String =
        popularAnimeNextPageSelector()

    // ======================= Search Anime =======================

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        if (query.isNotBlank()) {
            val encodedQuery = URLEncoder.encode(query.trim(), "UTF-8")
            return GET("$baseUrl/search/videos/$encodedQuery/$page.html", headers)
        }

        var category = ""
        for (filter in filters) {
            if (filter is CategoryFilter) {
                if (filter.selected.isNotBlank()) {
                    category = filter.selected
                }
            }
        }

        return if (category.isNotBlank()) {
            val pageStr = if (page == 1) "" else "-$page"
            GET("$baseUrl/videos/straight/$category-popular$pageStr.html", headers)
        } else {
            popularAnimeRequest(page)
        }
    }

    override fun searchAnimeSelector(): String =
        popularAnimeSelector()

    override fun searchAnimeFromElement(element: Element): SAnime =
        popularAnimeFromElement(element)

    override fun searchAnimeNextPageSelector(): String =
        popularAnimeNextPageSelector()

    // ======================= Anime Details =======================

    override fun animeDetailsParse(document: Document): SAnime = SAnime.create().apply {
        title = document.selectFirst("h1, .video-title, .title")?.text() ?: ""
        val img = document.selectFirst("meta[property=\"og:image\"], video[poster]")
        thumbnail_url = if (img?.tagName() == "meta") img.attr("content") else img?.attr("abs:poster")
        val desc = document.selectFirst("meta[property=\"og:description\"], .video-description, .description")
        description = if (desc?.tagName() == "meta") desc.attr("content") else desc?.text()
        genre = document.select("a[href*=\"/videos/straight/\"]")
            .map { it.text() }
            .distinct()
            .joinToString(", ")
        status = SAnime.COMPLETED
        initialized = true
    }

    // ======================= Episode List =======================

    override fun episodeListParse(response: Response): List<SEpisode> {
        val seriesUrl = response.request.url.toString()
        return listOf(
            SEpisode.create().apply {
                name = "Episode 1"
                episode_number = 1f
                setUrlWithoutDomain(seriesUrl)
            }
        )
    }

    override fun episodeListSelector(): String =
        throw UnsupportedOperationException("Not used")

    override fun episodeFromElement(element: Element): SEpisode =
        throw UnsupportedOperationException("Not used")

    // ======================= Video List =======================

    override fun videoListParse(response: Response): List<Video> {
        val document = response.asJsoup()
        val videoHeaders = headersBuilder()
            .set("Referer", "$baseUrl/")
            .build()

        val videoList = mutableListOf<Video>()

        // Check HLS source
        val hlsSource = document.selectFirst("source[src*=\".m3u8\"], video source[src*=\".m3u8\"]")
        if (hlsSource != null) {
            val m3u8Url = hlsSource.attr("abs:src")
            try {
                val hlsVideos = playlistUtils.extractFromHls(
                    m3u8Url,
                    videoNameGen = { q -> "HentaiCity - $q" }
                )
                videoList.addAll(hlsVideos)
            } catch (_: Exception) {}
        }

        // Check direct MP4 source
        val mp4Source = document.selectFirst("video[src*=\".mp4\"], source[src*=\".mp4\"]")
        if (mp4Source != null) {
            val mp4Url = if (mp4Source.tagName() == "video") mp4Source.attr("abs:src") else mp4Source.attr("abs:src")
            videoList.add(Video(mp4Url, "HentaiCity - MP4", mp4Url, headers = videoHeaders))
        }

        return videoList
    }

    override fun videoListSelector(): String =
        throw UnsupportedOperationException("Not used")

    override fun videoFromElement(element: Element): Video =
        throw UnsupportedOperationException("Not used")

    override fun videoUrlParse(document: Document): String =
        throw UnsupportedOperationException("Not used")

    // ======================= Filters =======================

    override fun getFilterList(): AnimeFilterList = AnimeFilterList(
        CategoryFilter("Category", CATEGORIES)
    )

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }
}
