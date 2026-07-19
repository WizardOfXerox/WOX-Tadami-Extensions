package eu.kanade.tachiyomi.animeextension.all.pornhub

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.ParsedAnimeHttpSource
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Headers
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.text.SimpleDateFormat
import java.util.Locale

class Pornhub : ParsedAnimeHttpSource(), ConfigurableAnimeSource {

    override val name = "Pornhub"

    override val baseUrl = "https://www.pornhub.com"

    override val lang = "all"

    override val supportsLatest = true

    override val id: Long = 8943174591L

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    override fun headersBuilder(): Headers.Builder {
        return Headers.Builder()
            .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .add("Cookie", "age_verified=1; accessAgeDisclaimerPH=1; accessAgeDisclaimerUK=1; accessPH=1")
            .add("Referer", "$baseUrl/")
            .add("Origin", baseUrl)
    }

    // ============================== Popular ==============================

    override fun popularAnimeRequest(page: Int): Request {
        return GET("$baseUrl/video?o=mv" + if (page > 1) "&page=$page" else "", headers)
    }

    override fun popularAnimeSelector(): String = "li.pcVideoListItem[data-video-vkey], div.videoBox[data-video-vkey]"

    override fun popularAnimeFromElement(element: Element): SAnime {
        val anime = SAnime.create()
        
        val titleEl = element.selectFirst("span.title a, a[title]")
        if (titleEl != null) {
            val attrTitle = titleEl.attr("title")
            anime.title = if (attrTitle.isNotBlank()) attrTitle else titleEl.text()
            
            val href = titleEl.attr("href")
            if (href.isNotBlank()) {
                anime.setUrlWithoutDomain(href)
            } else {
                anime.setUrlWithoutDomain("/view_video.php?viewkey=unknown")
            }
        } else {
            anime.title = "Video"
            anime.setUrlWithoutDomain("/view_video.php?viewkey=unknown")
        }
        
        val img = element.selectFirst("div.phimage img, img[data-src], img[src]")
        if (img != null) {
            val dataSrc = img.attr("abs:data-src")
            if (dataSrc.isNotBlank()) {
                anime.thumbnail_url = dataSrc
            } else {
                anime.thumbnail_url = img.attr("abs:src")
            }
        } else {
            anime.thumbnail_url = ""
        }
        
        return anime
    }

    override fun popularAnimeNextPageSelector(): String = "li.page-next a, li.page_next a, a.pageNext, li.next a"

    // ============================== Latest ==============================

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/video?o=mr" + if (page > 1) "&page=$page" else "", headers)
    }

    override fun latestUpdatesSelector(): String = popularAnimeSelector()

    override fun latestUpdatesFromElement(element: Element): SAnime = popularAnimeFromElement(element)

    override fun latestUpdatesNextPageSelector(): String = popularAnimeNextPageSelector()

    // ============================== Search ==============================

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        return try {
            val orderFilter = filters.filterIsInstance<PornhubFilters.OrderFilter>().firstOrNull()
            val order = orderFilter?.toUri() ?: "mv"

            if (query.isNotBlank()) {
                GET("$baseUrl/video/search?search=$query&o=$order" + if (page > 1) "&page=$page" else "", headers)
            } else {
                val categoryFilter = filters.filterIsInstance<PornhubFilters.CategoryFilter>().firstOrNull()
                val category = categoryFilter?.toUri() ?: ""

                if (category.isNotBlank()) {
                    GET("$baseUrl/categories/$category?o=$order" + if (page > 1) "&page=$page" else "", headers)
                } else {
                    GET("$baseUrl/video?o=$order" + if (page > 1) "&page=$page" else "", headers)
                }
            }
        } catch (_: Throwable) {
            GET("$baseUrl/video/search?search=$query&o=mv" + if (page > 1) "&page=$page" else "", headers)
        }
    }

    override fun searchAnimeSelector(): String = popularAnimeSelector()

    override fun searchAnimeFromElement(element: Element): SAnime = popularAnimeFromElement(element)

    override fun searchAnimeNextPageSelector(): String = popularAnimeNextPageSelector()

    // ============================== Details ==============================

    override fun animeDetailsParse(document: Document): SAnime {
        return SAnime.create().apply {
            status = SAnime.COMPLETED
            initialized = true
            
            val ldJson = document.selectFirst("script[type=application/ld+json]")?.html()
            if (!ldJson.isNullOrBlank()) {
                try {
                    val titleMatch = Regex(""""name"\s*:\s*"([^"]+)"""").find(ldJson)
                    val descMatch = Regex(""""description"\s*:\s*"([^"]+)"""").find(ldJson)
                    val thumbMatch = Regex(""""thumbnailUrl"\s*:\s*"([^"]+)"""").find(ldJson)
                    
                    title = titleMatch?.groupValues?.get(1)?.replace("\\\"", "\"") ?: ""
                    description = descMatch?.groupValues?.get(1)?.replace("\\\"", "\"") ?: ""
                    thumbnail_url = thumbMatch?.groupValues?.get(1) ?: ""
                } catch (_: Exception) {}
            }
            
            if (title.isBlank()) {
                title = document.selectFirst("h1.video-title, h1 span, h1")?.text() ?: ""
                description = document.selectFirst("div.video-description, p.description")?.text() ?: ""
                thumbnail_url = document.selectFirst("div.phimage img")?.attr("abs:src") ?: ""
            }
            
            artist = document.selectFirst("div.submitted-by a, a.username, .uploaderName")?.text() ?: "Unknown"
            
            val genres = mutableListOf<String>()
            document.select(".categoriesWrapper a, a[href*=/categories/]").forEach {
                genres.add(it.text())
            }
            document.select(".tagsWrapper a, a[href*=/tags/], a[href*=/video/search?keyword=]").forEach {
                genres.add(it.text())
            }
            genre = genres.distinct().joinToString(", ")
        }
    }

    // ============================== Episodes ==============================

    override fun episodeListParse(response: Response): List<SEpisode> {
        val document = response.asJsoup()
        val ldJson = document.selectFirst("script[type=application/ld+json]")?.html()
        val uploadDateText = if (!ldJson.isNullOrBlank()) {
            Regex(""""uploadDate"\s*:\s*"([^"]+)"""").find(ldJson)?.groupValues?.get(1)
        } else {
            null
        }
        
        val parsedDate = if (!uploadDateText.isNullOrBlank()) {
            try {
                val datePart = uploadDateText.substringBefore("T")
                SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).parse(datePart)?.time ?: 0L
            } catch (_: Exception) {
                0L
            }
        } else {
            0L
        }

        val episode = SEpisode.create().apply {
            setUrlWithoutDomain(response.request.url.toString())
            name = "Video"
            episode_number = 1.0f
            date_upload = parsedDate
        }
        return listOf(episode)
    }

    override fun episodeFromElement(element: Element): SEpisode {
        throw UnsupportedOperationException()
    }

    override fun episodeListSelector(): String {
        throw UnsupportedOperationException()
    }

    // ============================== Video Listing ==============================

    override suspend fun getVideoList(episode: SEpisode): List<Video> {
        val pageResponse = client.newCall(GET(baseUrl + episode.url, headers)).execute()
        if (!pageResponse.isSuccessful) {
            pageResponse.close()
            throw Exception("Failed to fetch episode page: ${pageResponse.code}")
        }
        val document = pageResponse.asJsoup()
        
        val script = document.select("script").toList().firstOrNull { it.html().contains("mediaDefinitions") }?.html()
            ?: throw Exception("Video configuration script not found")
            
        val mediaDefMatch = Regex(""""mediaDefinitions"\s*:\s*(\[.*?\])""").find(script)
            ?: throw Exception("mediaDefinitions array not found")
        val mediaDefJson = mediaDefMatch.groupValues[1]
        
        val objRegex = """\{([^}]+)\}""".toRegex()
        val videoList = mutableListOf<Video>()
        
        val videoHeaders = headersBuilder().apply {
            set("Origin", "https://www.pornhub.com")
            set("Referer", "https://www.pornhub.com/")
        }.build()
        
        for (objMatch in objRegex.findAll(mediaDefJson)) {
            val block = objMatch.groupValues[1]
            
            val quality = Regex(""""quality"\s*:\s*"?([^",\s]+)"?""").find(block)?.groupValues?.get(1)?.trim('"') ?: ""
            val format = Regex(""""format"\s*:\s*"([^"]+)"""").find(block)?.groupValues?.get(1) ?: ""
            val videoUrl = Regex(""""videoUrl"\s*:\s*"([^"]+)"""").find(block)?.groupValues?.get(1)?.replace("\\/", "/") ?: ""
            val remote = Regex(""""remote"\s*:\s*true""").containsMatchIn(block)
            
            if (videoUrl.isBlank()) continue
            
            if (format == "hls") {
                val qualityLabel = if (quality.isNotBlank()) "${quality}p" else "HLS"
                videoList.add(Video(videoUrl, qualityLabel, videoUrl, headers = videoHeaders))
            } else if (format == "mp4" && !remote) {
                val qualityLabel = if (quality.isNotBlank()) "${quality}p" else "MP4"
                videoList.add(Video(videoUrl, qualityLabel, videoUrl, headers = videoHeaders))
            } else if (format == "mp4" && remote) {
                try {
                    val mediaResponse = client.newCall(GET(videoUrl, videoHeaders)).execute()
                    if (mediaResponse.isSuccessful) {
                        val mediaBody = mediaResponse.body.string()
                        val remoteObjMatches = """\{([^}]+)\}""".toRegex().findAll(mediaBody)
                        for (rMatch in remoteObjMatches) {
                            val rBlock = rMatch.groupValues[1]
                            val rQuality = Regex(""""quality"\s*:\s*"?([^",\s]+)"?""").find(rBlock)?.groupValues?.get(1)?.trim('"') ?: ""
                            val rVideoUrl = Regex(""""videoUrl"\s*:\s*"([^"]+)"""").find(rBlock)?.groupValues?.get(1)?.replace("\\/", "/") ?: ""
                            if (rVideoUrl.isNotBlank()) {
                                val rQualityLabel = if (rQuality.isNotBlank()) "${rQuality}p" else "MP4"
                                videoList.add(Video(rVideoUrl, rQualityLabel, rVideoUrl, headers = videoHeaders))
                            }
                        }
                    }
                    mediaResponse.close()
                } catch (_: Exception) {}
            }
        }
        
        if (videoList.isEmpty()) {
            throw Exception("No playable video formats found")
        }
        
        return videoList.sort()
    }

    override fun videoListSelector(): String {
        throw UnsupportedOperationException()
    }

    override fun videoFromElement(element: Element): Video {
        throw UnsupportedOperationException()
    }

    override fun videoUrlParse(document: Document): String {
        throw UnsupportedOperationException()
    }

    // ============================== Preferences ==============================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = PREF_QUALITY_KEY
            title = "Preferred quality"
            entries = arrayOf("1080p", "720p", "480p", "240p")
            entryValues = arrayOf("1080", "720", "480", "240")
            setDefaultValue("720")
            summary = "%s"
            setOnPreferenceChangeListener { _, newValue ->
                val selected = newValue as String
                val index = findIndexOfValue(selected)
                val entry = entryValues[index] as String
                preferences.edit().putString(key, entry).commit()
            }
        }.also(screen::addPreference)
    }

    override fun List<Video>.sort(): List<Video> {
        val preferredQuality = preferences.getString(PREF_QUALITY_KEY, "720") ?: "720"
        return this.sortedWith(
            compareBy { !it.quality.contains(preferredQuality) }
        )
    }

    override fun getFilterList(): AnimeFilterList = PornhubFilters.getFilters()

    companion object {
        private const val PREF_QUALITY_KEY = "pref_quality_key"
    }
}
