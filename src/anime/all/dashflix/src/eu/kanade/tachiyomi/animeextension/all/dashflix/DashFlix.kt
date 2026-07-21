package eu.kanade.tachiyomi.animeextension.all.dashflix

import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.ParsedAnimeHttpSource
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class DashFlix : ParsedAnimeHttpSource() {
    override val name = "DASHFLIX"
    override val baseUrl = "https://dashflix.top"
    override val lang = "all"
    override val supportsLatest = true

    override val client: OkHttpClient = network.client.newBuilder()
        .addInterceptor { chain ->
            val response = chain.proceed(chain.request())
            response
        }
        .build()

    override fun popularAnimeRequest(page: Int): Request = GET("$baseUrl/movies.html", headers)
    override fun popularAnimeSelector(): String = "div.hero-content, div.movie-card, div.card, article"
    override fun popularAnimeFromElement(element: Element): SAnime = SAnime.create().apply {
        val a = element.selectFirst("a")
        setUrlWithoutDomain(a?.attr("href") ?: "")
        title = element.selectFirst("h2, h3, div.title")?.text() ?: a?.text() ?: "Featured Movie"
        thumbnail_url = element.selectFirst("img")?.attr("abs:src")
    }
    override fun popularAnimeNextPageSelector(): String? = null

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/", headers)
    override fun latestUpdatesSelector(): String = popularAnimeSelector()
    override fun latestUpdatesFromElement(element: Element): SAnime = popularAnimeFromElement(element)
    override fun latestUpdatesNextPageSelector(): String? = null

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request =
        GET("$baseUrl/", headers)
    override fun searchAnimeSelector(): String = popularAnimeSelector()
    override fun searchAnimeFromElement(element: Element): SAnime = popularAnimeFromElement(element)
    override fun searchAnimeNextPageSelector(): String? = null

    override fun animeDetailsParse(document: Document): SAnime = SAnime.create().apply {
        title = document.selectFirst("h1.title, h1")?.text() ?: "DASHFLIX Movie"
        description = document.selectFirst("div.description, p.synopsis")?.text() ?: "DASHFLIX Streaming Source"
        thumbnail_url = document.selectFirst("img.poster, div.hero-logo img")?.attr("abs:src")
        initialized = true
    }

    override fun episodeListSelector(): String = "a.episode-link, div.ep-item a"
    override fun episodeFromElement(element: Element): SEpisode = SEpisode.create().apply {
        setUrlWithoutDomain(element.attr("href"))
        name = "Full Movie / Episode 1"
        episode_number = 1.0f
    }

    override fun videoListParse(response: Response): List<Video> {
        val doc = response.asJsoup()
        val videoList = mutableListOf<Video>()
        doc.select("iframe, video source").toList().forEach { el ->
            val src = el.attr("abs:src")
            if (src.isNotBlank()) {
                videoList.add(Video(src, "DASHFLIX Stream", src, headers))
            }
        }
        return videoList
    }

    override fun videoListSelector(): String = "iframe, video source"
    override fun videoFromElement(element: Element): Video = throw UnsupportedOperationException()
    override fun videoUrlParse(document: Document): String = ""

    fun relatedAnimeListRequest(anime: SAnime): Request = GET("$baseUrl/", headers)

    override fun getFilterList() = AnimeFilterList(
        CategoryFilter(),
        SortFilter()
    )

    private open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) :
        AnimeFilter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }

    private class CategoryFilter : UriPartFilter("Category", arrayOf(
        Pair("All", "all"),
        Pair("Movies", "movies"),
        Pair("TV Shows", "tv")
    ))

    private class SortFilter : UriPartFilter("Sort By", arrayOf(
        Pair("Trending", "trending"),
        Pair("Top Rated", "top_rated")
    ))
}
