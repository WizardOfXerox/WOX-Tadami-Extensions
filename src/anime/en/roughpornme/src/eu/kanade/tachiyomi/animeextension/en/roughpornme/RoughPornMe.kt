package eu.kanade.tachiyomi.animeextension.en.roughpornme

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

class RoughPornMe : ParsedAnimeHttpSource(), ConfigurableAnimeSource {

    override val name = "RoughPorn"
    override val baseUrl = "https://roughporn.me"
    override val lang = "en"
    override val supportsLatest = true

    private val dateParser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)

    // ─── Listings ─────────────────────────────────────────────────────────────

    override fun popularAnimeRequest(page: Int): Request =
        GET("$baseUrl/en/category/112853074/Brazil/popular/$page/", headers)

    override fun popularAnimeSelector() = "div.thumbs-container > div.thumb:has(a[href*=/video/])"

    override fun popularAnimeFromElement(element: Element): SAnime {
        val a = element.selectFirst("a[href*=/video/]") ?: return SAnime.create()
        val img = element.selectFirst("img")
        val title = element.selectFirst(".info p")?.text()
            ?: img?.attr("alt")
            ?: a.attr("title")
        return SAnime.create().apply {
            url = a.attr("href").let { if (it.startsWith("/")) it else "/$it" }
            this.title = title.trim()
            thumbnail_url = img?.attr("src")?.let {
                if (it.startsWith("http")) it else "$baseUrl$it"
            }
        }
    }

    override fun popularAnimeNextPageSelector() = "div.pagination a"

    override fun latestUpdatesRequest(page: Int): Request =
        GET("$baseUrl/en/category/112853074/Brazil/latest/$page/", headers)

    override fun latestUpdatesSelector() = popularAnimeSelector()
    override fun latestUpdatesFromElement(element: Element) = popularAnimeFromElement(element)
    override fun latestUpdatesNextPageSelector() = popularAnimeNextPageSelector()

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        if (query.isNotBlank()) {
            return GET("$baseUrl/en/search/$page/?q=${query.trim().replace(" ", "+")}", headers)
        }
        var catId = "112853074"
        var catSlug = "Brazil"
        var sortMode = "popular"
        filters.forEach { f ->
            when (f) {
                is RoughPornMeFilters.CategoryFilter -> {
                    catId = f.selectedId
                    catSlug = f.selectedSlug
                }
                is RoughPornMeFilters.SortFilter -> sortMode = f.toSortMode()
                else -> {}
            }
        }
        return GET("$baseUrl/en/category/$catId/$catSlug/$sortMode/$page/", headers)
    }

    override fun searchAnimeSelector() = popularAnimeSelector()
    override fun searchAnimeFromElement(element: Element) = popularAnimeFromElement(element)
    override fun searchAnimeNextPageSelector() = popularAnimeNextPageSelector()

    // ─── Details ──────────────────────────────────────────────────────────────

    override fun animeDetailsParse(document: Document): SAnime {
        val title = document.selectFirst("h1, .video-title, title")?.text()?.trim() ?: "Video"
        val img = document.selectFirst("meta[property=og:image]")?.attr("content")
            ?: document.selectFirst("video img, .thumb img")?.attr("src")
        val desc = document.selectFirst("meta[name=description]")?.attr("content")
        return SAnime.create().apply {
            this.title = title
            thumbnail_url = img
            description = desc
            genre = "Live Action"
            status = SAnime.COMPLETED
        }
    }

    // ─── Episodes ─────────────────────────────────────────────────────────────

    override fun episodeListParse(response: Response): List<SEpisode> {
        val document = response.asJsoup()
        var dateText = document.selectFirst("meta[property=og:updated_time], time[datetime], .date")
            ?.attr("datetime") ?: ""
        if (dateText.isBlank()) {
            val jsonLd = document.selectFirst("script[type=application/ld+json]")?.data() ?: ""
            dateText = Regex(""""uploadDate"\s*:\s*"([^"]+)"""").find(jsonLd)?.groupValues?.get(1) ?: ""
        }
        val uploadDate = runCatching { dateParser.parse(dateText)?.time ?: 0L }.getOrDefault(0L)

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

        // Primary: JSON-LD schema "contentUrl" — always present on video pages
        val contentUrlRegex = Regex(""""contentUrl"\s*:\s*"([^"]+)"""")
        val contentUrl = contentUrlRegex.find(body)?.groupValues?.get(1)?.replace(Regex("""\\/"""), "/")
        if (!contentUrl.isNullOrBlank()) {
            return listOf(Video(contentUrl, "MP4 [RoughPorn]", contentUrl, headers))
        }

        // Fallback: <video><source src="...">
        val videoSrcRegex = Regex("""<source[^>]+src=['"]([^'"]+\.mp4[^'"]*)['"]""")
        val srcUrl = videoSrcRegex.find(body)?.groupValues?.get(1)
        if (srcUrl != null) {
            return listOf(Video(srcUrl, "MP4 [RoughPorn]", srcUrl, headers))
        }

        // Fallback 2: any .mp4 URL in the body
        val mp4Regex = Regex("""["'](https?://[^"'<>\s]+\.mp4[^"'<>\s]*)["']""")
        val mp4Url = mp4Regex.find(body)?.groupValues?.get(1)
        if (mp4Url != null) {
            return listOf(Video(mp4Url, "MP4 [RoughPorn]", mp4Url, headers))
        }

        return emptyList()
    }

    override fun videoListSelector() = throw UnsupportedOperationException()
    override fun videoFromElement(element: Element) = throw UnsupportedOperationException()
    override fun videoUrlParse(document: Document) = throw UnsupportedOperationException()

    // ─── Filters ──────────────────────────────────────────────────────────────

    override fun getFilterList(): AnimeFilterList = AnimeFilterList(
        AnimeFilter.Header("Search ignores filters"),
        RoughPornMeFilters.SortFilter(),
        RoughPornMeFilters.CategoryFilter(),
    )

    // ─── Preferences ──────────────────────────────────────────────────────────

    override fun setupPreferenceScreen(screen: PreferenceScreen) {}
}
