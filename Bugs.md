# Extension Filtering Results

> **Scan Date**: July 20, 2026  
> **Method**: HTTP HEAD request to each extension's `baseUrl` with 4-5 second timeout  
> **Mirror Search**: Web searched for alternative/new domains — none found for any dead site

---

## Summary

| Repository | Original | Dead | Removed | Remaining |
|------------|----------|------|---------|-----------|
| **Manga** (Keiyoushi) | 1,367 | 47 | ✅ 47 removed | 1,320 |
| **Anime** (Yuzono) | 158 | 13 | ✅ 13 removed | 145 |
| **Novel** (LNReader) | 115 | 10 | ✅ 10 removed | 105 |
| **Total** | **1,640** | **70** | **✅ 70 removed** | **1,570** |

---

## Cleanup Actions Performed

### Manga Extensions
- Deleted 47 APK files from `manga-extensions/apk/`
- Removed 47 entries from `manga-extensions/index.min.json`
- Index reduced from 1,367 → 1,320 entries

### Anime Extensions (source dirs deleted)
| # | Extension | Dead URL |
|---|-----------|----------|
| 1 | es/jkhentai | `jkhentai.net` |
| 2 | all/shabakatycinemana | `cinemana.shabakaty.com` |
| 3 | en/noobsubs | `noobftp1.noobsubs.com` |
| 4 | ar/animeiat | `api.animeiat.co` |
| 5 | it/aniplay | `aniplay.co` |
| 6 | es/verseriesonline | `verseriesonline.net` |
| 7 | en/asiaflix | `asiaflix.app` |
| 8 | es/estrenosdoramas | `estrenosdoramas.es` |
| 9 | ar/animelek | `animelek.me` |
| 10 | pt/animesgames | `animesgames.cc` |
| 11 | hi/yomovies | `yomovies.town` |
| 12 | fr/otakufr | `otakufr.cc` |
| 13 | pt/animesotaku | `animecore.net` |

### Novel Extensions (source dirs deleted)
| # | Plugin | Dead URL |
|---|--------|----------|
| 1 | en/leafstudio | `leafstudio.site` |
| 2 | ru/novelovh | `novel.ovh` |
| 3 | vi/truyenchu | `truyenchu.vn` |
| 4 | kr/agitoon | `agit664.xyz` |
| 5 | en/vynovel | `vynovel.com` |
| 6 | en/dreamyTranslations | `dreamy-translations.com` |
| 7 | en/mtlreader | `mtlreader.com` |
| 8 | cn/linovelib | `bilinovel.com` |
| 9 | en/earlynovel | `earlynovel.net` |
| 10 | en/faqwikius | `faqwiki.us` |

### Manga Extensions (APKs deleted + index entries removed)
| # | Extension Name | Dead URL |
|---|---------------|----------|
| 1 | FoamGirl | `foamgirl.net` |
| 2 | Ryumanga | `ryumanga.org` |
| 3 | Biblio Panda | `bibliopanda.com` |
| 4 | Dassou Scan | `dassouscan.com` |
| 5 | Commit Strip | `commitstrip.com` |
| 6 | Paradox Scans | `paradoxscans.com` |
| 7 | Manhwalover | `manhwalover.org` |
| 8 | Megatokyo | `megatokyo.com` |
| 9 | Manatoki | `manatoki552.net` |
| 10 | Catzaa | `catzaa.net` |
| 11 | Monopoly Scan | `monopolymanhua.com` |
| 12 | Vanilla Scans | `vanillascans.org` |
| 13 | Akai Comic | `akaicomic.org` |
| 14 | Rizz Comic | `rizzcomic.com` |
| 15 | Koinobori Scan | `visorkoi.com` |
| 16 | IsekaiScan.top | `isekaiscan.top` |
| 17 | Mono Manga | `monomanga.com.tr` |
| 18 | Olympus Scanlation | `olympusxyz.com` |
| 19 | Firescans | `firescans.xyz` |
| 20 | Manga Livre | `toonlivre.net` |
| 21 | YaoiChan | `yaoi-chan.me` |
| 22 | PornComix | `bestporncomix.com` |
| 23 | ReadComicOnline | `readcomiconline.li` |
| 24 | Dumanwu | `m.dumanwu1.com` |
| 25 | Ikigai Mangas | `zonaikigai.gamesview.shop` |
| 26 | Comic CX | `comic.cx` |
| 27 | Stray Fansub | `strayfansub.net` |
| 28 | Atemporal | `atemporal.cloud` |
| 29 | Nika Toons | `nikatoons.com` |
| 30 | NeverScans | `neverscans.com` |
| 31 | BiliManga | `bilimanga.net` |
| 32 | HentaiDex | `dexhentai.com` |
| 33 | Manhwax | `manhwax.top` |
| 34 | Tempest Scans | `tempestmangas.com` |
| 35 | IMHentai | `imhentai.xxx` |
| 36 | Manhuashe | `311s.com` |
| 37 | NineGrid | `9grid.cc` |
| 38 | YagamiProject | `read.yagami.me` |
| 39 | Utsukushii | `utsukushii-bg.com` |
| 40 | Rumanhua | `m.rumanhua2.com` |
| 41 | Lolivault | `lector.lolivault.net` |
| 42 | MH1234 | `m.wmh1234.com` |
| 43 | InsanosScan | `insanoslibrary.com` |
| 44 | MunTruyen | `munedge.com` |
| 45 | StashApp | `localhost:9999` |
| 46 | Komik Next G Online | `komiknextgonline.com` |
| 47 | Toon18 | `toon18.to` |

---

## Outstanding Bugs (Our Extensions)

| Extension | Type | Error | Status |
|-----------|------|-------|--------|
| **Pornhub** | Anime | `NullPointerException` on search — reflection fails on filter serialization | 🔴 Unresolved |

## Previously Fixed

| Extension | Type | Issue | Status |
|-----------|------|-------|--------|
| **XNXX** | Anime | Search used tag URL instead of title search | ✅ Fixed |
| **XNXX** | Anime | Popular page selector too restrictive | ✅ Fixed |
| **Pornhub** | Anime | Search returned ad cards in results | ✅ Fixed |
