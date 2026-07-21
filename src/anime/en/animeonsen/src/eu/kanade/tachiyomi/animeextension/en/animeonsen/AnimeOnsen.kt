package eu.kanade.tachiyomi.animeextension.en.animeonsen

import androidx.preference.ListPreference
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
import keiyoushi.utils.getPreferencesLazy
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class AnimeOnsen : ParsedAnimeHttpSource(), ConfigurableAnimeSource {

    override val name = "AnimeOnsen"
    override val baseUrl = "https://animeonsen.xyz"
    override val lang = "en"
    override val supportsLatest = true

    private val preferences by getPreferencesLazy()

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

    override fun popularAnimeRequest(page: Int): Request = GET("$baseUrl/browse?page=$page&sort=popular")

    override fun popularAnimeSelector(): String = "div.anime-card, div.media-card, div.grid-item, div.card"

    override fun popularAnimeFromElement(element: Element): SAnime {
        return SAnime.create().apply {
            val link = element.selectFirst("a[href]")
            val href = link?.attr("href") ?: ""
            url = href
            title = element.selectFirst("h3, h4, .title, .name")?.text() ?: link?.attr("title") ?: "AnimeOnsen Show"
            thumbnail_url = element.selectFirst("img")?.attr("abs:src") ?: element.selectFirst("img")?.attr("abs:data-src")
        }
    }

    override fun popularAnimeNextPageSelector(): String = "a.next, a[rel=next], div.pagination a:contains(Next)"

    // ============================== LATEST ==============================

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/browse?page=$page&sort=latest")

    override fun latestUpdatesSelector(): String = popularAnimeSelector()

    override fun latestUpdatesFromElement(element: Element): SAnime = popularAnimeFromElement(element)

    override fun latestUpdatesNextPageSelector(): String = popularAnimeNextPageSelector()

    // ============================== SEARCH & FILTERS ==============================

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        val url = StringBuilder("$baseUrl/browse?page=$page")
        if (query.isNotEmpty()) {
            url.append("&q=").append(java.net.URLEncoder.encode(query, "UTF-8"))
        }

        for (filter in filters) {
            when (filter) {
                is GenreFilter -> if (filter.state != 0) url.append("&genre=").append(filter.toUriPart())
                is StatusFilter -> if (filter.state != 0) url.append("&status=").append(filter.toUriPart())
                is TypeFilter -> if (filter.state != 0) url.append("&type=").append(filter.toUriPart())
                is SortFilter -> url.append("&sort=").append(filter.toUriPart())
                else -> {}
            }
        }

        return GET(url.toString())
    }

    override fun searchAnimeSelector(): String = popularAnimeSelector()

    override fun searchAnimeFromElement(element: Element): SAnime = popularAnimeFromElement(element)

    override fun searchAnimeNextPageSelector(): String = popularAnimeNextPageSelector()

    // ============================== DETAILS ==============================

    override fun animeDetailsParse(document: Document): SAnime {
        return SAnime.create().apply {
            title = document.selectFirst("h1.title, h1, div.anime-title")?.text() ?: "AnimeOnsen Show"
            genre = document.select("div.genres a, div.tags a, span.genre").joinToString(", ") { it.text() }
            description = document.selectFirst("div.synopsis, div.description, p.summary")?.text()
            status = parseStatus(document.selectFirst("span.status, div.status")?.text() ?: "")
            thumbnail_url = document.selectFirst("div.cover img, img.poster")?.attr("abs:src")
        }
    }

    private fun parseStatus(statusString: String): Int {
        return when {
            statusString.contains("ongoing", true) -> SAnime.ONGOING
            statusString.contains("completed", true) -> SAnime.COMPLETED
            else -> SAnime.UNKNOWN
        }
    }

    // ============================== EPISODES ==============================

    override fun episodeListSelector(): String = "div.episode-list a, ul.episodes li a, div.ep-item a"

    override fun episodeFromElement(element: Element): SEpisode {
        return SEpisode.create().apply {
            val href = element.attr("href")
            url = href
            name = element.text().ifEmpty { "Episode 1" }
            episode_number = 1f
        }
    }

    // ============================== VIDEO RESOLUTION ==============================

    override fun videoListParse(response: Response): List<Video> {
        val document = response.asJsoup()
        val videoList = mutableListOf<Video>()

        val iframe = document.selectFirst("iframe[src]")?.attr("abs:src")
        if (!iframe.isNullOrEmpty()) {
            videoList.add(Video(iframe, "1080p", iframe))
        }

        val videoSource = document.selectFirst("video source[src]")?.attr("abs:src")
        if (!videoSource.isNullOrEmpty()) {
            videoList.add(Video(videoSource, "Direct Stream", videoSource))
        }

        return videoList
    }

    override fun videoFromElement(element: Element): Video = throw UnsupportedOperationException()
    override fun videoUrlParse(document: Document): String = throw UnsupportedOperationException()
    override fun videoListSelector(): String = throw UnsupportedOperationException()

    // ============================== ROBUST FILTERS ==============================

    override fun getFilterList(): AnimeFilterList = AnimeFilterList(
        SortFilter(),
        GenreFilter(),
        StatusFilter(),
        TypeFilter()
    )

    private class GenreFilter : AnimeFilter.Select<String>("Genre", arrayOf(
        "All", "Action", "Adventure", "Comedy", "Drama", "Fantasy", "Horror",
        "Mecha", "Mystery", "Romance", "Sci-Fi", "Slice of Life", "Sports", "Supernatural", "Thriller"
    )) {
        fun toUriPart() = values[state].lowercase().replace(" ", "-")
    }

    private class StatusFilter : AnimeFilter.Select<String>("Status", arrayOf(
        "All", "Ongoing", "Completed", "Upcoming"
    )) {
        fun toUriPart() = values[state].lowercase()
    }

    private class TypeFilter : AnimeFilter.Select<String>("Type", arrayOf(
        "All", "TV", "Movie", "OVA", "ONA", "Special"
    )) {
        fun toUriPart() = values[state].lowercase()
    }

    private class SortFilter : AnimeFilter.Select<String>("Sort By", arrayOf(
        "Popularity", "Latest", "Title A-Z", "Title Z-A", "Release Date"
    )) {
        fun toUriPart() = when (state) {
            0 -> "popular"
            1 -> "latest"
            2 -> "az"
            3 -> "za"
            4 -> "release"
            else -> "popular"
        }
    }

    // ============================== PREFERENCES ==============================

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
