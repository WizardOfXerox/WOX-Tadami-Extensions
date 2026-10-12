package eu.kanade.tachiyomi.animeextension.en.underhentai

import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.ParsedAnimeHttpSource
import eu.kanade.tachiyomi.lib.luluextractor.LuluExtractor
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

    private val luluExtractor by lazy { LuluExtractor(client, headers) }
    private val playlistUtils by lazy { PlaylistUtils(client, headers) }

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        .add("User-Agent", USER_AGENT)

    // ======================= Popular Anime =======================

    override fun popularAnimeRequest(page: Int): Request =
        GET("$baseUrl/page/$page/", headers)

    override fun popularAnimeSelector(): String =
        "article"

    override fun popularAnimeFromElement(element: Element): SAnime = SAnime.create().apply {
        val link = element.selectFirst("a.flex-col") ?: element.selectFirst("a")!!
        setUrlWithoutDomain(link.attr("abs:href"))
        title = element.selectFirst("h3")?.text() ?: link.text()
        thumbnail_url = element.selectFirst("img")?.attr("abs:src")
    }

    override fun popularAnimeNextPageSelector(): String =
        "a.next, .pagination a[rel=\"next\"], a:contains(Next)"

    // ======================= Latest Anime =======================

    override fun latestUpdatesRequest(page: Int): Request =
        popularAnimeRequest(page)

    override fun latestUpdatesSelector(): String =
        popularAnimeSelector()

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

    override fun searchAnimeFromElement(element: Element): SAnime =
        popularAnimeFromElement(element)

    override fun searchAnimeNextPageSelector(): String =
        popularAnimeNextPageSelector()

    // ======================= Anime Details =======================

    override fun animeDetailsParse(document: Document): SAnime = SAnime.create().apply {
        title = document.selectFirst("h1, .entry-title")?.text() ?: ""
        val img = document.selectFirst("meta[property=\"og:image\"], .img-wrapper img")
        thumbnail_url = if (img?.tagName() == "meta") img.attr("content") else img?.attr("abs:src")
        val desc = document.selectFirst("meta[property=\"og:description\"], .desc, .entry-content, p")
        description = if (desc?.tagName() == "meta") desc.attr("content") else desc?.text()
        genre = document.select("a[href*=\"/genre/\"], a[href*=\"/tag/\"]")
            .map { it.text() }
            .distinct()
            .joinToString(", ")
        status = SAnime.COMPLETED
        initialized = true
    }

    // ======================= Episode List =======================

    override fun episodeListParse(response: Response): List<SEpisode> {
        val document = response.asJsoup()
        val seriesUrl = response.request.url.toString()
        val watchLinks = document.select("a[href*=\"/watch/?id=\"]")

        return if (watchLinks.isNotEmpty()) {
            val seen = mutableSetOf<String>()
            val episodes = mutableListOf<SEpisode>()

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
            episodes.sortedByDescending { it.episode_number }
        } else {
            listOf(
                SEpisode.create().apply {
                    name = "Episode 1"
                    episode_number = 1f
                    setUrlWithoutDomain(seriesUrl)
                }
            )
        }
    }

    override fun episodeListSelector(): String =
        throw UnsupportedOperationException("Not used")

    override fun episodeFromElement(element: Element): SEpisode =
        throw UnsupportedOperationException("Not used")

    // ======================= Video List =======================

    override fun videoListParse(response: Response): List<Video> {
        val document = response.asJsoup()
        val iframes = document.select("iframe[src]")
        val videoList = mutableListOf<Video>()

        for (iframe in iframes) {
            val src = iframe.attr("abs:src")
            when {
                src.contains("luluvdo.com") || src.contains("lulustream") -> {
                    try {
                        videoList.addAll(luluExtractor.videosFromUrl(src, "LuluStream - "))
                    } catch (_: Exception) {}
                }
                src.contains("gupload.site") -> {
                    try {
                        val gReq = GET(src, headersBuilder().set("Referer", response.request.url.toString()).build())
                        val gRes = client.newCall(gReq).execute()
                        if (gRes.isSuccessful) {
                            val gHtml = gRes.body.string()
                            val aaremsMatch = Regex("""https://aarems\.org/[^\s"']+""").find(gHtml)
                            if (aaremsMatch != null) {
                                val aaremsUrl = aaremsMatch.value
                                val aReq = GET(aaremsUrl, headersBuilder().set("Referer", src).build())
                                val aRes = client.newCall(aReq).execute()
                                if (aRes.isSuccessful) {
                                    val aHtml = aRes.body.string()
                                    val streamMatches = Regex("""https?://[^\s"'<>]+\.(?:m3u8|mp4)[^\s"'<>]*""").findAll(aHtml)
                                    for (sm in streamMatches) {
                                        val sUrl = sm.value
                                        if (sUrl.contains(".m3u8")) {
                                            videoList.addAll(playlistUtils.extractFromHls(sUrl, videoNameGen = { q -> "GUpload - $q" }))
                                        } else {
                                            videoList.add(Video(sUrl, "GUpload - MP4", sUrl, headers = headers))
                                        }
                                    }
                                }
                                aRes.close()
                            }
                        }
                        gRes.close()
                    } catch (_: Exception) {}
                }
            }
        }

        return videoList
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
