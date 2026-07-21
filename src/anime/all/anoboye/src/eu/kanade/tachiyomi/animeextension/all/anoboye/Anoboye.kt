package eu.kanade.tachiyomi.animeextension.all.anoboye

import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.ParsedAnimeHttpSource
import eu.kanade.tachiyomi.network.GET
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class Anoboye : ParsedAnimeHttpSource() {
    override val name = "Anoboye"
    override val baseUrl = "https://anoboye.com"
    override val lang = "all"
    override val supportsLatest = true

    override fun popularAnimeRequest(page: Int): Request = GET("$baseUrl/anime/?page=$page", headers)
    override fun popularAnimeSelector(): String = "div.swiper-slide.item, div.postbody article, div.animposx"
    override fun popularAnimeFromElement(element: Element): SAnime = SAnime.create().apply {
        val a = element.selectFirst("a")
        setUrlWithoutDomain(a?.attr("href") ?: "")
        title = element.selectFirst("h2, h3, div.title")?.text() ?: a?.text() ?: "Unknown"
        thumbnail_url = element.selectFirst("img")?.let { it.attr("abs:src").ifBlank { it.attr("data-src") } }
    }
    override fun popularAnimeNextPageSelector(): String? = "a.next, a.pagination-next, div.hpage a.r"

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
        title = document.selectFirst("h1.entry-title, h1")?.text() ?: "Unknown Title"
        description = document.selectFirst("div.entry-content, div.desc, div.synopsis")?.text()
        thumbnail_url = document.selectFirst("div.thumb img, div.poster img")?.attr("abs:src")
        initialized = true
    }

    override fun episodeListSelector(): String = "div.eplister li a, ul.episodelist li a"
    override fun episodeFromElement(element: Element): SEpisode = SEpisode.create().apply {
        setUrlWithoutDomain(element.attr("href"))
        name = element.text().ifBlank { "Episode 1" }
        episode_number = 1.0f
    }

    override fun videoListSelector(): String = "div.player-embed iframe, iframe"
    override fun videoFromElement(element: Element): Video {
        val src = element.attr("abs:src")
        return Video(src, "Default Stream", src, headers)
    }
    override fun videoUrlParse(document: Document): String = ""

    fun relatedAnimeListRequest(anime: SAnime): Request = GET("$baseUrl/recommendations", headers)
}
