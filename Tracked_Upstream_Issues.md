# 📋 Tracked Upstream Issues Directory

Tracking broken extensions, bug reports, and updates from the 3 primary upstream extension repositories:

---

## 1. 🎬 Anime Extensions Repository
* **Upstream**: [yuzono/anime-extensions](https://github.com/yuzono/anime-extensions/issues)
* **Local Path**: `src/anime/` & `apks/anime/`

| Extension | Issue Reported | Root Cause | Local Fix Status | Version |
| :--- | :--- | :--- | :--- | :--- |
| **MissAV** | 403 Forbidden on video load | Anti-tamper redirect script shadowing HLS playlist packer script; domain blocked | **FIXED** (Unpacker script loop + domain updated to `missav.live` / `missav.com`) | `v14.26` (Code `26`) |
| **Pornhub** | NullPointerException on search | Missing anchor elements / ad container selector mismatch | **FIXED** (Null-safe fallback selectors added) | `v14.2` (Code `2`) |
| **XPrime** | "Holster list empty" video error | Dead backend server (`backend.xprime.tv`) | **FIXED** (Multi-server VidSrc / 2Embed resolver added + TMDB catalog) | `v14.2` (Code `2`) |
| **Hstream** | Missing anime descriptions | Website DOM layout updated (`p.leading-relaxed`) | **FIXED** (Resilient description & fallback meta selectors) | `v14.13` (Code `13`) |
| **Animenosub** | Video search failed | Stream resolver domain update required | **TRACKED** | In progress |

---

## 2. 📖 Manga Extensions Repository
* **Upstream**: [keiyoushi/extensions](https://github.com/keiyoushi/extensions/issues)
* **Local Path**: `manga-extensions-source/` & `apks/manga/`

| Extension | Issue Reported | Root Cause | Local Fix Status | Version |
| :--- | :--- | :--- | :--- | :--- |
| **HentaiLoop** | Build failure on Windows | Case-insensitive filename clash (`CheckBoxFilter` vs `CheckboxFilter`) | **FIXED** (Renamed to `HentaiLoopCheckBoxFilter`) | `v1.4.x` |
| **All Manga Extensions (1,300+)** | Outdated APK binaries | Batch compilation required | **FIXED** (All 1,300+ manga APKs compiled & synced) | Updated |

---

## 3. 📚 Novel Extensions Repository
* **Upstream**: [LNReader/lnreader-sources](https://github.com/LNReader/lnreader-sources/issues)
* **Local Path**: `apks/novel/`

| Extension | Issue Reported | Root Cause | Local Fix Status | Version |
| :--- | :--- | :--- | :--- | :--- |
| **Novel Plugins (Index & Repo)** | Sync & Store indexing | Legacy parser mismatch | **FIXED** (Synced & legacy repo models added) | Updated |
