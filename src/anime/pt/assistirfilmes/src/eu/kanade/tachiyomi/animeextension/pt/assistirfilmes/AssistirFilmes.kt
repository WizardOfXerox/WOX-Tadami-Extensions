package eu.kanade.tachiyomi.animeextension.pt.assistirfilmes

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

class AssistirFilmes : ParsedAnimeHttpSource() {
    override val name = "AssistirFilmes"
    override val baseUrl = "https://assistirfilmes.co"
    override val lang = "pt"
    override val supportsLatest = true

    override fun popularAnimeRequest(page: Int): Request = GET("$baseUrl/page/$page/", headers)
    override fun popularAnimeSelector(): String = "div.film-poster, article.item, div.poster"
    override fun popularAnimeFromElement(element: Element): SAnime = SAnime.create().apply {
        val a = element.selectFirst("a")
        setUrlWithoutDomain(a?.attr("href") ?: "")
        title = element.selectFirst("img")?.attr("alt") ?: a?.text() ?: "Unknown"
        thumbnail_url = element.selectFirst("img")?.attr("abs:src")
    }
    override fun popularAnimeNextPageSelector(): String? = "a.next, a.pagination-next"

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/latest/page/$page/", headers)
    override fun latestUpdatesSelector(): String = popularAnimeSelector()
    override fun latestUpdatesFromElement(element: Element): SAnime = popularAnimeFromElement(element)
    override fun latestUpdatesNextPageSelector(): String? = popularAnimeNextPageSelector()

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request =
        GET("$baseUrl/?s=$query&page=$page", headers)
    override fun searchAnimeSelector(): String = popularAnimeSelector()
    override fun searchAnimeFromElement(element: Element): SAnime = popularAnimeFromElement(element)
    override fun searchAnimeNextPageSelector(): String? = popularAnimeNextPageSelector()

    override fun animeDetailsParse(document: Document): SAnime = SAnime.create().apply {
        title = document.selectFirst("h1, div.title")?.text() ?: "Unknown Title"
        description = document.selectFirst("div.description, div.summary, p.synopsis")?.text()
        thumbnail_url = document.selectFirst("div.poster img, div.cover img")?.attr("abs:src")
        initialized = true
    }

    override fun episodeListSelector(): String = "ul.episodes li, div.episode-item"
    override fun episodeFromElement(element: Element): SEpisode = SEpisode.create().apply {
        val a = element.selectFirst("a")
        setUrlWithoutDomain(a?.attr("href") ?: "")
        name = element.text().ifBlank { "Episode 1" }
        episode_number = 1.0f
    }

    override fun videoListSelector(): String = "iframe"
    override fun videoFromElement(element: Element): Video {
        val src = element.attr("abs:src")
        return Video(src, "Direct Stream", src, headers)
    }
    override fun videoUrlParse(document: Document): String = ""

    fun relatedAnimeListRequest(anime: SAnime): Request = GET("$baseUrl/recommendations", headers)
}
