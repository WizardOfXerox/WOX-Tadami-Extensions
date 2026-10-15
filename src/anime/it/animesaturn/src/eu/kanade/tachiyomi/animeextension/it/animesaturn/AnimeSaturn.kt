package eu.kanade.tachiyomi.animeextension.it.animesaturn

import android.util.Base64
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.ParsedAnimeHttpSource
import eu.kanade.tachiyomi.lib.playlistutils.PlaylistUtils
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.addListPreference
import keiyoushi.utils.delegate
import keiyoushi.utils.getPreferencesLazy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Headers
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URLEncoder

class AnimeSaturn :
    ParsedAnimeHttpSource(),
    ConfigurableAnimeSource {

    override val name = "AnimeSaturn"

    override val lang = "it"

    override val supportsLatest = true

    private val preferences by getPreferencesLazy()

    override val baseUrl by preferences.delegate(PREF_DOMAIN, DOMAIN_DEFAULT)

    private val playlistUtils by lazy { PlaylistUtils(client) }

    // ============================== Popular Anime ==============================

    override fun popularAnimeSelector(): String = "a.ac"

    override fun popularAnimeRequest(page: Int): Request = GET("$baseUrl/ongoing/$page")

    private fun formatTitle(title: String): String = title
        .replace("(ITA) ITA", "Dub ITA")
        .replace("(ITA)", "Dub ITA")
        .replace("Sub ITA", "")
        .trim()

    override fun popularAnimeFromElement(element: Element): SAnime {
        val anime = SAnime.create()
        anime.setUrlWithoutDomain(element.attr("href"))
        val titleText = element.selectFirst("h3")?.text() ?: element.text()
        anime.title = formatTitle(titleText)
        val img = element.selectFirst("img")
        anime.thumbnail_url = img?.let {
            it.attr("abs:src").ifEmpty { it.attr("src") }
        }
        return anime
    }

    override fun popularAnimeNextPageSelector(): String = "a.page-num[rel=next]"

    // ============================== Latest Updates =============================

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/newest/$page")

    override fun latestUpdatesSelector(): String = "a.ac"

    override fun latestUpdatesFromElement(element: Element): SAnime = popularAnimeFromElement(element)

    override fun latestUpdatesNextPageSelector(): String = "a.page-num[rel=next]"

    // ============================== Search Anime ===============================

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        val queryParams = mutableListOf<String>()
        if (query.isNotBlank()) {
            queryParams.add("key=" + URLEncoder.encode(query.trim(), "UTF-8"))
        }

        filters.forEach { filter ->
            when (filter) {
                is GenreList -> {
                    filter.state.filter { it.state }.forEach {
                        queryParams.add("categories%5B%5D=${it.id}")
                    }
                }
                is YearList -> {
                    filter.state.filter { it.state }.forEach {
                        queryParams.add("years%5B%5D=${it.id}")
                    }
                }
                is StateList -> {
                    filter.state.filter { it.state }.forEach {
                        queryParams.add("states%5B%5D=${it.id}")
                    }
                }
                is LangList -> {
                    filter.state.filter { it.state }.forEach {
                        queryParams.add("languages%5B%5D=${it.id}")
                    }
                }
                is TypeList -> {
                    filter.state.filter { it.state }.forEach {
                        queryParams.add("types%5B%5D=${it.id}")
                    }
                }
                else -> {}
            }
        }

        queryParams.add("page=$page")
        val queryString = queryParams.joinToString("&")
        return GET("$baseUrl/filter?$queryString")
    }

    override fun searchAnimeSelector(): String = "a.ac"

    override fun searchAnimeFromElement(element: Element): SAnime = popularAnimeFromElement(element)

    override fun searchAnimeNextPageSelector(): String = "a.page-num[rel=next]"

    // ============================== Anime Details ==============================

    override fun animeDetailsParse(document: Document): SAnime {
        val anime = SAnime.create()
        anime.title = formatTitle(document.selectFirst("h1")?.text() ?: "")
        anime.thumbnail_url = document.selectFirst("img[src*=\"locandine\"]")?.attr("abs:src")
            ?: document.selectFirst("img.anime-hero__bg")?.attr("abs:src")

        val descElement = document.selectFirst(".ag-story .story-clip")
            ?: document.selectFirst(".ag-story")
        anime.description = descElement?.ownText()?.ifEmpty { descElement.text() }

        val genres = document.select(".ag-genres a, .ag-genres span")
            .map { it.text().trim() }
            .filter { it.isNotEmpty() }
        anime.genre = genres.joinToString(", ")

        val meta = document.selectFirst(".ag-meta")
        if (meta != null) {
            val studioEl = meta.selectFirst("a[href*=\"studios\"]")
            if (studioEl != null) {
                anime.author = studioEl.text().trim()
            }
            val metaText = meta.text()
            anime.status = parseStatus(metaText)
        } else {
            anime.status = SAnime.UNKNOWN
        }

        return anime
    }

    private fun parseStatus(statusString: String): Int = when {
        statusString.contains("In corso", ignoreCase = true) -> SAnime.ONGOING
        statusString.contains("Finito", ignoreCase = true) -> SAnime.COMPLETED
        else -> SAnime.UNKNOWN
    }

    // ============================== Episode List ===============================

    override fun episodeListSelector(): String = "a.ep-tile"

    override fun episodeListParse(response: Response): List<SEpisode> {
        val document = response.asJsoup()
        return document.select(episodeListSelector()).map { episodeFromElement(it) }.reversed()
    }

    override fun episodeFromElement(element: Element): SEpisode {
        val episode = SEpisode.create()
        val epHref = element.attr("href")
        // Route directly to stream path /anime/<slug>/ep-<num>
        val streamPath = if (epHref.contains("/episode/")) {
            epHref.replace("/episode/", "/anime/")
        } else {
            epHref
        }
        episode.setUrlWithoutDomain(streamPath)

        val epText = element.text().trim()
        episode.name = "Episodio $epText"
        episode.episode_number = epText.toFloatOrNull() ?: 1f
        return episode
    }

    // ============================== Video Resolution ===========================

    override fun videoListRequest(episode: SEpisode): Request = GET(baseUrl + episode.url)

    override fun videoListParse(response: Response): List<Video> {
        val document = response.asJsoup()
        val videoList = mutableListOf<Video>()

        // Follow stream link if landing on an episode detail wrapper
        val streamUrl = if (document.location().contains("/episode/")) {
            document.selectFirst("a[href*=\"/anime/\"][class*=\"btn\"], a.ept-btn--play")?.attr("abs:href")
                ?: document.location().replace("/episode/", "/anime/")
        } else {
            document.location()
        }

        val streamDoc = if (streamUrl != document.location()) {
            client.newCall(GET(streamUrl, headers)).execute().asJsoup()
        } else {
            document
        }

        val html = streamDoc.html()

        // 1. Resolve modern saturncdn embed player
        val embedUrl = streamDoc.selectFirst("iframe[src*=\"saturncdn.net/embed\"]")?.attr("abs:src")
            ?: Regex("""https://play\.saturncdn\.net/embed/\d+\?[^"'\s<>]+""").find(html)?.value
            ?: Regex("""initialVideoUrl["']\s*:\s*["'](https:[^"']+)["']""").find(html)?.groupValues?.get(1)?.replace("\\/", "/")

        if (!embedUrl.isNullOrEmpty()) {
            val fixedEmbedUrl = embedUrl.replace("&amp;", "&")
            val saturnVideos = resolveSaturnVideo(fixedEmbedUrl)
            videoList.addAll(saturnVideos)
        }

        // 2. Fallback to legacy embedded player/jwplayer if present
        if (videoList.isEmpty()) {
            val fallbackVideos = videosFromElement(streamDoc)
            videoList.addAll(fallbackVideos)
        }

        return videoList.sort()
    }

    private fun resolveSaturnVideo(embedUrl: String): List<Video> {
        return try {
            val epId = Regex("""/embed/(\d+)""").find(embedUrl)?.groupValues?.get(1) ?: return emptyList()
            val token = Regex("""token=([^&]+)""").find(embedUrl)?.groupValues?.get(1) ?: return emptyList()
            val expires = Regex("""expires=([^&]+)""").find(embedUrl)?.groupValues?.get(1) ?: return emptyList()

            val playlistUrl = "https://play.saturncdn.net/embed/$epId/playlist?token=$token&expires=$expires"
            val playlistHeaders = headers.newBuilder()
                .set("Referer", embedUrl)
                .build()

            val respStr = client.newCall(GET(playlistUrl, playlistHeaders)).execute().body.string()
            val json = Json.parseToJsonElement(respStr).jsonObject
            val b64Data = json["d"]?.jsonPrimitive?.contentOrNull ?: return emptyList()

            val rawBytes = Base64.decode(b64Data, Base64.DEFAULT)
            val keyBytes = token.toByteArray(Charsets.ISO_8859_1)
            val decryptedBytes = ByteArray(rawBytes.size) { i ->
                (rawBytes[i].toInt() xor keyBytes[i % keyBytes.size].toInt()).toByte()
            }
            val videoSrc = String(decryptedBytes, Charsets.UTF_8).trim()

            val videoHeaders = headers.newBuilder()
                .set("Referer", "https://play.saturncdn.net/")
                .build()

            if (videoSrc.contains(".m3u8")) {
                playlistUtils.extractFromHls(
                    videoSrc,
                    referer = "https://play.saturncdn.net/",
                    masterHeaders = videoHeaders,
                    videoHeaders = videoHeaders,
                    videoNameGen = { "Server Saturn - $it" },
                )
            } else if (videoSrc.isNotEmpty()) {
                listOf(Video(videoSrc, "Server Saturn", videoSrc, videoHeaders))
            } else {
                emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun videosFromElement(document: Document): List<Video> {
        val html = document.html()
        val url = if (html.contains("jwplayer(")) {
            html.substringAfter("file: \"").substringBefore("\"")
        } else {
            document.select("source").attr("src")
        }
        if (url.isEmpty()) return emptyList()

        val referer = document.location()
        return if (url.contains(".m3u8")) {
            val videoHeaders = headers.newBuilder().set("Referer", referer).build()
            playlistUtils.extractFromHls(
                url,
                referer = referer,
                masterHeaders = videoHeaders,
                videoHeaders = videoHeaders,
                videoNameGen = { "Server Fallback - $it" },
            )
        } else {
            listOf(
                Video(
                    url,
                    "Qualità predefinita",
                    url,
                    headers = Headers.headersOf("Referer", referer),
                ),
            )
        }
    }

    override fun videoListSelector() = throw UnsupportedOperationException()

    override fun videoFromElement(element: Element) = throw UnsupportedOperationException()

    override fun videoUrlParse(document: Document) = throw UnsupportedOperationException()

    override fun List<Video>.sort(): List<Video> {
        val quality = preferences.getString(PREF_QUALITY, QUALITY_DEFAULT)!!
        val qualityList = mutableListOf<Video>()
        var preferred = 0
        for (video in this) {
            if (video.quality.contains(quality)) {
                qualityList.add(preferred, video)
                preferred++
            } else {
                qualityList.add(video)
            }
        }
        return qualityList
    }

    // ============================== Filters ====================================

    internal class Genre(val id: String, name: String) : AnimeFilter.CheckBox(name)
    private class GenreList(genres: List<Genre>) : AnimeFilter.Group<Genre>("Generi", genres)
    private fun getGenres() = listOf(
        Genre("3", "Arti Marziali"),
        Genre("5", "Avanguardia"),
        Genre("2", "Avventura"),
        Genre("1", "Azione"),
        Genre("47", "Bambini"),
        Genre("4", "Commedia"),
        Genre("6", "Demoni"),
        Genre("7", "Drammatico"),
        Genre("8", "Ecchi"),
        Genre("9", "Fantasy"),
        Genre("10", "Gioco"),
        Genre("11", "Harem"),
        Genre("43", "Hentai"),
        Genre("13", "Horror"),
        Genre("49", "Isekai"),
        Genre("14", "Josei"),
        Genre("16", "Magia"),
        Genre("18", "Mecha"),
        Genre("19", "Militari"),
        Genre("21", "Mistero"),
        Genre("20", "Musicale"),
        Genre("22", "Parodia"),
        Genre("23", "Polizia"),
        Genre("24", "Psicologico"),
        Genre("46", "Romantico"),
        Genre("26", "Samurai"),
        Genre("28", "Sci-Fi"),
        Genre("27", "Scolastico"),
        Genre("29", "Seinen"),
        Genre("25", "Sentimentale"),
        Genre("30", "Shoujo"),
        Genre("31", "Shoujo Ai"),
        Genre("32", "Shounen"),
        Genre("33", "Shounen Ai"),
        Genre("34", "Slice of Life"),
        Genre("37", "Soprannaturale"),
        Genre("35", "Spazio"),
        Genre("36", "Sport"),
        Genre("12", "Storico"),
        Genre("38", "Superpoteri"),
        Genre("39", "Thriller"),
        Genre("40", "Vampiri"),
        Genre("48", "Veicoli"),
        Genre("41", "Yaoi"),
        Genre("42", "Yuri"),
    )

    internal class Year(val id: String) : AnimeFilter.CheckBox(id)
    private class YearList(years: List<Year>) : AnimeFilter.Group<Year>("Anno", years)
    private fun getYears() = (2026 downTo 1960).map { Year(it.toString()) }

    internal class State(val id: String, name: String) : AnimeFilter.CheckBox(name)
    private class StateList(states: List<State>) : AnimeFilter.Group<State>("Stato", states)
    private fun getStates() = listOf(
        State("0", "In corso"),
        State("1", "Finito"),
        State("2", "Non rilasciato"),
        State("3", "Droppato"),
    )

    internal class Lang(val id: String, name: String) : AnimeFilter.CheckBox(name)
    private class LangList(langs: List<Lang>) : AnimeFilter.Group<Lang>("Lingua", langs)
    private fun getLangs() = listOf(
        Lang("jp", "Giapponese"),
        Lang("it", "Italiano"),
        Lang("en", "Inglese"),
        Lang("kr", "Coreano"),
        Lang("ch", "Cinese"),
    )

    internal class Type(val id: String, name: String) : AnimeFilter.CheckBox(name)
    private class TypeList(types: List<Type>) : AnimeFilter.Group<Type>("Tipo", types)
    private fun getTypes() = listOf(
        Type("1", "TV"),
        Type("2", "Movie"),
        Type("3", "OVA"),
        Type("4", "ONA"),
        Type("5", "Special"),
    )

    override fun getFilterList(): AnimeFilterList = AnimeFilterList(
        AnimeFilter.Header("Filtri di ricerca"),
        GenreList(getGenres()),
        YearList(getYears()),
        StateList(getStates()),
        LangList(getLangs()),
        TypeList(getTypes()),
    )

    // ============================== Preferences ================================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        screen.addListPreference(
            key = PREF_QUALITY,
            title = "Qualità preferita",
            entries = QUALITY_ENTRIES,
            entryValues = QUALITY_VALUES,
            default = QUALITY_DEFAULT,
            summary = "%s",
        )

        screen.addListPreference(
            key = PREF_DOMAIN,
            title = "Dominio in uso",
            entries = DOMAIN_ENTRIES,
            entryValues = DOMAIN_VALUES,
            default = DOMAIN_DEFAULT,
            summary = "%s",
        )
    }

    companion object {
        private const val PREF_QUALITY = "preferred_quality"
        private val QUALITY_VALUES = listOf("1080", "720", "480", "360", "240", "144")
        private val QUALITY_ENTRIES = QUALITY_VALUES.map { "${it}p" }
        private val QUALITY_DEFAULT = QUALITY_VALUES.first()

        private const val PREF_DOMAIN = "preferred_domain"
        private val DOMAIN_ENTRIES = listOf("www.animesaturn.net", "www.animemars.org")
        private val DOMAIN_VALUES = DOMAIN_ENTRIES.map { "https://$it" }
        private val DOMAIN_DEFAULT = DOMAIN_VALUES.first()
    }
}
