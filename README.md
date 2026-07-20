# WOX Tadami Extensions 🚀

Welcome to the **Wizard Of Xerox (WOX) Tadami Extensions** repository! This is a comprehensive, self-hosted custom extension repository for **Tadami**, **Aniyomi**, **Mihon**, **Tachiyomi**, and **LNReader**.

It features custom-built and updated extension APKs, JavaScript novel plugins, and JSON index manifests across **Anime**, **Manga**, and **Light Novels**.

---

## 📺 Extension Store URLs

Add the following store URLs to your application to browse and install extension sources directly:

### 🎬 1. Anime Extensions Store (Tadami / Aniyomi)
```text
https://raw.githubusercontent.com/WizardOfXerox/WOX-Tadami-Extensions/main/apks/anime/index.min.json
```

### 📖 2. Manga Extensions Store (Tadami / Mihon / Tachiyomi)
```text
https://raw.githubusercontent.com/WizardOfXerox/WOX-Tadami-Extensions/main/apks/manga/index.min.json
```

### 📚 3. Light Novel Plugins Store (Tadami / LNReader)
```text
https://raw.githubusercontent.com/WizardOfXerox/WOX-Tadami-Extensions/main/apks/novel/plugins.min.json
```

---

## ⚙️ Setup Instructions

### 🌟 Tadami (v0.56.1+ "Extension Stores")
1. Open **Tadami** and go to **Browse** $\to$ **Extensions**.
2. Select your desired media category (**Anime**, **Manga**, or **Novel**) at the top.
3. Tap **Extension stores** (or access store settings).
4. Under **Custom stores**, tap **Add**.
5. Paste the corresponding store URL above and tap **OK**.
6. Refresh the extension list to install extensions.

### 📱 Aniyomi / Mihon / Tachiyomi
1. Go to **Settings** $\to$ **Browse** $\to$ **Extension repositories** (or **Manage Repositories**).
2. Tap **Add repository**.
3. Paste the Anime or Manga store URL and confirm.

### 📖 LNReader (Light Novels)
1. Go to **Settings** $\to$ **Plugins** (or **Plugin Repositories**).
2. Tap **Add Repository** / **+**.
3. Paste the Light Novel plugin store URL and tap **Add**.

---

## 🛠️ Recent Source Updates & Fixes

This repository actively maintains and updates broken extensions, domain shifts, and API changes:

### 🎬 Anime Source Fixes:
- **AnimeSogo [EN]**: Fixed 404 embed server fallbacks, updated proxy routing for `megaplay` & `mewstream`, auto-reset stale preferred servers.
- **Anikoto [EN]**: Domain updated (`anikoto.site`), fixed dead 404 player fallbacks and stream headers.
- **Hanime1 [ZH]**: Fixed video loading by injecting browser headers into stream objects.
- **AV1Encodes [EN]**: Added modern browser headers (`Sec-Ch-Ua`, `Sec-Fetch-Mode`) for Cloudflare compatibility.
- **Wiflix [FR]**: Updated domain (`flemmix.me`), navigation routes, and player stream extractors.
- **Pornhub [All]**: Added null-safe attribute parsing and updated search query formats.
- **Ted & Twitch [All]**: Verified missing app icons across all density folders and manifest bindings.

### 📖 Manga Source Fixes:
- **EternalMangas [ES]**: Updated domain (`eternalmangas.org`) and null-safe DTO fields.
- **MangaYi [EN]**: Resolved JSON parsing error with `SearchResultDto` wrapper.
- **Hentai 4 Free [EN]**: Fixed HTTP 400 Bad Request error on search/browse.
- **AllManga [EN]**: Increased GraphQL API rate-limit delay to 2s to prevent 429 errors.
- **Wolf.com [KO]**: Updated active domain (`wfwf414.com`) and `@Source` constructor parameters.
- **Lua Scans [EN] & Omega Scans [EN]**: Updated HeanCMS API routes, `apiUrl` overrides, and status filters.

### 📚 Light Novel Plugin Fixes:
- **Novel Fire**: Domain update (`novelfire.net`), stripped duplicate chapter headers, and fixed `/book/[slug]/chapters` URL parsing.
- **NovelBin**: Updated `sourceSite` to active mirror domain `https://novelbin.net/`.
- **Riwyat**: Updated domain to `https://riwyat.com/`.
- **Belle Repository**: Renamed from Belle Reservoir (`https://bellerepository.com/`) & cleared dead site status.
- **Genesis**: Fixed unhandled null `chapter_content` dereferencing in chapter parser.
- **SakuraNovel**: Added fallback container selectors (`.readerarea`, `.epcontent`, `.entry-content`).
- **Foxaholic**: Updated domain (`foxaholic.com`) and forced `useNewChapterEndpoint: false` for `admin-ajax.php` compatibility.
- **Light Novel FR**: Updated domain from dead `lightnovelfr.com` to `https://novel-fr.net/`.

---

## 📂 Repository Structure

```text
WOX-Tadami-Extensions/
├── apks/
│   ├── anime/               # Compiled Anime Extension APKs & index.min.json
│   ├── manga/               # Compiled Manga Extension APKs & index.min.json
│   └── novel/               # Light Novel JS plugins & plugins.min.json
├── aniyomi-extensions/      # Source code for Anime extensions
├── manga-extensions-source/ # Source code for Manga extensions
└── README.md                # Repository documentation & store directory
```
