package eu.kanade.tachiyomi.animeextension.id.nimegami

import android.util.Base64
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.ParsedAnimeHttpSource
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.lib.jsunpacker.JsUnpacker
import keiyoushi.utils.parallelCatchingFlatMapBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URLDecoder
import eu.kanade.tachiyomi.lib.synchrony.Deobfuscator as Synchrony

class NimeGami : ParsedAnimeHttpSource() {

    override val name = "NimeGami"

    override val baseUrl = "https://nimegami.id"

    override val lang = "id"

    override val supportsLatest = true

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // ============================== Popular ===============================
    override fun popularAnimeRequest(page: Int) = GET("$baseUrl/anime/?sort=updated&page=$page", headers)

    override fun popularAnimeSelector() = "article.anime-card, div.wrapper-2-a > article > a"

    override fun popularAnimeFromElement(element: Element) = SAnime.create().apply {
        val linkElem = element.selectFirst("a.poster-link, a.card-title, a")
        val link = linkElem?.attr("href").orEmpty()
        setUrlWithoutDomain(link)

        val img = element.selectFirst("img.poster-image, img")
        title = element.selectFirst("a.card-title")?.text()?.trim()
            ?: img?.attr("alt")?.trim()
            ?: element.selectFirst("div.title-post2")?.text()?.trim()
            ?: "Anime"

        val rawSrc = img?.attr("src")?.takeIf { it.isNotBlank() } ?: img?.attr("data-lazy-src")
        thumbnail_url = if (rawSrc != null && rawSrc.contains("url=")) {
            runCatching {
                val encUrl = rawSrc.substringAfter("url=").substringBefore("&")
                URLDecoder.decode(encUrl, "UTF-8")
            }.getOrDefault(rawSrc)
        } else {
            img?.attr("abs:src")
        }
    }

    override fun popularAnimeNextPageSelector() = "a.page-arrow, a[href*='page='], span.next-page"

    // =============================== Latest ===============================
    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/anime/?status=RELEASING&sort=updated&page=$page", headers)

    override fun latestUpdatesSelector() = popularAnimeSelector()

    override fun latestUpdatesFromElement(element: Element) = popularAnimeFromElement(element)

    override fun latestUpdatesNextPageSelector() = popularAnimeNextPageSelector()

    // =============================== Search ===============================
    override suspend fun getSearchAnime(page: Int, query: String, filters: AnimeFilterList): AnimesPage {
        if (query.startsWith("https://")) {
            val url = query.toHttpUrl()
            if (url.host != baseUrl.toHttpUrl().host) {
                throw Exception("Unsupported url")
            }
            val id = url.pathSegments.firstOrNull()?.takeIf(String::isNotBlank)
                ?: throw Exception("Unsupported url")
            return getSearchAnime(page, "${PREFIX_SEARCH}$id", filters)
        }

        if (query.startsWith(PREFIX_SEARCH)) {
            val id = query.removePrefix(PREFIX_SEARCH)
            return client.newCall(GET("$baseUrl/$id", headers))
                .awaitSuccess()
                .use(::searchAnimeByIdParse)
        }

        return super.getSearchAnime(page, query, filters)
    }

    private fun searchAnimeByIdParse(response: Response): AnimesPage {
        val details = animeDetailsParse(response.asJsoup()).apply {
            setUrlWithoutDomain(response.request.url.toString())
            initialized = true
        }

        return AnimesPage(listOf(details), false)
    }

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList) =
        GET("$baseUrl/anime/?q=$query&page=$page", headers)

    override fun searchAnimeSelector() = popularAnimeSelector()

    override fun searchAnimeFromElement(element: Element) = popularAnimeFromElement(element)

    override fun searchAnimeNextPageSelector() = popularAnimeNextPageSelector()

    // =========================== Anime Details ============================
    override fun animeDetailsParse(document: Document) = SAnime.create().apply {
        setUrlWithoutDomain(document.location())

        val img = document.selectFirst("img.poster-image, img[src*='posters'], div.coverthumbnail img, img")
        val rawSrc = img?.attr("src")?.takeIf { it.isNotBlank() } ?: img?.attr("data-lazy-src")
        thumbnail_url = if (rawSrc != null && rawSrc.contains("url=")) {
            runCatching {
                val encUrl = rawSrc.substringAfter("url=").substringBefore("&")
                URLDecoder.decode(encUrl, "UTF-8")
            }.getOrDefault(rawSrc)
        } else {
            img?.attr("abs:src")
        }

        val infosDiv = document.selectFirst("div.info2 > table > tbody")
        title = document.selectFirst("h1")?.text()?.trim()
            ?: infosDiv?.getInfo("Judul:")
            ?: document.selectFirst("h2[itemprop=name]")?.text()
            ?: "Anime"

        genre = document.select("a[href*='/category/'], a[href*='/genre/']").eachText()
            .filterNot { it.equals("genre", ignoreCase = true) }
            .joinToString()
            .ifBlank { infosDiv?.getInfo("Kategori").orEmpty() }

        artist = infosDiv?.getInfo("Studio")

        val statusText = document.select("span.ongoing-badge, h1.title, div.info2").text()
        status = when {
            statusText.contains("ONGOING", ignoreCase = true) || statusText.contains("(On-Going)", ignoreCase = true) -> SAnime.ONGOING
            statusText.contains("COMPLETED", ignoreCase = true) || statusText.contains("(End)", ignoreCase = true) || statusText.contains("(Movie)", ignoreCase = true) -> SAnime.COMPLETED
            else -> SAnime.UNKNOWN
        }

        description = buildString {
            document.selectFirst("p.synopsis-text, div.synopsis, div#Sinopsis p")?.text()?.also {
                append(it.trim())
            }
            if (infosDiv != null) {
                append("\n\n")
                val nonNeeded = listOf("Judul:", "Kategori", "Studio")
                infosDiv.select("tr")
                    .eachText()
                    .filterNot(nonNeeded::contains)
                    .forEach { append("$it\n") }
            }
        }.trim()
    }

    private fun Element.getInfo(info: String) = selectFirst("tr:has(td.tablex:contains($info))")?.text()?.substringAfter(": ")

    // ============================== Episodes ==============================
    override fun episodeListParse(response: Response): List<SEpisode> {
        val document = response.asJsoup()

        // Check if old layout episode elements exist
        val oldElements = document.select("div.list_eps_stream > li.select-eps")
        if (oldElements.isNotEmpty()) {
            return oldElements.map(::episodeFromElement).reversed()
        }

        // New Next.js download link mapping
        val dlLinks = document.select("a[href*='/download/']")
        val epGroups = dlLinks.mapNotNull { a ->
            val href = a.attr("href")
            val m = Regex("""/download/(?:alt/)?(\d+)/""").find(href)
            m?.let { it.groupValues[1].toInt() to a }
        }.groupBy({ it.first }, { it.second })

        if (epGroups.isNotEmpty()) {
            return epGroups.map { (epNum, links) ->
                SEpisode.create().apply {
                    episode_number = epNum.toFloat()
                    name = "Episode $epNum"
                    val linksData = links.map {
                        val q = it.attr("href").split("/").filter(String::isNotBlank).let { parts ->
                            parts.firstOrNull { p -> p.endsWith("p") } ?: "Default"
                        }
                        val serverName = it.text().trim().ifBlank { "Server" }
                        "$q|$serverName|${it.attr("href")}"
                    }.distinct().joinToString(";;")
                    url = "$epNum@@$linksData"
                }
            }.sortedByDescending { it.episode_number }
        }

        // Fallback for movies or single episode
        if (dlLinks.isNotEmpty()) {
            val linksData = dlLinks.map {
                val q = it.attr("href").split("/").filter(String::isNotBlank).let { parts ->
                    parts.firstOrNull { p -> p.endsWith("p") } ?: "Default"
                }
                "$q|${it.text().trim()}|${it.attr("href")}"
            }.distinct().joinToString(";;")
            return listOf(
                SEpisode.create().apply {
                    episode_number = 1F
                    name = "Full Movie / Episode 1"
                    url = "1@@$linksData"
                }
            )
        }

        return emptyList()
    }

    override fun episodeListSelector() = "div.list_eps_stream > li.select-eps"

    override fun episodeFromElement(element: Element) = SEpisode.create().apply {
        val num = element.attr("id").substringAfterLast("_")
        episode_number = num.toFloatOrNull() ?: 1F
        name = "Episode $num"
        url = element.attr("data")
    }

    // ============================ Video Links =============================
    override suspend fun getVideoList(episode: SEpisode): List<Video> {
        val epUrl = episode.url

        if (epUrl.contains("@@")) {
            val linksData = epUrl.substringAfter("@@")
            val items = linksData.split(";;").filter(String::isNotBlank)
            val videoList = mutableListOf<Video>()

            for (item in items) {
                val parts = item.split("|")
                if (parts.size < 3) continue
                val quality = parts[0]
                val serverName = parts[1]
                val path = parts[2]
                val fullUrl = if (path.startsWith("http")) path else "$baseUrl$path"

                runCatching {
                    val resp = client.newCall(GET(fullUrl, headers)).execute()
                    val finalUrl = resp.request.url.toString()
                    val doc = resp.asJsoup()

                    val directLink = doc.selectFirst("a[href*='public/download'], a.btn-download, a:contains(Download Sekarang)")?.attr("abs:href")
                    if (!directLink.isNullOrBlank()) {
                        videoList.add(Video(directLink, "$serverName - $quality", directLink))
                    } else if (!finalUrl.contains("/download/")) {
                        val extracted = extractVideos(finalUrl, quality, episode.episode_number.toInt() - 1)
                        if (extracted.isNotEmpty()) {
                            videoList.addAll(extracted)
                        } else {
                            videoList.add(Video(finalUrl, "$serverName - $quality", finalUrl))
                        }
                    }
                    Unit
                }
            }
            return videoList
        }

        // Legacy b64 encoded qualities fallback
        val qualities = runCatching {
            json.decodeFromString<List<VideoQuality>>(epUrl.b64Decode())
        }.getOrNull() ?: return emptyList()

        val episodeIndex = episode.episode_number.toInt() - 1
        var usedBunga = false
        return qualities.flatMap {
            val quality = it.format
            it.url.mapNotNull { url ->
                if (url.contains("bunga.nimegami")) {
                    if (usedBunga) {
                        return@mapNotNull null
                    } else {
                        usedBunga = true
                    }
                }
                runCatching {
                    extractVideos(url, quality, episodeIndex)
                }.getOrElse { emptyList() }
            }.flatten()
        }
    }

    private fun extractVideos(url: String, quality: String, episodeIndex: Int): List<Video> = with(url) {
        when {
            contains("video.nimegami.id") -> {
                val realUrl = url.substringAfter("url=").substringBefore("&").b64Decode()
                extractVideos(realUrl, quality, episodeIndex)
            }

            contains("berkasdrive") || contains("drive.nimegami") -> {
                client.newCall(GET(url, headers)).execute()
                    .asJsoup()
                    .let { doc ->
                        val directLink = doc.selectFirst("a[href*='public/download'], a.btn-download")?.attr("abs:href")
                        if (!directLink.isNullOrBlank()) {
                            listOf(Video(directLink, "BerkasDrive - $quality", directLink))
                        } else {
                            extractFromBerkasDrive(doc, quality)
                        }
                    }
            }

            contains("bunga.nimegami") -> {
                val headers = headers.newBuilder()
                    .add("Accept", "video/webm,video/ogg,video/*;q=0.9,application/ogg;q=0.7,audio/*;q=0.6,*/*;q=0.5")
                    .add("Referer", baseUrl)
                    .build()
                listOf(Video(url, "Bunga ($quality)", url, headers))
            }

            else -> emptyList()
        }
    }

    private fun extractFromBerkasDrive(doc: Document, quality: String): List<Video> {
        val script = doc.selectFirst("script:containsData(eval):containsData(sources)")
            ?.data()
            ?.let(JsUnpacker::unpackAndCombine)
            ?: return emptyList()

        val rawSources = script.substringAfter("sources:[").substringBefore("],")
        val deobfuscatedSources = Synchrony.deobfuscateScript(rawSources)
            ?: return emptyList()

        return deobfuscatedSources.split("{")
            .drop(1)
            .mapNotNull { source ->
                val src = source.substringAfter("file:\"").substringBefore("\"")
                if (src.isBlank()) return@mapNotNull null
                val label = source.substringAfter("label:\"").substringBefore("\"")
                Video(src, "BerkasDrive: $label ($quality)", src, headers)
            }
    }

    override fun videoListSelector() = throw UnsupportedOperationException()
    override fun videoFromElement(element: Element) = throw UnsupportedOperationException()
    override fun videoUrlParse(document: Document) = throw UnsupportedOperationException()

    // ============================= Utilities ==============================
    private fun String.b64Decode() = String(Base64.decode(this, Base64.DEFAULT))

    @Serializable
    data class VideoQuality(
        val format: String,
        val url: List<String>,
    )

    companion object {
        const val PREFIX_SEARCH = "id:"
    }
}
