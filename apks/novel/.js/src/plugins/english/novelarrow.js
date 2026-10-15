"use strict";
var __awaiter = (this && this.__awaiter) || function (thisArg, _arguments, P, generator) {
    function adopt(value) { return value instanceof P ? value : new P(function (resolve) { resolve(value); }); }
    return new (P || (P = Promise))(function (resolve, reject) {
        function fulfilled(value) { try { step(generator.next(value)); } catch (e) { reject(e); } }
        function rejected(value) { try { step(generator["throw"](value)); } catch (e) { reject(e); } }
        function step(result) { result.done ? resolve(result.value) : adopt(result.value).then(fulfilled, rejected); }
        step((generator = generator.apply(thisArg, _arguments || [])).next());
    });
};
Object.defineProperty(exports, "__esModule", { value: true });

const cheerio = require("cheerio");
const fetch_1 = require("@libs/fetch");
const defaultCover_1 = require("@libs/defaultCover");
const novelStatus_1 = require("@libs/novelStatus");

class NovelArrow {
    constructor() {
        this.id = "novelarrow";
        this.name = "Novel Arrow";
        this.icon = "src/en/novelarrow/icon.png";
        this.site = "https://novelping.com/";
        this.version = "2.0.0";
    }

    popularNovels(page) {
        return __awaiter(this, void 0, void 0, function* () {
            const url = `${this.site}sort/popular?page=${page}`;
            const res = yield (0, fetch_1.fetchApi)(url);
            const html = yield res.text();
            const $ = cheerio.load(html);

            const novels = [];
            $(".row, .col-novel-main, .list-novel .row").each((i, el) => {
                const link = $(el).find('a[href*="/book/"]').first();
                const href = link.attr("href");
                const name = link.attr("title") || link.text().trim();
                const cover = $(el).find("img").first().attr("src") || $(el).find("img").first().attr("data-src");

                if (href && name && !href.includes("/chapter-") && !novels.some(n => n.name === name)) {
                    const cleanPath = href.replace(this.site, "").replace(/(^\/+|\/+$)/g, "");
                    novels.push({
                        name: name.replace(/\s+/g, " ").trim(),
                        path: cleanPath,
                        cover: cover ? (cover.startsWith("http") ? cover : this.site + cover.replace(/^\//, "")) : defaultCover_1.defaultCover
                    });
                }
            });
            return novels;
        });
    }

    parseNovel(novelPath) {
        return __awaiter(this, void 0, void 0, function* () {
            const url = this.site + novelPath.replace(/(^\/+|\/+$)/g, "");
            const res = yield (0, fetch_1.fetchApi)(url);
            const html = yield res.text();
            const $ = cheerio.load(html);

            const name = $("h1, .novel-title").first().text().trim() || "Untitled";
            const coverImg = $('meta[property="og:image"]').attr("content") ||
                             $(".cover img, img.cover, .book-info img").first().attr("src");
            const cover = coverImg ? (coverImg.startsWith("http") ? coverImg : this.site + coverImg.replace(/^\//, "")) : defaultCover_1.defaultCover;
            const author = $('meta[name="author"], .author a, .author span').first().text().trim() || "Unknown";
            const summary = $(".desc-text, .summary, .description").text().trim();

            const chapters = [];
            $('a[href*="/chapter-"]').each((i, el) => {
                const cHref = $(el).attr("href");
                const cName = $(el).text().replace(/\s+/g, " ").trim();
                if (cHref && cName && !cName.toLowerCase().includes("read now") && !chapters.some(c => c.path === cHref.replace(this.site, ""))) {
                    const cPath = cHref.replace(this.site, "").replace(/(^\/+|\/+$)/g, "");
                    chapters.push({
                        name: cName,
                        path: cPath,
                        chapterNumber: chapters.length + 1
                    });
                }
            });

            return {
                path: novelPath,
                name,
                cover,
                author,
                summary,
                status: novelStatus_1.NovelStatus.Ongoing,
                chapters
            };
        });
    }

    parseChapter(chapterPath) {
        return __awaiter(this, void 0, void 0, function* () {
            const url = this.site + chapterPath.replace(/(^\/+|\/+$)/g, "");
            const res = yield (0, fetch_1.fetchApi)(url);
            const html = yield res.text();
            const $ = cheerio.load(html);

            $("script, style, noscript, .sharedaddy, .ads, .advertisement").remove();
            const content = $("#chr-content, #chapter-content, .chr-c").html() || "";
            return content;
        });
    }

    searchNovels(searchTerm, page) {
        return __awaiter(this, void 0, void 0, function* () {
            const url = `${this.site}search?keyword=${encodeURIComponent(searchTerm)}&page=${page}`;
            const res = yield (0, fetch_1.fetchApi)(url);
            const html = yield res.text();
            const $ = cheerio.load(html);

            const novels = [];
            $(".row, .col-novel-main, .list-novel .row").each((i, el) => {
                const link = $(el).find('a[href*="/book/"]').first();
                const href = link.attr("href");
                const name = link.attr("title") || link.text().trim();
                const cover = $(el).find("img").first().attr("src") || $(el).find("img").first().attr("data-src");

                if (href && name && !href.includes("/chapter-") && !novels.some(n => n.name === name)) {
                    const cleanPath = href.replace(this.site, "").replace(/(^\/+|\/+$)/g, "");
                    novels.push({
                        name: name.replace(/\s+/g, " ").trim(),
                        path: cleanPath,
                        cover: cover ? (cover.startsWith("http") ? cover : this.site + cover.replace(/^\//, "")) : defaultCover_1.defaultCover
                    });
                }
            });
            return novels;
        });
    }
}

exports.default = new NovelArrow();