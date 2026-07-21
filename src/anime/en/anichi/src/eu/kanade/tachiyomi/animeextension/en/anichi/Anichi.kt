package eu.kanade.tachiyomi.animeextension.en.anichi

import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.ParsedAnimeHttpSource
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class Anichi : ParsedAnimeHttpSource() {
    override val name = "Anichi"
    override val baseUrl = "https://anichi.to"
    override val lang = "en"
    override val supportsLatest = true

    override fun popularAnimeRequest(page: Int): Request = GET("$baseUrl/anime?page=$page", headers)
    override fun popularAnimeSelector(): String = "div.landing-visual img, div.series-card, div.flw-item, article"
    override fun popularAnimeFromElement(element: Element): SAnime = SAnime.create().apply {
        val a = element.selectFirst("a")
        setUrlWithoutDomain(a?.attr("href") ?: "")
        title = element.selectFirst("img")?.attr("alt") ?: a?.text() ?: "Unknown"
        thumbnail_url = element.selectFirst("img")?.attr("abs:src")
    }
    override fun popularAnimeNextPageSelector(): String? = "a.next, a.pagination-next, ul.pagination li.active + li a"

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/latest?page=$page", headers)
    override fun latestUpdatesSelector(): String = popularAnimeSelector()
    override fun latestUpdatesFromElement(element: Element): SAnime = popularAnimeFromElement(element)
    override fun latestUpdatesNextPageSelector(): String? = popularAnimeNextPageSelector()

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request =
        GET("$baseUrl/search?q=$query&page=$page", headers)
    override fun searchAnimeSelector(): String = popularAnimeSelector()
    override fun searchAnimeFromElement(element: Element): SAnime = popularAnimeFromElement(element)
    override fun searchAnimeNextPageSelector(): String? = popularAnimeNextPageSelector()

    override fun animeDetailsParse(document: Document): SAnime = SAnime.create().apply {
        title = document.selectFirst("h1, div.series-intro__title")?.text() ?: "Unknown Title"
        description = document.selectFirst("div.series-intro__description, div.description, div.summary")?.text()
        thumbnail_url = document.selectFirst("div.series-intro__poster img, div.poster img")?.attr("abs:src")
        genre = document.select("div.genres a, div.tags a").joinToString(", ") { it.text() }
        initialized = true
    }

    override fun episodeListSelector(): String = "ul.episodes li, div.episode-item, div.ep-list a"
    override fun episodeFromElement(element: Element): SEpisode = SEpisode.create().apply {
        val a = if (element.tagName() == "a") element else element.selectFirst("a")
        setUrlWithoutDomain(a?.attr("href") ?: "")
        name = element.text().ifBlank { "Episode 1" }
        episode_number = element.text().filter { it.isDigit() }.toFloatOrNull() ?: 1.0f
    }

    override fun videoListSelector(): String = "iframe, video source"
    override fun videoFromElement(element: Element): Video {
        val src = element.attr("abs:src")
        return Video(src, "Direct Stream", src, headers)
    }
    override fun videoUrlParse(document: Document): String = ""

    fun relatedAnimeListRequest(anime: SAnime): Request = GET("$baseUrl/recommendations", headers)
}
