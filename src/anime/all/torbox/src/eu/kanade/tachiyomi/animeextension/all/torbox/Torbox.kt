package eu.kanade.tachiyomi.animeextension.all.torbox

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.network.GET
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.IOException

class Torbox : AnimeHttpSource(), ConfigurableAnimeSource {

    override val name = "Torbox"
    override val baseUrl = "https://api.torbox.app"
    override val lang = "all"
    override val supportsLatest = true

    private val json: Json by lazy {
        Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }
    }

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0)
    }

    override val client: OkHttpClient = network.client.newBuilder()
        .addInterceptor { chain ->
            val request = chain.request()
            val apiKey = preferences.getString(PREF_API_KEY, "") ?: ""
            val newRequest = if (apiKey.isNotEmpty()) {
                request.newBuilder()
                    .addHeader("Authorization", "Bearer $apiKey")
                    .build()
            } else {
                request
            }
            chain.proceed(newRequest)
        }
        .build()

    override fun fetchPopularAnime(page: Int): Observable<AnimesPage> {
        return Observable.just(AnimesPage(emptyList(), false))
    }

    override fun popularAnimeRequest(page: Int): Request = GET("$baseUrl/v1/api/torrents/mylist")

    override fun popularAnimeParse(response: Response): AnimesPage {
        val body = response.body.string()
        val animeList = mutableListOf<SAnime>()
        try {
            val jsonObj = json.parseToJsonElement(body).jsonObject
            val data = jsonObj["data"]?.jsonArray ?: return AnimesPage(emptyList(), false)
            for (elem in data) {
                val item = elem.jsonObject
                val anime = SAnime.create().apply {
                    val nameStr = item["name"]?.jsonPrimitive?.content ?: "Torbox File"
                    val idStr = item["id"]?.jsonPrimitive?.content ?: ""
                    title = nameStr
                    url = "/v1/api/torrents/mylist?id=$idStr"
                }
                animeList.add(anime)
            }
        } catch (e: Exception) {
            // fallback
        }
        return AnimesPage(animeList, false)
    }

    override fun fetchLatestUpdates(page: Int): Observable<AnimesPage> = fetchPopularAnime(page)

    override fun latestUpdatesRequest(page: Int): Request = popularAnimeRequest(page)

    override fun latestUpdatesParse(response: Response): AnimesPage = popularAnimeParse(response)

    override fun fetchSearchAnime(page: Int, query: String, filters: AnimeFilterList): Observable<AnimesPage> {
        return fetchPopularAnime(page)
    }

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request = popularAnimeRequest(page)

    override fun searchAnimeParse(response: Response): AnimesPage = popularAnimeParse(response)

    override fun fetchAnimeDetails(anime: SAnime): Observable<SAnime> {
        return Observable.just(anime)
    }

    override fun animeDetailsParse(response: Response): SAnime {
        return SAnime.create()
    }

    override fun fetchEpisodeList(anime: SAnime): Observable<List<SEpisode>> {
        val episode = SEpisode.create().apply {
            name = anime.title
            url = anime.url
            episode_number = 1f
        }
        return Observable.just(listOf(episode))
    }

    override fun episodeListParse(response: Response): List<SEpisode> = emptyList()

    override fun fetchVideoList(episode: SEpisode): Observable<List<Video>> {
        return Observable.just(emptyList())
    }

    override fun videoListParse(response: Response): List<Video> = emptyList()

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val apiKeyPref = EditTextPreference(screen.context).apply {
            key = PREF_API_KEY
            title = "Torbox API Key"
            summary = "Enter your Torbox API Key for stream playback"
            setDefaultValue("")

            setOnPreferenceChangeListener { _, newValue ->
                val text = newValue as String
                preferences.edit().putString(PREF_API_KEY, text.trim()).apply()
                true
            }
        }
        screen.addPreference(apiKeyPref)
    }

    companion object {
        private const val PREF_API_KEY = "torbox_api_key"
    }
}
