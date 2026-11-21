"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.MTLNovelPlugin = void 0;
var r = require("cheerio");
var n = require("@libs/fetch");
var a = require("@libs/novelStatus");
var i = require("@libs/defaultCover");

var MTLNovelPlugin = /** @class */ (function () {
    function MTLNovelPlugin(e) {
        this.imageRequestInit = { headers: { Referer: e.sourceSite } };
        this.id = e.id;
        this.name = e.sourceName;
        this.icon = "multisrc/mtlnovel/mtlnovel/icon.png";
        this.site = e.sourceSite;
        this.mainUrl = "https://www.mtlnovels.com/";
        this.version = "1.1.4";
        this.options = e.options || {};
        this.filters = e.filters;
    }

    MTLNovelPlugin.prototype.safeFetch = async function (url, init) {
        var res = await (0, n.fetchApi)(url, init);
        if (!res.ok) {
            throw new Error("Could not reach site (" + res.status + ") try to open in webview.");
        }
        return res;
    };

    MTLNovelPlugin.prototype.parseNovelCards = function (html) {
        var $ = (0, r.load)(html);
        var novels = [];
        var self = this;
        $("div.novel-box").each(function (_, el) {
            var linkEl = $(el).find("h3.title a, .novel-content a, a").first();
            var name = linkEl.text().trim() || $(el).find("h3.title").text().trim();
            var href = linkEl.attr("href") || "";
            var path = href.replace(self.mainUrl, "").replace(self.site, "").replace(/^\/+/, "");
            var imgSrc = $(el).find("img").first().attr("src") || $(el).find("img").first().attr("data-src") || "";
            var cover = imgSrc
                ? (imgSrc.indexOf("http") === 0 ? imgSrc : new URL(imgSrc, self.site).toString())
                : i.defaultCover;
            if (name && path) {
                novels.push({ name: name, path: path, cover: cover });
            }
        });
        return novels;
    };

    MTLNovelPlugin.prototype.popularNovels = async function (pageNo, opts) {
        opts = opts || {};
        var f = opts.filters;
        var showLatestNovels = opts.showLatestNovels;
        var url = this.site + "list/?page=" + pageNo;
        if (f && f.storyStatus && f.storyStatus.value && f.storyStatus.value !== "all") {
            url += "&status=" + encodeURIComponent(f.storyStatus.value);
        }
        if (showLatestNovels) {
            url += "&orderby=date";
        } else if (f && f.order && f.order.value) {
            url += "&orderby=" + encodeURIComponent(f.order.value);
        }
        if (f && f.sort && f.sort.value) {
            url += "&order=" + encodeURIComponent(f.sort.value);
        }
        var html = await (await this.safeFetch(url)).text();
        return this.parseNovelCards(html);
    };

    MTLNovelPlugin.prototype.searchNovels = async function (searchTerm, pageNo) {
        var page = pageNo || 1;
        var url = this.site + "search/?q=" + encodeURIComponent(searchTerm) + "&page=" + page;
        var html = await (await this.safeFetch(url)).text();
        return this.parseNovelCards(html);
    };

    MTLNovelPlugin.prototype.parseNovel = async function (novelPath) {
        var cleanPath = novelPath.replace(this.mainUrl, "").replace(this.site, "").replace(/^\/+/, "");
        var url = this.site + cleanPath;
        var html = await (await this.safeFetch(url)).text();
        var $ = (0, r.load)(html);
        var imgSrc = $(".content-main-image img").first().attr("src") || "";
        var cover = imgSrc
            ? (imgSrc.indexOf("http") === 0 ? imgSrc : new URL(imgSrc, this.site).toString())
            : i.defaultCover;
        var summary = $("div.m-card.text-break").clone().children("h5").remove().end().text().trim();
        var novel = {
            path: cleanPath,
            name: $("h5.m-card-header").first().text().trim() || "Untitled",
            cover: cover,
            summary: summary,
            chapters: [],
        };
        $(".col-md-8 ul li").each(function (_, li) {
            var label = $(li).find("span.text-bold").text().trim();
            var value = $(li).find(".pull-right").text().trim();
            if (/^Status/i.test(label)) {
                if (/completed/i.test(value)) novel.status = a.NovelStatus.Completed;
                else if (/hiatus/i.test(value)) novel.status = a.NovelStatus.OnHiatus;
                else novel.status = a.NovelStatus.Ongoing;
            } else if (/^Author/i.test(label)) {
                novel.author = value;
            } else if (/^Genre/i.test(label)) {
                var genreLinks = $(li).find(".pull-right a").map(function (_, el) { return $(el).text().trim(); }).get().filter(Boolean);
                novel.genres = genreLinks.length ? genreLinks.join(", ") : value;
            }
        });
        var slug = cleanPath.replace(/^info\//, "").replace(/\/.*$/, "");
        var chapUrl = this.site + "ajax/chapters/?slug=" + encodeURIComponent(slug);
        var chapHtml = await (await this.safeFetch(chapUrl)).text();
        var $c = (0, r.load)(chapHtml);
        var chapters = [];
        var self = this;
        $c('a[href*="/read/"]').each(function (_, el) {
            var href = $c(el).attr("href") || "";
            var cPath = href.replace(self.mainUrl, "").replace(self.site, "").replace(/^\/+/, "");
            var cName = $c(el).text().replace(/\s+/g, " ").trim();
            if (cPath && cName) {
                chapters.push({ path: cPath, name: cName, releaseTime: null });
            }
        });
        novel.chapters = chapters;
        return novel;
    };

    MTLNovelPlugin.prototype.parseChapter = async function (chapterPath) {
        var cleanPath = chapterPath.replace(this.mainUrl, "").replace(this.site, "").replace(/^\/+/, "");
        var html = await (await this.safeFetch(this.site + cleanPath)).text();
        var $ = (0, r.load)(html);
        return $("div.content.text-break").html() || $("div.par").html() || "";
    };

    return MTLNovelPlugin;
}());

exports.MTLNovelPlugin = MTLNovelPlugin;
var o = new MTLNovelPlugin({
    id: "mtlnovel-ru",
    sourceSite: "https://mtlnovel.me/",
    sourceName: "MTL Novel (RU)",
    options: { lang: "Russian" },
    filters: {
        order: {
            value: "view",
            label: "Order by",
            options: [
                { label: "Date", value: "date" },
                { label: "Name", value: "name" },
                { label: "Rating", value: "rating" },
                { label: "View", value: "view" }
            ],
            type: "Picker"
        },
        sort: {
            value: "desc",
            label: "Sort by",
            options: [
                { label: "Descending", value: "desc" },
                { label: "Ascending", value: "asc" }
            ],
            type: "Picker"
        },
        storyStatus: {
            value: "all",
            label: "Status",
            options: [
                { label: "All", value: "all" },
                { label: "Ongoing", value: "ongoing" },
                { label: "Complete", value: "completed" }
            ],
            type: "Picker"
        }
    }
});
exports.default = o;
