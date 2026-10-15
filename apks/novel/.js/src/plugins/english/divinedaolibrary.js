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

class DivineDaoLibrary {
    constructor() {
        this.id = "DDL.com";
        this.name = "Divine Dao Library";
        this.icon = "src/en/divinedaolibrary/icon.png";
        this.site = "https://www.divinedaolibrary.com/";
        this.version = "2.0.0";
    }

    popularNovels(page, { showLatestNovels } = {}) {
        return __awaiter(this, void 0, void 0, function* () {
            if (page > 1) return [];
            const url = this.site + "novels/";
            const res = yield (0, fetch_1.fetchApi)(url);
            const html = yield res.text();
            const $ = cheerio.load(html);

            const novels = [];
            $('a[href*="/story/"]').each((i, el) => {
                const href = $(el).attr("href");
                const name = $(el).text().trim();
                if (href && name && !novels.some(n => n.name === name)) {
                    const cleanPath = href.replace(this.site, "").replace(/(^\/+|\/+$)/g, "");
                    novels.push({
                        name,
                        path: cleanPath,
                        cover: defaultCover_1.defaultCover
                    });
                }
            });
            return novels;
        });
    }

    parseNovel(novelPath) {
        return __awaiter(this, void 0, void 0, function* () {
            const url = this.site + novelPath.replace(/(^\/+|\/+$)/g, "") + "/";
            const res = yield (0, fetch_1.fetchApi)(url);
            const html = yield res.text();
            const $ = cheerio.load(html);

            const name = $("h1.story__identity-title, h1").first().text().trim() || "Untitled";
            const cover = $("figure.story__thumbnail img, .story__thumbnail img").first().attr("src") ||
                          $("figure.story__thumbnail a").first().attr("href") ||
                          defaultCover_1.defaultCover;
            const author = $("div.story__identity-meta").text().split("|")[0].replace("Author:", "").replace("by", "").trim();
            const summary = $("section.story__summary").text().trim();
            const statusText = $("span.story__status").text().trim().toLowerCase();

            let status = novelStatus_1.NovelStatus.Unknown;
            if (statusText.includes("ongoing")) status = novelStatus_1.NovelStatus.Ongoing;
            else if (statusText.includes("completed")) status = novelStatus_1.NovelStatus.Completed;
            else if (statusText.includes("dropped")) status = novelStatus_1.NovelStatus.Cancelled;

            const genres = $("div.tag-group a, section.tag-group a")
                .map((i, el) => $(el).text().trim())
                .toArray()
                .join(", ");

            const chapters = [];
            $("li.chapter-group__list-item a, ul.chapter-group__list a").each((idx, el) => {
                const cHref = $(el).attr("href");
                const cName = $(el).text().trim();
                if (cHref && cName) {
                    const cPath = cHref.replace(this.site, "").replace(/(^\/+|\/+$)/g, "");
                    chapters.push({
                        name: cName,
                        path: cPath,
                        chapterNumber: idx + 1
                    });
                }
            });

            return {
                path: novelPath,
                name,
                cover,
                author,
                summary,
                genres,
                status,
                chapters
            };
        });
    }

    parseChapter(chapterPath) {
        return __awaiter(this, void 0, void 0, function* () {
            const url = this.site + chapterPath.replace(/(^\/+|\/+$)/g, "") + "/";
            const res = yield (0, fetch_1.fetchApi)(url);
            const html = yield res.text();
            const $ = cheerio.load(html);

            $("script, style, noscript, .sharedaddy, .fictioneer-chapter, .chapter-actions, .chapter-nav, .comment-respond").remove();
            const content = $("section#chapter-content > div, section#chapter-content, #chapter-content").html() || "";
            return content;
        });
    }

    searchNovels(searchTerm, page) {
        return __awaiter(this, void 0, void 0, function* () {
            if (page > 1) return [];
            const url = this.site + "?s=" + encodeURIComponent(searchTerm) + "&post_type=fcn_story";
            const res = yield (0, fetch_1.fetchApi)(url);
            const html = yield res.text();
            const $ = cheerio.load(html);

            const novels = [];
            $('a[href*="/story/"]').each((i, el) => {
                const href = $(el).attr("href");
                const name = $(el).text().trim();
                if (href && name && !novels.some(n => n.name === name)) {
                    const cleanPath = href.replace(this.site, "").replace(/(^\/+|\/+$)/g, "");
                    novels.push({
                        name,
                        path: cleanPath,
                        cover: defaultCover_1.defaultCover
                    });
                }
            });
            return novels;
        });
    }
}

exports.default = new DivineDaoLibrary();