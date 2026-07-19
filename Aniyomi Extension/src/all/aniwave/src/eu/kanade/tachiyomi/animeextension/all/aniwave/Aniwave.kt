package eu.kanade.tachiyomi.animeextension.all.aniwave

import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.ParsedAnimeHttpSource
import eu.kanade.tachiyomi.network.GET
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class Aniwave : ParsedAnimeHttpSource() {

    override val name = "Aniwave"
    override val baseUrl = "https://aniwave.is"
    override val lang = "all"
    override val supportsLatest = true

    private val json = Json { ignoreUnknownKeys = true }

    private val docHeaders get() = headers.newBuilder().apply {
        add("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
        add("Referer", "$baseUrl/")
    }.build()

    private fun Element.getImageUrl(): String {
        val url = when {
            hasAttr("data-src") -> attr("abs:data-src")
            hasAttr("data-lazy-src") -> attr("abs:data-lazy-src")
            hasAttr("srcset") -> attr("abs:srcset").substringBefore(" ").trim()
            else -> attr("abs:src")
        }
        return if (url.startsWith("http")) url else if (url.isNotBlank()) "$baseUrl${if (url.startsWith("/")) "" else "/"}$url" else ""
    }

    override fun popularAnimeRequest(page: Int): Request = GET("$baseUrl/trending-anime${if (page > 1) "?page=$page" else ""}", docHeaders)
    override fun popularAnimeSelector() = "a[href*='/watch/']"
    override fun popularAnimeFromElement(element: Element): SAnime {
        val anime = SAnime.create()
        val link = element.attr("abs:href")
        if (link.isBlank() || !link.contains("/watch/")) return anime
        anime.setUrlWithoutDomain(link)
        anime.title = element.attr("title").ifBlank {
            element.parent()?.select("a[href*='/watch/']")?.firstOrNull()?.text()?.trim()
                ?: element.ownText().trim()
        }.ifBlank { "Anime" }
        anime.thumbnail_url = element.select("img").first()?.getImageUrl() ?: ""
        anime.initialized = false
        return anime
    }

    override fun popularAnimeNextPageSelector(): String? = "a.page-link:contains(Next), .pagination a[rel=next], a.next, li.next a"

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/anime-list${if (page > 1) "?page=$page" else ""}", docHeaders)
    override fun latestUpdatesSelector() = popularAnimeSelector()
    override fun latestUpdatesFromElement(element: Element): SAnime = popularAnimeFromElement(element)
    override fun latestUpdatesNextPageSelector(): String? = popularAnimeNextPageSelector()

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        return if (query.isNotBlank()) {
            GET("$baseUrl/filter?keyword=${java.net.URLEncoder.encode(query, "UTF-8")}&page=$page", docHeaders)
        } else {
            GET("$baseUrl/filter?page=$page", docHeaders)
        }
    }

    override fun searchAnimeSelector() = popularAnimeSelector()
    override fun searchAnimeFromElement(element: Element): SAnime = popularAnimeFromElement(element)
    override fun searchAnimeNextPageSelector(): String? = popularAnimeNextPageSelector()

    override fun getFilterList(): AnimeFilterList = AnimeFilterList(emptyList())

    override fun animeDetailsParse(document: Document): SAnime {
        val anime = SAnime.create()
        anime.setUrlWithoutDomain(document.select("link[rel=canonical]").attr("abs:href").ifBlank { document.location() })
        anime.title = document.select("h2.film-name, h1.title, .anime-name, h2").first()?.text()?.trim() ?: ""
        anime.thumbnail_url = document.select("img.film-poster-img, .anime-poster img, img[src*=/posters/], img[data-src*=/posters/]").first()?.getImageUrl() ?: ""
        anime.description = document.select(".film-description, .anime-description, .description, [itemprop=description]").first()?.text()?.trim()
        anime.genre = document.select("a[href*=/genre/], .genre a").joinToString(", ") { it.text().trim() }.ifBlank { null }
        anime.status = parseStatus(document.select(".film-status, .status, [class*='status']").first()?.text())
        anime.initialized = true
        return anime
    }

    private fun parseStatus(statusStr: String?): Int = when (statusStr?.lowercase()) {
        "ongoing", "releasing", "airing" -> SAnime.ONGOING
        "completed", "finished" -> SAnime.COMPLETED
        else -> SAnime.UNKNOWN
    }

    override fun episodeListRequest(anime: SAnime): Request {
        val id = anime.url.substringAfterLast("/").trimEnd('/')
        val apiHeaders = headers.newBuilder().apply {
            add("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            add("Referer", baseUrl + anime.url)
            add("X-Requested-With", "XMLHttpRequest")
        }.build()
        return GET("$baseUrl/ajax/v2/episode/list/$id", apiHeaders)
    }

    override fun episodeListSelector() = "a.ep-item, a[href*='/watch/'][href*='-ep-'], .episode-list a, .episodes a, ul.episodes li a, div.eplister ul li a, a[href*='ep-']"
    override fun episodeListParse(response: Response): List<SEpisode> {
        val body = response.body?.string() ?: return emptyList()
        val doc = when {
            body.trimStart().startsWith("{") -> runCatching {
                val html = json.parseToJsonElement(body).jsonObject["html"]?.jsonPrimitive?.content ?: body
                Jsoup.parseBodyFragment(html, baseUrl)
            }.getOrNull() ?: Jsoup.parse(body, baseUrl)
            else -> Jsoup.parse(body, baseUrl)
        }
        val items = doc.select(episodeListSelector())
        return items.mapNotNull { element ->
            val abs = element.attr("abs:href")
            val href = if (abs.isNotBlank()) {
                abs
            } else {
                val r = element.attr("href")
                if (r.startsWith("http")) r else "$baseUrl${if (r.startsWith("/")) r else "/$r"}"
            }
            if (href.isBlank() || !href.contains("/watch/")) return@mapNotNull null
            SEpisode.create().apply {
                setUrlWithoutDomain(href)
                val num = element.attr("data-number")
                val nameText = element.attr("title").ifBlank { element.text() }.trim()
                name = nameText.ifBlank { "Episode ${num.ifBlank { "1" }}" }
                episode_number = num.toFloatOrNull()
                    ?: runCatching {
                        Regex("""ep[-.]?\s*(\d+)""", RegexOption.IGNORE_CASE).find(element.attr("href") + " " + nameText)?.groupValues?.get(1)?.toFloat()
                            ?: Regex("""(\d+)""").find(nameText)?.groupValues?.get(1)?.toFloat()
                    }.getOrNull() ?: 0f
                date_upload = 0L
            }
        }.distinctBy { it.url }.reversed()
    }

    override fun episodeFromElement(element: Element): SEpisode {
        val ep = SEpisode.create()
        ep.setUrlWithoutDomain(element.attr("abs:href"))
        val nameText = element.attr("title").ifBlank { element.text() }.trim()
        ep.name = nameText.ifBlank { "Episode ${ep.episode_number.toInt()}" }
        ep.episode_number = runCatching {
            Regex("""ep[-.]?\s*(\d+)""", RegexOption.IGNORE_CASE).find(element.attr("href") + " " + nameText)?.groupValues?.get(1)?.toFloat()
                ?: Regex("""(\d+)""").find(nameText)?.groupValues?.get(1)?.toFloat()
        }.getOrNull() ?: 0f
        ep.date_upload = 0L
        return ep
    }

    override fun videoListSelector(): String = throw UnsupportedOperationException()
    override fun videoFromElement(element: Element): Video = throw UnsupportedOperationException()
    override fun videoUrlParse(document: Document): String = throw UnsupportedOperationException()

    override suspend fun getVideoList(episode: SEpisode): List<Video> {
        val response = client.newCall(GET(episode.url, docHeaders)).execute()
        val doc = org.jsoup.Jsoup.parse(response.body?.string() ?: "", baseUrl)
        val videos = mutableListOf<Video>()
        doc.select("[data-id], .server-item, .embed-server, select.server option, [data-src]").forEach { el ->
            val serverName = el.text().trim().ifBlank { el.attr("data-server") }.ifBlank { "Server" }
            val embedUrl = el.attr("abs:data-src").ifBlank { el.attr("abs:data-id") }.ifBlank { el.select("a").attr("abs:href") }
            if (embedUrl.isNotBlank()) {
                videos.add(Video(embedUrl, serverName, embedUrl))
            }
        }
        if (videos.isEmpty()) {
            doc.select("iframe[src*='embed'], source[src], video source[src]").forEach { el ->
                val src = el.attr("abs:src")
                if (src.isNotBlank()) {
                    videos.add(Video(src, "Default", src))
                }
            }
        }
        return videos
    }

    override fun popularAnimeParse(response: Response): AnimesPage {
        val page = super.popularAnimeParse(response)
        return AnimesPage(page.animes.distinctBy { it.url }, page.hasNextPage)
    }

    override fun latestUpdatesParse(response: Response): AnimesPage {
        val page = super.latestUpdatesParse(response)
        return AnimesPage(page.animes.distinctBy { it.url }, page.hasNextPage)
    }

    override fun searchAnimeParse(response: Response): AnimesPage {
        val page = super.searchAnimeParse(response)
        return AnimesPage(page.animes.distinctBy { it.url }, page.hasNextPage)
    }
}
