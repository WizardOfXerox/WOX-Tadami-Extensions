package eu.kanade.tachiyomi.animeextension.all.loklok

import android.app.Application
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Track
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class Loklok : ConfigurableAnimeSource, AnimeHttpSource() {

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    private fun showToast(message: String) {
        try {
            val context = Injekt.get<Application>()
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        } catch (_: Exception) {}
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val qualityPref = ListPreference(screen.context).apply {
            key = PREF_QUALITY_KEY
            title = "Preferred video quality"
            entries = arrayOf("1080p", "720p", "480p", "360p")
            entryValues = arrayOf("1080P", "720P", "480P", "360P")
            setDefaultValue("1080P")
            summary = "%s"
        }
        screen.addPreference(qualityPref)

        val countryPref = ListPreference(screen.context).apply {
            key = PREF_COUNTRY_KEY
            title = "Spoofed IP Country"
            entries = arrayOf("Philippines", "Indonesia", "Malaysia", "Thailand", "Vietnam", "Disable Spoofing")
            entryValues = arrayOf("ph", "id", "my", "th", "vn", "none")
            setDefaultValue("ph")
            summary = "%s"
        }
        screen.addPreference(countryPref)

        EditTextPreference(screen.context).apply {
            key = PREF_TOKEN_KEY
            title = "Session Token (Optional)"
            summary = "Paste your Loklok account token here to unlock VIP/authenticated streams."
            dialogTitle = "Session Token"
        }.also(screen::addPreference)
    }

    override val name = "Loklok"

    override val baseUrl = "https://www.loklok.com"

    override val lang = "all"

    override val supportsLatest = true

    override val id: Long = 527189562810L

    override val client: OkHttpClient = network.client.newBuilder().addInterceptor(eu.kanade.tachiyomi.network.interceptor.RateLimitInterceptor(2, 1, java.util.concurrent.TimeUnit.SECONDS)).build().newBuilder()
        .retryOnConnectionFailure(true)
        .addInterceptor { chain ->
            val request = chain.request()
            var response: Response? = null
            var lastException: Exception? = null
            var tryCount = 0
            val maxLimit = 3

            while (tryCount < maxLimit) {
                try {
                    response?.close()
                    response = chain.proceed(request)
                    if (response.isSuccessful) {
                        return@addInterceptor response
                    }
                    if (response.code in 400..499) {
                        return@addInterceptor response
                    }
                } catch (e: Exception) {
                    lastException = e
                }

                tryCount++
                if (tryCount < maxLimit) {
                    try {
                        Thread.sleep(1000L * tryCount)
                    } catch (_: Exception) {}
                }
            }

            response ?: throw lastException ?: java.io.IOException("Request failed after $maxLimit retries")
        }
        .build()

    private val apiBaseUrl = "https://ga-mobile-api.loklok.tv/cms/app"

    private val json = Json { ignoreUnknownKeys = true }

    // Cache for cursor pagination: page -> sort key
    private val filterCursorCache = mutableMapOf<Int, String>()

    // Unified headers mimicking the official mobile client to avoid 403 Forbidden and bypass geo-blocking
    private val mobileHeaders get() = headersBuilder().apply {
        add("Accept", "application/json")
        add("lang", "en")
        add("versioncode", "33")
        add("clienttype", "android_tem3")
        add("deviceid", "60A3305FDAAC489AAF4C7DD33B1483B4")

        val token = try {
            preferences.getString(PREF_TOKEN_KEY, "") ?: ""
        } catch (_: Exception) {
            ""
        }
        if (token.isNotBlank()) {
            add("token", token)
        }

        val country = try {
            preferences.getString(PREF_COUNTRY_KEY, "ph")
        } catch (_: Exception) {
            "ph"
        }
        val ip = when (country) {
            "ph" -> "120.28.0.1"
            "id" -> "103.10.66.1"
            "my" -> "175.139.192.1"
            "th" -> "171.96.0.1"
            "vn" -> "113.160.0.1"
            else -> ""
        }
        if (ip.isNotEmpty()) {
            add("X-Forwarded-For", ip)
            add("X-Real-IP", ip)
            add("clientip", ip)
            add("True-Client-IP", ip)
            add("CF-Connecting-IP", ip)
            add("X-Client-IP", ip)
            add("X-Originating-IP", ip)
            add("X-Remote-IP", ip)
            add("X-Remote-Addr", ip)
            add("Fastly-Client-IP", ip)
            add("Forwarded", "for=$ip;proto=https")
        }
        add("User-Agent", "Dalvik/2.1.0 (Linux; U; Android 12)")
    }.build()

    private fun encodeUrl(url: String): String {
        try {
            val schemeAndHost = url.substringBefore("://") + "://" + url.substringAfter("://").substringBefore("/")
            val path = url.substringAfter("://").substringAfter("/")
            val encodedPath = path.split("/").joinToString("/") { segment ->
                java.net.URLEncoder.encode(segment, "UTF-8")
                    .replace("+", "%20")
                    .replace("%21", "!")
                    .replace("%27", "'")
                    .replace("%28", "(")
                    .replace("%29", ")")
                    .replace("%7E", "~")
            }
            return "$schemeAndHost/$encodedPath"
        } catch (_: Exception) {
            return url
        }
    }

    private fun extractDramaId(mediaObj: JsonObject): String? {
        val jumpAddress = mediaObj["jumpAddress"]?.jsonPrimitive?.content
        if (!jumpAddress.isNullOrBlank()) {
            val idParam = jumpAddress.substringAfter("id=", "").substringBefore("&", "")
            if (idParam.isNotBlank()) return idParam
        }
        return mediaObj["id"]?.jsonPrimitive?.content
    }

    private fun ensureAbsoluteCoverUrl(url: String?): String {
        if (url.isNullOrBlank()) return ""
        val absoluteUrl = if (url.startsWith("http://") || url.startsWith("https://")) {
            url
        } else {
            val path = if (url.startsWith("/")) url else "/$url"
            "https://img.chhhn.com$path"
        }
        return encodeUrl(absoluteUrl.replace("img.loklok.tv", "img.chhhn.com"))
    }

    // Parse URL scheme: /outer/drama/{category}/{id}
    private fun parseAnimeUrl(url: String): Pair<String, String> {
        val parts = url.trimStart('/').split("/")
        if (parts.size >= 4 && parts[0] == "outer" && parts[1] == "drama") {
            return parts[3] to parts[2] // id to category
        }
        throw IllegalArgumentException("Invalid anime url: $url")
    }

    // Episode URL format: contentId|episodeId|category|code1:desc1,code2:desc2...|encodedSubtitles
    private fun parseEpisodeUrl(url: String): Triple<String, String, String> {
        val parts = url.split("|")
        require(parts.size >= 3) { "Invalid episode url: $url" }
        return Triple(parts[0], parts[1], parts[2])
    }

    // Extract definition list (code:desc pairs) from episode URL
    private fun parseEpisodeDefinitions(url: String): List<Pair<String, String>> {
        val parts = url.split("|")
        if (parts.size < 4) return emptyList()
        return parts[3].split(",").mapNotNull {
            val subParts = it.split(":")
            if (subParts.size == 2) subParts[0] to subParts[1] else null
        }
    }

    private fun searchPayload(keyword: String, page: Int): String {
        val escaped = keyword.replace("\\", "\\\\").replace("\"", "\\\"")
        return """{"searchKeyWord":"$escaped","size":20,"sort":"","searchType":"","page":$page}"""
    }

    // ============================== Popular / Latest ==============================

    override fun popularAnimeRequest(page: Int): Request {
        val pageIndex = (page - 1).coerceAtLeast(0)
        return GET("$apiBaseUrl/homePage/getHome?page=$pageIndex", mobileHeaders)
    }

    override fun popularAnimeParse(response: Response): AnimesPage {
        return parseHomeResponse(response)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val pageIndex = (page - 1).coerceAtLeast(0)
        return GET("$apiBaseUrl/homePage/getHome?page=$pageIndex", mobileHeaders)
    }

    override fun latestUpdatesParse(response: Response): AnimesPage {
        return parseHomeResponse(response)
    }

    private fun parseHomeResponse(response: Response): AnimesPage {
        checkResponse(response)
        val body = ensureJsonBody(response.body?.string(), "browse")
        if (body.isBlank()) return AnimesPage(emptyList(), false)

        val root = json.parseToJsonElement(body).jsonObject
        val data = root["data"]?.jsonObject ?: return AnimesPage(emptyList(), false)
        val recommendItems = data["recommendItems"] as? JsonArray ?: return AnimesPage(emptyList(), false)

        val animeList = mutableListOf<SAnime>()
        for (section in recommendItems) {
            val sectionObj = section as? JsonObject ?: continue
            val sectionType = sectionObj["homeSectionType"]?.jsonPrimitive?.content ?: ""
            if (sectionType == "BANNER" || sectionType == "BLOCK_GROUP") continue

            val mediaList = sectionObj["media"] as? JsonArray
                ?: sectionObj["recommendContentVOList"] as? JsonArray
                ?: continue

            for (media in mediaList) {
                val mediaObj = media as? JsonObject ?: continue
                val id = extractDramaId(mediaObj) ?: continue
                val category = mediaObj["category"]?.jsonPrimitive?.content
                    ?: mediaObj["domainType"]?.jsonPrimitive?.content ?: "1"
                val title = mediaObj["name"]?.jsonPrimitive?.content
                    ?: mediaObj["title"]?.jsonPrimitive?.content ?: ""
                val cover = mediaObj["coverVerticalUrl"]?.jsonPrimitive?.content
                    ?: mediaObj["imageUrl"]?.jsonPrimitive?.content
                    ?: mediaObj["cover"]?.jsonPrimitive?.content ?: ""

                animeList.add(
                    SAnime.create().apply {
                        setUrlWithoutDomain("/outer/drama/$category/$id")
                        this.title = title
                        thumbnail_url = ensureAbsoluteCoverUrl(cover)
                        initialized = false
                    },
                )
            }
        }

        return AnimesPage(animeList, animeList.isNotEmpty())
    }

    // ============================== Search ==============================

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        val pageIndex = (page - 1).coerceAtLeast(0)

        if (query.isNotBlank()) {
            val keyword = query.trim()
            val body = searchPayload(keyword, pageIndex).toRequestBody("application/json; charset=utf-8".toMediaType())
            return POST("$apiBaseUrl/search/v1/searchWithKeyWord", body = body, headers = mobileHeaders)
        } else {
            val typeFilter = filters.filterIsInstance<LoklokFilters.TypeFilter>().firstOrNull()
            val regionFilter = filters.filterIsInstance<LoklokFilters.RegionFilter>().firstOrNull()
            val categoryFilter = filters.filterIsInstance<LoklokFilters.CategoryFilter>().firstOrNull()
            val yearFilter = filters.filterIsInstance<LoklokFilters.YearFilter>().firstOrNull()
            val sortFilter = filters.filterIsInstance<LoklokFilters.SortFilter>().firstOrNull()

            val typeVal = typeFilter?.getValue() ?: "MOVIE,TV,VARIETY,COMIC,DOCUMENTARY,TVSPECIAL,MINISERIES,SETI,TALK"
            val regionVal = regionFilter?.getValue() ?: ""
            val categoryVal = categoryFilter?.getValue() ?: ""
            val yearVal = yearFilter?.getValue() ?: ""
            val sortVal = sortFilter?.getValue() ?: "count"

            if (page == 1) {
                filterCursorCache.clear()
            }
            val lastSort = filterCursorCache[page] ?: ""

            val body = """{"size":20,"params":"$typeVal","area":"$regionVal","category":"$categoryVal","year":"$yearVal","order":"$sortVal","sort":"$lastSort"}"""
                .toRequestBody("application/json; charset=utf-8".toMediaType())
            return POST("$apiBaseUrl/search/v1/search?page=$page", body = body, headers = mobileHeaders)
        }
    }

    override fun searchAnimeParse(response: Response): AnimesPage {
        checkResponse(response)
        val body = ensureJsonBody(response.body?.string(), "search")
        if (body.isBlank()) return AnimesPage(emptyList(), false)

        val root = json.parseToJsonElement(body).jsonObject
        val data = root["data"]?.jsonObject ?: return AnimesPage(emptyList(), false)
        val searchResults = data["searchResults"] as? JsonArray ?: return AnimesPage(emptyList(), false)

        val page = response.request.url.queryParameter("page")?.toIntOrNull() ?: 1
        val lastItem = searchResults.lastOrNull() as? JsonObject
        val lastSort = lastItem?.get("sort")?.jsonPrimitive?.content ?: ""
        if (lastSort.isNotBlank()) {
            filterCursorCache[page + 1] = lastSort
        }

        val animeList = mutableListOf<SAnime>()
        for (item in searchResults) {
            val obj = item as? JsonObject ?: continue
            val id = obj["id"]?.jsonPrimitive?.content ?: continue
            val category = obj["category"]?.jsonPrimitive?.content
                ?: obj["domainType"]?.jsonPrimitive?.content ?: "1"
            val title = obj["name"]?.jsonPrimitive?.content
                ?: obj["title"]?.jsonPrimitive?.content ?: ""
            val cover = obj["coverVerticalUrl"]?.jsonPrimitive?.content
                ?: obj["imageUrl"]?.jsonPrimitive?.content
                ?: obj["cover"]?.jsonPrimitive?.content ?: ""

            animeList.add(
                SAnime.create().apply {
                    setUrlWithoutDomain("/outer/drama/$category/$id")
                    this.title = title
                    thumbnail_url = ensureAbsoluteCoverUrl(cover)
                    initialized = false
                },
            )
        }

        return AnimesPage(animeList, animeList.isNotEmpty())
    }

    // ============================== Anime Details ==============================

    override fun animeDetailsRequest(anime: SAnime): Request {
        val (id, category) = parseAnimeUrl(anime.url)
        return GET("$apiBaseUrl/movieDrama/get?id=$id&category=$category", mobileHeaders)
    }

    override fun animeDetailsParse(response: Response): SAnime {
        checkResponse(response)
        val body = ensureJsonBody(response.body?.string(), "details")
        val root = json.parseToJsonElement(body).jsonObject
        val data = root["data"]?.jsonObject ?: throw Exception("No data")

        val u = response.request.url.toString()
        val id = u.substringAfter("id=").substringBefore("&")
        val category = u.substringAfter("category=").substringBefore("&")
        val name = data["name"]?.jsonPrimitive?.content ?: ""
        val cover = data["coverVerticalUrl"]?.jsonPrimitive?.content
            ?: data["coverHorizontalUrl"]?.jsonPrimitive?.content ?: ""
        val intro = data["introduction"]?.jsonPrimitive?.content ?: ""

        val genres = data["tagNameList"]?.jsonArray?.mapNotNull { it.jsonPrimitive.content } ?: emptyList()
        val areas = data["areaNameList"]?.jsonArray?.mapNotNull { it.jsonPrimitive.content } ?: emptyList()
        val score = data["score"]?.jsonPrimitive?.content ?: ""
        val year = data["year"]?.jsonPrimitive?.content ?: ""

        val descBuilder = StringBuilder(intro)
        if (year.isNotBlank() || score.isNotBlank()) {
            descBuilder.append("\n\n")
            if (year.isNotBlank()) descBuilder.append("Year: $year\n")
            if (score.isNotBlank()) descBuilder.append("Rating: $score★\n")
        }

        return SAnime.create().apply {
            setUrlWithoutDomain("/outer/drama/$category/$id")
            title = name
            thumbnail_url = ensureAbsoluteCoverUrl(cover)
            description = descBuilder.toString()
            genre = (genres + areas).joinToString(", ")
            status = SAnime.UNKNOWN
            initialized = true
        }
    }

    // ============================== Episodes ==============================

    override fun episodeListRequest(anime: SAnime): Request = animeDetailsRequest(anime)

    override fun episodeListParse(response: Response): List<SEpisode> {
        checkResponse(response)
        val body = ensureJsonBody(response.body?.string(), "episodes")
        if (body.isBlank()) return emptyList()

        val root = json.parseToJsonElement(body).jsonObject
        val data = root["data"]?.jsonObject ?: return emptyList()

        val (contentId, category) = response.request.url.toString().let { u ->
            val idParam = u.substringAfter("id=").substringBefore("&")
            val catParam = u.substringAfter("category=").substringBefore("&")
            idParam to catParam
        }

        val episodeVo = data["episodeVo"] as? JsonArray ?: return emptyList()
        return episodeVo.mapIndexed { index, item ->
            val obj = item as? JsonObject ?: return@mapIndexed SEpisode.create()
            val episodeId = obj["id"]?.jsonPrimitive?.content ?: ""
            val seriesNo = obj["seriesNo"]?.jsonPrimitive?.content?.toFloatOrNull() ?: (index + 1).toFloat()
            var name = obj["name"]?.jsonPrimitive?.content ?: ""
            if (name.isBlank()) {
                val seriesNoInt = seriesNo.toInt()
                name = if (seriesNo == seriesNoInt.toFloat()) {
                    "Episode $seriesNoInt"
                } else {
                    "Episode $seriesNo"
                }
            }

            // Construct definition code list string
            val defs = obj["definitionList"]?.jsonArray?.mapNotNull { defItem ->
                val defObj = defItem as? JsonObject ?: return@mapNotNull null
                val code = defObj["code"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val desc = defObj["description"]?.jsonPrimitive?.content ?: "Default"
                "$code:$desc"
            }?.joinToString(",") ?: ""

            SEpisode.create().apply {
                setUrlWithoutDomain("$contentId|$episodeId|$category|$defs")
                this.name = name
                episode_number = seriesNo
                date_upload = 0L
            }
        }.sortedByDescending { it.episode_number }
    }

    // ============================== Video Extraction ==============================

    override suspend fun getVideoList(episode: SEpisode): List<Video> {
        val (contentId, episodeId, category) = parseEpisodeUrl(episode.url)
        val definitions = parseEpisodeDefinitions(episode.url)

        // Fetch subtitle tracks live from the details endpoint
        val subtitleTracks = try {
            val detailUrl = "$apiBaseUrl/movieDrama/get?id=$contentId&category=$category"
            val detailResponse = client.newCall(GET(detailUrl, mobileHeaders)).execute()
            if (detailResponse.isSuccessful) {
                val detailBody = detailResponse.body.string()
                val detailRoot = json.parseToJsonElement(detailBody).jsonObject
                val detailData = detailRoot["data"]?.jsonObject
                val episodeVo = detailData?.get("episodeVo")

                // episodeVo can be a single JsonObject (movies) or a JsonArray (series)
                val episodeObj = when {
                    episodeVo is JsonArray -> episodeVo.firstOrNull { ep ->
                        (ep as? JsonObject)?.get("id")?.jsonPrimitive?.content == episodeId
                    } as? JsonObject
                    episodeVo is JsonObject -> episodeVo
                    else -> null
                }

                episodeObj?.get("subtitlingList")?.jsonArray?.mapNotNull { subItem ->
                    val subObj = subItem as? JsonObject ?: return@mapNotNull null
                    val lang = subObj["language"]?.jsonPrimitive?.content ?: "Unknown"
                    val subUrl = subObj["subtitlingUrl"]?.jsonPrimitive?.content ?: ""
                    if (subUrl.isNotBlank()) Track(subUrl, lang) else null
                } ?: emptyList()
            } else {
                emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        }

        val videoList = mutableListOf<Video>()

        // Fallback definition list if parsed definitions are empty
        val defs = definitions.ifEmpty {
            listOf("GROOT_HD" to "1080P", "GROOT_SD" to "720P", "GROOT_LD" to "360P")
        }

        for (def in defs) {
            try {
                val url = "$apiBaseUrl/media/previewInfo?category=$category&contentId=$contentId&episodeId=$episodeId&definition=${def.first}"
                val request = GET(url, mobileHeaders)
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) continue

                val respBody = response.body.string()
                if (respBody.isBlank() || respBody.trimStart().startsWith("<")) continue

                val root = json.parseToJsonElement(respBody).jsonObject
                val data = root["data"]?.jsonObject ?: continue
                val mediaUrl = data["mediaUrl"]?.jsonPrimitive?.content ?: continue

                if (mediaUrl.isNotBlank()) {
                    videoList.add(Video(mediaUrl, def.second, mediaUrl, mobileHeaders, subtitleTracks))
                }
            } catch (_: Exception) {
                // Keep trying other qualities if one fails
            }
        }

        if (videoList.isEmpty()) {
            throw Exception("No playable video links found for this episode.")
        }

        val preferredQuality = try {
            preferences.getString(PREF_QUALITY_KEY, "1080P") ?: "1080P"
        } catch (_: Exception) {
            "1080P"
        }

        return videoList.sortedWith(
            compareBy { it.quality != preferredQuality }
        )
    }

    // ============================== Helper Functions ==============================

    private fun checkResponse(response: Response) {
        if (!response.isSuccessful) {
            val msg = response.body?.string()?.take(200) ?: response.message
            if (response.code == 403) {
                showToast("Loklok: Geo-blocked (403). Please connect to a Southeast Asian VPN (Philippines/Indonesia).")
            }
            throw Exception("API error ${response.code}: $msg")
        }
    }

    private fun ensureJsonBody(body: String?, context: String): String {
        val b = body ?: throw Exception("Empty response")
        if (b.trimStart().startsWith("<")) {
            showToast("Loklok: Connection blocked by CDN. Please connect to a Southeast Asian VPN (Philippines/Indonesia).")
            throw Exception("API returned HTML instead of JSON ($context). Server may be blocking; try again later.")
        }
        return b
    }

    override fun getFilterList() = LoklokFilters.getFilters()

    companion object {
        private const val PREF_QUALITY_KEY = "preferred_quality"
        private const val PREF_COUNTRY_KEY = "spoofed_country"
        private const val PREF_TOKEN_KEY = "session_token"
    }
}
