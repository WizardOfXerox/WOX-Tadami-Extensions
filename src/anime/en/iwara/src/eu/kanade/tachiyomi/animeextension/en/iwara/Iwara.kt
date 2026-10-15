package eu.kanade.tachiyomi.animeextension.en.iwara

import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.network.GET
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Locale

class Iwara : AnimeHttpSource() {

    override val name = "Iwara"

    override val baseUrl = "https://www.iwara.tv"

    private val apiUrl = "https://api.iwara.tv"

    private val mediaUrl = "https://files.iwara.tv"

    private val secretKey = "mSvL05GfEmeEmsEYfGCnVpEjYgTJraJN"

    override val lang = "en"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .build()

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("User-Agent", "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36")
        .add("Origin", baseUrl)
        .add("Referer", "$baseUrl/")

    // ============================== Popular ===============================

    override fun popularAnimeRequest(page: Int): Request {
        val apiPage = page - 1
        return GET("$apiUrl/videos?sort=trending&page=$apiPage&limit=32&rating=all", headers)
    }

    override fun popularAnimeParse(response: Response): AnimesPage {
        val json = JSONObject(response.body.string())
        return videoListFromJson(json)
    }

    // =============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request {
        val apiPage = page - 1
        return GET("$apiUrl/videos?sort=date&page=$apiPage&limit=32&rating=all", headers)
    }

    override fun latestUpdatesParse(response: Response): AnimesPage {
        val json = JSONObject(response.body.string())
        return videoListFromJson(json)
    }

    // =============================== Search ===============================

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        val apiPage = page - 1
        var sort = "date"
        var rating = "all"

        for (filter in filters) {
            when (filter) {
                is SortFilter -> sort = filter.toValue()
                is RatingFilter -> rating = filter.toValue()
                else -> {}
            }
        }

        return if (query.isNotBlank()) {
            val tag = query.trim().lowercase(Locale.ROOT).replace(" ", "_")
            GET("$apiUrl/videos?tags=$tag&sort=$sort&rating=$rating&page=$apiPage&limit=32", headers)
        } else {
            GET("$apiUrl/videos?sort=$sort&rating=$rating&page=$apiPage&limit=32", headers)
        }
    }

    override fun searchAnimeParse(response: Response): AnimesPage {
        val json = JSONObject(response.body.string())
        return videoListFromJson(json)
    }

    // =========================== Anime Details ============================

    override fun animeDetailsRequest(anime: SAnime): Request {
        val id = anime.url.removePrefix("/video/").removePrefix("/")
        return GET("$apiUrl/video/$id", headers)
    }

    override fun animeDetailsParse(response: Response): SAnime {
        val json = JSONObject(response.body.string())
        return SAnime.create().apply {
            title = json.getString("title")
            description = json.optString("body", "")
            author = json.optJSONObject("user")?.optString("name")
            artist = author
            status = SAnime.COMPLETED

            val tagsArr = json.optJSONArray("tags")
            if (tagsArr != null && tagsArr.length() > 0) {
                val tagList = mutableListOf<String>()
                for (i in 0 until tagsArr.length()) {
                    tagList.add(tagsArr.getJSONObject(i).optString("id"))
                }
                genre = tagList.joinToString(", ")
            }

            var thumbUrl: String? = null
            if (json.has("customThumbnail") && !json.isNull("customThumbnail")) {
                val thumb = json.getJSONObject("customThumbnail")
                val thumbId = thumb.optString("id")
                val thumbName = thumb.optString("name")
                if (thumbId.isNotBlank() && thumbName.isNotBlank()) {
                    thumbUrl = "$mediaUrl/image/original/$thumbId/$thumbName"
                }
            }
            if (thumbUrl == null && json.has("file") && !json.isNull("file")) {
                val file = json.getJSONObject("file")
                val fileId = file.optString("id")
                val thumbIndex = json.optInt("thumbnail", 0)
                if (fileId.isNotBlank()) {
                    thumbUrl = "$mediaUrl/image/original/$fileId/thumbnail-%02d.jpg".format(thumbIndex)
                }
            }
            thumbnail_url = thumbUrl
        }
    }

    // ============================== Episodes ==============================

    override fun episodeListRequest(anime: SAnime): Request {
        val id = anime.url.removePrefix("/video/").removePrefix("/")
        return GET("$apiUrl/video/$id", headers)
    }

    override fun episodeListParse(response: Response): List<SEpisode> {
        val json = JSONObject(response.body.string())
        val id = json.getString("id")
        val ep = SEpisode.create().apply {
            name = "Video"
            episode_number = 1f
            url = "/video/$id"
            date_upload = parseDate(json.optString("createdAt"))
        }
        return listOf(ep)
    }

    // ============================ Video Links =============================

    override fun videoListRequest(episode: SEpisode): Request {
        val id = episode.url.removePrefix("/video/").removePrefix("/")
        return GET("$apiUrl/video/$id", headers)
    }

    override fun videoListParse(response: Response): List<Video> {
        val json = JSONObject(response.body.string())
        val fileUrl = json.optString("fileUrl")
        if (fileUrl.isBlank()) {
            return emptyList()
        }

        val uri = fileUrl.toHttpUrlOrNull() ?: return emptyList()
        val fileId = uri.pathSegments.lastOrNull() ?: return emptyList()
        val expires = uri.queryParameter("expires") ?: return emptyList()

        val toHash = "${fileId}_${expires}_$secretKey"
        val xVersion = sha1(toHash)

        val fileRequest = Request.Builder()
            .url(fileUrl)
            .headers(headers)
            .header("X-Version", xVersion)
            .build()

        val fileResponse = client.newCall(fileRequest).execute()
        if (!fileResponse.isSuccessful) {
            fileResponse.close()
            return emptyList()
        }

        val responseBody = fileResponse.body.string()
        fileResponse.close()

        val array = JSONArray(responseBody)
        val videoList = mutableListOf<Video>()
        val videoHeaders = headersBuilder()
            .set("Origin", baseUrl)
            .set("Referer", "$baseUrl/")
            .build()

        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            val name = item.optString("name", "Source")
            val src = item.optJSONObject("src") ?: continue
            val view = src.optString("view")
            val download = src.optString("download")
            val chosenUrl = if (view.isNotBlank()) view else download
            if (chosenUrl.isBlank()) continue

            val fullUrl = if (chosenUrl.startsWith("//")) "https:$chosenUrl" else chosenUrl
            val qualityLabel = when (name.lowercase()) {
                "source" -> "Source (Original)"
                "preview" -> "Preview"
                else -> if (name.endsWith("p")) name else "${name}p"
            }
            videoList.add(Video(fullUrl, qualityLabel, fullUrl, headers = videoHeaders))
        }

        return videoList
    }

    override fun List<Video>.sort(): List<Video> {
        return this.sortedWith(
            compareByDescending {
                when {
                    it.quality.contains("Source") -> 1080
                    it.quality.contains("540") -> 540
                    it.quality.contains("360") -> 360
                    it.quality.contains("Preview") -> 1
                    else -> 0
                }
            }
        )
    }

    override fun videoUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================= Utilities ==============================

    private fun videoListFromJson(json: JSONObject): AnimesPage {
        val results = json.optJSONArray("results") ?: JSONArray()
        val animeList = mutableListOf<SAnime>()

        for (i in 0 until results.length()) {
            val item = results.getJSONObject(i)
            val id = item.getString("id")
            val title = item.getString("title")

            var thumbUrl: String? = null
            if (item.has("customThumbnail") && !item.isNull("customThumbnail")) {
                val thumb = item.getJSONObject("customThumbnail")
                val thumbId = thumb.optString("id")
                val thumbName = thumb.optString("name")
                if (thumbId.isNotBlank() && thumbName.isNotBlank()) {
                    thumbUrl = "$mediaUrl/image/original/$thumbId/$thumbName"
                }
            }
            if (thumbUrl == null && item.has("file") && !item.isNull("file")) {
                val file = item.getJSONObject("file")
                val fileId = file.optString("id")
                val thumbIndex = item.optInt("thumbnail", 0)
                if (fileId.isNotBlank()) {
                    thumbUrl = "$mediaUrl/image/original/$fileId/thumbnail-%02d.jpg".format(thumbIndex)
                }
            }

            animeList.add(SAnime.create().apply {
                this.title = title
                this.url = "/video/$id"
                this.thumbnail_url = thumbUrl
            })
        }

        val page = json.optInt("page", 0)
        val limit = json.optInt("limit", 32)
        val count = json.optInt("count", 0)
        val hasNextPage = (page + 1) * limit < count && results.length() > 0

        return AnimesPage(animeList, hasNextPage)
    }

    private fun sha1(input: String): String {
        val md = MessageDigest.getInstance("SHA-1")
        val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private val dateFormat by lazy {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
    }

    private fun parseDate(dateStr: String?): Long {
        if (dateStr.isNullOrBlank()) return 0L
        return try {
            dateFormat.parse(dateStr)?.time ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    // ============================== Filters ===============================

    override fun getFilterList(): AnimeFilterList = AnimeFilterList(
        SortFilter(),
        RatingFilter()
    )

    private class SortFilter : AnimeFilter.Select<String>(
        "Sort",
        arrayOf("Trending", "Latest Date", "Popularity", "Views", "Likes")
    ) {
        fun toValue(): String = when (state) {
            0 -> "trending"
            1 -> "date"
            2 -> "popularity"
            3 -> "views"
            4 -> "likes"
            else -> "trending"
        }
    }

    private class RatingFilter : AnimeFilter.Select<String>(
        "Rating",
        arrayOf("All", "Ecchi", "General")
    ) {
        fun toValue(): String = when (state) {
            0 -> "all"
            1 -> "ecchi"
            2 -> "general"
            else -> "all"
        }
    }
}
