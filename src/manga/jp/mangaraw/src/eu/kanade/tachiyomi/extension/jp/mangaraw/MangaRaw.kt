package eu.kanade.tachiyomi.extension.jp.mangaraw

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
class MangaRaw(
    override val lang: String = "jp",
    override val id: Long = 0,
) : HttpSource() {
    override val name = "MangaRaw.co.uk"
    override val baseUrl = "https://mangaraw.co.uk"
    override val supportsLatest = true

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/page/$page/", headers)
    override fun popularMangaParse(response: Response): MangasPage {
        val doc = Jsoup.parse(response.body.string())
        val mangas = doc.select("div.film-poster, article.item, div.poster, div.bsx").map { element ->
            SManga.create().apply {
                val a = element.selectFirst("a")
                setUrlWithoutDomain(a?.attr("href") ?: "")
                title = element.selectFirst("img")?.attr("alt") ?: a?.text() ?: "Unknown"
                thumbnail_url = element.selectFirst("img")?.attr("abs:src")
            }
        }
        val hasNextPage = doc.selectFirst("a.next, a.pagination-next") != null
        return MangasPage(mangas, hasNextPage)
    }

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/latest/page/$page/", headers)
    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request =
        GET("$baseUrl/?s=$query&page=$page", headers)
    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    override fun mangaDetailsParse(response: Response): SManga {
        val doc = Jsoup.parse(response.body.string())
        return SManga.create().apply {
            title = doc.selectFirst("h1, div.title")?.text() ?: "Unknown Title"
            description = doc.selectFirst("div.description, div.summary, p.synopsis")?.text()
            thumbnail_url = doc.selectFirst("div.poster img, div.cover img")?.attr("abs:src")
            initialized = true
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val doc = Jsoup.parse(response.body.string())
        return doc.select("ul.episodes li, div.chapter-item, div.eplister li").map { element ->
            SChapter.create().apply {
                val a = element.selectFirst("a")
                setUrlWithoutDomain(a?.attr("href") ?: "")
                name = element.text().ifBlank { "Chapter 1" }
                chapter_number = 1.0f
            }
        }
    }

    override fun pageListParse(response: Response): List<Page> {
        val doc = Jsoup.parse(response.body.string())
        return doc.select("div#readerarea img, div.page-break img").mapIndexed { i, img ->
            Page(i, "", img.attr("abs:src"))
        }
    }

    override fun imageUrlParse(response: Response): String = ""
}
