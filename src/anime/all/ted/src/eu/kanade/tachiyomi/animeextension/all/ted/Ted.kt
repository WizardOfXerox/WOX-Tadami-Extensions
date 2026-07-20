package eu.kanade.tachiyomi.animeextension.all.ted

import android.app.Application
import android.content.SharedPreferences
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
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy

class Ted : ParsedAnimeHttpSource(), ConfigurableAnimeSource {

    override val name = "Ted"

    override val baseUrl = "https://www.ted.com"

    override val lang = "all"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    private val json: Json by injectLazy()

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    // ============================== Popular ==============================

    override fun popularAnimeSelector(): String = "div div div.talk-link, div.talk-link, div[data-ga-context=talks]"

    override fun popularAnimeRequest(page: Int): Request = GET("$baseUrl/talks?page=$page&sort=relevance", headers)

    override fun popularAnimeFromElement(element: Element): SAnime {
        return SAnime.create().apply {
            val linkEl = element.selectFirst("div div a, a[href*=/talks/]") ?: element.selectFirst("a")
            val href = linkEl?.attr("href") ?: ""
            if (href.isNotBlank()) {
                setUrlWithoutDomain(href)
            } else {
                setUrlWithoutDomain("/talks")
            }

            val titleText = element.selectFirst("h4 a.ga-link, h4 a, a.ga-link")?.text()?.takeIf { it.isNotBlank() }
                ?: linkEl?.text()?.takeIf { it.isNotBlank() }
                ?: "TED Talk"
            title = titleText.trim()

            val img = element.selectFirst("img")
            thumbnail_url = img?.attr("abs:src")?.takeIf { it.isNotBlank() }
                ?: img?.attr("src")
                ?: ""
        }
    }

    override fun popularAnimeNextPageSelector(): String = "a[rel=next], ul.pagination li.next a"

    // ============================== Latest ==============================

    override fun latestUpdatesSelector(): String = popularAnimeSelector()

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/talks?page=$page&sort=newest", headers)

    override fun latestUpdatesFromElement(element: Element): SAnime = popularAnimeFromElement(element)

    override fun latestUpdatesNextPageSelector(): String = popularAnimeNextPageSelector()

    // ============================== Search ==============================

    override fun searchAnimeSelector(): String = popularAnimeSelector()

    override fun searchAnimeFromElement(element: Element): SAnime = popularAnimeFromElement(element)

    override fun searchAnimeNextPageSelector(): String = popularAnimeNextPageSelector()

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        val url = "$baseUrl/talks".toHttpUrlOrNull()!!.newBuilder()
        if (page > 1) {
            url.addQueryParameter("page", page.toString())
        }

        for (filter in filters) {
            when (filter) {
                is LanguageFilter -> {
                    if (filter.toUriPart().isNotEmpty()) {
                        url.addQueryParameter("language", filter.toUriPart())
                    }
                }
                is SortFilter -> {
                    if (filter.toUriPart().isNotEmpty()) {
                        url.addQueryParameter("sort", filter.toUriPart())
                    }
                }
                is DurationFilter -> {
                    if (filter.toUriPart().isNotEmpty()) {
                        url.addQueryParameter("duration", filter.toUriPart())
                    }
                }
                else -> {}
            }
        }
        if (query.isNotBlank()) {
            url.addQueryParameter("q", query)
        }
        return GET(url.build().toString(), headers)
    }

    // ============================== Details ==============================

    override fun animeDetailsParse(document: Document): SAnime {
        val docStr = document.select("script[type=application/json]").toString()
        return SAnime.create().apply {
            status = SAnime.COMPLETED
            initialized = true

            title = docStr.substringAfter("\"title\":\"", "").substringBefore("\",\"", "").takeIf { it.isNotBlank() }
                ?: document.selectFirst("h1, meta[property=og:title]")?.attr("content")
                ?: document.title()

            author = docStr.substringAfter("\"presenterDisplayName\":\"", "").substringBefore("\",\"", "").takeIf { it.isNotBlank() }
                ?: document.selectFirst("meta[name=author]")?.attr("content")
                ?: "TED Speaker"

            description = docStr.substringAfter("\"socialDescription\":\"", "").substringBefore("\",\"", "").takeIf { it.isNotBlank() }
                ?: document.selectFirst("meta[name=description]")?.attr("content")
                ?: ""

            thumbnail_url = document.selectFirst("meta[property=og:image]")?.attr("content") ?: ""
        }
    }

    // ============================== Episodes ==============================

    override fun episodeListParse(response: Response): List<SEpisode> {
        val document = response.asJsoup().select("script[type=application/json]").toString()
        val uploadDate = try {
            val pub = document.substringAfter("\\\"published\\\":", "").substringBefore("}", "").substringBefore(",").trim()
            pub.toLong() * 1000
        } catch (_: Exception) {
            0L
        }

        return listOf(
            SEpisode.create().apply {
                setUrlWithoutDomain(response.request.url.toString())
                name = "TED Talk Video"
                episode_number = 1F
                date_upload = uploadDate
            }
        )
    }

    override fun episodeListSelector(): String = throw UnsupportedOperationException()

    override fun episodeFromElement(element: Element): SEpisode = throw UnsupportedOperationException()

    // ============================== Video List ==============================

    override fun videoListParse(response: Response): List<Video> {
        val bodyStr = response.asJsoup().select("script[type=application/json]").toString()
        val videoUrl = bodyStr.substringAfter(",\\\"file\\\":\\\"", "").substringBefore("\\\"}],", "")

        if (videoUrl.isBlank()) {
            throw Exception("Failed to extract video stream URL for TED Talk")
        }

        val subtitleList = mutableListOf<Track>()
        try {
            val trackJsonUrl = bodyStr.substringAfter("\\\",\\\"metadata\\\":\\\"", "").substringBefore("\\\"}}", "")
            if (trackJsonUrl.isNotBlank()) {
                val trackResponse = client.newCall(GET(trackJsonUrl, headers)).execute()
                if (trackResponse.isSuccessful) {
                    val jsonStr = trackResponse.body.string()
                    val jsonObj = json.decodeFromString<JsonObject>(jsonStr)
                    jsonObj["subtitles"]?.jsonArray?.forEach { item ->
                        val subUrl = item.jsonObject["webvtt"]?.jsonPrimitive?.content ?: ""
                        val subLang = item.jsonObject["name"]?.jsonPrimitive?.content ?: "Subtitle"
                        if (subUrl.isNotBlank()) {
                            subtitleList.add(Track(subUrl, subLang))
                        }
                    }
                }
                trackResponse.close()
            }
        } catch (_: Exception) {}

        val sortedSubs = sortSubtitles(subtitleList)
        return listOf(
            Video(videoUrl, "TED Quality Video", videoUrl, subtitleTracks = sortedSubs)
        )
    }

    private fun sortSubtitles(tracks: List<Track>): List<Track> {
        val prefLang = preferences.getString(PREF_SUB_KEY, "English") ?: "English"
        return tracks.sortedWith(compareBy { it.lang != prefLang })
    }

    override fun videoListSelector(): String = throw UnsupportedOperationException()

    override fun videoFromElement(element: Element): Video = throw UnsupportedOperationException()

    override fun videoUrlParse(document: Document): String = throw UnsupportedOperationException()

    // ============================== Preferences & Filters ==============================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = PREF_SUB_KEY
            title = "Preferred Subtitle Language"
            entries = arrayOf("English", "Spanish", "French", "German", "Chinese, Simplified", "Japanese")
            entryValues = entries
            setDefaultValue("English")
            summary = "%s"
            setOnPreferenceChangeListener { _, newValue ->
                val selected = newValue as String
                preferences.edit().putString(key, selected).commit()
            }
        }.also(screen::addPreference)
    }

    override fun getFilterList(): AnimeFilterList = AnimeFilterList(
        LanguageFilter(),
        DurationFilter(),
        SortFilter()
    )

    private abstract class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) :
        AnimeFilter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }

    private class LanguageFilter : UriPartFilter(
        "Language",
        arrayOf(
            Pair("Any", ""),
            Pair("English", "en"),
            Pair("Spanish", "es"),
            Pair("French", "fr"),
            Pair("German", "de"),
            Pair("Japanese", "ja"),
            Pair("Chinese, Simplified", "zh-cn"),
            Pair("Korean", "ko"),
            Pair("Russian", "ru")
        )
    )

    private class DurationFilter : UriPartFilter(
        "Duration",
        arrayOf(
            Pair("Any", ""),
            Pair("0–6 minutes", "0-6"),
            Pair("6–12 minutes", "6-12"),
            Pair("12–18 minutes", "12-18"),
            Pair("18+ minutes", "18%2B")
        )
    )

    private class SortFilter : UriPartFilter(
        "Sort by",
        arrayOf(
            Pair("Relevance", "relevance"),
            Pair("Newest", "newest"),
            Pair("Oldest", "oldest"),
            Pair("Most Viewed", "popular")
        )
    )

    companion object {
        private const val PREF_SUB_KEY = "pref_ted_sub_lang"
    }
}
