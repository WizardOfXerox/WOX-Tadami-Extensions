package eu.kanade.tachiyomi.animeextension.en.hentaigasm

import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.animeextension.en.hentaigasm.HentaigasmFilters.GENRES
import eu.kanade.tachiyomi.animeextension.en.hentaigasm.HentaigasmFilters.GenreFilter
import eu.kanade.tachiyomi.animeextension.en.hentaigasm.HentaigasmFilters.SortFilter
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.ParsedAnimeHttpSource
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.addListPreference
import keiyoushi.utils.addSwitchPreference
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class Hentaigasm :
    ParsedAnimeHttpSource(),
    ConfigurableAnimeSource {

    override val name = "Hentaigasm"

    override val baseUrl = "https://hentaigasm.com"

    override val lang = "en"

    override val supportsLatest = true

    private val preferences by getPreferencesLazy()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    override val client: OkHttpClient = network.client.newBuilder()
        .retryOnConnectionFailure(true)
        .build()

    // Short-timeout client used exclusively for probing CDN mirror hosts
    private val mirrorProbeClient: OkHttpClient by lazy {
        client.newBuilder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.SECONDS)
            .build()
    }

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // ======================= Helpers & Cleaning =======================

    private fun sanitizeMediaUrl(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return url.trim().replace(" ", "%20")
    }

    private fun isValidPostHref(href: String): Boolean {
        if (href.isBlank()) return false
        if (href.contains("d.php", ignoreCase = true) || href.contains("zlinkp", ignoreCase = true)) {
            return false
        }
        return !href.startsWith("http") || href.contains("hentaigasm.com", ignoreCase = true)
    }

    private fun normalizePath(url: String): String {
        val rawPath = url.toHttpUrlOrNull()?.encodedPath ?: url.substringBefore("?").substringBefore("#")
        val trimmed = rawPath.trim('/')
        return if (trimmed.isEmpty()) "/" else "/$trimmed/"
    }

    private fun cleanAnimeList(animes: List<SAnime>): List<SAnime> {
        val groupSeries = preferences.getBoolean(PREF_GROUP_SERIES_KEY, PREF_GROUP_SERIES_DEFAULT)
        val valid = animes.filter { anime ->
            anime.url.isNotBlank() &&
                !anime.url.contains("d.php", ignoreCase = true) &&
                anime.thumbnail_url?.contains("/preview/ad", ignoreCase = true) != true
        }

        if (!groupSeries) {
            return valid.map { anime ->
                SAnime.create().apply {
                    this.url = anime.url
                    this.title = anime.title
                    this.thumbnail_url = sanitizeMediaUrl(anime.thumbnail_url)
                    this.initialized = anime.initialized
                }
            }.distinctBy { it.url }
        }

        val seenSlugs = HashSet<String>()
        val seenTitles = HashSet<String>()
        return valid.mapNotNull { anime ->
            val slug = anime.url.trim('/').substringAfterLast('/').lowercase()
            val cleanSlug = slug.replace(CLEAN_SLUG_REGEX, "").ifBlank { slug }
            val cleanTitle = anime.title.replace(CLEAN_TITLE_REGEX, "").trim().ifBlank { anime.title }

            if (!seenSlugs.add(cleanSlug) || !seenTitles.add(cleanTitle.lowercase())) {
                return@mapNotNull null
            }

            SAnime.create().apply {
                // Keep the real post URL so detail/episode requests resolve directly without relying on redirects
                this.url = anime.url
                this.title = cleanTitle
                this.thumbnail_url = sanitizeMediaUrl(anime.thumbnail_url)
                this.initialized = anime.initialized
            }
        }
    }

    private fun parsePostCard(element: Element): SAnime {
        val anime = SAnime.create()
        val linkEl = element.selectFirst("a.clip-link, h2.title a, .thumb a")
        val rawHref = linkEl?.attr("href")?.trim().orEmpty()
        if (!isValidPostHref(rawHref)) {
            anime.url = ""
            return anime
        }

        anime.setUrlWithoutDomain(rawHref)
        anime.title = linkEl?.attr("title")?.takeIf { it.isNotBlank() }
            ?: element.selectFirst("h2.title a, .title a")?.text()
            ?: "Unknown"

        val img = element.selectFirst("div.thumb img, img")
        val rawThumb = img?.let {
            val src = it.attr("abs:src")
            if (src.isNotBlank()) src else it.attr("abs:data-src")
        }
        anime.thumbnail_url = sanitizeMediaUrl(rawThumb)
        return anime
    }

    private fun extractEpisodeNumber(title: String): Float {
        return runCatching {
            EP_REGEX.find(title)?.groupValues?.get(1)?.toFloat()
        }.getOrNull() ?: 1F
    }

    private fun extractEpisodeBadges(title: String, tagNames: List<String> = emptyList()): String? {
        val badges = LinkedHashSet<String>()
        if (title.contains("Uncensored", ignoreCase = true) || tagNames.any { it.equals("Uncensored", true) }) {
            badges.add("Uncensored")
        }
        when {
            title.contains("Subbed", ignoreCase = true) -> badges.add("Subbed")
            title.contains("Dubbed", ignoreCase = true) -> badges.add("Dubbed")
            title.contains("Raw", ignoreCase = true) -> badges.add("Raw")
        }
        if (title.contains("3D", ignoreCase = true) || tagNames.any { it.equals("3D", true) }) {
            badges.add("3D")
        }
        if (title.contains("Motion Anime", ignoreCase = true) || tagNames.any { it.equals("Motion Anime", true) }) {
            badges.add("Motion Anime")
        }
        return badges.takeIf { it.isNotEmpty() }?.joinToString(", ")
    }

    private fun parseIsoDate(dateStr: String?): Long =
        synchronized(DATE_FORMAT_ISO) { DATE_FORMAT_ISO.tryParse(dateStr) }

    private fun parseHtmlDate(dateStr: String?): Long =
        synchronized(DATE_FORMAT_HTML) { DATE_FORMAT_HTML.tryParse(dateStr) }

    // ============================== Popular ===============================

    override fun popularAnimeRequest(page: Int): Request {
        val url = if (page == 1) "$baseUrl/?orderby=views" else "$baseUrl/page/$page/?orderby=views"
        return GET(url, headers)
    }

    override fun popularAnimeSelector(): String = "div.item-post:not(.sticky), div.item:not(.sticky)"

    override fun popularAnimeFromElement(element: Element): SAnime = parsePostCard(element)

    override fun popularAnimeNextPageSelector(): String = "div.wp-pagenavi a.nextpostslink, div.wp-pagenavi a.larger"

    override fun popularAnimeParse(response: Response): AnimesPage {
        val page = super.popularAnimeParse(response)
        return AnimesPage(cleanAnimeList(page.animes), page.hasNextPage)
    }

    // =============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request {
        val url = if (page == 1) "$baseUrl/" else "$baseUrl/page/$page/"
        return GET(url, headers)
    }

    override fun latestUpdatesSelector(): String = popularAnimeSelector()

    override fun latestUpdatesFromElement(element: Element): SAnime = parsePostCard(element)

    override fun latestUpdatesNextPageSelector(): String = popularAnimeNextPageSelector()

    override fun latestUpdatesParse(response: Response): AnimesPage {
        val page = super.latestUpdatesParse(response)
        return AnimesPage(cleanAnimeList(page.animes), page.hasNextPage)
    }

    // =============================== Search ===============================

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        if (query.isNotBlank()) {
            val url = "$baseUrl/wp-json/wp/v2/posts".toHttpUrl().newBuilder()
                .addQueryParameter("search", query.trim())
                .addQueryParameter("page", page.toString())
                .addQueryParameter("per_page", "12")
                .addQueryParameter("_fields", "id,link,title,date,categories,content")
                .build()
            return GET(url, headers)
        }

        val sort = filters.filterIsInstance<SortFilter>().firstOrNull()?.selected ?: "date"
        val genre = filters.filterIsInstance<GenreFilter>().firstOrNull()?.selected ?: ""

        val basePath = when {
            genre.isBlank() -> if (page == 1) "$baseUrl/" else "$baseUrl/page/$page/"
            genre.contains("/") -> if (page == 1) "$baseUrl/$genre/" else "$baseUrl/$genre/page/$page/"
            else -> if (page == 1) "$baseUrl/genre/$genre/" else "$baseUrl/genre/$genre/page/$page/"
        }

        val url = if (sort != "date") "$basePath?orderby=$sort" else basePath
        return GET(url, headers)
    }

    override fun searchAnimeSelector(): String = popularAnimeSelector()

    override fun searchAnimeFromElement(element: Element): SAnime = parsePostCard(element)

    override fun searchAnimeNextPageSelector(): String = popularAnimeNextPageSelector()

    override fun searchAnimeParse(response: Response): AnimesPage {
        if (response.request.url.encodedPath.contains("wp-json")) {
            val jsonString = response.body.string()
            val posts = json.decodeFromString<List<WpPost>>(jsonString)
            val totalPages = response.header("X-WP-TotalPages")?.toIntOrNull() ?: 1
            val page = response.request.url.queryParameter("page")?.toIntOrNull() ?: 1
            val hasNextPage = page < totalPages

            val animes = posts.map { post ->
                val title = Jsoup.parse(post.title.rendered).text()
                SAnime.create().apply {
                    if (isValidPostHref(post.link)) {
                        setUrlWithoutDomain(post.link)
                    } else {
                        url = ""
                    }
                    this.title = title
                    this.thumbnail_url = sanitizeMediaUrl(
                        extractThumbnail(post.content.rendered)
                            ?: "https://hgasm1.com/preview/$title.webp",
                    )
                }
            }
            return AnimesPage(cleanAnimeList(animes), hasNextPage)
        }
        val page = super.searchAnimeParse(response)
        return AnimesPage(cleanAnimeList(page.animes), page.hasNextPage)
    }

    override fun getFilterList(): AnimeFilterList = AnimeFilterList(
        SortFilter(),
        GenreFilter("Genre", GENRES),
    )

    // ============================== Details ===============================

    override fun animeDetailsParse(document: Document): SAnime {
        val anime = SAnime.create()
        val rawTitle = document.selectFirst("h1#title, h1.entry-title")?.text()
            ?: document.selectFirst("meta[property=og:title]")?.attr("content")
            ?: document.selectFirst("title")?.text().orEmpty()
        val cleanTitle = rawTitle.replace(CLEAN_TITLE_REGEX, "").trim().ifBlank { rawTitle }
        anime.title = cleanTitle

        // Prioritize JWPlayer poster image to avoid matching unrelated thumbnails in div.related-posts
        val html = document.html()
        val jwPoster = JW_IMAGE_REGEX.find(html)?.groupValues?.get(1)
        val ogImage = document.selectFirst("meta[property=og:image]")?.attr("content")
        val contentThumb = document.selectFirst("div#details div.thumb img, div.entry-content img")
            ?.let { it.attr("abs:src").ifBlank { it.attr("abs:data-src") } }
        anime.thumbnail_url = sanitizeMediaUrl(
            jwPoster ?: ogImage ?: contentThumb ?: "https://hgasm1.com/preview/$rawTitle.webp",
        )

        val genreTags = document.select("div#extras a[href*=/genre/], a[rel=tag]")
            .map { it.text().trim() }
            .filter { it.isNotBlank() }
            .distinct()
        anime.genre = genreTags.joinToString()

        val seriesName = document.selectFirst("div#extras a[href*=/hentai/]")
            ?.takeUnless { it.attr("href").contains("new-hentai-release", ignoreCase = true) }
            ?.text()
            ?.trim()
            ?.takeIf { it.isNotBlank() }

        val releaseDate = document.selectFirst("div#extras h4")?.ownText()?.trim()?.takeIf { it.isNotBlank() }

        val notesText = document.selectFirst("div.entry-content")?.let { el ->
            val clone = el.clone()
            clone.select("script, style, a.btn, a[href*=gamingadlt], a[href*=zlinkp]").remove()
            clone.select("p").filter { p ->
                val txt = p.text()
                txt.contains("HENTAIGASM EXCLUSIVE", ignoreCase = true) || txt.isBlank()
            }.forEach { it.remove() }
            clone.text().trim()
        }

        val descParts = buildList {
            if (!notesText.isNullOrBlank()) add(notesText)
            if (seriesName != null) add("Series: $seriesName")
            if (releaseDate != null) add("Updated: $releaseDate")
        }
        anime.description = descParts.joinToString("\n\n").ifBlank { null }

        anime.status = if (genreTags.any { it.equals("Preview", ignoreCase = true) }) {
            SAnime.ONGOING
        } else {
            SAnime.COMPLETED
        }
        return anime
    }

    // ============================== Related ===============================

    // Omit override for compatibility across standard Aniyomi compilation and Tadami runtime dispatch
    fun relatedAnimeListRequest(anime: SAnime): Request = animeDetailsRequest(anime)

    fun relatedAnimeListParse(response: Response): List<SAnime> {
        val document = response.asJsoup()
        val related = document.select("div.related-posts div.item-post:not(.sticky), div.related-posts div.item:not(.sticky)")
            .map(::parsePostCard)
        return cleanAnimeList(related)
    }

    // ============================== Episodes ==============================

    override fun episodeListParse(response: Response): List<SEpisode> {
        val document = response.asJsoup()
        val currentUrl = response.request.url.toString()
        val currentPath = response.request.url.encodedPath
        val currentTitle = document.selectFirst("h1#title, h1.entry-title")?.text()
            ?: document.selectFirst("meta[property=og:title]")?.attr("content")
            ?: ""
        val cleanTitle = currentTitle.replace(CLEAN_TITLE_REGEX, "").trim().ifBlank { currentTitle }
        val slug = currentUrl.trim('/').substringAfterLast('/')
        val cleanSlug = slug.replace(CLEAN_SLUG_REGEX, "").lowercase()
        val currentDateUpload = parseHtmlDate(document.selectFirst("div#extras h4")?.ownText()?.trim())
        val tagNames = document.select("div#extras a[href*=/genre/], a[rel=tag]")
            .map { it.text().trim() }
            .filter { it.isNotBlank() }

        val episodes = mutableListOf<SEpisode>()

        fun isMatchingEpisode(postTitle: String, postHref: String): Boolean {
            val postCleanTitle = postTitle.replace(CLEAN_TITLE_REGEX, "").trim()
            val postSlug = postHref.trim('/').substringAfterLast('/').lowercase()
            val postCleanSlug = postSlug.replace(CLEAN_SLUG_REGEX, "")

            return postCleanTitle.equals(cleanTitle, ignoreCase = true) ||
                (cleanSlug.isNotBlank() && postCleanSlug == cleanSlug) ||
                (cleanSlug.isNotBlank() && postCleanSlug.startsWith("$cleanSlug-")) ||
                (cleanTitle.isNotBlank() && postCleanTitle.startsWith("$cleanTitle ", ignoreCase = true))
        }

        // 1. Fetch complete episode list via WP REST API using minimal _fields projection
        if (cleanTitle.isNotBlank()) {
            runCatching {
                val apiUrl = "$baseUrl/wp-json/wp/v2/posts".toHttpUrl().newBuilder()
                    .addQueryParameter("search", cleanTitle)
                    .addQueryParameter("per_page", "100")
                    .addQueryParameter("_fields", "id,link,title,date,categories")
                    .build()
                client.newCall(GET(apiUrl.toString(), headers)).execute().use { apiResp ->
                    if (apiResp.isSuccessful) {
                        val posts = json.decodeFromString<List<WpPost>>(apiResp.body.string())
                        posts.forEach { post ->
                            val postTitle = Jsoup.parse(post.title.rendered).text()
                            if (isValidPostHref(post.link) && isMatchingEpisode(postTitle, post.link)) {
                                episodes.add(
                                    SEpisode.create().apply {
                                        setUrlWithoutDomain(post.link)
                                        name = postTitle
                                        episode_number = extractEpisodeNumber(postTitle)
                                        date_upload = parseIsoDate(post.date)
                                        scanlator = extractEpisodeBadges(postTitle, tagNames)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. Fallback to the series taxonomy page (/hentai/<series-slug>/) if REST search yielded nothing
        if (episodes.isEmpty()) {
            val seriesUrl = document.selectFirst("div#extras a[href*=/hentai/]")
                ?.attr("abs:href")
                ?.takeUnless { it.contains("new-hentai-release", ignoreCase = true) }
            if (!seriesUrl.isNullOrBlank()) {
                runCatching {
                    client.newCall(GET(seriesUrl, headers)).execute().use { serResp ->
                        if (serResp.isSuccessful) {
                            val serDoc = serResp.asJsoup()
                            serDoc.select(popularAnimeSelector()).forEach { card ->
                                val linkEl = card.selectFirst("a.clip-link, h2.title a, .thumb a")
                                val epHref = linkEl?.attr("abs:href").orEmpty()
                                val epTitle = linkEl?.attr("title")?.takeIf { it.isNotBlank() }
                                    ?: card.selectFirst("h2.title a, .title a")?.text()
                                    ?: ""
                                if (isValidPostHref(epHref) && epTitle.isNotBlank()) {
                                    episodes.add(
                                        SEpisode.create().apply {
                                            setUrlWithoutDomain(epHref)
                                            name = epTitle.trim()
                                            episode_number = extractEpisodeNumber(epTitle)
                                            date_upload = 0L
                                            scanlator = extractEpisodeBadges(epTitle, tagNames)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Fallback to related posts section if still empty
        if (episodes.isEmpty()) {
            val relatedLinks = document.select("div.related-posts a.clip-link, div.related-posts h2.title a")
            relatedLinks.forEach { link ->
                val epTitle = link.attr("title").ifBlank { link.text() }.trim()
                val epHref = link.attr("abs:href").ifBlank { link.attr("href") }
                if (isValidPostHref(epHref) && epTitle.isNotBlank() && isMatchingEpisode(epTitle, epHref)) {
                    episodes.add(
                        SEpisode.create().apply {
                            setUrlWithoutDomain(epHref)
                            name = epTitle
                            episode_number = extractEpisodeNumber(epTitle)
                            date_upload = 0L
                            scanlator = extractEpisodeBadges(epTitle, tagNames)
                        },
                    )
                }
            }
        }

        // Always ensure the current post page itself is present in the episode list
        val normCurrent = normalizePath(currentPath)
        if (episodes.none { normalizePath(it.url) == normCurrent }) {
            val epTitle = currentTitle.ifBlank { "Episode 1" }
            episodes.add(
                SEpisode.create().apply {
                    setUrlWithoutDomain(currentPath)
                    name = epTitle
                    episode_number = extractEpisodeNumber(epTitle)
                    date_upload = currentDateUpload
                    scanlator = extractEpisodeBadges(epTitle, tagNames)
                },
            )
        } else if (currentDateUpload > 0L) {
            episodes.firstOrNull { normalizePath(it.url) == normCurrent && it.date_upload == 0L }?.let {
                it.date_upload = currentDateUpload
            }
        }

        return episodes.distinctBy { normalizePath(it.url) }
            .sortedWith(
                compareByDescending<SEpisode> { it.episode_number }
                    .thenByDescending { it.date_upload },
            )
    }

    override fun episodeListSelector(): String = throw UnsupportedOperationException("Not used")

    override fun episodeFromElement(element: Element): SEpisode = throw UnsupportedOperationException("Not used")

    // =============================== Videos ===============================

    override fun videoListParse(response: Response): List<Video> {
        val html = response.body.string()
        val videoHeaders = headers.newBuilder()
            .set("Referer", "$baseUrl/")
            .build()

        val videos = mutableListOf<Video>()
        val seenUrls = HashSet<String>()

        fun addUniqueVideo(rawUrl: String?, qualityLabel: String) {
            val cleanUrl = sanitizeMediaUrl(rawUrl) ?: return
            if (seenUrls.add(cleanUrl)) {
                videos.add(Video(cleanUrl, qualityLabel, cleanUrl, videoHeaders))
            }
        }

        // 1. Primary JWPlayer setup stream URL
        val primaryStreamUrls = mutableListOf<String>()
        JW_FILE_REGEX.findAll(html).forEach { match ->
            val fileUrl = sanitizeMediaUrl(match.groupValues[1]) ?: return@forEach
            val host = fileUrl.toHttpUrlOrNull()?.host ?: "JWPlayer"
            primaryStreamUrls.add(fileUrl)
            addUniqueVideo(fileUrl, "Stream ($host)")
        }

        // 2. Direct download / mp4 anchor links
        MP4_LINK_REGEX.findAll(html).forEach { match ->
            val fileUrl = sanitizeMediaUrl(match.groupValues[1]) ?: return@forEach
            val host = fileUrl.toHttpUrlOrNull()?.host ?: "Direct"
            addUniqueVideo(fileUrl, "Download ($host)")
        }

        // 3. Verify and expose live mirror hosts (hgasm3.com / hgasm2.com / hgasm1.com)
        val primaryUrl = primaryStreamUrls.firstOrNull()
        val primaryParsed = primaryUrl?.toHttpUrlOrNull()
        if (primaryParsed != null && primaryParsed.host in MIRROR_HOSTS) {
            MIRROR_HOSTS.filter { it != primaryParsed.host }.forEach { mirrorHost ->
                val mirrorUrl = primaryParsed.newBuilder().host(mirrorHost).build().toString()
                if (mirrorUrl !in seenUrls && probeMirrorUrl(mirrorUrl, videoHeaders)) {
                    addUniqueVideo(mirrorUrl, "Mirror ($mirrorHost)")
                }
            }
        }

        // 4. Fallback to non-ad embedded iframes
        val doc = Jsoup.parse(html, response.request.url.toString())
        doc.select("div.entry-content iframe[src], iframe[src]").forEach { iframe ->
            val src = iframe.attr("abs:src")
            if (src.isNotBlank() &&
                !src.contains("ads", ignoreCase = true) &&
                !src.contains("magsrv", ignoreCase = true) &&
                !src.contains("zlinkp", ignoreCase = true) &&
                !src.contains("gamingadlt", ignoreCase = true)
            ) {
                val host = src.toHttpUrlOrNull()?.host ?: "Embed"
                addUniqueVideo(src, "Embed ($host)")
            }
        }

        val prefServer = preferences.getString(PREF_SERVER_KEY, PREF_SERVER_DEFAULT).orEmpty()
        return videos.sortedWith(
            compareByDescending { video ->
                when (prefServer) {
                    "Stream", "Download" -> video.quality.startsWith(prefServer, ignoreCase = true)
                    else -> video.quality.contains(prefServer, ignoreCase = true)
                }
            },
        )
    }

    private fun probeMirrorUrl(url: String, headers: Headers): Boolean {
        return runCatching {
            val request = Request.Builder()
                .url(url)
                .headers(headers)
                .head()
                .build()
            mirrorProbeClient.newCall(request).execute().use { resp ->
                resp.isSuccessful && resp.header("Content-Type")?.contains("html", ignoreCase = true) != true
            }
        }.getOrDefault(false)
    }

    override fun videoListSelector(): String = throw UnsupportedOperationException("Not used")

    override fun videoFromElement(element: Element): Video = throw UnsupportedOperationException("Not used")

    override fun videoUrlParse(document: Document): String = throw UnsupportedOperationException("Not used")

    // ============================ Preferences =============================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        screen.addListPreference(
            key = PREF_SERVER_KEY,
            title = "Preferred Server / Mirror",
            summary = "%s",
            entries = arrayOf("Stream (Primary)", "Download", "hgasm3.com", "hgasm2.com", "hgasm1.com"),
            entryValues = arrayOf("Stream", "Download", "hgasm3.com", "hgasm2.com", "hgasm1.com"),
            defaultValue = PREF_SERVER_DEFAULT,
        )

        screen.addSwitchPreference(
            key = PREF_GROUP_SERIES_KEY,
            title = "Group Episodes into Series",
            summary = "Merge duplicate episode cards for the same anime in browse and search feeds.",
            defaultValue = PREF_GROUP_SERIES_DEFAULT,
        )
    }

    // ============================== Helpers ===============================

    private fun extractThumbnail(renderedContent: String): String? {
        return JW_IMAGE_REGEX.find(renderedContent)?.groupValues?.get(1)
            ?: IMG_SRC_REGEX.find(renderedContent)?.groupValues?.get(1)
    }

    @Serializable
    private data class WpPost(
        val id: Long = 0L,
        val link: String = "",
        val date: String = "",
        val title: WpRendered = WpRendered(),
        val content: WpRendered = WpRendered(),
    )

    @Serializable
    private data class WpRendered(
        val rendered: String = "",
    )

    companion object {
        private val JW_FILE_REGEX = Regex("""(?i)file\s*:\s*["'](https?://[^"']+\.mp4[^"']*)["']""")
        private val JW_IMAGE_REGEX = Regex("""(?i)image\s*:\s*["'](https?://[^"']+\.(?:jpg|jpeg|png|webp)[^"']*)["']""")
        private val MP4_LINK_REGEX = Regex("""(?i)href\s*=\s*["'](https?://[^"']+\.mp4[^"']*)["']""")
        private val IMG_SRC_REGEX = Regex("""(?i)src\s*=\s*["'](https?://[^"']+\.(?:jpg|jpeg|png|webp)[^"']*)["']""")

        private val EP_REGEX = Regex(
            """(?i)(?:\s+(?:season|episode|\bep\b|\bpart\b)?\s*|-)(\d+(?:\.\d+)?)(?:\s*(?:uncensored|subbed|raw|dubbed))*\s*$""",
        )

        private val CLEAN_TITLE_REGEX = Regex(
            """(?i)(?:\s+(?:season|episode|\bep\b|\bpart\b)?\s*\d+(?:\.\d+)?(?:\s+(?:uncensored|subbed|raw|dubbed))*|\s+(?:uncensored|subbed|raw|dubbed))\s*$""",
        )

        private val CLEAN_SLUG_REGEX = Regex(
            """(?i)-(?:(?:season|episode|ep|part)-)?\d+(?:\.\d+)?(?:-(?:uncensored|subbed|raw|dubbed))*$|-(?:uncensored|subbed|raw|dubbed)+$""",
        )

        private val MIRROR_HOSTS = listOf("hgasm3.com", "hgasm2.com", "hgasm1.com")

        private val DATE_FORMAT_ISO = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        private val DATE_FORMAT_HTML = SimpleDateFormat("MMMM d, yyyy", Locale.US)

        private const val PREF_SERVER_KEY = "pref_server"
        private const val PREF_SERVER_DEFAULT = "Stream"
        private const val PREF_GROUP_SERIES_KEY = "pref_group_series"
        private const val PREF_GROUP_SERIES_DEFAULT = true
    }
}
