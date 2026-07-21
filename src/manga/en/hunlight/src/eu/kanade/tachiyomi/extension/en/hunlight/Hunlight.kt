package eu.kanade.tachiyomi.extension.en.hunlight

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.annotation.Source
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup

@Source
class Hunlight(
    override val lang: String = "en",
    override val id: Long = 0,
) : HttpSource() {
    override val name = "Hunlight"
    override val baseUrl = "https://hunlightcomics.com"
    override val supportsLatest = true

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/manga/page/$page/?m_orderby=views", headers)
    override fun popularMangaParse(response: Response): MangasPage {
        val doc = Jsoup.parse(response.body.string())
        val mangas = doc.select("div.page-item-detail, div.manga-item").map { element ->
            SManga.create().apply {
                val a = element.selectFirst("a")
                setUrlWithoutDomain(a?.attr("href") ?: "")
                title = element.selectFirst("h3 a, h4 a, a.title")?.text() ?: a?.text() ?: "Unknown"
                thumbnail_url = element.selectFirst("img")?.let { it.attr("abs:src").ifBlank { it.attr("data-src") } }
            }
        }
        val hasNextPage = doc.selectFirst("a.next, a.pagination-next") != null
        return MangasPage(mangas, hasNextPage)
    }

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/manga/page/$page/?m_orderby=latest", headers)
    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request =
        GET("$baseUrl/?s=$query&post_type=wp-manga&page=$page", headers)
    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    override fun mangaDetailsParse(response: Response): SManga {
        val doc = Jsoup.parse(response.body.string())
        return SManga.create().apply {
            title = doc.selectFirst("div.post-title h1, h1")?.text() ?: "Unknown Title"
            description = doc.selectFirst("div.summary__content, div.description")?.text()
            thumbnail_url = doc.selectFirst("div.summary_image img")?.attr("abs:src")
            genre = doc.select("div.genres-content a").joinToString(", ") { it.text() }
            initialized = true
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val doc = Jsoup.parse(response.body.string())
        return doc.select("li.wp-manga-chapter a").map { element ->
            SChapter.create().apply {
                setUrlWithoutDomain(element.attr("href"))
                name = element.text().ifBlank { "Chapter 1" }
                chapter_number = element.text().filter { it.isDigit() }.toFloatOrNull() ?: 1.0f
            }
        }
    }

    override fun pageListParse(response: Response): List<Page> {
        val doc = Jsoup.parse(response.body.string())
        return doc.select("div.page-break img, div.reading-content img").mapIndexed { i, img ->
            val src = img.attr("abs:src").ifBlank { img.attr("abs:data-src") }
            Page(i, "", src)
        }
    }

    override fun imageUrlParse(response: Response): String = ""
}
