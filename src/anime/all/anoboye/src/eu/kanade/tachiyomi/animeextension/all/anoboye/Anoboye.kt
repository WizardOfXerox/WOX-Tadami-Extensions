package eu.kanade.tachiyomi.animeextension.all.anoboye

import android.app.Application
import android.content.SharedPreferences
import android.util.Base64
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Track
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.ParsedAnimeHttpSource
import eu.kanade.tachiyomi.lib.doodextractor.DoodExtractor
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.net.URLEncoder

class Anoboye : ParsedAnimeHttpSource(), ConfigurableAnimeSource {

    override val name = "Anoboye"
    override val baseUrl = "https://anoboye.com"
    override val lang = "all"
    override val supportsLatest = true

    private val doodExtractor by lazy { DoodExtractor(client) }

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0)
    }

    override val client: OkHttpClient = network.client.newBuilder()
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Referer", "$baseUrl/")
                .build()
            chain.proceed(request)
        }
        .build()

    // ============================== POPULAR ==============================

    override fun popularAnimeRequest(page: Int): Request = GET("$baseUrl/anime/page/$page/", headers)

    override fun popularAnimeSelector(): String = "div.swiper-slide.item, div.postbody article, div.animposx"

    override fun popularAnimeFromElement(element: Element): SAnime = SAnime.create().apply {
        val a = element.selectFirst("a")
        setUrlWithoutDomain(a?.attr("href") ?: "")
        title = element.selectFirst("h2, h3, div.title")?.text() ?: a?.text() ?: "Unknown Anime"
        thumbnail_url = element.selectFirst("img")?.let { it.attr("abs:src").ifBlank { it.attr("data-src") } }
    }

    override fun popularAnimeNextPageSelector(): String? = "a.next, a.pagination-next, div.hpage a.r"

    // ============================== LATEST ==============================

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/page/$page/", headers)

    override fun latestUpdatesSelector(): String = popularAnimeSelector()

    override fun latestUpdatesFromElement(element: Element): SAnime = popularAnimeFromElement(element)

    override fun latestUpdatesNextPageSelector(): String? = popularAnimeNextPageSelector()

    // ============================== SEARCH & ROBUST FILTERS ==============================

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        if (query.isNotBlank()) {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            return GET("$baseUrl/page/$page/?s=$encodedQuery", headers)
        }

        var genre = ""
        var status = ""
        var order = ""

        filters.forEach { filter ->
            when (filter) {
                is GenreFilter -> genre = filter.toUriPart()
                is StatusFilter -> status = filter.toUriPart()
                is OrderFilter -> order = filter.toUriPart()
                else -> {}
            }
        }

        val url = when {
            genre.isNotEmpty() -> "$baseUrl/genre/$genre/page/$page/"
            status.isNotEmpty() -> "$baseUrl/status/$status/page/$page/"
            order.isNotEmpty() -> "$baseUrl/order/$order/page/$page/"
            else -> "$baseUrl/anime/page/$page/"
        }

        return GET(url, headers)
    }

    override fun searchAnimeSelector(): String = popularAnimeSelector()

    override fun searchAnimeFromElement(element: Element): SAnime = popularAnimeFromElement(element)

    override fun searchAnimeNextPageSelector(): String? = popularAnimeNextPageSelector()

    // ============================== DETAILS ==============================

    override fun animeDetailsParse(document: Document): SAnime = SAnime.create().apply {
        title = document.selectFirst("h1.entry-title, h1")?.text() ?: "Unknown Title"
        description = document.selectFirst("div.entry-content, div.desc, div.synopsis")?.text()
        thumbnail_url = document.selectFirst("div.thumb img, div.poster img")?.attr("abs:src")
        genre = document.select("div.genxed a, div.mgen a").joinToString(", ") { it.text() }
        initialized = true
    }

    // ============================== EPISODES ==============================

    override fun episodeListSelector(): String = "div.eplister li a, ul.episodelist li a"

    override fun episodeFromElement(element: Element): SEpisode = SEpisode.create().apply {
        setUrlWithoutDomain(element.attr("href"))
        name = element.selectFirst("div.epl-num, span.ep")?.text() ?: element.text().ifBlank { "Episode 1" }
        episode_number = element.text().filter { it.isDigit() }.toFloatOrNull() ?: 1.0f
    }

    // ============================== ROBUST VIDEO RESOLVER ==============================

    override fun videoListParse(response: Response): List<Video> {
        val doc = response.asJsoup()
        val videoList = mutableListOf<Video>()
        val embedUrls = mutableSetOf<String>()

        // 1. Direct iframes in HTML
        doc.select("div.player-embed iframe, div.embed-container iframe, iframe").forEach { iframe ->
            val src = iframe.attr("abs:src")
            if (src.isNotBlank()) embedUrls.add(src)
        }

        // 2. Decode Base64 option values in server selector dropdowns
        doc.select("select option, div.player-embed option").forEach { option ->
            val valStr = option.attr("value").trim()
            if (valStr.length > 20 && !valStr.startsWith("http")) {
                try {
                    val decodedHtml = String(Base64.decode(valStr, Base64.DEFAULT))
                    val parsedDoc = Jsoup.parse(decodedHtml)
                    parsedDoc.select("iframe[src]").forEach { iframe ->
                        val src = iframe.attr("abs:src")
                        if (src.isNotBlank()) embedUrls.add(src)
                    }
                } catch (_: Exception) {}
            }
        }

        // 3. Resolve videos from all discovered embed URLs
        embedUrls.forEach { embedUrl ->
            try {
                when {
                    embedUrl.contains("dood") || embedUrl.contains("ds2play") -> {
                        videoList.addAll(doodExtractor.videosFromUrl(embedUrl, "DoodStream"))
                    }
                    embedUrl.contains("exartplayer.php") -> {
                        val playerReq = GET(embedUrl, headers)
                        val playerHtml = client.newCall(playerReq).execute().body.string()
                        val m3u8Match = Regex("videoUrl\\s*:\\s*\"([^\"]+)\"").find(playerHtml)
                        if (m3u8Match != null) {
                            val streamUrl = m3u8Match.groupValues[1].replace("\\/", "/")
                            videoList.add(Video(streamUrl, "Chiki Multi-Sub", streamUrl))
                        }
                    }
                    embedUrl.contains("dailyplayer.php") -> {
                        val playerReq = GET(embedUrl, headers)
                        val playerHtml = client.newCall(playerReq).execute().body.string()
                        val vidMatch = Regex("VID\\s*=\\s*\"([^\"]+)\"").find(playerHtml)
                        if (vidMatch != null) {
                            val vid = vidMatch.groupValues[1]
                            val dmUrl = "https://www.dailymotion.com/embed/video/$vid"
                            videoList.add(Video(dmUrl, "Chiki DailyPlayer (Dailymotion)", dmUrl))
                        }
                    }
                    embedUrl.contains("dailymotion.com") -> {
                        videoList.add(Video(embedUrl, "Dailymotion", embedUrl))
                    }
                    embedUrl.contains(".m3u8") || embedUrl.contains(".mp4") -> {
                        videoList.add(Video(embedUrl, "Direct Stream", embedUrl, headers))
                    }
                }
            } catch (_: Exception) {}
        }

        return videoList
    }

    override fun videoListSelector(): String = "div.player-embed iframe, iframe"
    override fun videoFromElement(element: Element): Video = throw UnsupportedOperationException()
    override fun videoUrlParse(document: Document): String = ""

    fun relatedAnimeListRequest(anime: SAnime): Request = GET("$baseUrl/recommendations", headers)

    // ============================== ROBUST FILTERS ==============================

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
        Pair("Ecchi", "ecchi"),
        Pair("Fantasy", "fantasy"),
        Pair("Harem", "harem"),
        Pair("Historical", "historical"),
        Pair("Horror", "horror"),
        Pair("Martial Arts", "martial-arts"),
        Pair("Mecha", "mecha"),
        Pair("Mystery", "mystery"),
        Pair("Romance", "romance"),
        Pair("Sci-Fi", "sci-fi"),
        Pair("Slice of Life", "slice-of-life"),
        Pair("Sports", "sports"),
        Pair("Supernatural", "supernatural"),
        Pair("Thriller", "thriller")
    ))

    private class StatusFilter : UriPartFilter("Status", arrayOf(
        Pair("All", ""),
        Pair("Ongoing", "ongoing"),
        Pair("Completed", "completed")
    ))

    private class OrderFilter : UriPartFilter("Sort By", arrayOf(
        Pair("Latest", "latest"),
        Pair("Popular", "popular"),
        Pair("A-Z", "title"),
        Pair("Z-A", "titlereverse")
    ))

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val qualityPref = ListPreference(screen.context).apply {
            key = PREF_QUALITY
            title = "Preferred Video Quality"
            entries = arrayOf("1080p", "720p", "480p", "360p")
            entryValues = arrayOf("1080p", "720p", "480p", "360p")
            setDefaultValue("1080p")
            summary = "%s"

            setOnPreferenceChangeListener { _, newValue ->
                val selected = newValue as String
                val index = findIndexOfValue(selected)
                summary = entryValues[index] as String
                preferences.edit().putString(PREF_QUALITY, selected).apply()
                true
            }
        }
        screen.addPreference(qualityPref)
    }

    companion object {
        private const val PREF_QUALITY = "preferred_quality"
    }
}
