package eu.kanade.tachiyomi.animeextension.en.sickr

import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
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
import java.text.SimpleDateFormat
import java.util.Locale

class SickR : ParsedAnimeHttpSource(), ConfigurableAnimeSource {

    override val name = "SickR"
    override val baseUrl = "https://sick-r.com"
    override val lang = "en"
    override val supportsLatest = true

    // ─── Listings ─────────────────────────────────────────────────────────────

    override fun popularAnimeRequest(page: Int): Request =
        GET("$baseUrl/latest-updates/$page/", headers)

    override fun popularAnimeSelector(): String = "div.item-video"

    override fun popularAnimeFromElement(element: Element): SAnime {
        val a = element.selectFirst("a[href*=/videos/]") ?: return SAnime.create()
        val img = element.selectFirst("img")
        val title = element.selectFirst(".listing__item-title")?.text()
            ?: a.attr("title").ifBlank { img?.attr("alt") ?: "" }
        val thumb = img?.attr("data-original")?.ifBlank { null }
            ?: img?.attr("data-webp")?.ifBlank { null }
            ?: img?.attr("src")?.ifBlank { null }
        return SAnime.create().apply {
            url = a.absUrl("href").ifBlank { a.attr("href") }
                .removePrefix(baseUrl)
            this.title = title.trim()
            thumbnail_url = thumb
        }
    }

    override fun popularAnimeNextPageSelector(): String =
        "a.pagination__arrow-next, a.next"

    override fun latestUpdatesRequest(page: Int): Request =
        GET("$baseUrl/latest-updates/$page/", headers)

    override fun latestUpdatesSelector() = popularAnimeSelector()
    override fun latestUpdatesFromElement(element: Element) = popularAnimeFromElement(element)
    override fun latestUpdatesNextPageSelector() = popularAnimeNextPageSelector()

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        if (query.isNotBlank()) {
            val q = query.trim().replace(" ", "+")
            val pageParam = if (page > 1) "&from_videos=$page" else ""
            return GET("$baseUrl/search/?q=$q$pageParam", headers)
        }
        var path = "latest-updates"
        filters.forEach { f ->
            when (f) {
                is CategoryFilter -> if (f.state != 0) path = f.toPath()
                else -> {}
            }
        }
        return GET("$baseUrl/$path/$page/", headers)
    }

    override fun searchAnimeSelector() = popularAnimeSelector()
    override fun searchAnimeFromElement(element: Element) = popularAnimeFromElement(element)
    override fun searchAnimeNextPageSelector() = popularAnimeNextPageSelector()

    // ─── Details ──────────────────────────────────────────────────────────────

    override fun animeDetailsParse(document: Document): SAnime {
        val title = document.selectFirst("h1.video-title, h1, title")?.text()?.trim() ?: "Video"
        val img = document.selectFirst("meta[property=og:image]")?.attr("content")
            ?: document.selectFirst(".player-wrap img, .video-thumb img")?.attr("src")
        val desc = document.selectFirst("div.video-description, .description")?.text()
            ?: document.selectFirst("meta[name=description]")?.attr("content")
        val tags = document.select(".tags a, .categories a, .category a")
            .joinToString(", ") { it.text().trim() }
        return SAnime.create().apply {
            this.title = title
            thumbnail_url = img
            description = desc
            genre = tags.ifBlank { "Fetish" }
            status = SAnime.COMPLETED
        }
    }

    // ─── Episodes ─────────────────────────────────────────────────────────────

    override fun episodeListParse(response: Response): List<SEpisode> {
        val document = response.asJsoup()
        val pubDateStr = document.selectFirst("time[datetime]")?.attr("datetime")
            ?: document.selectFirst("meta[property=article:published_time]")?.attr("content")
            ?: ""
        val uploadDate = runCatching {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).parse(pubDateStr)?.time
                ?: 0L
        }.getOrDefault(0L)

        return listOf(
            SEpisode.create().apply {
                url = response.request.url.toString()
                name = "Full Video"
                episode_number = 1f
                date_upload = uploadDate
            }
        )
    }

    override fun episodeListSelector() = throw UnsupportedOperationException()
    override fun episodeFromElement(element: Element) = throw UnsupportedOperationException()

    // ─── Video Sources ────────────────────────────────────────────────────────

    override fun videoListParse(response: Response): List<Video> {
        val body = response.body.string()
        val videos = mutableListOf<Video>()

        val urlHd = Regex("""video_url_hd\s*:\s*['"]([^'"]+)['"]""").find(body)?.groupValues?.get(1)
        val urlSd = Regex("""video_url\s*:\s*['"]([^'"]+)['"]""").find(body)?.groupValues?.get(1)

        if (!urlHd.isNullOrBlank()) {
            videos.add(Video(urlHd, "720p [SickR]", urlHd, headers))
        }
        if (!urlSd.isNullOrBlank()) {
            videos.add(Video(urlSd, "SD [SickR]", urlSd, headers))
        }

        if (videos.isNotEmpty()) return videos

        val getFileRegex = Regex("""["'](https?://sick-r\.com/get_file/[^"'<>\s]+)["']""")
        getFileRegex.findAll(body).forEachIndexed { idx, m ->
            val url = m.groupValues[1]
            if (!url.contains("preview")) {
                val quality = if (url.contains("720p")) "720p" else "SD"
                videos.add(Video(url, "$quality [SickR] #$idx", url, headers))
            }
        }

        return videos.distinctBy { it.url }
    }

    override fun videoListSelector() = throw UnsupportedOperationException()
    override fun videoFromElement(element: Element) = throw UnsupportedOperationException()
    override fun videoUrlParse(document: Document) = throw UnsupportedOperationException()

    // ─── Filters ──────────────────────────────────────────────────────────────

    override fun getFilterList(): AnimeFilterList = AnimeFilterList(
        AnimeFilter.Header("Search ignores filters"),
        CategoryFilter(),
    )

    class CategoryFilter : AnimeFilter.Select<String>(
        "Category",
        arrayOf("All", "Teen", "Incest", "BDSM", "Torture", "Voyeur", "Gangbang"),
    ) {
        private val paths = arrayOf("", "teen", "incest", "bdsm", "torture", "voyeur", "gangbang")
        fun toPath() = "categories/${paths[state]}"
    }

    // ─── Preferences ──────────────────────────────────────────────────────────

    override fun setupPreferenceScreen(screen: PreferenceScreen) {}
}
