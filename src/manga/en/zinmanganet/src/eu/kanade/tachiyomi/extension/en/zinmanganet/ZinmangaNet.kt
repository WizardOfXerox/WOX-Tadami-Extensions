package eu.kanade.tachiyomi.extension.en.zinmanganet

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@Source
abstract class ZinmangaNet : Madara() {
    override val dateFormat = SimpleDateFormat("MM/dd/yyyy", Locale.ROOT)
    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = false
    override val filterNonMangaItems = false

    private val apiDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private val apiHeaders by lazy {
        headers.newBuilder()
            .add("Accept", "application/json")
            .build()
    }

    private val imageHeaders by lazy {
        headersBuilder()
            .set("Referer", "$baseUrl/")
            .set("Origin", baseUrl)
            .build()
    }

    override fun fetchChapterList(manga: SManga): rx.Observable<List<SChapter>> = rx.Observable.fromCallable {
        val allChapters = mutableListOf<SChapter>()
        val slug = "$baseUrl${manga.url}".toHttpUrl().pathSegments.getOrNull(1) ?: return@fromCallable emptyList()

        var currentPage = 1
        var lastPage = 1

        do {
            val apiUrl = "$baseUrl/api/comics/$slug/chapters?page=$currentPage&per_page=50&order=desc"
            val response = client.newCall(GET(apiUrl, apiHeaders)).execute()
            if (!response.isSuccessful) {
                response.close()
                break
            }
            val data = response.parseAs<ChapterListResponse>().data

            lastPage = data.lastPage
            data.chapters.forEach { chapter ->
                allChapters.add(chapter.toSChapter(slug) { apiDateFormat.tryParse(it?.substringBefore(".")) })
            }

            currentPage++
        } while (currentPage <= lastPage)

        allChapters
    }

    override fun imageRequest(page: Page): Request = GET(page.imageUrl!!, imageHeaders)
}

@Serializable
class ChapterListResponse(
    val data: ChapterData,
)

@Serializable
class ChapterData(
    val chapters: List<ChapterDto>,
    @SerialName("last_page") val lastPage: Int,
)

@Serializable
class ChapterDto(
    @SerialName("chapter_name") private val chapterName: String,
    @SerialName("chapter_slug") private val chapterSlug: String,
    @SerialName("updated_at") private val updatedAt: String? = null,
) {
    fun toSChapter(slug: String, parseDate: (String?) -> Long) = SChapter.create().apply {
        url = "/manga/$slug/$chapterSlug"
        name = chapterName
        date_upload = parseDate(updatedAt)
    }
}
