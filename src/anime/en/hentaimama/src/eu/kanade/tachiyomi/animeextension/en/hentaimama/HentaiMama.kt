package eu.kanade.tachiyomi.animeextension.en.hentaimama

import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.ParsedAnimeHttpSource
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.getPreferencesLazy
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class HentaiMama :
    ParsedAnimeHttpSource(),
    ConfigurableAnimeSource {

    override val name = "HentaiMama"

    override val baseUrl = "https://hentaimama.io"

    override val lang = "en"

    override val supportsLatest = true

    private val preferences by getPreferencesLazy()

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Referer", baseUrl)

    // Popular Anime

    override fun popularAnimeSelector(): String = "article.series-card, article.tvshows, article"

    override fun popularAnimeRequest(page: Int): Request = GET("$baseUrl/advance-search/page/$page/?submit=Submit&filter=weekly")

    override fun popularAnimeFromElement(element: Element): SAnime {
        val anime = SAnime.create()
        val link = element.select("h3.sc-title a, a.sc-poster, div.data h3 a, div.details > div.title a, a").first()
        anime.setUrlWithoutDomain(link?.attr("href") ?: "")
        anime.title = element.select("h3.sc-title a, div.data h3 a, div.details > div.title a, h3").text()
        val img = element.select("img")
        anime.thumbnail_url = img.attr("src").ifEmpty { img.attr("data-src") }
        return anime
    }

    override fun popularAnimeNextPageSelector(): String = "a.dt-pg-next, div.pagination-wraper div.resppages a, a.arrow_pag, link[rel=next]"

    // episodes

    override fun episodeListParse(response: Response): List<SEpisode> = super.episodeListParse(response)

    override fun episodeListSelector() = "a.dt-se-item, div.dt-se-list a, div.series div.items article"

    override fun episodeFromElement(element: Element): SEpisode {
        val episode = SEpisode.create()
        val href = if (element.tagName() == "a") element.attr("href") else element.select("a").attr("href")
        episode.setUrlWithoutDomain(href)

        val title = element.select("b.dt-se-title, div.data h3").text().ifEmpty { element.text() }
        episode.name = title

        val numText = element.select("b.dt-se-num, div.season_m a span.c").text()
        val epNumPattern = Regex("""(?:Episode|EP)\s*(\d+\.?\d*)""", RegexOption.IGNORE_CASE)
        val epNumMatch = epNumPattern.find(numText) ?: epNumPattern.find(href) ?: epNumPattern.find(title)
        episode.episode_number = epNumMatch?.groupValues?.get(1)?.toFloatOrNull() ?: 1F

        val dateText = element.select("span.dt-se-date, div.data > span").text()
        val date = runCatching {
            SimpleDateFormat("MMM dd, yyyy", Locale.US).parse(dateText)
        }.getOrNull() ?: runCatching {
            SimpleDateFormat("MMM. dd, yyyy", Locale.US).parse(dateText)
        }.getOrNull()
        episode.date_upload = date?.time ?: 0L

        return episode
    }

    // Video Extractor

    override fun videoListParse(response: Response): List<Video> {
        val htmlContent = response.body.string()
        val document = Jsoup.parse(htmlContent, response.request.url.toString())

        val idpost = document.selectFirst("input[name=idpost], [data-post-id]")?.let {
            it.attr("value").ifEmpty { it.attr("data-post-id") }
        } ?: Regex("""name=["']idpost["'][^>]+value=["'](\d+)["']""").find(htmlContent)?.groupValues?.get(1)
          ?: Regex("""data-post-id=["'](\d+)["']""").find(htmlContent)?.groupValues?.get(1)
          ?: document.selectFirst("#post_report input:nth-child(5)")?.attr("value")

        if (idpost.isNullOrEmpty()) {
            return emptyList()
        }

        val body = FormBody.Builder()
            .add("action", "get_player_contents")
            .add("a", idpost)
            .build()

        val ajaxHeaders = headers.newBuilder()
            .set("Referer", response.request.url.toString())
            .build()

        val ajaxResp = runCatching {
            client.newCall(POST("$baseUrl/wp-admin/admin-ajax.php", ajaxHeaders, body)).execute().body.string()
        }.getOrNull() ?: return emptyList()

        val iframeSrcs = Regex("""src=["']([^"']+)["']""").findAll(ajaxResp)
            .map { it.groupValues[1].replace("&#038;", "&").replace(Regex("""\\/"""), "/") }
            .toList()

        val videoList = mutableListOf<Video>()
        val seenUrls = mutableSetOf<String>()

        for (iframeUrl in iframeSrcs) {
            val server = when {
                iframeUrl.contains("r2-mp4") -> "R2"
                iframeUrl.contains("hls-mp4") -> "HLS-MP4"
                iframeUrl.contains("hls") -> "HLS"
                iframeUrl.contains("newjav") -> "Jav"
                iframeUrl.contains("rtmp") -> "RTMP"
                else -> "Mirror"
            }

            val playerHtml = runCatching {
                val playerHeaders = headers.newBuilder()
                    .set("Referer", response.request.url.toString())
                    .build()
                client.newCall(GET(iframeUrl, playerHeaders)).execute().body.string()
            }.getOrDefault("")

            val streamRegex = Regex("""(https?:[\\/]+[^\s"'<>]+\.(?:mp4|m3u8)[^\s"'<>]*)""")
            val matches = streamRegex.findAll(playerHtml)

            for (m in matches) {
                val streamUrl = m.groupValues[1].replace(Regex("""\\/"""), "/")
                if (streamUrl in seenUrls) continue
                seenUrls.add(streamUrl)

                val res = when {
                    streamUrl.contains("1080p") -> "1080p"
                    streamUrl.contains("720p") -> "720p"
                    streamUrl.contains("480p") -> "480p"
                    streamUrl.endsWith(".m3u8") || streamUrl.contains(".m3u8?") -> "Auto (HLS)"
                    else -> "Default"
                }
                val label = "$server - $res"
                videoList.add(Video(streamUrl, label, streamUrl))
            }
        }

        return videoList
    }

    override fun videoListSelector() = throw UnsupportedOperationException()

    override fun List<Video>.sort(): List<Video> {
        val quality = preferences.getString("preferred_quality", null)
        if (quality != null) {
            val newList = mutableListOf<Video>()
            var preferred = 0
            for (video in this) {
                if (video.quality.contains(quality)) {
                    newList.add(preferred, video)
                    preferred++
                } else {
                    newList.add(video)
                }
            }
            return newList
        }
        return this
    }

    override fun videoFromElement(element: Element) = throw UnsupportedOperationException()

    override fun videoUrlParse(document: Document) = throw UnsupportedOperationException()

    // Search
    private var filterSearch = false

    override fun searchAnimeFromElement(element: Element): SAnime = popularAnimeFromElement(element)

    override fun searchAnimeNextPageSelector(): String = popularAnimeNextPageSelector()

    override fun searchAnimeSelector(): String = "article.series-card, article.tvshows, article"

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        val parameters = getSearchParameters(filters)
        return if (query.isNotEmpty()) {
            filterSearch = false
            GET("$baseUrl/page/$page/?s=${query.replace(Regex("""[\W]"""), " ")}") // regular search
        } else {
            filterSearch = true
            GET("$baseUrl/advance-search/page/$page/?$parameters") // filter search
        }
    }

    // Details

    override fun animeDetailsParse(document: Document): SAnime {
        val anime = SAnime.create()
        anime.title = document.select("h1.dsc-title, h1.entry-title, #info1 div:nth-child(2) span, h1").text()
        val img = document.selectFirst(".dsc-poster img, div.sheader div.poster img, div.poster img, img.wp-post-image")
        anime.thumbnail_url = img?.let { it.attr("src").ifEmpty { it.attr("data-src") } }
        anime.description = document.select(".dsc-desc p, .dsc-desc, div.wp-content p, #info1 div.wp-content p").text()
        anime.genre = document.select(".sc-genres a, .dsc-genres a, div.sheader div.data div.sgeneros a, a[href*='/genre/']")
            .map { it.text() }
            .distinct()
            .joinToString(", ")
        anime.author = document.select("a[href*='/studio/'], #info1 div:nth-child(3) span div div a")
            .map { it.text() }
            .distinct()
            .joinToString(", ")
        anime.status = parseStatus(document.select(".dsc-meta, #info1 div:nth-child(6) span").text())
        return anime
    }

    private fun parseStatus(statusString: String): Int = when (statusString) {
        "Ongoing" -> SAnime.ONGOING
        else -> SAnime.COMPLETED
    }

    // Latest

    override fun latestUpdatesSelector(): String = "article.series-card, article.tvshows, article"

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/tvshows/page/$page/")

    override fun latestUpdatesFromElement(element: Element): SAnime = popularAnimeFromElement(element)

    override fun latestUpdatesNextPageSelector(): String = popularAnimeNextPageSelector()

    // Settings

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val videoQualityPref = ListPreference(screen.context).apply {
            key = PREF_QUALITY_KEY
            title = PREF_QUALITY_TITLE
            entries = PREF_QUALITY_ENTRIES
            entryValues = PREF_QUALITY_ENTRIES
            setDefaultValue("Mirror 2")
            summary = "%s"

            setOnPreferenceChangeListener { _, newValue ->
                val selected = newValue as String
                val index = findIndexOfValue(selected)
                val entry = entryValues[index] as String
                preferences.edit().putString(key, entry).commit()
            }
        }
        screen.addPreference(videoQualityPref)
    }

    // Filters

    internal class Genre(val id: String) : AnimeFilter.CheckBox(id)
    private class GenreList(genres: List<Genre>) : AnimeFilter.Group<Genre>("Genre", genres)
    private fun getGenres() = listOf(
        Genre("3D"),
        Genre("Action"),
        Genre("Adventure"),
        Genre("Ahegao"),
        Genre("Anal"),
        Genre("Animal Ears"),
        Genre("Beastiality"),
        Genre("Blackmail"),
        Genre("Blowjob"),
        Genre("Bondage"),
        Genre("Brainwashed"),
        Genre("Bukakke"),
        Genre("Cat Girl"),
        Genre("Comedy"),
        Genre("Cosplay"),
        Genre("Creampie"),
        Genre("Cross-dressing"),
        Genre("Dark Skin"),
        Genre("DeepThroat"),
        Genre("Demons"),
        Genre("Doctor"),
        Genre("Double Penatration"),
        Genre("Drama"),
        Genre("Dubbed"),
        Genre("Ecchi"),
        Genre("Elf"),
        Genre("Eroge"),
        Genre("Facesitting"),
        Genre("Facial"),
        Genre("Fantasy"),
        Genre("Female Doctor"),
        Genre("Female Teacher"),
        Genre("Femdom"),
        Genre("Footjob"),
        Genre("Futanari"),
        Genre("Gangbang"),
        Genre("Gore"),
        Genre("Gyaru"),
        Genre("Harem"),
        Genre("Historical"),
        Genre("Horny Slut"),
        Genre("Housewife"),
        Genre("Humiliation"),
        Genre("Incest"),
        Genre("Inflation"),
        Genre("Internal Cumshot"),
        Genre("Lactation"),
        Genre("Large Breasts"),
        Genre("Lolicon"),
        Genre("Magical Girls"),
        Genre("Maid"),
        Genre("Martial Arts"),
        Genre("Megane"),
        Genre("MILF"),
        Genre("Mind Break"),
        Genre("Molestation"),
        Genre("Non-Japanese"),
        Genre("NTR"),
        Genre("Nuns"),
        Genre("Nurses"),
        Genre("Office Ladies"),
        Genre("Police"),
        Genre("POV"),
        Genre("Pregnant"),
        Genre("Princess"),
        Genre("Public Sex"),
        Genre("Rape"),
        Genre("Rim job"),
        Genre("Romance"),
        Genre("Scat"),
        Genre("School Girls"),
        Genre("Sci-Fi"),
        Genre("Shimapan"),
        Genre("Short"),
        Genre("Shoutacon"),
        Genre("Slaves"),
        Genre("Sports"),
        Genre("Squirting"),
        Genre("Stocking"),
        Genre("Strap-on"),
        Genre("Strapped On"),
        Genre("Succubus"),
        Genre("Super Power"),
        Genre("Supernatural"),
        Genre("Swimsuit"),
        Genre("Tentacles"),
        Genre("Three some"),
        Genre("Tits Fuck"),
        Genre("Torture"),
        Genre("Toys"),
        Genre("Train Molestation"),
        Genre("Tsundere"),
        Genre("Uncensored"),
        Genre("Urination"),
        Genre("Vampire"),
        Genre("Vanilla"),
        Genre("Virgins"),
        Genre("Widow"),
        Genre("X-Ray"),
        Genre("Yuri"),
    )

    internal class Year(val id: String) : AnimeFilter.CheckBox(id)
    private class YearList(years: List<Year>) : AnimeFilter.Group<Year>("Year", years)
    private fun getYears() = listOf(
        Year("2022"),
        Year("2021"),
        Year("2020"),
        Year("2019"),
        Year("2018"),
        Year("2017"),
        Year("2016"),
        Year("2015"),
        Year("2014"),
        Year("2013"),
        Year("2012"),
        Year("2011"),
        Year("2010"),
        Year("2009"),
        Year("2008"),
        Year("2007"),
        Year("2006"),
        Year("2005"),
        Year("2004"),
        Year("2003"),
        Year("2002"),
        Year("2001"),
        Year("2000"),
        Year("1999"),
        Year("1998"),
        Year("1997"),
        Year("1996"),
        Year("1995"),
        Year("1994"),
        Year("1993"),
        Year("1992"),
        Year("1991"),
        Year("1987"),
    )

    internal class Producer(val id: String) : AnimeFilter.CheckBox(id)
    private class ProducerList(producers: List<Producer>) : AnimeFilter.Group<Producer>("Producer", producers)
    private fun getProducer() = listOf(
        Producer("8bit"),
        Producer("Actas"),
        Producer("Active"),
        Producer("AIC"),
        Producer("AIC A.S.T.A."),
        Producer("Alice Soft"),
        Producer("An DerCen"),
        Producer("Angelfish"),
        Producer("Animac"),
        Producer("AniMan"),
        Producer("Animax"),
        Producer("Antechinus"),
        Producer("APPP"),
        Producer("Armor"),
        Producer("Arms"),
        Producer("Asahi Production"),
        Producer("AT-2"),
        Producer("Blue Eyes"),
        Producer("BOMB! CUTE! BOMB!"),
        Producer("BOOTLEG"),
        Producer("Bunnywalker"),
        Producer("Central Park Media"),
        Producer("CherryLips"),
        Producer("ChiChinoya"),
        Producer("Chippai"),
        Producer("ChuChu"),
        Producer("Circle Tribute"),
        Producer("CLOCKUP"),
        Producer("Collaboration Works"),
        Producer("Comic Media"),
        Producer("Cosmic Ray"),
        Producer("Cosmo"),
        Producer("Cotton Doll"),
        Producer("Cranberry"),
        Producer("D3"),
        Producer("Daiei"),
        Producer("Digital Works"),
        Producer("Discovery"),
        Producer("Dream Force"),
        Producer("Dubbed"),
        Producer("Easy Film"),
        Producer("Echo"),
        Producer("EDGE"),
        Producer("Filmlink International"),
        Producer("Five Ways"),
        Producer("Front Line"),
        Producer("Frontier Works"),
        Producer("Godoy"),
        Producer("Gold Bear"),
        Producer("Green Bunny"),
        Producer("Himajin Planning"),
        Producer("Hokiboshi"),
        Producer("Hoods Entertainment"),
        Producer("Horipro"),
        Producer("Hot Bear"),
        Producer("HydraFXX"),
        Producer("Innocent Grey"),
        Producer("Jam"),
        Producer("JapanAnime"),
        Producer("King Bee"),
        Producer("Kitty Films"),
        Producer("Kitty Media"),
        Producer("Knack Productions"),
        Producer("KSS"),
        Producer("Lemon Heart"),
        Producer("Lune Pictures"),
        Producer("Majin"),
        Producer("Marvelous Entertainment"),
        Producer("Mary Jane"),
        Producer("Media"),
        Producer("Media Blasters"),
        Producer("Milkshake"),
        Producer("Mitsu"),
        Producer("Moonstone Cherry"),
        Producer("Mousou Senka"),
        Producer("MS Pictures"),
        Producer("Nihikime no Dozeu"),
        Producer("Nur"),
        Producer("NuTech Digital"),
        Producer("Obtain Future"),
        Producer("Office Take Off"),
        Producer("OLE-M"),
        Producer("Oriental Light and Magic"),
        Producer("Oz"),
        Producer("Pashmina"),
        Producer("Pink Pineapple"),
        Producer("Pixy"),
        Producer("PoRO"),
        Producer("Production I.G"),
        Producer("Queen Bee"),
        Producer("Sakura Purin Animation"),
        Producer("Schoolzone"),
        Producer("Selfish"),
        Producer("Seven"),
        Producer("Shelf"),
        Producer("Shinkuukan"),
        Producer("Shinyusha"),
        Producer("Shouten"),
        Producer("Silky’s"),
        Producer("Soft Garage"),
        Producer("SoftCel Pictures"),
        Producer("SPEED"),
        Producer("Studio 9 Maiami"),
        Producer("Studio Eromatick"),
        Producer("Studio Fantasia"),
        Producer("Studio Jack"),
        Producer("Studio Kyuuma"),
        Producer("Studio Matrix"),
        Producer("Studio Sign"),
        Producer("Studio Tulip"),
        Producer("Studio Unicorn"),
        Producer("Suzuki Mirano"),
        Producer("T-Rex"),
        Producer("The Right Stuf International"),
        Producer("Toho Company"),
        Producer("Top-Marschal"),
        Producer("Toranoana"),
        Producer("Toshiba Entertainment"),
        Producer("Triangle Bitter"),
        Producer("Triple X"),
        Producer("Union Cho"),
        Producer("Valkyria"),
        Producer("White Bear"),
        Producer("Y.O.U.C"),
        Producer("ZIZ Entertainment"),
        Producer("Zyc"),

    )

    private data class Order(val name: String, val id: String)
    private class OrderList(Orders: Array<String>) : AnimeFilter.Select<String>("Order", Orders)
    private val orderName = getOrder().map {
        it.name
    }.toTypedArray()
    private fun getOrder() = listOf(
        Order("Weekly Views", "weekly"),
        Order("Monthly Views", "monthly"),
        Order("Alltime Views", "alltime"),
        Order("A-Z", "alphabet"),
        Order("Rating", "rating"),

    )

    private fun getSearchParameters(filters: AnimeFilterList): String {
        var totalstring = ""
        var sortBy = ""

        filters.forEach { filter ->
            when (filter) {
                is GenreList -> { // ---Genre
                    filter.state.forEach { Genre ->
                        if (Genre.state) {
                            totalstring =
                                totalstring + "&genres_filter%5B" + "%5D=" + Genre.id
                        }
                    }
                }

                is YearList -> { // ---Year
                    filter.state.forEach { Year ->
                        if (Year.state) {
                            totalstring =
                                totalstring + "&years_filter%5B" + "%5D=" + Year.id
                        }
                    }
                }

                is ProducerList -> { // ---Producer
                    filter.state.forEach { Producer ->
                        if (Producer.state) {
                            totalstring =
                                totalstring + "&studios_filter%5B" + "%5D=" + Producer.id
                        }
                    }
                }

                is OrderList -> { // ---Order
                    sortBy = getOrder()[filter.state].id
                }

                else -> {}
            }
        }

        return "$totalstring&submit=Submit&filter=$sortBy"
    }

    override fun getFilterList(): AnimeFilterList = AnimeFilterList(
        AnimeFilter.Header("Ignored if using Text Search"),
        AnimeFilter.Separator(),
        OrderList(orderName),
        GenreList(getGenres()),
        YearList(getYears()),
        ProducerList(getProducer()),
    )

    companion object {
        private const val PREF_QUALITY_KEY = "preferred_quality"
        private const val PREF_QUALITY_TITLE = "Preferred video quality"
        private val PREF_QUALITY_ENTRIES = arrayOf("Mirror 1", "Mirror 2", "Mirror 3", "Beta")
    }
}
