package eu.kanade.tachiyomi.animeextension.id.kuronime

import android.util.Base64
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.ParsedAnimeHttpSource
import eu.kanade.tachiyomi.lib.cryptoaes.CryptoAES
import eu.kanade.tachiyomi.lib.doodextractor.DoodExtractor
import eu.kanade.tachiyomi.lib.mp4uploadextractor.Mp4uploadExtractor
import eu.kanade.tachiyomi.lib.vidhideextractor.VidHideExtractor
import eu.kanade.tachiyomi.lib.youruploadextractor.YourUploadExtractor
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.getPreferencesLazy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class Kuronime :
    ParsedAnimeHttpSource(),
    ConfigurableAnimeSource {
    override val baseUrl: String = "https://kuronime.sbs"
    override val lang: String = "id"
    override val name: String = "Kuronime"
    override val supportsLatest: Boolean = true

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    private val preferences by getPreferencesLazy()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val doodExtractor by lazy { DoodExtractor(client) }
    private val mp4uploadExtractor by lazy { Mp4uploadExtractor(client) }
    private val vidhideExtractor by lazy { VidHideExtractor(client, headers) }
    private val youruploadExtractor by lazy { YourUploadExtractor(client) }

    // =========================== Popular Anime ============================
    override fun popularAnimeRequest(page: Int): Request {
        val url = if (page == 1) "$baseUrl/anime/" else "$baseUrl/anime/page/$page/"
        return GET(url, headers)
    }

    override fun popularAnimeSelector(): String = "div.listupd article.bs, div.listupd div.bsx, div.listupd article"

    override fun popularAnimeFromElement(element: Element): SAnime = SAnime.create().apply {
        val link = element.selectFirst("a")!!
        setUrlWithoutDomain(link.attr("href"))
        val titleElement = element.selectFirst("div.tt > h2, div.tt > h4, a[title]")
        title = titleElement?.text()?.trim()?.ifEmpty { link.attr("title").trim() } ?: link.attr("title").trim()
        val img = element.selectFirst("img")
        thumbnail_url = img?.attr("src")?.takeIf { it.startsWith("http") && !it.contains("data:image") }
            ?: img?.attr("data-lazy-src")?.takeIf { it.startsWith("http") }
            ?: img?.attr("data-src")?.takeIf { it.startsWith("http") }
            ?: ""
    }

    override fun popularAnimeNextPageSelector(): String = "div.pagination > a.next, a.next.page-numbers"

    // =========================== Latest Updates ===========================
    override fun latestUpdatesRequest(page: Int): Request {
        val url = if (page == 1) "$baseUrl/" else "$baseUrl/page/$page/"
        return GET(url, headers)
    }

    override fun latestUpdatesSelector(): String = popularAnimeSelector()

    override fun latestUpdatesFromElement(element: Element): SAnime = popularAnimeFromElement(element)

    override fun latestUpdatesNextPageSelector(): String = popularAnimeNextPageSelector()

    // =============================== Search ===============================
    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        val url = if (page == 1) "$baseUrl/?s=$query" else "$baseUrl/page/$page/?s=$query"
        return GET(url, headers)
    }

    override fun searchAnimeSelector(): String = popularAnimeSelector()

    override fun searchAnimeFromElement(element: Element): SAnime = popularAnimeFromElement(element)

    override fun searchAnimeNextPageSelector(): String = popularAnimeNextPageSelector()

    // ============================ Anime Details ===========================
    override fun animeDetailsParse(document: Document): SAnime = SAnime.create().apply {
        title = document.select("h1.entry-title, div.entry-title, h1").text().trim()
        thumbnail_url = document.select("div.thumb img, div.bigcontent img, div.infodetail img").attr("abs:src")
        description = document.select("div.entry-content, div.main-info, div.con, div.synp div.entry-content").text().trim()
        genre = document.select("div.genre-info a, div.infodetail ul li:nth-child(2) a, div.genxed a").joinToString(", ") { it.text().trim() }
        status = parseStatus(document.select("div.infodetail ul li:contains(Status), div.spe span:contains(Status)").text())
        initialized = true
    }

    private fun parseStatus(statusString: String): Int = when {
        statusString.contains("completed", ignoreCase = true) -> SAnime.COMPLETED
        statusString.contains("ongoing", ignoreCase = true) -> SAnime.ONGOING
        else -> SAnime.UNKNOWN
    }

    // ============================= Episode List ===========================
    override fun episodeListSelector(): String = "div.bixbox.bxcl ul li, div.eplister ul li, ul.episodelst li"

    override fun episodeFromElement(element: Element): SEpisode = SEpisode.create().apply {
        val link = element.selectFirst("a")!!
        setUrlWithoutDomain(link.attr("href"))
        val titleText = element.select("span.lchx, a").text().trim()
        name = titleText
        val epsNum = titleText.filter { it.isDigit() || it == '.' }
        episode_number = epsNum.toFloatOrNull() ?: 1F
    }

    // ============================= Video Links ============================
    override fun videoListParse(response: Response): List<Video> {
        val document = response.asJsoup()
        val videoList = mutableListOf<Video>()

        // 1. Try legacy mirror select options if present
        document.select("select.mirror > option[value]").forEach { opt ->
            val value = opt.attr("value")
            if (value.isNotEmpty()) {
                val decoded = runCatching {
                    val decodedHtml = String(Base64.decode(value, Base64.DEFAULT))
                    org.jsoup.Jsoup.parse(decodedHtml).select("iframe[data-src], iframe[src]").let {
                        it.attr("data-src").ifEmpty { it.attr("src") }
                    }
                }.getOrNull() ?: ""

                extractVideosFromEmbed(decoded, opt.text().trim(), videoList)
            }
        }

        // 2. Try encrypted Animeku API source token
        if (videoList.isEmpty()) {
            val script = document.select("script:containsData(_0xa100d42aa)").html()
            val token = Regex("""_0xa100d42aa\s*=\s*["']([^"']+)["']""").find(script)?.groupValues?.get(1)
            if (!token.isNullOrBlank()) {
                extractFromAnimekuApi(token, videoList)
            }
        }

        // 3. Fallback direct iframe extraction
        if (videoList.isEmpty()) {
            document.select("div#pembed iframe, div.player-embed iframe, div.video-content iframe").forEach { iframe ->
                val src = iframe.attr("data-src").ifEmpty { iframe.attr("src") }
                if (src.isNotBlank() && src != "about:blank") {
                    extractVideosFromEmbed(src, "Direct Player", videoList)
                }
            }
        }

        return videoList.sort()
    }

    private fun extractFromAnimekuApi(token: String, videoList: MutableList<Video>) {
        runCatching {
            val apiHeaders = Headers.Builder()
                .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .add("Referer", "$baseUrl/")
                .add("Origin", baseUrl)
                .add("Content-Type", "application/json")
                .build()

            val body = """{"id":"$token"}""".toRequestBody("application/json; charset=utf-8".toMediaType())
            val apiReq = POST("https://animeku.org/api/v9/sources", apiHeaders, body)
            val apiRes = client.newCall(apiReq).execute()
            if (!apiRes.isSuccessful) return

            val resJson = json.parseToJsonElement(apiRes.body.string()).jsonObject
            val mirrorB64 = resJson["mirror"]?.jsonPrimitive?.content ?: return
            val mirrorJsonStr = String(Base64.decode(mirrorB64, Base64.DEFAULT))
            val cipherObj = json.parseToJsonElement(mirrorJsonStr).jsonObject
            val ct = cipherObj["ct"]?.jsonPrimitive?.content ?: return
            val salt = cipherObj["s"]?.jsonPrimitive?.content ?: return

            val decrypted = CryptoAES.decryptWithSalt(ct, salt, "3&!Z0M,VIZ;dZW==")
            if (decrypted.isBlank()) return

            val decryptedJson = json.parseToJsonElement(decrypted).jsonObject
            val embedObj = decryptedJson["embed"]?.jsonObject ?: return

            embedObj.forEach { (qualityKey, hostsElem) ->
                val qualityLabel = qualityKey.removePrefix("v")
                val hosts = hostsElem.jsonObject

                hosts["mp4upload"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }?.let { mp4Url ->
                    runCatching {
                        videoList.addAll(mp4uploadExtractor.videosFromUrl(mp4Url, headers, suffix = " - $qualityLabel"))
                    }
                }

                hosts["doodstream"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }?.let { doodUrl ->
                    runCatching {
                        videoList.addAll(doodExtractor.videosFromUrl(doodUrl, quality = "Doodstream - $qualityLabel"))
                    }
                }

                hosts["filelions"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }?.let { vidhideUrl ->
                    runCatching {
                        videoList.addAll(vidhideExtractor.videosFromUrl(vidhideUrl, videoNameGen = { "VidHide - $qualityLabel ($it)" }))
                    }
                }

                hosts["pixeldrain"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }?.let { pixelUrl ->
                    videoList.add(Video(pixelUrl, "Pixeldrain - $qualityLabel", pixelUrl, headers))
                }
            }
        }
    }

    private fun extractVideosFromEmbed(url: String, hoster: String, videoList: MutableList<Video>) {
        if (url.isBlank() || url == "about:blank") return
        runCatching {
            when {
                url.contains("mp4upload.com") -> {
                    videoList.addAll(mp4uploadExtractor.videosFromUrl(url, headers, suffix = " - $hoster"))
                }
                url.contains("dood") || url.contains("ds2play") || url.contains("dsvplay") -> {
                    videoList.addAll(doodExtractor.videosFromUrl(url, quality = "Doodstream - $hoster"))
                }
                url.contains("vidhide") || url.contains("filelions") -> {
                    videoList.addAll(vidhideExtractor.videosFromUrl(url, videoNameGen = { "VidHide - $hoster ($it)" }))
                }
                url.contains("yourupload.com") -> {
                    videoList.addAll(youruploadExtractor.videoFromUrl(url, headers, hoster, "Original - "))
                }
                else -> {}
            }
        }
    }

    override fun videoFromElement(element: Element): Video = throw UnsupportedOperationException()

    override fun videoListSelector(): String = throw UnsupportedOperationException()

    override fun videoUrlParse(document: Document) = throw UnsupportedOperationException()

    override fun List<Video>.sort(): List<Video> {
        val quality = preferences.getString(PREF_QUALITY_KEY, "720p") ?: "720p"
        return sortedWith(
            compareByDescending { it.quality.contains(quality, ignoreCase = true) }
        )
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = PREF_QUALITY_KEY
            title = "Preferred Quality"
            entries = arrayOf("1080p", "720p", "480p", "360p")
            entryValues = arrayOf("1080", "720", "480", "360")
            setDefaultValue("720")
            summary = "%s"
        }.also(screen::addPreference)
    }

    companion object {
        private const val PREF_QUALITY_KEY = "preferred_quality"
    }
}
