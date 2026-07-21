package eu.kanade.tachiyomi.animeextension.all.anoboye

import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.ParsedAnimeHttpSource
import eu.kanade.tachiyomi.lib.doodextractor.DoodExtractor
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class Anoboye : ParsedAnimeHttpSource() {
    override val name = "Anoboye"
    override val baseUrl = "https://anoboye.com"
    override val lang = "all"
    override val supportsLatest = true

    private val doodExtractor by lazy { DoodExtractor(client) }

    override val client: OkHttpClient = network.client.newBuilder()
        .addInterceptor { chain ->
            val response = chain.proceed(chain.request())
            response
        }
        .build()

    override fun popularAnimeRequest(page: Int): Request = GET("$baseUrl/anime/page/$page/", headers)
    override fun popularAnimeSelector(): String = "div.swiper-slide.item, div.postbody article, div.animposx"
    override fun popularAnimeFromElement(element: Element): SAnime = SAnime.create().apply {
        val a = element.selectFirst("a")
        setUrlWithoutDomain(a?.attr("href") ?: "")
        title = element.selectFirst("h2, h3, div.title")?.text() ?: a?.text() ?: "Unknown"
        thumbnail_url = element.selectFirst("img")?.let { it.attr("abs:src").ifBlank { it.attr("data-src") } }
    }
    override fun popularAnimeNextPageSelector(): String? = "a.next, a.pagination-next, div.hpage a.r"

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/page/$page/", headers)
    override fun latestUpdatesSelector(): String = popularAnimeSelector()
    override fun latestUpdatesFromElement(element: Element): SAnime = popularAnimeFromElement(element)
    override fun latestUpdatesNextPageSelector(): String? = popularAnimeNextPageSelector()

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        val url = if (query.isNotBlank()) {
            "$baseUrl/page/$page/?s=$query"
        } else {
            var filterUrl = "$baseUrl/anime/page/$page/?"
            filters.forEach { filter ->
                when (filter) {
                    is GenreFilter -> filterUrl += "&genre=${filter.toUriPart()}"
                    is StatusFilter -> filterUrl += "&status=${filter.toUriPart()}"
                    is OrderFilter -> filterUrl += "&order=${filter.toUriPart()}"
                    else -> {}
                }
            }
            filterUrl
        }
        return GET(url, headers)
    }

    override fun searchAnimeSelector(): String = popularAnimeSelector()
    override fun searchAnimeFromElement(element: Element): SAnime = popularAnimeFromElement(element)
    override fun searchAnimeNextPageSelector(): String? = popularAnimeNextPageSelector()

    override fun animeDetailsParse(document: Document): SAnime = SAnime.create().apply {
        title = document.selectFirst("h1.entry-title, h1")?.text() ?: "Unknown Title"
        description = document.selectFirst("div.entry-content, div.desc, div.synopsis")?.text()
        thumbnail_url = document.selectFirst("div.thumb img, div.poster img")?.attr("abs:src")
        genre = document.select("div.genxed a, div.mgen a").joinToString(", ") { it.text() }
        initialized = true
    }

    override fun episodeListSelector(): String = "div.eplister li a, ul.episodelist li a"
    override fun episodeFromElement(element: Element): SEpisode = SEpisode.create().apply {
        setUrlWithoutDomain(element.attr("href"))
        name = element.selectFirst("div.epl-num, span.ep")?.text() ?: element.text().ifBlank { "Episode 1" }
        episode_number = element.text().filter { it.isDigit() }.toFloatOrNull() ?: 1.0f
    }

    override fun videoListParse(response: Response): List<Video> {
        val doc = response.asJsoup()
        val videoList = mutableListOf<Video>()

        doc.select("div.player-embed iframe, div.embed-container iframe, iframe").toList().forEach { iframe ->
            val src = iframe.attr("abs:src")
            if (src.contains("dood") || src.contains("ds2play")) {
                videoList.addAll(doodExtractor.videosFromUrl(src, "DoodStream"))
            } else if (src.isNotBlank() && (src.contains(".m3u8") || src.contains(".mp4"))) {
                videoList.add(Video(src, "Direct Stream", src, headers))
            }
        }

        doc.select("video source").toList().forEach { source ->
            val src = source.attr("abs:src")
            if (src.isNotBlank()) {
                videoList.add(Video(src, "Direct Source", src, headers))
            }
        }

        return videoList
    }

    override fun videoListSelector(): String = "div.player-embed iframe, iframe"
    override fun videoFromElement(element: Element): Video = throw UnsupportedOperationException()
    override fun videoUrlParse(document: Document): String = ""

    fun relatedAnimeListRequest(anime: SAnime): Request = GET("$baseUrl/recommendations", headers)

    override fun getFilterList() = AnimeFilterList(
        GenreFilter(),
        StatusFilter(),
        OrderFilter()
    )

    private open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) :
        AnimeFilter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }

    private class GenreFilter : UriPartFilter("Genre", arrayOf(
        Pair("All", ""),
        Pair("Action", "action"),
        Pair("Adventure", "adventure"),
        Pair("Comedy", "comedy"),
        Pair("Drama", "drama"),
        Pair("Fantasy", "fantasy"),
        Pair("Romance", "romance"),
        Pair("Sci-Fi", "sci-fi"),
        Pair("Slice of Life", "slice-of-life")
    ))

    private class StatusFilter : UriPartFilter("Status", arrayOf(
        Pair("All", ""),
        Pair("Ongoing", "ongoing"),
        Pair("Completed", "completed")
    ))

    private class OrderFilter : UriPartFilter("Sort By", arrayOf(
        Pair("Latest", "latest"),
        Pair("Popular", "popular"),
        Pair("A-Z", "title")
    ))
}
