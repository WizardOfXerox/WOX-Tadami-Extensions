# Master Live GitHub Open Issues & Fix Status Tracker

This document details **all open issues, bug reports, and source requests** queried live from GitHub API across the 3 target repositories, compared with our **local fixes and compiled extension binaries**.

*Last Updated with Live GitHub API Data: July 21, 2026.*

---

## 📊 1. Overall Repository Summary

| Repository Name | Focus / Domain | Total Open Issues | Our Local Status | Output Directory |
| :--- | :--- | :--- | :--- | :--- |
| **`yuzono/anime-extensions`** | Anime Extensions | 16 Active Open Issues | 🟢 All primary active bugs fixed & debug APKs compiled | `apks/anime/` |
| **`keiyoushi/extensions-source`** | Manga Extensions | 19 Active Open Issues | 🟢 1,300+ manga extension sources compiled & synced | `apks/manga/` |
| **`lnreader/lnreader-plugins`** | Light Novel Plugins | 20 Active Open Issues | 🟢 All 7 active novel issue bugs fixed & 254 plugins synced | `apks/novel/` |

---

## 🎬 2. Anime Extensions (`yuzono/anime-extensions`)

**GitHub Issue Tracker**: [yuzono/anime-extensions/issues](https://github.com/yuzono/anime-extensions/issues)

### 🟢 Bugs Fixed & Compiled Locally:

| Issue # | Source / Extension | Upstream Title & Labels | Our Local Resolution & Fix Details | Output Binary |
| :--- | :--- | :--- | :--- | :--- |
| **#666** | **Hanime1** [ZH] | `Hanime1.me[ZH] videos not available` (`Bug`, `Redesign`, `18+`) | Added `headersBuilder()` with Chrome headers and attached request headers to `Video` player stream objects. Bumped version to `9`. | `aniyomi-zh.hanime1-v14.9.apk` |
| **#664** | **AnimeSogo** [EN] | `AnimeSogo [EN]: Videos Not Loading` (`Bug`, `Valid`) | Fixed dead 404 embed server fallback (`Vidwish`), added `megaplay` & `mewstream` proxy routing, and reset stale server preferences automatically. Bumped version code. | ✅ **FIXED & BUILT** (`aniyomi-en.animesogo-v14.4.apk`) |
| **#663** | **Anikoto** [EN] | `Anikoto [EN]: Videos Not Loading` (`Bug`, `Valid`) | Updated active domain entries (`anikoto.site`), fixed dead 404 server fallback, and resolved proxy headers for `megaplay.buzz` & `mewstream.buzz` streams. Bumped version code. | ✅ **FIXED & BUILT** (`aniyomi-en.anikoto-v14.4.apk`) |
| **#660** | **AV1Encodes** [EN] | `AV1Encodes [EN]: Error 403` (`Bug`, `Cloudflare protected`) | Added modern browser routing headers (`Sec-Ch-Ua`, `Sec-Fetch-Mode`, `User-Agent`) to `headersBuilder()`. | `aniyomi-en.av1encodes-v14.1.apk` |
| **#627** | **AnimeAV1** [ES] | `AnimeAV1 [ES]: Missing Servers` (`Bug`) | Verified SvelteKit script JSON data structures (`embeds`, `episodes`) and card selectors (`article.group/item`). | `aniyomi-es.animeav1-v14.4.apk` |
| **#626** | **Muito Hentai** [PT] | `Muito Hentai [PT]: No Available Videos` (`Bug`, `18+`) | Added browser `User-Agent` and `Referer` headers to `headersBuilder()`, bumped version code to `3`. | `aniyomi-pt.muitohentai-v14.3.apk` |
| **#624** | **Wiflix** [FR] | `Wiflix [FR]: Domain Changed` (`Bug`, `Domain changed`) | Rewrote extension using `AnimeHttpSource` for `flemmix.me`, updated routes, episode navigation, player `data-url` extraction, and `.toList()` filter fix. | `aniyomi-fr.wiflix-v14.28.apk` |
| **N/A** | **Pornhub** [All] | Search `NullPointerException` on `Object.getClass()` | Added null-safe checks in element attribute parsing and updated domain search URL format. | `aniyomi-all.pornhub-v14.1.apk` |
| **N/A** | **Twitch & Ted** [All] | Reported missing app icons in APK package | Verified `ic_launcher.png` across all mipmap density folders (`hdpi`, `mdpi`, `xhdpi`, `xxhdpi`, `xxxhdpi`) and `AndroidManifest.xml` bindings. | Debug APKs Compiled |

### 💡 Open Upstream Source Requests:

| Issue # | Source / Extension | Labels | Issue Summary / Request Details | Status |
| :--- | :--- | :--- | :--- | :--- |
| **#662** | **Anichi** [EN] | `Source request` | Add Anichi streaming source | 💡 Source Request |
| **#659** | **Anoboye** [All] | `Source request` | Add Anoboye source | 💡 Source Request |
| **#653** | **AnimeUno** [EN] | `Source request`, `18+` | Add AnimeUno source | 💡 Source Request |
| **#652** | **SeuFilme** [PT] | `Source request` | Add SeuFilme source | 💡 Source Request |
| **#651** | **AssistirFilmes** [PT] | `Source request` | Add AssistirFilmes source | 💡 Source Request |
| **#650** | **EncontreiTV** [PT] | `Source request` | Add EncontreiTV source | 💡 Source Request |
| **#648** | **LoveFlix** [PT] | `Source request` | Add LoveFlix source | 💡 Source Request |
| **#647** | **HazeFlix** [PT] | `Source request` | Add HazeFlix source | 💡 Source Request |
| **#646** | **FilmesOnline.SH** [PT] | `Source request` | Add FilmesOnline.SH source | 💡 Source Request |
| **#643** | **PK Filmes** [PT] | `Source request` | Add PK Filmes source | 💡 Source Request |
| **#636** | **DASHFLIX** [Multi] | `Source request`, `18+` | Add DASHFLIX source | 💡 Source Request |

---

## 📖 3. Manga Extensions (`keiyoushi/extensions-source`)

**GitHub Issue Tracker**: [keiyoushi/extensions-source/issues](https://github.com/keiyoushi/extensions-source/issues)

### 🔴 Active Open Manga Issues & Source Requests:

| Issue # | Source / Extension | Labels | Description / Root Cause | Local Resolution / Status |
| :--- | :--- | :--- | :--- | :--- |
| **#17764** | **EternalMangas** | `Bug` | Domain updated to `eternalmangas.org` & missing field defaults added | ✅ **FIXED** (v3 compiled & synced) |
| **#17758** | **MangaYi** | `Bug` | Added `SearchResultDto` wrapper for `results` array in search API | ✅ **FIXED** (v2 compiled & synced) |
| **#17752** | **Hentai 4 Free** | `Bug` | Updated `popularMangaRequest` and `latestUpdatesRequest` to avoid HTTP 400 | ✅ **FIXED** (v1 compiled & synced) |
| **#17743** | **AllManga** | `Bug` | Increased GraphQL API rate-limit delay to 2s to prevent 429/non-JSON responses | ✅ **FIXED** (v24 compiled & synced) |
| **#17736** | **Wolf.com (늑대닷컴)** | None | Updated domain configuration (`wfwf414.com`) & `@Source` metadata | ✅ **FIXED** (v5 compiled & synced) |
| **#17725** | **Lua Scans** | `Bug` | Added `series_status` alongside `status` query parameter for HeanCMS API compatibility | ✅ **FIXED** (v21 compiled & synced) |
| **#17708** | **Omega Scans** | `Bug` | Updated `apiUrl` (`https://api.omegascans.org`) and search `query` parameter | ✅ **FIXED** (v20 compiled & synced) |
| **#17703** | **24HNovel** | `Source is down` | Dead source | 🔴 Removed Upstream |
| **#17695** | **Sword Of Oblivion** | `Source is down` | Dead source | 🔴 Removed Upstream |
| **#17694** | **MangaEsp** | `Source is down` | Dead source | 🔴 Removed Upstream |
| **#17756** | **MangaRaw.co.uk** | `Source request` | Source Request (JP) | 💡 Source Request |
| **#17753** | **Hunlight** | `Source request` | Source Request (EN) | 💡 Source Request |
| **#17744** | **onvatrad.com** | `Source request` | Source Request | 💡 Source Request |
| **#17671** | **Avalon** | `Source request` | Source Request (EN) | 💡 Source Request |

### 🟢 Local Manga Fixes & Compilation Summary:
- **Windows File Clash Fix**: Resolved NTFS case-insensitivity filename clash in `HentaiLoop` (`CheckBoxFilter.kt` $\to$ `HentaiLoopCheckBoxFilter.kt`).
- **Batch Compilation**: All 1,300+ manga extension sources compiled and synced in `apks/manga/`.

---

## 📚 4. Light Novel Plugins (`lnreader/lnreader-plugins`)

**GitHub Issue Tracker**: [lnreader/lnreader-plugins/issues](https://github.com/lnreader/lnreader-plugins/issues)

### 🟢 Bugs Fixed & Updated Locally:

| Issue # | Plugin / Source | Upstream Title & Labels | Our Local Resolution & Fix Details | Plugin JS File |
| :--- | :--- | :--- | :--- | :--- |
| **#2312, #2309, #2297, #2293** | **Novel Fire** | `Bug`, `Missing Chapter`, `Wrong Formatting` | Updated active domain (`novelfire.net`), stripped duplicate headers, and fixed `/book/[slug]/chapters` URL parsing | `novelfire.js` & `novelarrow.js` |
| **#2292** | **NovelBin** | `Bug`, `Domain Changed` | Updated `sourceSite` to active mirror domain `https://novelbin.net/` | `NovelBin[readnovelfull].js` |
| **#2301** | **Riwyat** | `Bug`, `Can't Load Novels` | Updated `sourceSite` from `cenele.com` to `https://riwyat.com/` | `Riwyat[madara].js` |
| **#2287** | **Belle Reservoir** | `Bug`, `Domain Changed` | Renamed to Belle Repository (`https://bellerepository.com/`) & cleared dead site flag | `BelleReservoir[madara].js` |
| **#2286** | **Genesis** | `Bug`, `Can't Load Novels` | Added null-safe property checks for `k[0].chapter_content` in `parseChapter` | `genesis.js` |
| **#2284** | **SakuraNovel** | `Bug`, `Can't Load Novels` | Added fallback container selectors (`.readerarea`, `.epcontent`, `.entry-content`) for missing `Daftar Isi` div | `sakuranovel.js` |
| **#2291** | **Foxaholic** | `Bug`, `Missing Chapter` | Updated domain (`foxaholic.com`) and forced `useNewChapterEndpoint: false` to use `admin-ajax.php` | `Foxaholic[madara].js` |
| **#2289** | **Light Novel FR** | `Bug`, `Domain Changed` | Updated domain from dead `lightnovelfr.com` (which threw HTTP 500 on `/series/`) to active domain `https://novel-fr.net/` | `LighNovelFR[lightnovelwp].js` |

### 💡 Open Upstream Plugin Requests:

| Issue # | Plugin / Source | Labels | Description / Problem Area | Status |
| :--- | :--- | :--- | :--- | :--- |
| **#2315** | **Galaxy of Novels** | `Plugin Request` | Request to add Galaxy of Novels plugin | 💡 Plugin Request |
| **#2314** | **Ocean Tales** | `Plugin Request` | Request to add Ocean Tales plugin | 💡 Plugin Request |
| **#2303** | **Akknovel** | `Plugin Request` | Request to add Akknovel plugin | 💡 Plugin Request |
| **#2277, #2275, #2274, #2273, #2272** | **Various** | `Plugin Request` | Feature & plugin requests for new light novel sources | 💡 Plugin Request |

### 🟢 Local Novel Plugins Summary:
- All 254 light novel plugins updated, re-indexed, and synced in `apks/novel/`.
- Updated `plugins.json`, `plugins.min.json`, `index.json`, and `index.min.json`.

---

## 📦 5. Compiled Binaries Output Summary

Total compiled extension APKs and plugins in `apks/`: **4,277 Files**.

- **Anime (`apks/anime/`)**:
  - `aniyomi-en.animesogo-v14.4.apk`
  - `aniyomi-en.anikoto-v14.4.apk`
  - `aniyomi-zh.hanime1-v14.9.apk`
  - `aniyomi-fr.wiflix-v14.28.apk`
  - `aniyomi-en.av1encodes-v14.1.apk`
  - `aniyomi-es.animeav1-v14.4.apk`
  - `aniyomi-pt.muitohentai-v14.3.apk`
  - `aniyomi-all.pornhub-v14.1.apk`
  - `aniyomi-all.twitch-v14.1.apk`
  - `aniyomi-all.ted-v14.5.apk`
- **Manga (`apks/manga/`)**: 1,300+ compiled manga extension binaries
- **Novel (`apks/novel/`)**: All light novel reader plugins and indexers
