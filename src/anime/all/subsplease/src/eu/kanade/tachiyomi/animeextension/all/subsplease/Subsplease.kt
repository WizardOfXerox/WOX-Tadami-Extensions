package eu.kanade.tachiyomi.animeextension.all.subsplease

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale

class Subsplease :
    AnimeHttpSource(),
    ConfigurableAnimeSource {

    override val name = "Subsplease"

    override val baseUrl = "https://subsplease.org"

    override val lang = "all"

    override val supportsLatest = false

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_\$id", 0)
    }

    private val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
    }

    val supportsRelatedAnimes = false

    override fun popularAnimeRequest(page: Int): Request = GET("$baseUrl/api/?f=schedule&tz=Europe/Berlin")

    override fun popularAnimeParse(response: Response): AnimesPage {
        val responseString = response.body.string()
        return parsePopularAnimeJson(responseString)
    }

    private fun parsePopularAnimeJson(jsonLine: String?): AnimesPage {
        val jsonData = jsonLine ?: return AnimesPage(emptyList(), false)
        val jObject = json.decodeFromString<JsonObject>(jsonData)
        val jOe = jObject.jsonObject["schedule"]?.jsonObject?.entries
        val animeList = jOe?.flatMap {
            it.value.jsonArray.mapNotNull { item ->
                val title = item.jsonObject["title"]?.jsonPrimitive?.content
                val url = item.jsonObject["page"]?.jsonPrimitive?.content
                if (title == null || url == null) return@mapNotNull null

                SAnime.create().apply {
                    this.title = title
                    setUrlWithoutDomain("$baseUrl/shows/$url")
                    item.jsonObject["image_url"]?.jsonPrimitive?.content?.let {
                        thumbnail_url = "$baseUrl$it"
                    }
                }
            }
        } ?: emptyList()
        return AnimesPage(animeList, hasNextPage = false)
    }

    // episodes

    override fun episodeListParse(response: Response): List<SEpisode> {
        val document = response.asJsoup()
        val sId = document.select("#show-release-table").attr("sid")
        val url = "$baseUrl/api/?f=show&tz=Europe/Berlin&sid=$sId"
        val responseString = client.newCall(GET(url))
            .execute().use { it.body.string() }
        return parseEpisodeAnimeJson(responseString, url)
    }

    private fun parseEpisodeAnimeJson(jsonLine: String?, url: String): List<SEpisode> {
        val jsonData = jsonLine ?: return emptyList()
        val jObject = json.decodeFromString<JsonObject>(jsonData)
        val episodeList = mutableListOf<SEpisode>()
        val epE = jObject["episode"]?.jsonObject?.entries
        epE?.forEach {
            val itJ = it.value.jsonObject
            val episode = SEpisode.create()
            val num = itJ["episode"]?.jsonPrimitive?.content ?: return@forEach
            val ep = num.takeWhile { it.isDigit() || it == '.' }.toFloatOrNull()
            if (ep == null) {
                if (episodeList.size > 0) {
                    episode.episode_number = episodeList.last().episode_number - 0.5F
                } else {
                    episode.episode_number = 0F
                }
            } else {
                episode.episode_number = ep
            }
            episode.name = "Episode $num"
            episode.date_upload = itJ["release_date"]?.jsonPrimitive?.content.toDate()
            episode.setUrlWithoutDomain("$url&num=$num")
            episodeList.add(episode)
        }
        return episodeList
    }

    private fun String?.toDate(): Long = this?.let {
        runCatching { dateTimeFormat.parse(it.trim())?.time }.getOrNull()
    } ?: 0L

    private val dateTimeFormat by lazy { SimpleDateFormat("E, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH) }

    // Video Extractor

    override fun videoListParse(response: Response): List<Video> {
        val responseString = response.body.string()
        val num = response.request.url.toString()
            .substringAfter("num=")
        return videosFromElement(responseString, num)
    }

    private fun decodeBase32(b32: String): String {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        var bits = 0
        var valBuf = 0
        val bytes = mutableListOf<Byte>()
        for (c in b32.uppercase(Locale.ROOT)) {
            val idx = alphabet.indexOf(c)
            if (idx == -1) continue
            valBuf = (valBuf shl 5) or idx
            bits += 5
            if (bits >= 8) {
                bytes.add(((valBuf shr (bits - 8)) and 0xFF).toByte())
                bits -= 8
            }
        }
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun debrid(magnet: String): String {
        val regex = Regex("xt=urn:btih:([A-Fa-f0-9]{40}|[A-Za-z0-9]{32})|dn=([^&]+)")
        var infohash = ""
        var title = ""
        regex.findAll(magnet).forEach { match ->
            match.groups[1]?.value?.let {
                infohash = if (it.length == 32) decodeBase32(it) else it
            }
            match.groups[2]?.value?.let { title = it }
        }
        val token = preferences.getString(PREF_TOKEN_KEY, PREF_TOKEN_DEFAULT)!!
        val debridProvider = preferences.getString(PREF_DEBRID_KEY, PREF_DEBRID_DEFAULT)!!
        return "https://torrentio.strem.fun/resolve/$debridProvider/$token/$infohash/null/0/$title"
    }

    private fun videosFromElement(jsonLine: String?, num: String): List<Video> {
        val jsonData = jsonLine ?: return emptyList()
        val jObject = json.decodeFromString<JsonObject>(jsonData)
        val epE = jObject["episode"]?.jsonObject?.entries
        return epE?.mapNotNull {
            val itJ = it.value.jsonObject
            val epN = itJ["episode"]?.jsonPrimitive?.content
            if (num != epN) return@mapNotNull null

            itJ["downloads"]?.jsonArray?.flatMap inner@{ item ->
                val quality = item.jsonObject["res"]?.jsonPrimitive?.content?.plus("p") ?: "Unknown"
                val magnetUrl = item.jsonObject["magnet"]?.jsonPrimitive?.content
                val torrentUrl = item.jsonObject["torrent"]?.jsonPrimitive?.content
                val resultList = mutableListOf<Video>()

                if (!magnetUrl.isNullOrBlank()) {
                    when (preferences.debridProvider) {
                        PREF_DEBRID_DEFAULT -> {
                            resultList.add(Video(magnetUrl, "$quality (Magnet)", magnetUrl))
                            if (!torrentUrl.isNullOrBlank()) {
                                resultList.add(Video(torrentUrl, "$quality (Torrent File)", torrentUrl))
                            }
                        }
                        else -> {
                            val debridUrl = debrid(magnetUrl)
                            resultList.add(Video(debridUrl, "$quality (${preferences.debridProvider.uppercase()})", debridUrl))
                            resultList.add(Video(magnetUrl, "$quality (Direct Magnet)", magnetUrl))
                        }
                    }
                } else if (!torrentUrl.isNullOrBlank()) {
                    resultList.add(Video(torrentUrl, "$quality (Torrent File)", torrentUrl))
                }

                resultList
            }
        }?.flatten() ?: emptyList()
    }

    override fun List<Video>.sort(): List<Video> {
        val quality = preferences.quality
        return this.sortedByDescending { it.quality.contains(quality) }
    }

    // Search

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request = GET("$baseUrl/api/?f=search&tz=Europe/Berlin&s=$query")

    override fun searchAnimeParse(response: Response): AnimesPage {
        val responseString = response.body.string()
        return parseSearchAnimeJson(responseString)
    }

    private fun parseSearchAnimeJson(jsonLine: String?): AnimesPage {
        val jsonData = jsonLine ?: return AnimesPage(emptyList(), false)
        val jObject = json.decodeFromString<JsonObject>(jsonData)
        val jE = jObject.entries
        val animeList = jE.mapNotNull {
            val itJ = it.value.jsonObject
            val title = itJ.jsonObject["show"]?.jsonPrimitive?.content
            val page = itJ.jsonObject["page"]?.jsonPrimitive?.content
            if (title == null || page == null) return@mapNotNull null

            SAnime.create().apply {
                this.title = title
                setUrlWithoutDomain("$baseUrl/shows/$page")
                itJ.jsonObject["image_url"]?.jsonPrimitive?.content?.let {
                    thumbnail_url = "$baseUrl$it"
                }
            }
        }
        return AnimesPage(animeList, hasNextPage = false)
    }

    // Details

    override fun animeDetailsParse(response: Response): SAnime {
        val document = response.asJsoup()
        val anime = SAnime.create()
        anime.description = document.select("div.series-syn p ").text()
        return anime
    }

    // Latest

    override fun latestUpdatesParse(response: Response): AnimesPage = throw UnsupportedOperationException("Not used")

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException("Not used")

    // Preferences

    private var token: String
        get() = preferences.getString(PREF_TOKEN_KEY, PREF_TOKEN_DEFAULT)!!
        set(value) = preferences.edit().putString(PREF_TOKEN_KEY, value).apply()

    private val SharedPreferences.debridProvider
        get() = getString(PREF_DEBRID_KEY, PREF_DEBRID_DEFAULT)!!

    private val SharedPreferences.quality
        get() = getString(PREF_QUALITY_KEY, PREF_QUALITY_DEFAULT)!!

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = PREF_QUALITY_KEY
            title = "Default-Quality"
            entries = PREF_QUALITY_ENTRIES.toTypedArray()
            entryValues = PREF_QUALITY_VALUES.toTypedArray()
            setDefaultValue(PREF_QUALITY_DEFAULT)
            summary = "%s"
            setOnPreferenceChangeListener { _, newValue ->
                preferences.edit().putString(PREF_QUALITY_KEY, newValue as String).commit()
            }
        }.also(screen::addPreference)

        ListPreference(screen.context).apply {
            key = PREF_DEBRID_KEY
            title = "Debrid Provider"
            entries = PREF_DEBRID_ENTRIES.toTypedArray()
            entryValues = PREF_DEBRID_VALUES.toTypedArray()
            setDefaultValue(PREF_DEBRID_DEFAULT)
            summary = "%s"
            setOnPreferenceChangeListener { _, newValue ->
                preferences.edit().putString(PREF_DEBRID_KEY, newValue as String).commit()
            }
        }.also(screen::addPreference)

        EditTextPreference(screen.context).apply {
            key = PREF_TOKEN_KEY
            title = "Token"
            setDefaultValue(PREF_TOKEN_DEFAULT)
            summary = PREF_TOKEN_SUMMARY
            setOnPreferenceChangeListener { _, newValue ->
                val value = (newValue as String).trim().ifBlank { PREF_TOKEN_DEFAULT }
                token = value
                true
            }
        }.also(screen::addPreference)
    }

    companion object {
        private const val PREF_QUALITY_KEY = "preferred_quality"
        private val PREF_QUALITY_ENTRIES = listOf("1080p", "720p", "480p")
        private val PREF_QUALITY_VALUES = listOf("1080", "720", "480")
        private val PREF_QUALITY_DEFAULT = PREF_QUALITY_VALUES.first()

        // Token
        private const val PREF_TOKEN_KEY = "token"
        private const val PREF_TOKEN_DEFAULT = ""
        private const val PREF_TOKEN_SUMMARY = "Debrid API Token"

        // Debrid
        private const val PREF_DEBRID_KEY = "debrid_provider"
        private val PREF_DEBRID_ENTRIES = listOf(
            "None",
            "RealDebrid",
            "Premiumize",
            "AllDebrid",
            "DebridLink",
            "Offcloud",
            "TorBox",
        )
        private val PREF_DEBRID_VALUES = listOf(
            "none",
            "realdebrid",
            "premiumize",
            "alldebrid",
            "debridlink",
            "offcloud",
            "torbox",
        )
        private val PREF_DEBRID_DEFAULT = PREF_DEBRID_VALUES.first()
    }
}
