package eu.kanade.tachiyomi.animeextension.all.xnxx

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.ParsedAnimeHttpSource
import eu.kanade.tachiyomi.network.GET
import okhttp3.Headers
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class Xnxx : ParsedAnimeHttpSource(), ConfigurableAnimeSource {

    override val name = "XNXX"

    override val baseUrl = "https://www.xnxx.com"

    override val lang = "all"

    override val supportsLatest = true

    override val id: Long = 8523674512L

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    // ============================== Headers ==============================

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        .add("Origin", baseUrl)

    // ============================== Popular Anime ==============================

    override fun popularAnimeRequest(page: Int): Request {
        val dateStr = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())
        val pageIndex = page - 1
        return GET("$baseUrl/best/$dateStr/$pageIndex", headers)
    }

    override fun popularAnimeSelector(): String = "div.thumb-block[id^=video_], div.thumb-block.video"

    override fun popularAnimeFromElement(element: Element): SAnime {
        return SAnime.create().apply {
            val aElement = element.selectFirst("a[href*=/video-], div.thumb a, a")
                ?: throw Exception("Video link not found in element")
            val href = aElement.attr("href")
            setUrlWithoutDomain(href)
            
            val titleElement = element.selectFirst("div.thumb-under a.title, div.thumb-under a, a.title")
            title = titleElement?.attr("title")?.ifBlank { titleElement.text() }
                ?: titleElement?.text()
                ?: "Video"
            title = title.trim()
            
            val img = element.selectFirst("div.thumb img, img")
            thumbnail_url = img?.let {
                val dataSrc = it.attr("data-src")
                if (dataSrc.isNotBlank()) dataSrc else it.attr("src")
            } ?: ""
        }
    }

    override fun popularAnimeNextPageSelector(): String = "div.pagination a.next"

    // ============================== Latest Anime ==============================

    override fun latestUpdatesRequest(page: Int): Request {
        val pageIndex = page - 1
        return if (pageIndex == 0) {
            GET("$baseUrl/todays-selection", headers)
        } else {
            GET("$baseUrl/todays-selection/$pageIndex", headers)
        }
    }

    override fun latestUpdatesSelector(): String = popularAnimeSelector()

    override fun latestUpdatesFromElement(element: Element): SAnime = popularAnimeFromElement(element)

    override fun latestUpdatesNextPageSelector(): String = popularAnimeNextPageSelector()

    // ============================== Search ==============================

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        val pageIndex = page - 1
        
        if (query.isNotBlank()) {
            val encodedWord = query.trim().replace(" ", "+")
            val sortFilter = filters.filterIsInstance<XnxxFilters.SortFilter>().firstOrNull()
            val sort = sortFilter?.toUri() ?: "relevance"
            val durationFilter = filters.filterIsInstance<XnxxFilters.DurationFilter>().firstOrNull()
            val duration = durationFilter?.toUri() ?: "all"
            
            val url = if (duration == "all") {
                "$baseUrl/search/hits/$sort/$encodedWord/$pageIndex"
            } else {
                "$baseUrl/search/hits/$sort/$encodedWord/$pageIndex/$duration"
            }
            return GET(url, headers)
        }
        
        val tagFilter = filters.filterIsInstance<XnxxFilters.TagFilter>().firstOrNull()
        val tag = tagFilter?.state?.trim() ?: ""
        if (tag.isNotBlank()) {
            val encodedTag = tag.replace(" ", "-").lowercase()
            val url = if (pageIndex == 0) {
                "$baseUrl/tags/$encodedTag"
            } else {
                "$baseUrl/tags/$encodedTag/$pageIndex"
            }
            return GET(url, headers)
        }
        
        return popularAnimeRequest(page)
    }

    override fun searchAnimeSelector(): String = popularAnimeSelector()

    override fun searchAnimeFromElement(element: Element): SAnime = popularAnimeFromElement(element)

    override fun searchAnimeNextPageSelector(): String = popularAnimeNextPageSelector()

    // ============================== Details ==============================

    override fun animeDetailsParse(document: Document): SAnime {
        return SAnime.create().apply {
            title = document.selectFirst("h1.video-title, title, meta[property=og:title], #video-content-metadata > div.clear-infobar strong")?.let {
                val textVal = it.text()
                if (textVal.isNotBlank()) textVal else it.attr("content")
            }?.trim()?.removeSuffix(" - XNXX.COM") ?: "Unknown"

            description = buildString {
                document.selectFirst("span.duration, .video-metadata span")?.text()?.let {
                    if (it.isNotBlank()) appendLine("Duration: $it")
                }
                val tags = document.select("a.is-keyword, div.metadata-row a[href*=/tags/], #video-content-metadata > div.metadata-row.video-tags > a")
                    .map { it.text().trim() }
                    .filter { it.isNotBlank() }
                if (tags.isNotEmpty()) {
                    appendLine("Tags: ${tags.joinToString(", ")}")
                }
                val descText = document.select("#video-content-metadata > p").text()
                if (descText.isNotBlank()) {
                    appendLine(descText.replace("\n", ""))
                }
            }.trim()

            genre = document.select("a.is-keyword, div.metadata-row a[href*=/tags/], #video-content-metadata > div.metadata-row.video-tags > a")
                .joinToString { it.text().trim() }
                .ifBlank { null }

            status = SAnime.COMPLETED

            thumbnail_url = document.selectFirst("meta[property=og:image]")?.attr("content")
        }
    }

    // ============================== Episodes ==============================

    override fun episodeListParse(response: Response): List<SEpisode> {
        return listOf(
            SEpisode.create().apply {
                name = "Video"
                setUrlWithoutDomain(response.request.url.toString())
                date_upload = System.currentTimeMillis()
            }
        )
    }

    override fun episodeFromElement(element: Element): SEpisode = throw UnsupportedOperationException()
    override fun episodeListSelector(): String = throw UnsupportedOperationException()

    // ============================== Video Listing ==============================

    override fun videoListParse(response: Response): List<Video> {
        val pageContent = response.body.string()
        val videoList = mutableListOf<Video>()

        val hlsUrl = extractJsPlayerUrl(pageContent, "setVideoHLS")
        val highUrl = extractJsPlayerUrl(pageContent, "setVideoUrlHigh")
        val lowUrl = extractJsPlayerUrl(pageContent, "setVideoUrlLow")

        if (!hlsUrl.isNullOrBlank()) {
            videoList.add(Video(hlsUrl, "HLS", hlsUrl, headers))
        }
        if (!highUrl.isNullOrBlank()) {
            videoList.add(Video(highUrl, "High", highUrl, headers))
        }
        if (!lowUrl.isNullOrBlank()) {
            videoList.add(Video(lowUrl, "Low", lowUrl, headers))
        }

        if (videoList.isEmpty()) {
            val document = Jsoup.parse(pageContent)
            val ogVideo = document.selectFirst("meta[property=og:video], meta[property=og:video:url]")
                ?.attr("content")
            if (!ogVideo.isNullOrBlank()) {
                videoList.add(Video(ogVideo, "Default", ogVideo, headers))
            }
        }

        if (videoList.isEmpty()) {
            val document = Jsoup.parse(pageContent)
            document.select("video source").forEach { source ->
                val src = source.attr("src")
                val type = source.attr("type").ifBlank { "video/mp4" }
                if (src.isNotBlank()) {
                    videoList.add(Video(src, "Source ($type)", src, headers))
                }
            }
        }

        return videoList.sort()
    }

    private fun extractJsPlayerUrl(html: String, methodName: String): String? {
        val regex = Regex("""html5player\.$methodName\(['"]([^'"]+)['"]\)""")
        return regex.find(html)?.groupValues?.getOrNull(1)
    }

    override fun videoFromElement(element: Element): Video = throw UnsupportedOperationException()
    override fun videoListSelector(): String = throw UnsupportedOperationException()
    override fun videoUrlParse(document: Document): String = throw UnsupportedOperationException()

    // ============================== Preferences ==============================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = PREF_QUALITY_KEY
            title = "Preferred quality"
            entries = arrayOf("High", "Low", "HLS")
            entryValues = arrayOf("High", "Low", "HLS")
            setDefaultValue("HLS")
            summary = "%s"
        }.also(screen::addPreference)
    }

    override fun List<Video>.sort(): List<Video> {
        val preferredQuality = preferences.getString(PREF_QUALITY_KEY, "HLS") ?: "HLS"
        return sortedWith(
            compareBy { !it.quality.equals(preferredQuality, ignoreCase = true) }
        )
    }

    override fun getFilterList(): AnimeFilterList = XnxxFilters.getFilters()

    companion object {
        private const val PREF_QUALITY_KEY = "preferred_quality"
    }
}
