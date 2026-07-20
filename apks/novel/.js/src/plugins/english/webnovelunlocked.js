const { fetchApi } = require("@libs/fetch");
const { load } = require("cheerio");
const { FilterTypes } = require("@libs/filterInputs");

class WebnovelUnlockedPlugin {
  constructor() {
    this.id = "webnovelunlocked";
    this.name = "Webnovel (Unlocked)";
    this.version = "1.0.0";
    this.icon = "src/en/webnovel/icon.png";
    this.site = "https://www.webnovel.com";
    this.headers = {
      "User-Agent":
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    };
    this.imageRequestInit = { headers: { referrer: this.site } };

    // Fallback sources for locked chapters
    this.fallbackSources = [
      {
        name: "NovelBin",
        site: "https://novelbin.com",
        searchPath: "/search?keyword=",
        chapterContentSelector: "#chr-content, #chapter-content, .chr-content, .chapter-content",
        removeSelectors: [".ads", ".unlock-buttons", "sub", "iframe", '[class*="app-promo"]'],
      },
      {
        name: "NovelFull",
        site: "https://novelfull.com",
        searchPath: "/search?keyword=",
        chapterContentSelector: "#chapter-content, .chapter-c",
        removeSelectors: [".ads", "sub", "iframe"],
      },
      {
        name: "LibRead",
        site: "https://libread.com",
        searchPath: "/search?keyword=",
        chapterContentSelector: "#chapter-content, .chapter-c",
        removeSelectors: [".ads", "sub", "iframe"],
      },
    ];

    this.filters = {
      sort: {
        label: "Sort Results By",
        value: "1",
        options: [
          { label: "Popular", value: "1" },
          { label: "Recommended", value: "2" },
          { label: "Most Collections", value: "3" },
          { label: "Rating", value: "4" },
          { label: "Time Updated", value: "5" },
        ],
        type: FilterTypes.Picker,
      },
      status: {
        label: "Content Status",
        value: "0",
        options: [
          { label: "All", value: "0" },
          { label: "Completed", value: "2" },
          { label: "Ongoing", value: "1" },
        ],
        type: FilterTypes.Picker,
      },
      genres_gender: {
        label: "Genres (Male/Female)",
        value: "1",
        options: [
          { label: "Male", value: "1" },
          { label: "Female", value: "2" },
        ],
        type: FilterTypes.Picker,
      },
      genres_male: {
        label: "Male Genres",
        value: "1",
        options: [
          { label: "All", value: "1" },
          { label: "Action", value: "novel-action-male" },
          { label: "Animation, Comics, Games", value: "novel-acg-male" },
          { label: "Eastern", value: "novel-eastern-male" },
          { label: "Fantasy", value: "novel-fantasy-male" },
          { label: "Games", value: "novel-games-male" },
          { label: "History", value: "novel-history-male" },
          { label: "Horror", value: "novel-horror-male" },
          { label: "Realistic", value: "novel-realistic-male" },
          { label: "Sci-fi", value: "novel-scifi-male" },
          { label: "Sports", value: "novel-sports-male" },
          { label: "Urban", value: "novel-urban-male" },
          { label: "War", value: "novel-war-male" },
        ],
        type: FilterTypes.Picker,
      },
      genres_female: {
        label: "Female Genres",
        value: "2",
        options: [
          { label: "All", value: "2" },
          { label: "Fantasy", value: "novel-fantasy-female" },
          { label: "General", value: "novel-general-female" },
          { label: "History", value: "novel-history-female" },
          { label: "LGBT+", value: "novel-lgbt-female" },
          { label: "Sci-fi", value: "novel-scifi-female" },
          { label: "Teen", value: "novel-teen-female" },
          { label: "Urban", value: "novel-urban-female" },
        ],
        type: FilterTypes.Picker,
      },
      type: {
        label: "Content Type",
        value: "0",
        options: [
          { label: "All", value: "0" },
          { label: "Translate", value: "1" },
          { label: "Original", value: "2" },
          { label: "MTL (Machine Translation)", value: "3" },
        ],
        type: FilterTypes.Picker,
      },
    };
  }

  // Browse / Popular Novels
  async popularNovels(page, { showLatestNovels, filters }) {
    let url = this.site + "/stories/";

    if (showLatestNovels) {
      url += "novel?orderBy=5&pageIndex=" + page;
    } else if (filters) {
      const params = new URLSearchParams();
      if (filters.genres_gender.value === "1") {
        if (filters.genres_male.value !== "1") {
          url += filters.genres_male.value;
        } else {
          url += "novel";
          params.append("gender", "1");
        }
      } else if (filters.genres_gender.value === "2") {
        if (filters.genres_female.value !== "2") {
          url += filters.genres_female.value;
        } else {
          url += "novel";
          params.append("gender", "2");
        }
      }
      if (filters.type.value === "3") {
        params.append("translateMode", "3");
        params.append("sourceType", "1");
      } else {
        params.append("sourceType", filters.type.value);
      }
      params.append("bookStatus", filters.status.value);
      params.append("orderBy", filters.sort.value);
      params.append("pageIndex", page.toString());
      url += "?" + params.toString();
    } else {
      url += "novel?orderBy=1&pageIndex=" + page;
    }

    const res = await fetchApi(url, { headers: this.headers });
    const body = await res.text();
    const $ = load(body);

    return $(".j_category_wrapper li")
      .map((i, el) => {
        const title = $(el).find(".g_thumb").attr("title") || "No Title Found";
        const cover = $(el).find(".g_thumb > img").attr("data-original");
        const href = $(el).find(".g_thumb").attr("href");
        if (!href) return null;
        return { name: title, cover: "https:" + cover, path: href };
      })
      .get()
      .filter(Boolean);
  }

  // Novel Details
  async parseNovel(novelPath) {
    const url = this.site + novelPath;
    const res = await fetchApi(url, { headers: this.headers });
    const body = await res.text();
    const $ = load(body);

    const novel = {
      path: novelPath,
      name: $(".g_thumb > img").attr("alt") || "No Title Found",
      cover: "https:" + $(".g_thumb > img").attr("src"),
      genres: $(".det-hd-detail > .det-hd-tag").attr("title") || "",
      summary:
        $(".j_synopsis > p").find("br").replaceWith("\n").end().text().trim() ||
        "No Summary Found",
      author:
        $(".det-info .c_s")
          .filter((i, el) => $(el).text().trim() === "Author:")
          .next()
          .text()
          .trim() || "No Author Found",
      status:
        $(".det-hd-detail svg")
          .filter((i, el) => $(el).attr("title") === "Status")
          .next()
          .text()
          .trim() || "Unknown Status",
    };

    // Parse chapter list from catalog - ALL chapters, including locked ones
    novel.chapters = await this.parseChapters(novelPath);
    return novel;
  }

  // Chapter List - includes ALL chapters (locked shown with unlock icon)
  async parseChapters(novelPath) {
    const catalogUrl = this.site + novelPath + "/catalog";
    const res = await fetchApi(catalogUrl, { headers: this.headers });
    const body = await res.text();
    const $ = load(body);

    const chapters = [];
    $(".volume-item").each((i, vol) => {
      const volMatch = $(vol).first().text().trim().match(/Volume\s(\d+)/);
      const volName = volMatch ? "Volume " + volMatch[1] : "Unknown Volume";

      $(vol)
        .find("li")
        .each((j, li) => {
          const titleAttr = $(li).find("a").attr("title");
          const chName = volName + ": " + (titleAttr ? titleAttr.trim() : "No Title Found");
          const chPath = $(li).find("a").attr("href");
          const isLocked = $(li).find("svg").length > 0;

          if (chPath) {
            chapters.push({
              name: isLocked ? chName + " \uD83D\uDD13" : chName,
              path: chPath,
            });
          }
        });
    });

    return chapters;
  }

  // Chapter Content with automatic fallback for locked chapters
  async parseChapter(chapterPath) {
    // Step 1: Try webnovel.com directly
    const url = this.site + chapterPath;
    const res = await fetchApi(url, { headers: this.headers });
    const body = await res.text();
    const $ = load(body);

    // Remove comment sections
    $(".para-comment").remove();

    const chapterTitle = $(".cha-tit").html() || "";
    const chapterContent = $(".cha-words").html() || "";
    const textContent = $(".cha-words").text().trim();

    // Detect if chapter is locked
    const isLocked =
      textContent.length < 100 ||
      $(".cha-words .lock-icon").length > 0 ||
      $(".cha-words .j_locked_chap").length > 0 ||
      $(".lock-chapter-notice").length > 0 ||
      body.includes("This chapter is locked") ||
      body.includes("unlock this chapter") ||
      body.includes("Subscribe to unlock");

    if (!isLocked && textContent.length > 100) {
      return chapterTitle + chapterContent;
    }

    // Step 2: Extract novel name from the chapter page for fallback search
    const novelName =
      $(".cha-tit .dib-acc a").first().text().trim() ||
      $('meta[property="og:novel:novel_name"]').attr("content") ||
      $("h1").first().text().trim() ||
      $(".g_thumb > img").attr("alt") ||
      "";

    // Extract chapter number
    const chapTitleText = $(".cha-tit").text().trim();
    let chapterNumber = 1;
    const chMatch = chapTitleText.match(/(?:Chapter|Ch\.?)\s*(\d+)/i);
    if (chMatch) {
      chapterNumber = parseInt(chMatch[1]);
    } else {
      const urlMatch = chapterPath.match(/(\d+)\s*$/);
      if (urlMatch) chapterNumber = parseInt(urlMatch[1]);
    }

    if (novelName) {
      for (const source of this.fallbackSources) {
        try {
          const content = await this.fetchFromFallback(source, novelName, chapterNumber);
          if (content && content.length > 100) {
            return (
              chapterTitle +
              '<div style="padding:8px;margin-bottom:12px;background:#1a1a2e;border-left:3px solid #e94560;border-radius:4px;color:#aaa;font-size:12px;">' +
              "\uD83D\uDCD6 Content sourced from " + source.name +
              "</div>" +
              content
            );
          }
        } catch (e) {
          continue;
        }
      }
    }

    // Step 3: All fallbacks failed
    return (
      chapterTitle +
      '<div style="padding:20px;text-align:center;color:#e94560;">' +
      "<p>\uD83D\uDD12 This chapter is locked on Webnovel and could not be found on fallback sources.</p>" +
      '<p style="color:#888;font-size:12px;">Try searching for this novel on NovelBin, NovelFull, or LibRead directly.</p>' +
      "</div>"
    );
  }

  // Fetch chapter content from a fallback aggregator site
  async fetchFromFallback(source, novelName, chapterNumber) {
    // Search for the novel
    const searchUrl = source.site + source.searchPath + encodeURIComponent(novelName);
    const searchRes = await fetchApi(searchUrl, { headers: this.headers });
    if (!searchRes.ok) return null;
    const searchBody = await searchRes.text();
    const $search = load(searchBody);

    // Find the best matching novel
    let novelPath = null;
    const searchNorm = novelName.toLowerCase().replace(/[^a-z0-9]/g, "");

    $search(
      ".archive .novel-title a, h3.novel-title a, .truyen-title a, .col-content h3 a, h3 a[href*='novel']"
    ).each((i, el) => {
      if (novelPath) return;
      const title = $search(el).text().trim();
      const titleNorm = title.toLowerCase().replace(/[^a-z0-9]/g, "");
      const href = $search(el).attr("href");
      if (href && (titleNorm.includes(searchNorm) || searchNorm.includes(titleNorm))) {
        novelPath = href.startsWith("http") ? new URL(href).pathname : href;
      }
    });

    if (!novelPath) return null;

    // Fetch the novel page to get the AJAX chapter listing ID
    const novelUrl = source.site + novelPath;
    const novelRes = await fetchApi(novelUrl, { headers: this.headers });
    if (!novelRes.ok) return null;
    const novelBody = await novelRes.text();
    const $novel = load(novelBody);

    // Look for the chapter by number (try direct links first)
    let chapterHref = null;
    const chapterPatterns = [
      "chapter-" + chapterNumber + ".",
      "chapter-" + chapterNumber + "/",
      "chapter_" + chapterNumber,
      "-ch-" + chapterNumber,
    ];

    // Search in the page links
    $novel("a[href*='chapter']").each((i, el) => {
      if (chapterHref) return;
      const href = ($novel(el).attr("href") || "").toLowerCase();
      const text = $novel(el).text().trim().toLowerCase();

      for (const pattern of chapterPatterns) {
        if (href.includes(pattern)) {
          chapterHref = $novel(el).attr("href");
          return;
        }
      }

      const m = text.match(/(?:chapter|ch\.?)\s*(\d+)/i);
      if (m && parseInt(m[1]) === chapterNumber) {
        chapterHref = $novel(el).attr("href");
      }
    });

    // Try AJAX chapter listing if no chapter found
    if (!chapterHref) {
      const novelId =
        $novel("#rating").attr("data-novel-id") ||
        $novel("#indexListPage").attr("data-novel-id") ||
        $novel('a[class*="set-case"]').attr("data-articleid");

      if (novelId) {
        try {
          const ajaxUrl = source.site + "/ajax/chapter-archive?novelId=" + novelId;
          const ajaxRes = await fetchApi(ajaxUrl);
          if (ajaxRes.ok) {
            const ajaxBody = await ajaxRes.text();
            const $ajax = load(ajaxBody);

            $ajax("a").each((i, el) => {
              if (chapterHref) return;
              const href = ($ajax(el).attr("href") || "").toLowerCase();
              const text = $ajax(el).text().trim().toLowerCase();

              for (const pattern of chapterPatterns) {
                if (href.includes(pattern)) {
                  chapterHref = $ajax(el).attr("href");
                  return;
                }
              }

              const m = text.match(/(?:chapter|ch\.?)\s*(\d+)/i);
              if (m && parseInt(m[1]) === chapterNumber) {
                chapterHref = $ajax(el).attr("href");
              }
            });
          }
        } catch (e) {
          // AJAX failed, continue
        }
      }
    }

    if (!chapterHref) return null;

    // Fetch the actual chapter content
    const chapUrl = chapterHref.startsWith("http") ? chapterHref : source.site + chapterHref;
    const chapRes = await fetchApi(chapUrl, { headers: this.headers });
    if (!chapRes.ok) return null;
    const chapBody = await chapRes.text();
    const $chap = load(chapBody);

    // Remove ads and unwanted elements
    for (const sel of source.removeSelectors) {
      $chap(sel).remove();
    }

    return $chap(source.chapterContentSelector).html();
  }

  // Search
  async searchNovels(searchTerm, page) {
    const url =
      this.site +
      "/search?keywords=" +
      encodeURIComponent(searchTerm) +
      "&pageIndex=" +
      page;
    const res = await fetchApi(url, { headers: this.headers });
    const body = await res.text();
    const $ = load(body);

    return $(".j_list_container li")
      .map((i, el) => {
        const title = $(el).find(".g_thumb").attr("title") || "No Title Found";
        const cover = $(el).find(".g_thumb > img").attr("src");
        const href = $(el).find(".g_thumb").attr("href");
        if (!href) return null;
        return { name: title, cover: "https:" + cover, path: href };
      })
      .get()
      .filter(Boolean);
  }
}

module.exports.default = new WebnovelUnlockedPlugin();
