package eu.kanade.tachiyomi.animeextension.all.loklok

import android.app.Application
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
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
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.IOException
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

class Loklok : ConfigurableAnimeSource, AnimeHttpSource() {

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    override val name = "Loklok"

    override val baseUrl = "https://www.loklok.fun"

    override val lang = "all"

    override val supportsLatest = true

    override val id: Long = 527189562810L

    private val apiBaseUrl = "https://api.loklok.fun"
    private val apiHost = "api.loklok.fun"

    private val json = Json { ignoreUnknownKeys = true }

    private val cursorCache = ConcurrentHashMap<String, String>()
    private val warmupLock = Any()
    private val rateLimitLock = Any()
    private val authLock = Any()

    @Volatile
    private var sessionWarmed = false

    @Volatile
    private var lastApiCallAtMs = 0L

    @Volatile
    private var homeNavId = DEFAULT_HOME_NAV_ID

    @Volatile
    private var rankNavId = DEFAULT_RANK_NAV_ID

    private data class PageTag(
        val page: Int,
        val cacheKey: String,
    )

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = PREF_QUALITY_KEY
            title = "Preferred video quality"
            entries = arrayOf("1080p", "720p", "540p", "360p")
            entryValues = arrayOf("1080P", "720P", "540P", "360P")
            setDefaultValue("1080P")
            summary = "%s"
        }.also(screen::addPreference)

        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_FETCH_ALL_QUALITIES_KEY
            title = "Fetch all video qualities"
            summary = "Off (Recommended): Fetches only your preferred quality (1 request) to avoid 'Too Many Requests' errors. Turn on to fetch every quality (slower)."
            setDefaultValue(false)
        }.also(screen::addPreference)

        EditTextPreference(screen.context).apply {
            key = PREF_EMAIL_KEY
            title = "Loklok Account Email (Optional)"
            summary = "Used for automatic sign-in when loading videos. Alternatively, tap 'Open in WebView' and sign in on loklok.fun."
            dialogTitle = "Account Email"
        }.also(screen::addPreference)

        EditTextPreference(screen.context).apply {
            key = PREF_PASSWORD_KEY
            title = "Loklok Account Password (Optional)"
            summary = "Used together with Account Email for automatic sign-in."
            dialogTitle = "Account Password"
        }.also(screen::addPreference)

        EditTextPreference(screen.context).apply {
            key = PREF_TOKEN_KEY
            title = "Session Token (Optional)"
            summary = "Manual loklok_token override. Leave blank to auto-detect from WebView sign-in or Account Email/Password."
            dialogTitle = "Session Token"
        }.also(screen::addPreference)
    }

    override val client: OkHttpClient = network.client.newBuilder()
        .retryOnConnectionFailure(true)
        .addInterceptor { chain ->
            val request = chain.request()
            if (request.url.host != apiHost) {
                return@addInterceptor chain.proceed(request)
            }

            val path = request.url.encodedPath
            if (requiresSessionWarmup(path)) {
                warmUpSessionIfNeeded(chain)
            }

            executeSignedApiRequest(chain, request)
        }
        .build()

    private fun requiresSessionWarmup(path: String): Boolean {
        return path.startsWith("/cms/pc/movieDrama/") || path.startsWith("/media/pc/")
    }

    private fun warmUpSessionIfNeeded(chain: Interceptor.Chain) {
        if (sessionWarmed) return
        synchronized(warmupLock) {
            if (sessionWarmed) return
            try {
                acquireApiSlot("/home/pc/navigationBar")
                val (navReq, navAesKey) = signApiRequest(GET("$apiBaseUrl/home/pc/navigationBar"))
                chain.proceed(navReq).use { navResp ->
                    if (navResp.isSuccessful) {
                        val raw = navResp.body.string()
                        val decrypted = LoklokCrypto.decryptResponseIfNeeded(
                            raw,
                            navResp.header("ecy"),
                            navAesKey,
                            json,
                        )
                        updateNavIdsFromResponse(decrypted)
                    }
                }

                acquireApiSlot("/home/pc/getHome")
                val (homeReq, _) = signApiRequest(GET("$apiBaseUrl/home/pc/getHome?navigationId=$homeNavId&page=0"))
                chain.proceed(homeReq).use {
                    // Opening home warms the deviceId session cleanly.
                }
                sessionWarmed = true
            } catch (_: Exception) {
                // Warm-up failure is non-fatal; regular call path will retry if decoyed.
            }
        }
    }

    private fun updateNavIdsFromResponse(body: String) {
        try {
            val root = json.parseToJsonElement(body) as? JsonObject ?: return
            val list = (root["data"] as? JsonObject)?.get("navigationBarItemList") as? JsonArray ?: return
            var foundHome: String? = null
            var foundRank: String? = null
            for (item in list) {
                val obj = item as? JsonObject ?: continue
                val idVal = obj.optString("id") ?: continue
                val redirectType = (obj.optString("redirectContentType") ?: "").uppercase()
                if (foundHome == null && (redirectType.isEmpty() || redirectType == "HOME" || redirectType == "HOMEPAGE")) {
                    foundHome = idVal
                }
                if (foundRank == null && redirectType == "RANK") {
                    foundRank = idVal
                }
            }
            if (foundHome != null) homeNavId = foundHome
            if (foundRank != null) rankNavId = foundRank
        } catch (_: Exception) {
        }
    }

    private fun acquireApiSlot(path: String) {
        val minIntervalMs = if (path.startsWith("/media/pc/getPlayInfo")) {
            MIN_PLAY_INTERVAL_MS
        } else {
            MIN_API_INTERVAL_MS
        }
        synchronized(rateLimitLock) {
            val elapsed = System.currentTimeMillis() - lastApiCallAtMs
            val waitMs = minIntervalMs - elapsed
            if (waitMs > 0) {
                try {
                    Thread.sleep(waitMs)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }
            lastApiCallAtMs = System.currentTimeMillis()
        }
    }

    private fun executeSignedApiRequest(chain: Interceptor.Chain, original: Request): Response {
        val path = original.url.encodedPath
        val maxRetries = 3
        var lastResponse: Response? = null
        var lastException: IOException? = null
        var didWarmRetry = false

        var attempt = 0
        while (attempt < maxRetries) {
            acquireApiSlot(path)
            val (signedRequest, aesKeyRaw) = signApiRequest(original)

            try {
                lastResponse?.close()
                lastResponse = null
                val response = chain.proceed(signedRequest)

                if (response.code == 429 || response.code in 500..599) {
                    val retryAfterMs = parseRetryAfterMs(response, attempt)
                    if (attempt < maxRetries - 1) {
                        response.close()
                        sleepQuietly(retryAfterMs)
                        attempt++
                        continue
                    }
                    return response
                }

                if (!response.isSuccessful) {
                    return response
                }

                val rawBody = response.body.string()
                val decryptedBody = LoklokCrypto.decryptResponseIfNeeded(
                    rawBody,
                    response.header("ecy"),
                    aesKeyRaw,
                    json,
                )

                if (decryptedBody.contains(DECOY_CODE) && !didWarmRetry && path == "/cms/pc/movieDrama/get") {
                    response.close()
                    sessionWarmed = false
                    warmUpSessionIfNeeded(chain)
                    didWarmRetry = true
                    continue
                }

                if (isJsonRateLimited(decryptedBody) && attempt < maxRetries - 1) {
                    response.close()
                    sleepQuietly(BACKOFF_BASE_MS * (1L shl attempt))
                    attempt++
                    continue
                }

                val mediaType = response.body.contentType() ?: JSON_MEDIA_TYPE
                return response.newBuilder()
                    .body(decryptedBody.toResponseBody(mediaType))
                    .removeHeader("ecy")
                    .build()
            } catch (e: IOException) {
                lastException = e
                if (attempt < maxRetries - 1) {
                    sleepQuietly(BACKOFF_BASE_MS * (1L shl attempt))
                }
                attempt++
            }
        }

        return lastResponse ?: throw (lastException ?: IOException("Loklok API request failed after $maxRetries attempts"))
    }

    private fun isJsonRateLimited(body: String): Boolean {
        if (body.isBlank() || body.trimStart().startsWith("<")) return false
        return try {
            val root = json.parseToJsonElement(body) as? JsonObject ?: return false
            val code = root.optString("code") ?: return false
            if (code == "00000") return false
            val msg = (root.optString("msg") ?: "").lowercase()
            msg.contains("too many") || msg.contains("frequent") || msg.contains("rate limit")
        } catch (_: Exception) {
            false
        }
    }

    private fun parseRetryAfterMs(response: Response, attempt: Int): Long {
        val header = response.header("Retry-After")?.trim()?.toLongOrNull()
        if (header != null && header > 0) {
            return header.coerceIn(1L, 10L) * 1000L
        }
        return BACKOFF_BASE_MS * (1L shl attempt)
    }

    private fun sleepQuietly(durationMs: Long) {
        try {
            Thread.sleep(durationMs)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    private fun signApiRequest(original: Request): Pair<Request, String> {
        val url = original.url
        val path = url.encodedPath
        val bodyBytes: ByteArray?
        val bodyText: String
        val existingBody = original.body
        if (existingBody != null) {
            val buffer = Buffer()
            existingBody.writeTo(buffer)
            bodyBytes = buffer.readByteArray()
            bodyText = bodyBytes.toString(Charsets.UTF_8)
        } else {
            bodyBytes = null
            bodyText = ""
        }

        val aesKeyRaw = LoklokCrypto.generateAesKey()
        val currentTime = System.currentTimeMillis().toString()
        val deviceId = getOrCreateDeviceId()
        val querySignStr = LoklokCrypto.serializeQueryForSign(url)
        val payloadSignStr = querySignStr.ifEmpty {
            LoklokCrypto.serializeJsonBodyForSign(bodyText, json)
        }

        val rsaKey = LoklokCrypto.rsaEncryptBase64(aesKeyRaw)
        val sign = LoklokCrypto.computeSign(payloadSignStr, currentTime, aesKeyRaw)
        val usertype = LoklokCrypto.computeUserType(deviceId, aesKeyRaw)

        val builder = original.newBuilder()
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .header("aesKey", rsaKey)
            .header("clientType", CLIENT_TYPE)
            .header("currentTime", currentTime)
            .header("deviceId", deviceId)
            .header("keke", "false")
            .header("lang", "en")
            .header("sign", sign)
            .header("timezone", LoklokCrypto.currentTimezoneHeader())
            .header("usertype", usertype)
            .header("versionCode", VERSION_CODE)
            .header("Origin", baseUrl)
            .header("Referer", "$baseUrl/")
            .header("User-Agent", WEB_USER_AGENT)

        if (path != LOGIN_PATH) {
            val token = resolveAuthToken()
            if (!token.isNullOrBlank()) {
                builder.header("token", token)
            } else {
                builder.removeHeader("token")
            }
        } else {
            builder.removeHeader("token")
        }

        if (bodyBytes != null) {
            val contentType = existingBody?.contentType() ?: JSON_MEDIA_TYPE
            builder.method(original.method, bodyBytes.toRequestBody(contentType))
        }

        return builder.build() to aesKeyRaw
    }

    private fun getOrCreateDeviceId(): String {
        val cookieDeviceId = readCookieValue("deviceid")
        if (!cookieDeviceId.isNullOrBlank()) {
            savePreference(PREF_DEVICE_ID_KEY, cookieDeviceId)
            return cookieDeviceId
        }
        val stored = readPreference(PREF_DEVICE_ID_KEY)
        if (stored.isNotBlank()) return stored

        val generated = LoklokCrypto.generateDeviceId()
        savePreference(PREF_DEVICE_ID_KEY, generated)
        return generated
    }

    private fun resolveAuthToken(): String? {
        val manual = readPreference(PREF_TOKEN_KEY).trim()
        if (manual.isNotEmpty()) return manual

        val cookieToken = readCookieValue("loklok_token")
        if (!cookieToken.isNullOrBlank()) return cookieToken

        val cached = readPreference(PREF_CACHED_TOKEN_KEY).trim()
        if (cached.isNotEmpty()) return cached

        return null
    }

    private fun readCookieValue(cookieName: String): String? {
        try {
            val cookies = network.client.cookieJar.loadForRequest(baseUrl.toHttpUrl())
            val jarMatch = cookies.firstOrNull { it.name == cookieName && it.value.isNotBlank() }?.value
            if (!jarMatch.isNullOrBlank()) {
                return decodeCookieValue(jarMatch)
            }
        } catch (_: Exception) {
        }

        return try {
            val rawCookieHeader = CookieManager.getInstance()?.getCookie(baseUrl) ?: return null
            val prefix = "$cookieName="
            val entry = rawCookieHeader.split(";")
                .map { it.trim() }
                .firstOrNull { it.startsWith(prefix) }
                ?: return null
            val rawVal = entry.substring(prefix.length)
            decodeCookieValue(rawVal).takeIf { it.isNotBlank() }
        } catch (_: Throwable) {
            null
        }
    }

    private fun decodeCookieValue(raw: String): String {
        return try {
            URLDecoder.decode(raw, "UTF-8")
        } catch (_: Exception) {
            raw
        }
    }

    private fun loginWithCredentialsIfConfigured(forceRefresh: Boolean): String? {
        val email = readPreference(PREF_EMAIL_KEY).trim()
        val password = readPreference(PREF_PASSWORD_KEY)
        if (email.isEmpty() || password.isEmpty()) return null

        synchronized(authLock) {
            if (!forceRefresh) {
                val existing = resolveAuthToken()
                if (!existing.isNullOrBlank()) return existing
            } else {
                savePreference(PREF_CACHED_TOKEN_KEY, "")
            }

            val bodyJson = buildJsonObject {
                put("param", email)
                put("pwd", password)
                put("loginType", "email")
            }.toString()

            val request = POST(
                "$apiBaseUrl$LOGIN_PATH",
                body = bodyJson.toRequestBody(JSON_MEDIA_TYPE),
            )

            return try {
                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    val raw = resp.body.string()
                    if (raw.isBlank() || raw.trimStart().startsWith("<")) return@use null
                    val root = json.parseToJsonElement(raw).jsonObject
                    if (root.optString("code") != "00000") return@use null
                    val data = root["data"] as? JsonObject ?: return@use null
                    val token = data.optString("token") ?: data.optString("jwtToken") ?: return@use null
                    savePreference(PREF_CACHED_TOKEN_KEY, token)
                    token
                }
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun readPreference(key: String): String {
        return try {
            preferences.getString(key, "") ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    private fun savePreference(key: String, value: String) {
        try {
            preferences.edit().putString(key, value).apply()
        } catch (_: Exception) {
        }
    }

    private fun showToast(message: String) {
        try {
            val context = Injekt.get<Application>()
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        } catch (_: Exception) {
        }
    }

    private fun JsonObject.optString(key: String): String? {
        val el = this[key] ?: return null
        if (el is JsonNull) return null
        val primitive = el as? JsonPrimitive ?: return null
        val content = primitive.content
        return content.takeIf { it.isNotBlank() && it != "null" }
    }

    private fun encodeUrl(url: String): String {
        return try {
            val schemeAndHost = url.substringBefore("://") + "://" + url.substringAfter("://").substringBefore("/")
            val remainder = url.substringAfter("://").substringAfter("/", "")
            if (remainder.isEmpty()) return url
            val pathPart = remainder.substringBefore("?")
            val queryPart = remainder.substringAfter("?", "")
            val encodedPath = pathPart.split("/").joinToString("/") { segment ->
                URLEncoder.encode(segment, "UTF-8")
                    .replace("+", "%20")
                    .replace("%21", "!")
                    .replace("%27", "'")
                    .replace("%28", "(")
                    .replace("%29", ")")
                    .replace("%7E", "~")
            }
            if (queryPart.isNotEmpty()) {
                "$schemeAndHost/$encodedPath?$queryPart"
            } else {
                "$schemeAndHost/$encodedPath"
            }
        } catch (_: Exception) {
            url
        }
    }

    private fun extractDramaId(mediaObj: JsonObject): String? {
        val jumpAddress = mediaObj.optString("jumpAddress")
        if (!jumpAddress.isNullOrBlank()) {
            val idParam = jumpAddress.substringAfter("id=", "").substringBefore("&", "")
            if (idParam.isNotBlank()) return idParam
        }
        return mediaObj.optString("id") ?: mediaObj.optString("contentId")
    }

    private fun extractCategoryFromJump(jumpAddress: String?): String? {
        if (jumpAddress.isNullOrBlank()) return null
        val typeParam = jumpAddress.substringAfter("type=", "").substringBefore("&", "")
        return typeParam.takeIf { it.isNotBlank() }
    }

    private fun ensureAbsoluteCoverUrl(url: String?): String {
        if (url.isNullOrBlank()) return ""
        val absoluteUrl = if (url.startsWith("http://") || url.startsWith("https://")) {
            url
        } else {
            val path = if (url.startsWith("/")) url else "/$url"
            "https://img.netpop.app$path"
        }
        return encodeUrl(absoluteUrl)
    }

    private fun parseAnimeUrl(url: String): Pair<String, String> {
        val parts = url.trimStart('/').split("/")
        if (parts.size >= 4 && parts[0] == "outer" && parts[1] == "drama") {
            return parts[3] to parts[2]
        }
        throw IllegalArgumentException("Invalid anime url: $url")
    }

    private fun parseEpisodeUrl(url: String): Triple<String, String, String> {
        val parts = url.split("|")
        require(parts.size >= 3) { "Invalid episode url: $url" }
        return Triple(parts[0], parts[1], parts[2])
    }

    private fun parseEpisodeDefinitions(url: String): List<Pair<String, String>> {
        val parts = url.split("|")
        if (parts.size < 4 || parts[3].isBlank()) return emptyList()
        return parts[3].split(",").mapNotNull { entry ->
            val subParts = entry.split(":")
            if (subParts.size == 2 && subParts[0].isNotBlank()) subParts[0] to subParts[1] else null
        }
    }

    // ============================== Popular / Latest ==============================

    override fun popularAnimeRequest(page: Int): Request {
        val pageIndex = (page - 1).coerceAtLeast(0)
        return GET("$apiBaseUrl/home/pc/getHomeRank?navigationId=$rankNavId&page=$pageIndex&size=$PAGE_SIZE")
    }

    override fun popularAnimeParse(response: Response): AnimesPage {
        checkResponse(response)
        val body = ensureJsonBody(response.body.string(), "popular")
        if (body.isBlank()) return AnimesPage(emptyList(), false)

        val root = json.parseToJsonElement(body).jsonObject
        val code = root.optString("code")
        if (code != "00000") {
            throw Exception("API error ($code): ${root.optString("msg") ?: "Failed to load rankings"}")
        }

        val groups = root["data"] as? JsonArray ?: return AnimesPage(emptyList(), false)
        val animeList = mutableListOf<SAnime>()
        val seenKeys = mutableSetOf<String>()
        var hasMoreFromGroups = false

        for (group in groups) {
            val groupObj = group as? JsonObject ?: continue
            val rankingContents = groupObj["rankingContents"] as? JsonArray ?: continue
            if (rankingContents.isEmpty()) continue

            val endFlag = (groupObj["endFlag"] as? JsonPrimitive)?.content?.toBooleanStrictOrNull() ?: false
            if (!endFlag) {
                hasMoreFromGroups = true
            }

            for (item in rankingContents) {
                val obj = item as? JsonObject ?: continue
                val id = extractDramaId(obj) ?: continue
                val category = obj.optString("category")
                    ?: obj.optString("domainType")
                    ?: extractCategoryFromJump(obj.optString("jumpAddress"))
                    ?: "1"
                val title = obj.optString("title") ?: obj.optString("name") ?: continue
                val cover = obj.optString("verticalImage")
                    ?: obj.optString("coverVerticalUrl")
                    ?: obj.optString("imageUrl")
                    ?: ""

                if (!seenKeys.add("$category/$id")) continue

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

        return AnimesPage(animeList, animeList.isNotEmpty() && hasMoreFromGroups)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val pageIndex = (page - 1).coerceAtLeast(0)
        return GET("$apiBaseUrl/home/pc/getHome?navigationId=$homeNavId&page=$pageIndex")
    }

    override fun latestUpdatesParse(response: Response): AnimesPage {
        checkResponse(response)
        val body = ensureJsonBody(response.body.string(), "latest")
        if (body.isBlank()) return AnimesPage(emptyList(), false)

        val root = json.parseToJsonElement(body).jsonObject
        val code = root.optString("code")
        if (code != "00000") {
            throw Exception("API error ($code): ${root.optString("msg") ?: "Failed to load catalog"}")
        }

        val data = root["data"] as? JsonObject ?: return AnimesPage(emptyList(), false)
        val recommendItems = data["recommendItems"] as? JsonArray ?: return AnimesPage(emptyList(), false)

        val animeList = mutableListOf<SAnime>()
        val seenKeys = mutableSetOf<String>()

        for (section in recommendItems) {
            val sectionObj = section as? JsonObject ?: continue
            val sectionType = sectionObj.optString("homeSectionType") ?: ""
            if (sectionType == "BANNER" || sectionType == "BLOCK_GROUP") continue

            val mediaList = sectionObj["recommendContentVOList"] as? JsonArray
                ?: sectionObj["media"] as? JsonArray
                ?: continue

            for (media in mediaList) {
                val mediaObj = media as? JsonObject ?: continue
                val id = extractDramaId(mediaObj) ?: continue
                val category = mediaObj.optString("category")
                    ?: mediaObj.optString("domainType")
                    ?: extractCategoryFromJump(mediaObj.optString("jumpAddress"))
                    ?: "1"
                val title = mediaObj.optString("name") ?: mediaObj.optString("title") ?: continue
                val cover = mediaObj.optString("coverVerticalUrl")
                    ?: mediaObj.optString("imageUrl")
                    ?: mediaObj.optString("cover")
                    ?: ""

                if (!seenKeys.add("$category/$id")) continue

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
        if (query.isNotBlank()) {
            val keyword = query.trim()
            val cacheKey = "q:$keyword"
            if (page == 1) {
                clearCursorPrefix(cacheKey)
            }
            val lastSort = cursorCache["$cacheKey:$page"]
            val payload = buildJsonObject {
                put("size", PAGE_SIZE)
                put("searchKeyWord", keyword)
                if (page > 1) {
                    put("from", ((page - 1) * PAGE_SIZE).toString())
                    if (!lastSort.isNullOrBlank()) {
                        put("sort", lastSort)
                    }
                }
            }.toString()

            return POST(
                "$apiBaseUrl/cms/pc/search/searchWithKeyWord",
                body = payload.toRequestBody(JSON_MEDIA_TYPE),
            ).newBuilder()
                .tag(PageTag::class.java, PageTag(page, cacheKey))
                .build()
        }

        val typeFilter = filters.filterIsInstance<LoklokFilters.TypeFilter>().firstOrNull()
        val regionFilter = filters.filterIsInstance<LoklokFilters.RegionFilter>().firstOrNull()
        val categoryFilter = filters.filterIsInstance<LoklokFilters.CategoryFilter>().firstOrNull()
        val yearFilter = filters.filterIsInstance<LoklokFilters.YearFilter>().firstOrNull()
        val sortFilter = filters.filterIsInstance<LoklokFilters.SortFilter>().firstOrNull()

        val typeVal = typeFilter?.getValue() ?: DEFAULT_TYPE_PARAMS
        val typeLabel = typeFilter?.getLabel() ?: "ALL"
        val regionVal = regionFilter?.getValue() ?: ""
        val categoryVal = categoryFilter?.getValue() ?: ""
        val yearVal = yearFilter?.getValue() ?: ""
        val sortVal = sortFilter?.getValue() ?: "count"

        val cacheKey = "f:$typeVal|$regionVal|$categoryVal|$yearVal|$sortVal"
        if (page == 1) {
            clearCursorPrefix(cacheKey)
        }
        val lastSort = cursorCache["$cacheKey:$page"]

        val payload = buildJsonObject {
            put("size", PAGE_SIZE)
            put("params", typeVal)
            put("searchScreeningName", typeLabel)
            put("order", sortVal)
            if (regionVal.isNotBlank()) put("area", regionVal)
            if (categoryVal.isNotBlank()) put("category", categoryVal)
            if (yearVal.isNotBlank()) put("year", yearVal)
            if (page > 1) {
                put("from", ((page - 1) * PAGE_SIZE).toString())
                if (!lastSort.isNullOrBlank()) {
                    put("sort", lastSort)
                }
            }
        }.toString()

        return POST(
            "$apiBaseUrl/cms/pc/search/screen/list",
            body = payload.toRequestBody(JSON_MEDIA_TYPE),
        ).newBuilder()
            .tag(PageTag::class.java, PageTag(page, cacheKey))
            .build()
    }

    private fun clearCursorPrefix(prefix: String) {
        val matchPrefix = "$prefix:"
        cursorCache.keys.removeIf { it.startsWith(matchPrefix) }
    }

    override fun searchAnimeParse(response: Response): AnimesPage {
        checkResponse(response)
        val body = ensureJsonBody(response.body.string(), "search")
        if (body.isBlank()) return AnimesPage(emptyList(), false)

        val root = json.parseToJsonElement(body).jsonObject
        val code = root.optString("code")
        if (code != "00000") {
            throw Exception("API error ($code): ${root.optString("msg") ?: "Search failed"}")
        }

        val searchResults = root["data"] as? JsonArray
            ?: (root["data"] as? JsonObject)?.get("searchResults") as? JsonArray
            ?: return AnimesPage(emptyList(), false)

        val tag = response.request.tag(PageTag::class.java)
        val lastSort = (searchResults.lastOrNull() as? JsonObject)?.optString("sort")
        if (tag != null && !lastSort.isNullOrBlank()) {
            cursorCache["${tag.cacheKey}:${tag.page + 1}"] = lastSort
        }

        val animeList = mutableListOf<SAnime>()
        for (item in searchResults) {
            val obj = item as? JsonObject ?: continue
            val id = obj.optString("id") ?: continue
            val category = obj.optString("domainType")
                ?: obj.optString("category")
                ?: "1"
            val title = obj.optString("name") ?: obj.optString("title") ?: continue
            val cover = obj.optString("coverVerticalUrl")
                ?: obj.optString("coverHorizontalUrl")
                ?: obj.optString("imageUrl")
                ?: obj.optString("cover")
                ?: ""

            animeList.add(
                SAnime.create().apply {
                    setUrlWithoutDomain("/outer/drama/$category/$id")
                    this.title = title
                    thumbnail_url = ensureAbsoluteCoverUrl(cover)
                    initialized = false
                },
            )
        }

        return AnimesPage(animeList, searchResults.size >= PAGE_SIZE)
    }

    // ============================== Anime Details ==============================

    override fun animeDetailsRequest(anime: SAnime): Request {
        val (id, category) = parseAnimeUrl(anime.url)
        return GET("$apiBaseUrl/cms/pc/movieDrama/get?id=$id&category=$category&reliableDef=0")
    }

    override fun animeDetailsParse(response: Response): SAnime {
        checkResponse(response)
        val body = ensureJsonBody(response.body.string(), "details")
        val root = json.parseToJsonElement(body).jsonObject
        val apiCode = root.optString("code")
        if (apiCode != "00000") {
            throw Exception("API error ($apiCode): ${root.optString("msg") ?: "Failed to load details"}")
        }

        val data = root["data"] as? JsonObject ?: throw Exception("No details data returned")
        if (data.optString("code") == DECOY_CODE) {
            val decoyMsg = data.optString("msg") ?: DECOY_CODE
            throw Exception("Loklok title is temporarily unavailable or geo-restricted ($decoyMsg).")
        }

        val reqUrl = response.request.url
        val id = reqUrl.queryParameter("id") ?: ""
        val category = reqUrl.queryParameter("category") ?: "1"
        val name = data.optString("name") ?: data.optString("title") ?: ""
        val cover = data.optString("coverVerticalUrl")
            ?: data.optString("coverHorizontalUrl")
            ?: ""
        val intro = data.optString("introduction") ?: ""

        val genres = (data["tagNameList"] as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p !is JsonNull }?.content }
            ?: emptyList()
        val areas = (data["areaNameList"] as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p !is JsonNull }?.content }
            ?: emptyList()
        val score = data.optString("score") ?: ""
        val year = data.optString("year") ?: ""
        val updateStatus = (data["updateInfo"] as? JsonObject)?.optString("updateStatus")?.toIntOrNull()

        val descBuilder = StringBuilder(intro)
        if (year.isNotBlank() || score.isNotBlank()) {
            if (descBuilder.isNotEmpty()) descBuilder.append("\n\n")
            if (year.isNotBlank()) descBuilder.append("Year: $year\n")
            if (score.isNotBlank()) descBuilder.append("Rating: $score★\n")
        }

        return SAnime.create().apply {
            setUrlWithoutDomain("/outer/drama/$category/$id")
            title = name
            thumbnail_url = ensureAbsoluteCoverUrl(cover)
            description = descBuilder.toString().trimEnd()
            genre = (genres + areas).distinct().joinToString(", ")
            status = when (updateStatus) {
                4 -> SAnime.COMPLETED
                2, 3 -> SAnime.ONGOING
                else -> SAnime.UNKNOWN
            }
            initialized = true
        }
    }

    // ============================== Episodes ==============================

    override fun episodeListRequest(anime: SAnime): Request = animeDetailsRequest(anime)

    override fun episodeListParse(response: Response): List<SEpisode> {
        checkResponse(response)
        val body = ensureJsonBody(response.body.string(), "episodes")
        if (body.isBlank()) return emptyList()

        val root = json.parseToJsonElement(body).jsonObject
        val apiCode = root.optString("code")
        if (apiCode != "00000") {
            throw Exception("API error ($apiCode): ${root.optString("msg") ?: "Failed to load episodes"}")
        }

        val data = root["data"] as? JsonObject ?: return emptyList()
        if (data.optString("code") == DECOY_CODE) {
            val decoyMsg = data.optString("msg") ?: DECOY_CODE
            throw Exception("Loklok episodes unavailable or geo-restricted ($decoyMsg).")
        }

        val reqUrl = response.request.url
        val contentId = reqUrl.queryParameter("id") ?: return emptyList()
        val category = reqUrl.queryParameter("category") ?: "1"

        val episodeObjs = when (val episodeEl = data["episodeVo"]) {
            is JsonArray -> episodeEl.filterIsInstance<JsonObject>()
            is JsonObject -> listOf(episodeEl)
            else -> emptyList()
        }.ifEmpty {
            fetchFallbackEpisodeList(contentId, category)
        }

        val totalCount = episodeObjs.size
        return episodeObjs.mapIndexed { index, obj ->
            val episodeId = obj.optString("id") ?: ""
            val rawSeriesNo = obj.optString("seriesNo")?.toFloatOrNull()
            val seriesNo = when {
                rawSeriesNo != null && rawSeriesNo > 0f -> rawSeriesNo
                totalCount == 1 -> 1f
                else -> (index + 1).toFloat()
            }

            var epName = obj.optString("name") ?: ""
            if (epName.isBlank()) {
                epName = if (totalCount == 1 && (rawSeriesNo == null || rawSeriesNo <= 0f) && category == "0") {
                    "Movie"
                } else {
                    val seriesNoInt = seriesNo.toInt()
                    if (seriesNo == seriesNoInt.toFloat()) {
                        "Episode $seriesNoInt"
                    } else {
                        "Episode $seriesNo"
                    }
                }
            }

            val defs = (obj["definitionList"] as? JsonArray)?.mapNotNull { defItem ->
                val defObj = defItem as? JsonObject ?: return@mapNotNull null
                val code = defObj.optString("code") ?: return@mapNotNull null
                val desc = defObj.optString("description")
                    ?: defObj.optString("fullDescription")
                    ?: code
                "$code:$desc"
            }?.joinToString(",") ?: ""

            SEpisode.create().apply {
                setUrlWithoutDomain("$contentId|$episodeId|$category|$defs")
                name = epName
                episode_number = seriesNo
                date_upload = 0L
            }
        }.sortedByDescending { it.episode_number }
    }

    private fun fetchFallbackEpisodeList(contentId: String, category: String): List<JsonObject> {
        return try {
            val url = "$apiBaseUrl/cms/pc/movieDrama/queryEpisodeList?id=$contentId&category=$category"
            client.newCall(GET(url)).execute().use { resp ->
                if (!resp.isSuccessful) return@use emptyList()
                val raw = resp.body.string()
                if (raw.isBlank() || raw.trimStart().startsWith("<")) return@use emptyList()
                val root = json.parseToJsonElement(raw).jsonObject
                if (root.optString("code") != "00000") return@use emptyList()
                (root["data"] as? JsonArray)?.filterIsInstance<JsonObject>() ?: emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    // ============================== Video Extraction ==============================

    override suspend fun getVideoList(episode: SEpisode): List<Video> {
        val (contentId, episodeId, category) = parseEpisodeUrl(episode.url)
        val rawDefs = parseEpisodeDefinitions(episode.url).ifEmpty { DEFAULT_DEFINITIONS }

        val preferredQuality = readPreference(PREF_QUALITY_KEY).ifBlank { "1080P" }
        val fetchAllQualities = try {
            preferences.getBoolean(PREF_FETCH_ALL_QUALITIES_KEY, false)
        } catch (_: Exception) {
            false
        }

        val orderedDefs = rawDefs.sortedWith(
            compareBy<Pair<String, String>>(
                { !it.second.equals(preferredQuality, ignoreCase = true) },
                { qualityRank(it.first, it.second) },
            ),
        )

        if (resolveAuthToken().isNullOrBlank()) {
            loginWithCredentialsIfConfigured(forceRefresh = false)
        }

        val videoHeaders = Headers.Builder()
            .add("User-Agent", WEB_USER_AGENT)
            .build()

        val videoList = mutableListOf<Video>()
        val seenUrls = mutableSetOf<String>()
        var cachedFallbackSubtitles: List<Track>? = null
        var authRetried = false
        var lastError: String? = null
        var playRequestsCount = 0

        for (def in orderedDefs) {
            val playUrl = "$apiBaseUrl/media/pc/getPlayInfo?contentId=$contentId&category=$category&episodeId=$episodeId&definition=${def.first}"
            var shouldRetryCurrentDef: Boolean

            do {
                shouldRetryCurrentDef = false
                try {
                    playRequestsCount++
                    client.newCall(GET(playUrl)).execute().use { response ->
                        if (response.code == 429) {
                            lastError = "Loklok rate limit (429 Too Many Requests). Please wait a few seconds and try again."
                            if (videoList.isNotEmpty()) {
                                return videoList
                            }
                            throw RateLimitException(lastError!!)
                        }

                        if (!response.isSuccessful) {
                            lastError = "Play API HTTP ${response.code}"
                            return@use
                        }

                        val respBody = response.body.string()
                        if (respBody.isBlank() || respBody.trimStart().startsWith("<")) {
                            lastError = "Blocked or invalid response from Loklok media server."
                            return@use
                        }

                        val root = json.parseToJsonElement(respBody).jsonObject
                        val apiCode = root.optString("code")

                        if (apiCode == AUTH_REQUIRED_CODE) {
                            if (!authRetried) {
                                authRetried = true
                                val refreshedToken = loginWithCredentialsIfConfigured(forceRefresh = true)
                                if (!refreshedToken.isNullOrBlank()) {
                                    shouldRetryCurrentDef = true
                                    return@use
                                }
                            }
                            val msg = "Loklok requires sign-in to load video streams (A0230). " +
                                "Tap 'Open in WebView' to sign in on loklok.fun, or enter your Email/Password or Session Token in Extension Settings."
                            showToast("Loklok: Sign-in required (Open in WebView -> loklok.fun)")
                            throw AuthRequiredException(msg)
                        }

                        if (apiCode != "00000") {
                            lastError = root.optString("msg") ?: "API error ($apiCode)"
                            return@use
                        }

                        val data = root["data"] as? JsonObject ?: run {
                            lastError = "Empty media payload."
                            return@use
                        }

                        if (data.optString("code") == DECOY_CODE) {
                            lastError = data.optString("msg") ?: DECOY_CODE
                            return@use
                        }

                        val mediaUrl = data.optString("mediaUrl") ?: run {
                            lastError = "No mediaUrl returned for ${def.second}."
                            return@use
                        }

                        if (!seenUrls.add(mediaUrl)) return@use

                        var tracks = extractSubtitleTracks(data)
                        if (tracks.isEmpty()) {
                            if (cachedFallbackSubtitles == null) {
                                cachedFallbackSubtitles = fetchSubtitlesFallback(contentId, episodeId, category)
                            }
                            tracks = cachedFallbackSubtitles!!
                        }

                        val freeDuration = data.optString("freeDuration")?.toLongOrNull() ?: 0L
                        val totalDuration = data.optString("totalDuration")?.toLongOrNull() ?: 0L
                        val isPreview = freeDuration > 0L && totalDuration > 0L && freeDuration < totalDuration

                        val resolvedCode = data.optString("currentDefinition")
                        val baseLabel = rawDefs.firstOrNull { it.first == resolvedCode }?.second ?: def.second
                        val qualityLabel = if (isPreview) "$baseLabel (Preview)" else baseLabel

                        videoList.add(Video(mediaUrl, qualityLabel, mediaUrl, videoHeaders, tracks))

                        if (!fetchAllQualities && (!isPreview || playRequestsCount >= 2)) {
                            return videoList
                        }
                    }
                } catch (e: RateLimitException) {
                    if (videoList.isNotEmpty()) return videoList
                    throw Exception(e.message)
                } catch (e: AuthRequiredException) {
                    throw Exception(e.message)
                } catch (e: Exception) {
                    if (lastError == null) {
                        lastError = e.message ?: "Failed to resolve ${def.second}"
                    }
                }
            } while (shouldRetryCurrentDef)
        }

        if (videoList.isEmpty()) {
            throw Exception(lastError ?: "No playable video links found for this episode.")
        }

        return videoList
    }

    private class RateLimitException(message: String) : Exception(message)
    private class AuthRequiredException(message: String) : Exception(message)

    private fun qualityRank(code: String, desc: String): Int {
        return when {
            code == "GROOT_HD" || desc.contains("1080") -> 0
            code == "GROOT_SD" || desc.contains("720") -> 1
            code == "GROOT_LD" || desc.contains("540") || desc.contains("480") -> 2
            code == "GROOT_FD" || desc.contains("360") -> 3
            else -> 4
        }
    }

    private fun extractSubtitleTracks(container: JsonObject?): List<Track> {
        val list = container?.get("subtitlingList") as? JsonArray ?: return emptyList()
        return list.mapNotNull { subItem ->
            val subObj = subItem as? JsonObject ?: return@mapNotNull null
            val subUrl = subObj.optString("subtitlingUrl") ?: return@mapNotNull null
            val langLabel = subObj.optString("language")
                ?: subObj.optString("languageAbbr")
                ?: "Unknown"
            Track(subUrl, langLabel)
        }
    }

    private fun fetchSubtitlesFallback(contentId: String, episodeId: String, category: String): List<Track> {
        return try {
            val detailUrl = "$apiBaseUrl/cms/pc/movieDrama/get?id=$contentId&category=$category&reliableDef=0"
            client.newCall(GET(detailUrl)).execute().use { detailResponse ->
                if (!detailResponse.isSuccessful) return@use emptyList()
                val detailBody = detailResponse.body.string()
                if (detailBody.isBlank() || detailBody.trimStart().startsWith("<")) return@use emptyList()
                val detailRoot = json.parseToJsonElement(detailBody).jsonObject
                if (detailRoot.optString("code") != "00000") return@use emptyList()
                val detailData = detailRoot["data"] as? JsonObject ?: return@use emptyList()
                val episodeObj = when (val epEl = detailData["episodeVo"]) {
                    is JsonArray -> epEl.firstOrNull { ep ->
                        (ep as? JsonObject)?.optString("id") == episodeId
                    } as? JsonObject
                    is JsonObject -> epEl
                    else -> null
                }
                extractSubtitleTracks(episodeObj)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    // ============================== Helper Functions ==============================

    private fun checkResponse(response: Response) {
        if (!response.isSuccessful) {
            val msg = response.body.string().take(200).ifBlank { response.message }
            when (response.code) {
                429 -> {
                    showToast("Loklok: Too many requests (429). Please wait a few seconds and retry.")
                    throw Exception("Rate limited by Loklok (HTTP 429): $msg")
                }
                403 -> {
                    showToast("Loklok: Access denied (403). Your region or IP may be restricted.")
                }
            }
            throw Exception("API error ${response.code}: $msg")
        }
    }

    private fun ensureJsonBody(body: String?, context: String): String {
        val b = body ?: throw Exception("Empty response")
        if (b.trimStart().startsWith("<")) {
            showToast("Loklok: Connection blocked by CDN. Please open loklok.fun in WebView or retry later.")
            throw Exception("API returned HTML instead of JSON ($context). Server may be blocking; try again later.")
        }
        return b
    }

    override fun getFilterList() = LoklokFilters.getFilters()

    companion object {
        private const val PREF_QUALITY_KEY = "preferred_quality"
        private const val PREF_FETCH_ALL_QUALITIES_KEY = "fetch_all_qualities"
        private const val PREF_EMAIL_KEY = "account_email"
        private const val PREF_PASSWORD_KEY = "account_password"
        private const val PREF_TOKEN_KEY = "session_token"
        private const val PREF_CACHED_TOKEN_KEY = "cached_auth_token"
        private const val PREF_DEVICE_ID_KEY = "persistent_device_id"

        private const val DEFAULT_HOME_NAV_ID = "1000004"
        private const val DEFAULT_RANK_NAV_ID = "1000080"
        private const val PAGE_SIZE = 20

        private const val CLIENT_TYPE = "web-loklok"
        private const val VERSION_CODE = "32"
        private const val LOGIN_PATH = "/user/auth/pc/login"
        private const val AUTH_REQUIRED_CODE = "A0230"
        private const val DECOY_CODE = "FDLSxl1000129"

        private const val MIN_API_INTERVAL_MS = 300L
        private const val MIN_PLAY_INTERVAL_MS = 500L
        private const val BACKOFF_BASE_MS = 1500L

        private const val DEFAULT_TYPE_PARAMS =
            "MOVIE,TV,VARIETY,COMIC,DOCUMENTARY,TVSPECIAL,MINISERIES,SETI,TALK"

        private const val WEB_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        private val DEFAULT_DEFINITIONS = listOf(
            "GROOT_HD" to "1080P",
            "GROOT_SD" to "720P",
            "GROOT_LD" to "540P",
            "GROOT_FD" to "360P",
        )
    }
}
