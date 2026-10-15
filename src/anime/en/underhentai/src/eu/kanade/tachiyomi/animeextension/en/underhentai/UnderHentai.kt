package eu.kanade.tachiyomi.animeextension.en.underhentai

import android.util.Base64
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.ParsedAnimeHttpSource
import eu.kanade.tachiyomi.lib.playlistutils.PlaylistUtils
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URLEncoder

class UnderHentai : ParsedAnimeHttpSource() {

    override val name = "UnderHentai"

    override val baseUrl = "https://www.underhentai.net"

    override val lang = "en"

    override val supportsLatest = true

    override val client: OkHttpClient = network.client.newBuilder()
        .build()

    private val playlistUtils by lazy { PlaylistUtils(client, headers) }

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        .add("User-Agent", USER_AGENT)

    // ======================= Popular Anime =======================

    override fun popularAnimeRequest(page: Int): Request =
        GET("$baseUrl/page/$page/", headers)

    override fun popularAnimeSelector(): String =
        "article"

    override fun popularAnimeParse(response: Response): AnimesPage {
        val document = response.asJsoup()
        val animeList = document.select(popularAnimeSelector())
            .filterNot { article ->
                val link = article.selectFirst("a")?.attr("href").orEmpty()
                val badge = article.selectFirst(".badge")?.text().orEmpty()
                badge.contains("AD", ignoreCase = true) || link.startsWith("http")
            }
            .map { popularAnimeFromElement(it) }

        val hasNextPage = document.selectFirst(popularAnimeNextPageSelector()) != null
        return AnimesPage(animeList, hasNextPage)
    }

    override fun popularAnimeFromElement(element: Element): SAnime = SAnime.create().apply {
        val link = element.selectFirst("a.flex-col") ?: element.selectFirst("a")!!
        setUrlWithoutDomain(link.attr("abs:href"))
        title = element.selectFirst("h3")?.text()?.trim() ?: link.text().trim()
        thumbnail_url = element.selectFirst("img")?.attr("abs:src")
    }

    override fun popularAnimeNextPageSelector(): String =
        "a.next, .pagination a[rel=\"next\"], a:contains(Next)"

    // ======================= Latest Anime =======================

    override fun latestUpdatesRequest(page: Int): Request =
        popularAnimeRequest(page)

    override fun latestUpdatesSelector(): String =
        popularAnimeSelector()

    override fun latestUpdatesParse(response: Response): AnimesPage =
        popularAnimeParse(response)

    override fun latestUpdatesFromElement(element: Element): SAnime =
        popularAnimeFromElement(element)

    override fun latestUpdatesNextPageSelector(): String =
        popularAnimeNextPageSelector()

    // ======================= Search Anime =======================

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        return if (query.isNotBlank()) {
            val encodedQuery = URLEncoder.encode(query.trim(), "UTF-8")
            GET("$baseUrl/page/$page/?s=$encodedQuery", headers)
        } else {
            popularAnimeRequest(page)
        }
    }

    override fun searchAnimeSelector(): String =
        popularAnimeSelector()

    override fun searchAnimeParse(response: Response): AnimesPage =
        popularAnimeParse(response)

    override fun searchAnimeFromElement(element: Element): SAnime =
        popularAnimeFromElement(element)

    override fun searchAnimeNextPageSelector(): String =
        popularAnimeNextPageSelector()

    // ======================= Anime Details =======================

    override fun animeDetailsParse(document: Document): SAnime = SAnime.create().apply {
        title = document.selectFirst("h1, .entry-title")?.text()?.trim().orEmpty()
        val img = document.selectFirst("meta[property=\"og:image\"], .img-wrapper img")
        thumbnail_url = if (img?.tagName() == "meta") img.attr("content") else img?.attr("abs:src")
        val desc = document.selectFirst("meta[property=\"og:description\"], .desc, .entry-content, p")
        description = if (desc?.tagName() == "meta") desc.attr("content") else desc?.text()?.trim()
        genre = document.select("a[href*=\"/genre/\"], a[href*=\"/tag/\"]")
            .map { it.text().trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(", ")
        status = SAnime.COMPLETED
        initialized = true
    }

    // ======================= Episode List =======================

    override fun episodeListParse(response: Response): List<SEpisode> {
        val document = response.asJsoup()
        val seriesUrl = response.request.url.toString()
        val epBoxes = document.select(".ep-box")
        val episodes = mutableListOf<SEpisode>()

        if (epBoxes.isNotEmpty()) {
            for (epBox in epBoxes) {
                val epHeader = epBox.selectFirst(".ep-header")?.text()?.trim() ?: "Episode 1"
                val epNumMatch = Regex("""\d+""").find(epHeader)
                val baseEpNum = epNumMatch?.value?.toFloatOrNull() ?: 1f

                val actionsList = epBox.select(".variant-actions")
                for ((idx, actions) in actionsList.withIndex()) {
                    val watchLink = actions.selectFirst("a[href*=\"/watch/?id=\"]") ?: continue
                    val href = watchLink.attr("abs:href")

                    val vh = actions.previousElementSiblings().firstOrNull { it.hasClass("variant-header") }
                    val vm = actions.previousElementSiblings().firstOrNull { it.hasClass("variant-meta") }

                    val vtype = vh?.selectFirst(".variant-label")?.text()?.trim().orEmpty()
                    val vbadge = vh?.selectFirst(".variant-badge")?.text()?.trim().orEmpty()

                    var subs = ""
                    if (vm != null) {
                        for (item in vm.select(".meta-item")) {
                            val lbl = item.selectFirst(".meta-label")?.text().orEmpty()
                            val value = item.selectFirst(".meta-value")?.text().orEmpty()
                            if (lbl.contains("Subs", ignoreCase = true)) {
                                val cleanVal = value.replace(Regex("""[^a-zA-Z]"""), "").trim()
                                if (cleanVal.isNotEmpty() && !cleanVal.equals("None", ignoreCase = true)) {
                                    subs = cleanVal
                                }
                            }
                        }
                    }

                    val details = mutableListOf<String>()
                    if (subs.isNotEmpty()) {
                        details.add("$subs Sub")
                    } else if (vtype.isNotEmpty()) {
                        details.add(vtype)
                    }
                    if (vbadge.isNotEmpty()) {
                        details.add(vbadge)
                    }

                    val epTitle = if (details.isNotEmpty()) {
                        "$epHeader - ${details.joinToString(" ")}"
                    } else {
                        epHeader
                    }

                    episodes.add(
                        SEpisode.create().apply {
                            name = epTitle
                            episode_number = baseEpNum + (idx * 0.01f)
                            setUrlWithoutDomain(href)
                        }
                    )
                }
            }
        } else {
            val watchLinks = document.select("a[href*=\"/watch/?id=\"]")
            if (watchLinks.isNotEmpty()) {
                val seen = mutableSetOf<String>()
                for (link in watchLinks) {
                    val href = link.attr("abs:href")
                    if (href in seen) continue
                    seen.add(href)

                    val epMatch = Regex("""ep=(\d+)""").find(href)
                    val epRaw = epMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
                    val epNum = (epRaw + 1).toFloat()

                    episodes.add(
                        SEpisode.create().apply {
                            name = "Episode ${epNum.toInt()}"
                            episode_number = epNum
                            setUrlWithoutDomain(href)
                        }
                    )
                }
            } else {
                episodes.add(
                    SEpisode.create().apply {
                        name = "Episode 1"
                        episode_number = 1f
                        setUrlWithoutDomain(seriesUrl)
                    }
                )
            }
        }

        return episodes.sortedByDescending { it.episode_number }
    }

    override fun episodeListSelector(): String =
        throw UnsupportedOperationException("Not used")

    override fun episodeFromElement(element: Element): SEpisode =
        throw UnsupportedOperationException("Not used")

    // ======================= Video List =======================

    override fun videoListParse(response: Response): List<Video> {
        val document = response.asJsoup()
        val pageUrl = response.request.url.toString()
        val iframes = document.select("iframe[src]")
        val videoList = mutableListOf<Video>()

        for (iframe in iframes) {
            val src = iframe.attr("abs:src")
            when {
                src.contains("luluvdo.com") || src.contains("lulustream") -> {
                    videoList.addAll(extractLulu(src))
                }
                src.contains("gupload.site") -> {
                    videoList.addAll(extractGUpload(src, pageUrl))
                }
            }
        }

        return videoList
    }

    private fun extractLulu(url: String): List<Video> {
        val videos = mutableListOf<Video>()
        try {
            val luluHeaders = headersBuilder()
                .set("Referer", "https://luluvdo.com/")
                .set("Origin", "https://luluvdo.com")
                .build()
            val res = client.newCall(GET(url, luluHeaders)).execute()
            if (!res.isSuccessful) return emptyList()
            val html = res.body.string()

            val unpacked = if (html.contains("eval(function(p,a,c,k,e")) {
                unpackDeanEdwards(html)
            } else {
                html
            }

            val m3u8Regex = Regex("""sources:\s*\[\{file:\s*"([^"]+)"""")
            val m3u8Url = m3u8Regex.find(unpacked)?.groupValues?.get(1)
                ?: Regex("""file:\s*"([^"]+\.m3u8[^"]*)"""").find(unpacked)?.groupValues?.get(1)

            if (!m3u8Url.isNullOrBlank()) {
                val hlsVideos = playlistUtils.extractFromHls(
                    m3u8Url,
                    referer = "https://luluvdo.com/",
                    videoNameGen = { q -> "LuluStream - $q" }
                )
                if (hlsVideos.isNotEmpty()) {
                    videos.addAll(hlsVideos)
                } else {
                    videos.add(Video(m3u8Url, "LuluStream - Auto", m3u8Url, headers = luluHeaders))
                }
            }
        } catch (_: Exception) {}
        return videos
    }

    private fun unpackDeanEdwards(html: String): String {
        val match = Regex("""\}\('(.*)',(\d+),(\d+),'(.*)'\.split\('\|'\)""").find(html) ?: return html
        val payload = match.groupValues[1]
        val radix = match.groupValues[2].toIntOrNull() ?: 62
        val symtab = match.groupValues[4].split("|")

        fun decodeKey(key: String): String {
            val idx = try {
                if (radix <= 36) {
                    key.toInt(radix)
                } else {
                    var num = 0
                    for (c in key) {
                        val v = when (c) {
                            in '0'..'9' -> c - '0'
                            in 'a'..'z' -> c - 'a' + 10
                            in 'A'..'Z' -> c - 'A' + 36
                            else -> return key
                        }
                        num = num * radix + v
                    }
                    num
                }
            } catch (_: Exception) {
                -1
            }
            return if (idx in symtab.indices && symtab[idx].isNotEmpty()) symtab[idx] else key
        }

        return Regex("""\b\w+\b""").replace(payload) { decodeKey(it.value) }
    }

    private fun extractGUpload(src: String, referer: String): List<Video> {
        val videos = mutableListOf<Video>()
        try {
            val gReq = GET(src, headersBuilder().set("Referer", referer).build())
            val gRes = client.newCall(gReq).execute()
            if (!gRes.isSuccessful) return emptyList()
            val html = gRes.body.string()

            val pMatch = Regex("""var\s+_p\s*=\s*\[([^\]]+)\]""").find(html)
            val cfgMatch = Regex("""var\s+_cfg\s*=\s*_dp\(['"]([^'"]+)['"]\)""").find(html)

            if (pMatch != null && cfgMatch != null) {
                val key = pMatch.groupValues[1]
                    .split(",")
                    .map { it.trim().trim('\'', '"') }
                    .joinToString("")

                val encStr = cfgMatch.groupValues[1]
                val parts = encStr.split("~")
                if (parts.size >= 2 && key.isNotEmpty()) {
                    val rawBytes = Base64.decode(parts[1], Base64.DEFAULT)
                    val kBytes = key.toByteArray(Charsets.ISO_8859_1)
                    val decoded = StringBuilder()
                    for (i in rawBytes.indices) {
                        val b = rawBytes[i].toInt() and 0xFF
                        val kb = kBytes[i % kBytes.size].toInt() and 0xFF
                        decoded.append((b xor kb).toChar())
                    }
                    val jsonStr = decoded.toString()
                    val videoUrl = Regex(""""videoUrl"\s*:\s*"([^"]+)"""")
                        .find(jsonStr)
                        ?.groupValues?.get(1)
                        ?.replace("\\/", "/")

                    if (!videoUrl.isNullOrBlank()) {
                        if (videoUrl.contains(".m3u8")) {
                            val hlsVideos = playlistUtils.extractFromHls(
                                videoUrl,
                                referer = src,
                                videoNameGen = { q -> "GUpload - $q" }
                            )
                            if (hlsVideos.isNotEmpty()) {
                                videos.addAll(hlsVideos)
                            } else {
                                val gHeaders = headersBuilder().set("Referer", src).build()
                                videos.add(Video(videoUrl, "GUpload - Auto", videoUrl, headers = gHeaders))
                            }
                        } else {
                            val gHeaders = headersBuilder().set("Referer", src).build()
                            videos.add(Video(videoUrl, "GUpload - MP4", videoUrl, headers = gHeaders))
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return videos
    }

    override fun videoListSelector(): String =
        throw UnsupportedOperationException("Not used")

    override fun videoFromElement(element: Element): Video =
        throw UnsupportedOperationException("Not used")

    override fun videoUrlParse(document: Document): String =
        throw UnsupportedOperationException("Not used")

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }
}
