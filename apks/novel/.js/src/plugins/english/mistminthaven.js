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

const fetch_1 = require("@libs/fetch");
const defaultCover_1 = require("@libs/defaultCover");
const novelStatus_1 = require("@libs/novelStatus");

class MistmintHaven {
    constructor() {
        this.id = "mistminthaven";
        this.name = "Mistmint Haven";
        this.icon = "src/en/mistminthaven/icon.png";
        this.site = "https://www.mistminthaven.com/";
        this.apiSite = "https://api.mistminthaven.com/api/";
        this.version = "2.0.0";
    }

    popularNovels(page) {
        return __awaiter(this, void 0, void 0, function* () {
            const url = `${this.apiSite}novel?page=${page}&limit=20`;
            const res = yield (0, fetch_1.fetchApi)(url, {
                headers: { "Content-Type": "application/json", "type": "reader" }
            });
            const json = yield res.json();
            const list = json.data || [];
            return list.map(item => ({
                name: item.title || "Untitled",
                path: item.slug,
                cover: item.avatarUrl || defaultCover_1.defaultCover
            }));
        });
    }

    parseNovel(novelPath) {
        return __awaiter(this, void 0, void 0, function* () {
            const slug = novelPath.replace(/(^\/+|\/+$)/g, "");
            const novelRes = yield (0, fetch_1.fetchApi)(`${this.apiSite}novel/slug/${slug}`, {
                headers: { "type": "reader" }
            });
            const novelJson = yield novelRes.json();
            const n = novelJson.data || {};

            const chRes = yield (0, fetch_1.fetchApi)(`${this.apiSite}novels/slug/${slug}/chapters`, {
                headers: { "type": "reader" }
            });
            const chJson = yield chRes.json();
            const volumes = chJson.data || [];

            const chapters = [];
            for (const vol of volumes) {
                const volChapters = vol.chapters || [];
                for (const ch of volChapters) {
                    chapters.push({
                        name: ch.title || `Chapter ${ch.chapterNumber || chapters.length + 1}`,
                        path: `${slug}/${ch.slug}`,
                        chapterNumber: Number(ch.chapterNumber) || (chapters.length + 1),
                        releaseTime: ch.createdAt
                    });
                }
            }

            let status = novelStatus_1.NovelStatus.Unknown;
            if (n.status === 1) status = novelStatus_1.NovelStatus.Ongoing;
            else if (n.status === 2) status = novelStatus_1.NovelStatus.Completed;

            const genres = (n.genres || []).map(g => g.name).join(", ");

            return {
                path: slug,
                name: n.title || "Untitled",
                cover: n.avatarUrl || defaultCover_1.defaultCover,
                author: n.author || "Unknown",
                summary: n.description || "",
                genres,
                status,
                chapters
            };
        });
    }

    parseChapter(chapterPath) {
        return __awaiter(this, void 0, void 0, function* () {
            const parts = chapterPath.replace(/(^\/+|\/+$)/g, "").split("/");
            const novelSlug = parts[0];
            const chapterSlug = parts[1];

            const url = `${this.apiSite}chapters/${novelSlug}/${chapterSlug}/content`;
            const res = yield (0, fetch_1.fetchApi)(url, {
                headers: { "type": "reader" }
            });
            const json = yield res.json();
            const data = json.data || {};
            return data.content || "Content not found.";
        });
    }

    searchNovels(searchTerm, page) {
        return __awaiter(this, void 0, void 0, function* () {
            const url = `${this.apiSite}novel?search=${encodeURIComponent(searchTerm)}&page=${page}&limit=20`;
            const res = yield (0, fetch_1.fetchApi)(url, {
                headers: { "Content-Type": "application/json", "type": "reader" }
            });
            const json = yield res.json();
            const list = json.data || [];
            return list.map(item => ({
                name: item.title || "Untitled",
                path: item.slug,
                cover: item.avatarUrl || defaultCover_1.defaultCover
            }));
        });
    }
}

exports.default = new MistmintHaven();
