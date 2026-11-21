"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.MadaraPlugin = void 0;
var cheerio = require("cheerio");
var fetchMod = require("@libs/fetch");
var statusMod = require("@libs/novelStatus");
var coverMod = require("@libs/defaultCover");

var MadaraPlugin = /** @class */ (function () {
    function MadaraPlugin(cfg) {
        this.id = cfg.id;
        this.name = cfg.sourceName;
        this.icon = "multisrc/madara/" + cfg.id.toLowerCase() + "/icon.png";
        this.site = cfg.sourceSite;
        this.apiBase = "https://api.azorafly.com";
        this.version = "2.2.1";
        this.options = cfg.options || {};
        this.filters = cfg.filters || {};
    }

    MadaraPlugin.prototype.popularNovels = async function (pageNo, opts) {
        opts = opts || {};
        var url = this.apiBase + "/api/query?seriesType=NOVEL&perPage=20&page=" + (pageNo || 1);
        if (opts.showLatestNovels) {
            url += "&orderBy=updatedAt";
        }
        var res = await (0, fetchMod.fetchApi)(url);
        var data = await res.json();
        return (data.posts || []).map(function (p) {
            return {
                name: p.postTitle || "",
                path: "series/" + p.slug,
                cover: p.featuredImage || coverMod.defaultCover
            };
        });
    };

    MadaraPlugin.prototype.searchNovels = async function (searchTerm, pageNo) {
        var url = this.apiBase + "/api/query?seriesType=NOVEL&perPage=20&page=" + (pageNo || 1) + "&searchTerm=" + encodeURIComponent(searchTerm);
        var res = await (0, fetchMod.fetchApi)(url);
        var data = await res.json();
        return (data.posts || []).map(function (p) {
            return {
                name: p.postTitle || "",
                path: "series/" + p.slug,
                cover: p.featuredImage || coverMod.defaultCover
            };
        });
    };

    MadaraPlugin.prototype.parseNovel = async function (novelPath) {
        var slug = novelPath.replace(/^\/?series\//, "").replace(/\/.*$/, "");
        var postRes = await (0, fetchMod.fetchApi)(this.apiBase + "/api/post?postSlug=" + encodeURIComponent(slug));
        var postJson = await postRes.json();
        var post = postJson.post || postJson;
        var summary = (post.postContent || "").replace(/<[^>]+>/g, " ").replace(/\s+/g, " ").trim();
        var genres = Array.isArray(post.genres)
            ? post.genres.map(function (g) { return (g.name || "").trim(); }).filter(Boolean).join(", ")
            : "";
        var st = String(post.seriesStatus || "").toUpperCase();
        var novelStatus = st === "COMPLETED"
            ? statusMod.NovelStatus.Completed
            : (st === "HIATUS" ? statusMod.NovelStatus.OnHiatus : statusMod.NovelStatus.Ongoing);

        var chapters = [];
        var skip = 0;
        var take = 500;
        while (post.id) {
            var chUrl = this.apiBase + "/api/chapters?postId=" + post.id + "&skip=" + skip + "&take=" + take + "&order=asc";
            var chRes = await (0, fetchMod.fetchApi)(chUrl);
            var chJson = await chRes.json();
            var list = (chJson.post && chJson.post.chapters) ? chJson.post.chapters : [];
            if (!list.length) break;
            for (var i = 0; i < list.length; i++) {
                var c = list[i];
                if (!c.isPermanentlyLocked && !c.isShortLinkLocked && (!c.price || c.price === 0)) {
                    chapters.push({
                        name: c.title ? c.title : ("\u0627\u0644\u0641\u0635\u0644 " + c.number),
                        path: "series/" + slug + "/" + c.slug,
                        chapterNumber: c.number,
                        releaseTime: c.createdAt || null
                    });
                }
            }
            if (list.length < take) break;
            skip += take;
        }

        return {
            path: "series/" + slug,
            name: post.postTitle || slug,
            cover: post.featuredImage || coverMod.defaultCover,
            summary: summary,
            genres: genres,
            status: novelStatus,
            chapters: chapters
        };
    };

    MadaraPlugin.prototype.parseChapter = async function (chapterPath) {
        var url = this.site + chapterPath.replace(/^\/+/, "");
        var html = await (await (0, fetchMod.fetchApi)(url)).text();
        var $ = cheerio.load(html);
        return $('section[itemprop="articleBody"], section[itemProp="articleBody"]').html() || "";
    };

    return MadaraPlugin;
}());

exports.MadaraPlugin = MadaraPlugin;
var v = new MadaraPlugin({
    id: "azora",
    sourceSite: "https://azorafly.com/",
    sourceName: "Azora",
    options: { useNewChapterEndpoint: true, lang: "Arabic" },
    filters: {}
});
exports.default = v;
