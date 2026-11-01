package eu.kanade.tachiyomi.animeextension.en.forcedcinema

import android.app.Application
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.awaitSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.text.SimpleDateFormat
import java.util.Locale

class ForcedCinema : AnimeHttpSource(), ConfigurableAnimeSource {

    override val name = "ForcedCinema"
    override val baseUrl = "https://forcedcinema.net"
    override val lang = "en"
    override val supportsLatest = true

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    private val preferences = Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)

    private val apiUrl = "$baseUrl/wp-json/wp/v2"
    private val dateParser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)

    // ─── Listings ─────────────────────────────────────────────────────────────

    override fun popularAnimeRequest(page: Int): Request =
        GET("$apiUrl/posts?per_page=20&page=$page&orderby=date&order=desc&_fields=id,slug,title,date,link,featured_media,content", headers)

    override fun popularAnimeParse(response: Response): AnimesPage {
        val posts = json.decodeFromString<List<WpPost>>(response.body.string())
        val total = response.headers["X-WP-TotalPages"]?.toIntOrNull() ?: 1
        val page = response.request.url.queryParameter("page")?.toIntOrNull() ?: 1
        return AnimesPage(posts.map { it.toSAnime() }, page < total)
    }

    override fun latestUpdatesRequest(page: Int): Request =
        popularAnimeRequest(page)

    override fun latestUpdatesParse(response: Response): AnimesPage =
        popularAnimeParse(response)

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        val url = "$apiUrl/posts?per_page=20&page=$page&_fields=id,slug,title,date,link,featured_media,content".toHttpUrl().newBuilder()
        if (query.isNotBlank()) {
            url.addQueryParameter("search", query)
        }
        filters.forEach { filter ->
            when (filter) {
                is ForcedCinemaFilters.CategoryFilter -> if (filter.selectedId > 0) {
                    url.addQueryParameter("categories", filter.selectedId.toString())
                }
                is ForcedCinemaFilters.TagFilter -> if (filter.selectedId > 0) {
                    url.addQueryParameter("tags", filter.selectedId.toString())
                }
                is ForcedCinemaFilters.SortFilter -> {
                    url.addQueryParameter("orderby", filter.toOrderBy())
                    url.addQueryParameter("order", filter.toOrder())
                }
                else -> {}
            }
        }
        return GET(url.build(), headers)
    }

    override fun searchAnimeParse(response: Response): AnimesPage =
        popularAnimeParse(response)

    // ─── Details ──────────────────────────────────────────────────────────────

    override fun animeDetailsRequest(anime: SAnime): Request {
        // anime.url = /wp-json/wp/v2/posts/{id}
        return GET("$baseUrl${anime.url}", headers)
    }

    override fun animeDetailsParse(response: Response): SAnime {
        val post = json.decodeFromString<WpPost>(response.body.string())
        return post.toSAnime()
    }

    // ─── Episodes ─────────────────────────────────────────────────────────────

    override fun episodeListRequest(anime: SAnime): Request =
        animeDetailsRequest(anime)

    override fun episodeListParse(response: Response): List<SEpisode> {
        val post = json.decodeFromString<WpPost>(response.body.string())
        val ep = SEpisode.create().apply {
            url = post.link
            name = "Full Video"
            episode_number = 1f
            date_upload = runCatching { dateParser.parse(post.date)?.time ?: 0L }.getOrDefault(0L)
        }
        return listOf(ep)
    }

    // ─── Video Sources ────────────────────────────────────────────────────────

    override fun videoListRequest(episode: SEpisode): Request =
        GET(episode.url, headers)

    override fun videoListParse(response: Response): List<Video> {
        val body = response.body.string()

        // The FV WordPress Flowplayer plugin stores video data in a data-item attribute:
        // data-item="{&quot;sources&quot;:[{&quot;src&quot;:&quot;https://vod.forcedcinema.net/xxx.mp4&quot;,...}],...}"
        val dataItemRegex = Regex("""data-item="(\{[^"]+\})"""")
        val encoded = dataItemRegex.find(body)?.groupValues?.get(1) ?: ""
        val decoded = encoded
            .replace("&quot;", "\"")
            .replace("&amp;", "&")
            .replace("\\/", "/")

        // Extract src from {"sources":[{"src":"...","type":"video/mp4"}],...}
        val srcRegex = Regex(""""src"\s*:\s*"([^"]+\.mp4[^"]*)"""")
        val videos = srcRegex.findAll(decoded).map { m ->
            val url = m.groupValues[1]
            Video(url, "MP4 [ForcedCinema]", url, headers)
        }.toList()

        if (videos.isNotEmpty()) return videos

        // Fallback: JSON-LD contentUrl
        val jsonLdRegex = Regex(""""contentUrl"\s*:\s*"([^"]+)"""")
        val fallback = jsonLdRegex.find(body)?.groupValues?.get(1)?.replace("\\/", "/")
        if (fallback != null) {
            return listOf(Video(fallback, "MP4 [ForcedCinema]", fallback, headers))
        }

        return emptyList()
    }

    // ─── Filters ──────────────────────────────────────────────────────────────

    override fun getFilterList(): AnimeFilterList = AnimeFilterList(
        AnimeFilter.Header("Search ignores filters"),
        ForcedCinemaFilters.SortFilter(),
        ForcedCinemaFilters.CategoryFilter(),
        ForcedCinemaFilters.TagFilter(),
    )

    // ─── Preferences ──────────────────────────────────────────────────────────

    override fun setupPreferenceScreen(screen: PreferenceScreen) {}

    // ─── Data Classes ─────────────────────────────────────────────────────────

    @Serializable
    data class WpPost(
        val id: Int = 0,
        val slug: String = "",
        val date: String = "",
        val link: String = "",
        val title: WpTitle = WpTitle(),
        val content: WpContent = WpContent(),
        val featured_media: Int = 0,
    ) {
        fun toSAnime() = SAnime.create().apply {
            // Store API URL so we can re-fetch for details/episodes
            url = "/wp-json/wp/v2/posts/$id"
            title = this@WpPost.title.rendered
                .replace(Regex("<[^>]+>"), "")
                .trim()
            // Thumbnail: wp-content/uploads/<year>/<month>/<slug>.jpg
            thumbnail_url = if (slug.isNotEmpty()) {
                val year = date.take(4)
                val month = date.drop(5).take(2)
                "https://forcedcinema.net/wp-content/uploads/$year/$month/$slug.jpg"
            } else null
            genre = "Live Action"
            status = SAnime.COMPLETED
        }
    }

    @Serializable
    data class WpTitle(val rendered: String = "")

    @Serializable
    data class WpContent(val rendered: String = "")
}
