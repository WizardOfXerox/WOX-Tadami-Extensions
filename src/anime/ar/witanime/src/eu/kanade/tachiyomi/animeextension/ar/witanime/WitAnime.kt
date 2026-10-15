package eu.kanade.tachiyomi.animeextension.ar.witanime

import android.util.Base64
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.animeextension.ar.witanime.extractors.SharedExtractor
import eu.kanade.tachiyomi.animeextension.ar.witanime.extractors.SoraPlayExtractor
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.ParsedAnimeHttpSource
import eu.kanade.tachiyomi.lib.dailymotionextractor.DailymotionExtractor
import eu.kanade.tachiyomi.lib.doodextractor.DoodExtractor
import eu.kanade.tachiyomi.lib.mp4uploadextractor.Mp4uploadExtractor
import eu.kanade.tachiyomi.lib.okruextractor.OkruExtractor
import eu.kanade.tachiyomi.lib.vidbomextractor.VidBomExtractor
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parallelCatchingFlatMapBlocking
import keiyoushi.utils.useAsJsoup
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class WitAnime :
    ParsedAnimeHttpSource(),
    ConfigurableAnimeSource {

    override val name = "WIT ANIME"

    override val baseUrl = "https://witanime.site"

    override val lang = "ar"

    override val supportsLatest = true

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val preferences by getPreferencesLazy()

    // ============================== Popular ===============================
    override fun popularAnimeSelector() = "div[data-test=browse-grid] > a, div.anime-list-content div.row div.anime-card-poster div.ehover6"

    override fun popularAnimeNextPageSelector() = "a[rel=next], ul.pagination a.next"

    override fun popularAnimeRequest(page: Int) = GET("$baseUrl/browse?page=$page", headers)

    override fun popularAnimeFromElement(element: Element) = SAnime.create().apply {
        val link = if (element.tagName() == "a") {
            element.attr("href")
        } else {
            element.selectFirst("a")?.attr("href")?.takeUnless { it.contains("javascript:") }
                ?: element.selectFirst("a")?.getEncodedUrl().orEmpty()
        }
        setUrlWithoutDomain(link)

        val img = element.selectFirst("img")
        title = element.selectFirst("h3")?.text()?.trim() ?: img?.attr("alt")?.trim() ?: "Anime"
        thumbnail_url = img?.attr("abs:src")?.takeIf { it.isNotBlank() } ?: img?.attr("src")
    }

    // =============================== Latest ===============================
    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/browse?page=$page", headers)

    override fun latestUpdatesSelector() = popularAnimeSelector()
    override fun latestUpdatesNextPageSelector() = popularAnimeNextPageSelector()
    override fun latestUpdatesFromElement(element: Element) = popularAnimeFromElement(element)

    // =============================== Search ===============================
    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList) =
        GET("$baseUrl/browse?search=$query&page=$page", headers)

    override fun searchAnimeFromElement(element: Element) = popularAnimeFromElement(element)
    override fun searchAnimeNextPageSelector() = popularAnimeNextPageSelector()
    override fun searchAnimeSelector() = popularAnimeSelector()

    // =========================== Anime Details ============================
    override fun animeDetailsParse(document: Document) = SAnime.create().apply {
        val doc = getRealDoc(document)

        title = doc.selectFirst("h1")?.text()?.trim() ?: "Anime"
        thumbnail_url = doc.selectFirst("img[src*=/posters/], img[src*=/banners/]")?.attr("abs:src")
            ?: doc.selectFirst("img.thumbnail, img")?.attr("abs:src")

        genre = doc.select("ul.anime-genres > li > a, div.anime-info > a").eachText().joinToString()

        description = buildString {
            doc.selectFirst("p.leading-relaxed, p[class*=leading-relaxed], p.anime-story")?.text()?.also {
                append(it.trim())
            }
            val extraInfo = doc.select("div.anime-info").eachText()
            if (extraInfo.isNotEmpty()) {
                append("\n\n")
                extraInfo.forEach { append("$it\n") }
            }
        }.trim()

        val statusText = doc.select("div.anime-info:contains(حالة الأنمي), div:contains(حالة الأنمي)").text()
        status = when {
            statusText.contains("يعرض الان", true) || statusText.contains("مستمر", true) -> SAnime.ONGOING
            statusText.contains("مكتمل", true) -> SAnime.COMPLETED
            else -> SAnime.UNKNOWN
        }
    }

    // ============================== Episodes ==============================
    override fun episodeListParse(response: Response): List<SEpisode> {
        val document = getRealDoc(response.asJsoup())
        val epElements = document.select(episodeListSelector())
        if (epElements.isEmpty()) return emptyList()

        return epElements
            .distinctBy { if (it.tagName() == "a") it.attr("href") else it.selectFirst("a")?.attr("href") ?: it.text() }
            .map(::episodeFromElement)
            .reversed()
    }

    override fun episodeListSelector() = "a[href*=/watch/], div.ehover6 > div.episodes-card-title > h3 a"

    override fun episodeFromElement(element: Element) = SEpisode.create().apply {
        val link = if (element.tagName() == "a") {
            element.attr("href")
        } else {
            element.selectFirst("a")?.attr("href") ?: element.getEncodedUrl()
        }
        setUrlWithoutDomain(link)
        val epNumStr = link.trimEnd('/').substringAfterLast('/')
        episode_number = epNumStr.toFloatOrNull() ?: 0F
        val titleText = element.text().trim()
        name = if (titleText.isNotBlank()) titleText else "الحلقة $epNumStr"
    }

    // ============================ Video Links =============================
    override fun videoListParse(response: Response): List<Video> {
        val document = response.asJsoup()
        val html = document.html()
        val pageUrl = response.request.url.toString()

        // Legacy server support
        val legacyServers = document.select("ul#episode-servers li a")
        if (legacyServers.isNotEmpty()) {
            return legacyServers.distinctBy { it.text().substringBefore(" -") }
                .parallelCatchingFlatMapBlocking {
                    val url = it.attr("data-url")
                        .takeUnless(String::isBlank)
                        ?.let { enc -> String(Base64.decode(enc, Base64.DEFAULT)) }
                        ?: it.getEncodedUrl()

                    extractVideos(url)
                }
        }

        // New witanime.site player API
        val csrfToken = document.selectFirst("meta[name=csrf-token]")?.attr("content").orEmpty()
        val sourcesMatch = Regex("""sourcesUrl:\s*['"]([^'"]+)['"]""").find(html)
        val sourcesPath = sourcesMatch?.groupValues?.get(1)?.replace("\\/", "/") ?: "/watch/${pageUrl.substringAfter("/watch/")}/sources"
        val sourcesUrl = if (sourcesPath.startsWith("http")) sourcesPath else "$baseUrl$sourcesPath"

        val postHeaders = headers.newBuilder()
            .set("Accept", "application/json")
            .set("X-CSRF-TOKEN", csrfToken)
            .set("X-Requested-With", "XMLHttpRequest")
            .set("Referer", pageUrl)
            .build()

        val sourcesReq = Request.Builder()
            .url(sourcesUrl)
            .post("".toRequestBody("application/json".toMediaTypeOrNull()))
            .headers(postHeaders)
            .build()

        val sourcesResp = runCatching { client.newCall(sourcesReq).execute() }.getOrNull() ?: return emptyList()
        val sourcesJsonStr = sourcesResp.body.string()
        val jsonElement = runCatching { json.parseToJsonElement(sourcesJsonStr) }.getOrNull()?.jsonObject ?: return emptyList()
        val playersObj = jsonElement["players"]?.jsonObject ?: return emptyList()

        val videoList = mutableListOf<Video>()

        playersObj.entries.forEach { (quality, serverListJson) ->
            val serverList = serverListJson.jsonArray
            for (srvElem in serverList) {
                val srvObj = srvElem.jsonObject
                val token = srvObj["token"]?.jsonPrimitive?.content ?: continue
                val label = srvObj["label"]?.jsonPrimitive?.content ?: "Server"

                // 1. POST to stream-source
                val streamSourceUrl = "$baseUrl/watch/stream-source/$token"
                val sourceReq = Request.Builder()
                    .url(streamSourceUrl)
                    .post("".toRequestBody("application/json".toMediaTypeOrNull()))
                    .headers(postHeaders)
                    .build()
                runCatching { client.newCall(sourceReq).execute().close() }

                // 2. GET stream-gate with no referer to obtain destination embed / player URL
                val gateHeaders = headers.newBuilder()
                    .removeAll("Referer")
                    .add("Sec-Fetch-Dest", "iframe")
                    .add("Sec-Fetch-Mode", "navigate")
                    .add("Sec-Fetch-Site", "same-origin")
                    .build()
                val gateReq = Request.Builder()
                    .url("$baseUrl/watch/stream-gate/$token")
                    .headers(gateHeaders)
                    .build()

                runCatching {
                    val gateResp = client.newCall(gateReq).execute()
                    val embedUrl = gateResp.request.url.toString()
                    val gateDoc = gateResp.asJsoup()

                    val extracted = extractVideos(embedUrl, quality)
                    if (extracted.isNotEmpty()) {
                        videoList.addAll(extracted)
                    } else {
                        val videoSrc = gateDoc.selectFirst("video source")?.attr("abs:src")
                        if (!videoSrc.isNullOrBlank()) {
                            videoList.add(Video(videoSrc, "$label ($quality)", videoSrc))
                        } else if (!embedUrl.contains("/stream-gate/")) {
                            videoList.add(Video(embedUrl, "$label ($quality)", embedUrl))
                        }
                    }
                    Unit
                }
            }
        }

        return videoList
    }

    private val soraPlayExtractor by lazy { SoraPlayExtractor(client) }
    private val doodExtractor by lazy { DoodExtractor(client) }
    private val sharedExtractor by lazy { SharedExtractor(client) }
    private val dailymotionExtractor by lazy { DailymotionExtractor(client, headers) }
    private val okruExtractor by lazy { OkruExtractor(client) }
    private val mp4uploadExtractor by lazy { Mp4uploadExtractor(client) }
    private val vidBomExtractor by lazy { VidBomExtractor(client) }

    private fun extractVideos(url: String, quality: String = ""): List<Video> = runCatching {
        when {
            url.contains("yonaplay") -> extractFromMulti(url)

            url.contains("soraplay") -> {
                if (url.contains("/mirror")) extractFromMulti(url)
                else soraPlayExtractor.videosFromUrl(url, headers)
            }

            url.contains("dood") -> doodExtractor.videoFromUrl(url, "Dood $quality")?.let(::listOf)

            url.contains("4shared") -> sharedExtractor.videosFromUrl(url, quality)?.let(::listOf)

            url.contains("ok.ru") -> okruExtractor.videosFromUrl(url)

            url.contains("mp4upload.com") -> mp4uploadExtractor.videosFromUrl(url, headers)

            url.contains("dailymotion") -> dailymotionExtractor.videosFromUrl(url)

            VIDBOM_REGEX.containsMatchIn(url) -> vidBomExtractor.videosFromUrl(url)

            url.contains("mega.nz") -> listOf(Video(url, "Mega ($quality)", url))

            url.contains("videa.hu") -> listOf(Video(url, "Videa ($quality)", url))

            url.contains("dropbox") -> listOf(Video(url, "Dropbox ($quality)", url))

            else -> emptyList()
        }
    }.getOrNull() ?: emptyList()

    private fun extractFromMulti(url: String): List<Video> {
        val newHeaders = when {
            url.contains("soraplay") ->
                super.headersBuilder().set("referer", "https://yonaplay.org").build()

            else -> headers
        }
        val doc = client.newCall(GET(url, newHeaders)).execute().useAsJsoup()
        return doc.select(".OD li").parallelCatchingFlatMapBlocking { element ->
            val videoUrl = element.attr("onclick").substringAfter("go_to_player('")
                .substringBefore("')")
                .let {
                    if (it.startsWith("https:")) it else "https:$it"
                }

            extractVideos(videoUrl)
        }
    }

    override fun videoListSelector() = throw UnsupportedOperationException()
    override fun videoFromElement(element: Element) = throw UnsupportedOperationException()
    override fun videoUrlParse(document: Document) = throw UnsupportedOperationException()

    override fun List<Video>.sort(): List<Video> {
        val quality = preferences.getString(PREF_QUALITY_KEY, PREF_QUALITY_DEFAULT)!!
        return sortedWith(
            compareBy { it.quality.contains(quality) },
        ).reversed()
    }

    // ============================== Settings ==============================
    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = PREF_QUALITY_KEY
            title = PREF_QUALITY_TITLE
            entries = PREF_QUALITY_ENTRIES
            entryValues = PREF_QUALITY_VALUES
            setDefaultValue(PREF_QUALITY_DEFAULT)
            summary = "%s"

            setOnPreferenceChangeListener { _, newValue ->
                val selected = newValue as String
                val index = findIndexOfValue(selected)
                val entry = entryValues[index] as String
                preferences.edit().putString(key, entry).commit()
            }
        }.also(screen::addPreference)
    }

    // ============================= Utilities ==============================
    private fun getRealDoc(document: Document): Document = document.selectFirst("div.anime-page-link a")?.let {
        client.newCall(GET(it.attr("href"), headers)).execute().asJsoup()
    } ?: document

    private fun Element.getEncodedUrl(): String = runCatching {
        attr("onclick")
            .substringAfter("'")
            .substringBefore("'")
            .let { String(Base64.decode(it, Base64.DEFAULT)) }
    }.getOrDefault("")

    companion object {
        private val VIDBOM_REGEX by lazy { Regex("//v[aie]d[bp][aoe]?m") }

        private const val PREF_QUALITY_KEY = "preferred_quality"
        private const val PREF_QUALITY_TITLE = "Preferred quality"
        private const val PREF_QUALITY_DEFAULT = "1080"
        private val PREF_QUALITY_ENTRIES = arrayOf("1080p", "720p", "480p", "380p", "360p", "240p")
        private val PREF_QUALITY_VALUES by lazy {
            PREF_QUALITY_ENTRIES.map { it.substringBefore("p") }.toTypedArray()
        }
    }
}
