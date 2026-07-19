# Aniyomi / Tachiyomi Anime Extension — Creation & Debugging Manual

> **Purpose**: This document is a comprehensive, battle-tested manual for building, fixing, and maintaining anime source extensions for **Aniyomi** (and its forks like **Tadami**). It is written to serve as an instructions template for other AI agents or developers to diagnose and resolve issues across any extension.

---

## Table of Contents

1. [Architecture & Lifecycle Overview](#1-architecture--lifecycle-overview)
2. [Project Structure & Setup](#2-project-structure--setup)
3. [Core Class API Reference](#3-core-class-api-reference)
4. [Required Overrides Checklist](#4-required-overrides-checklist)
5. [Universal Feature Checklist](#5-universal-feature-checklist)
6. [General HTML Scraping Guidelines (Jsoup)](#6-general-html-scraping-guidelines-jsoup)
7. [Bypassing Cloudflare & CDN Protections](#7-bypassing-cloudflare--cdn-protections)
8. [Decrypting Streams & Third-Party Video Hosts](#8-decrypting-streams--third-party-video-hosts)
9. [Bugs Encountered & Solutions (Loklok Case Study)](#9-bugs-encountered--solutions-loklok-case-study)
10. [Universal Diagnostic & Debugging Toolkit](#10-universal-diagnostic--debugging-toolkit)
11. [General Code Best Practices](#11-general-code-best-practices)

---

## 1. Architecture & Lifecycle Overview

Aniyomi extensions are **standalone Android APK files** loaded dynamically by the host app (Aniyomi/Tadami) at runtime. 
* Extensions are built using Kotlin.
* They extend `AnimeHttpSource` (for API-based sources) or `ParsedAnimeHttpSource` (for HTML scraping with Jsoup).
* The extension class runs inside the host app's process, sharing the host's OkHttp client (`network.client`), Injekt DI container, and Android application context.
* **No UI code is allowed** inside the source classes, other than preference settings and Toast notifications.

---

## 2. Project Structure & Setup

```
aniyomi-extensions/
├── buildSrc/                          # Build plugins and compile configs
├── core/                              # Base classes and resources
├── common.gradle                      # Shared build rules
├── gradle/libs.versions.toml          # Central dependency catalog
├── settings.gradle.kts                # Active build modules list
└── src/
    └── all/                           # "all" is for multi-language extensions
        └── <extension-name>/          # e.g., "loklok"
            ├── build.gradle           # Metadata (Name, version, class path)
            └── src/
                └── eu/kanade/tachiyomi/animeextension/all/<extension-name>/
                    ├── <ExtensionName>.kt       # Main source class
                    └── <ExtensionName>Filters.kt # Search filter classes
```

### `build.gradle` (Extension Configuration)
```groovy
ext {
    extName = 'Loklok'          // Display name in Aniyomi
    extClass = '.Loklok'        // Relative path to main source class
    extVersionCode = 1          // Increment on every release
    isNsfw = false              // Set true for adult content sources
}
apply from: "$rootDir/common.gradle"
```

---

## 3. Core Class API Reference

> [!IMPORTANT]
> These signatures are decompiled directly from `extensions-lib-14.aar` and represent the compiled runtime interface.

### `Video` Model
Represents a playable stream, optionally containing soft subtitles and alternate audio tracks.
```kotlin
data class Video(
    val url: String,               // Stream playlist URL (HLS .m3u8, DASH .mpd, or direct MP4)
    val quality: String,           // Resolution label (e.g., "1080p", "720p", "Backup")
    var videoUrl: String?,         // Resolvable URL (usually equal to url)
    val headers: Headers? = null,  // Custom headers needed to play the video (e.g., Referer)
    val subtitleTracks: List<Track> = emptyList(),  // List of soft subtitles
    val audioTracks: List<Track> = emptyList()      // Alternate audio channels
)
```

### `Track` Model
Represents a soft subtitle track or audio track.
```kotlin
data class Track(
    val url: String,    // Direct URL to .srt, .vtt, or .ass subtitle file
    val lang: String    // Language label (e.g., "English", "Spanish")
)
```
> [!CAUTION]
> The parameters for `Track` are `url` first, `lang` second. Reversing this will result in broken subtitle tracks in the player.

### `SAnime` Model
Represents the details card of a show or movie.
```kotlin
SAnime.create().apply {
    setUrlWithoutDomain("/drama/10418")  // Identifier path (domain excluded)
    title = "Name of Show"
    thumbnail_url = "https://example.com/cover.jpg"
    description = "Synopsis..."
    genre = "Action, Drama, Fantasy"      // Comma-separated list
    status = SAnime.UNKNOWN               // UNKNOWN, ONGOING, COMPLETED, LICENSED, ON_HIATUS
    initialized = true                    // Set true after full detail page load
}
```

### `SEpisode` Model
Represents an individual episode or stream entry.
```kotlin
SEpisode.create().apply {
    setUrlWithoutDomain("episode-id-12345") // Unique identifier path
    name = "Episode 1"
    episode_number = 1.0f                   // Floating-point number (supports decimal specials)
    date_upload = 0L                        // Epoch millis (0L if unavailable)
}
```

---

## 4. Required Overrides Checklist

When implementing `AnimeHttpSource()`, the following members must be overridden:

- [ ] `val name: String` — Public display name of the extension source.
- [ ] `val baseUrl: String` — Base domain of the streaming platform.
- [ ] `val lang: String` — ISO language code (`"en"`, `"all"`, `"es"`, etc.).
- [ ] `val supportsLatest: Boolean` — Set `true` if the site offers a "latest uploads" page.
- [ ] `fun popularAnimeRequest(page: Int): Request` — HTTP request for fetching the popular section.
- [ ] `fun popularAnimeParse(response: Response): AnimesPage` — Parses popular listings into an `AnimesPage` list.
- [ ] `fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request` — HTTP request for text search.
- [ ] `fun searchAnimeParse(response: Response): AnimesPage` — Parses search results.
- [ ] `fun animeDetailsParse(response: Response): SAnime` — Extracts deep-link details (synopsis, genre, cover).
- [ ] `fun episodeListParse(response: Response): List<SEpisode>` — Extracts episode lists.
- [ ] `suspend fun getVideoList(episode: SEpisode): List<Video>` — Asynchronously resolves playable stream URLs.

---

## 5. Universal Feature Checklist

A complete, robust extension should satisfy the following feature checklist:

### Core Capabilities
- [ ] **Unified Search**: Text searching handles alphanumeric symbols, spaces, and non-English characters.
- [ ] **Title Search vs Tag/Filter Search**: Text search queries (`query`) must always map to the platform's general keyword/title search. Avoid mapping search text to tag-specific endpoints (like `/tags/name`), as they fail for generic search queries. Filter/tag queries should instead be driven strictly by the extension's filter interface (`AnimeFilterList`).
- [ ] **Stable Pagination**: Scroll-loading triggers next-page requests accurately until no items remain.
- [ ] **Strict Ordering**: Episode lists are sorted in **descending** order (`sortedByDescending { it.episode_number }`) to prevent Tachiyomi's indexing warnings.
- [ ] **Error Fallback**: Fails gracefully if a preferred resolution/quality is missing.

### Customizations & Preferences
- [ ] **Quality Priority Selection**: Allows users to select their default quality (e.g. `1080P`, `720P`) in extension settings.
- [ ] **Network Resiliency**: Overridden client with connection retries and exponential backoff.
- [ ] **Alternate Headers**: Supports Referer, User-Agent, and custom Origin headers if streaming links require them.

---

## 6. General HTML Scraping Guidelines (Jsoup)

When an official API is unavailable, the extension must extract data by parsing HTML blocks using Jsoup.

### HTML Selector Cheatsheet
* **Document Parsing**: `val doc = response.asJsoup()` (converts Response body to a Jsoup Document).
* **Extracting Listings**:
  ```kotlin
  val elements = doc.select("div.video-item, div.anime-card")
  for (el in elements) {
      val title = el.select("a.title, h2").text()
      val url = el.select("a").attr("href")
      val cover = el.select("img").attr("abs:src") // Use abs:src to resolve relative paths!
  }
  ```
* **Extracting Details**:
  ```kotlin
  val desc = doc.select("div.description, p.synopsis").text()
  val genres = doc.select("div.genres a").map { it.text() }.joinToString(", ")
  ```
* **Parsing Script Blocks (Regular Expressions)**:
  Often, video links or configuration JSON are embedded inside raw `<script>` blocks. Extract them using regex:
  ```kotlin
  val scriptContent = doc.select("script:containsData(playerConfig, var player)").html()
  val regex = """"(https?://[^"]+\.m3u8)"""".toRegex()
  val streamUrl = regex.find(scriptContent)?.groupValues?.get(1) ?: ""
  ```

---

## 7. Bypassing Cloudflare & CDN Protections

Many streaming websites use CDN firewalls (Cloudflare, DDOS-Guard, Sucuri) to block scrapers. Use these techniques to bypass them:

### A. Spoofing Request Headers
Mimic a modern web browser exactly. The most critical headers are `User-Agent`, `Referer`, and `Origin`:
```kotlin
val spoofHeaders = headersBuilder().apply {
    add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
    add("Accept-Language", "en-US,en;q=0.9")
    add("Referer", baseUrl)
}.build()
```

### B. Rate Limiting Interceptor
Spamming connections will trigger Cloudflare's IP rate limiting. Add a rate limiter to your custom client:
```kotlin
import eu.kanade.tachiyomi.network.interceptor.RateLimitInterceptor
import java.util.concurrent.TimeUnit

override val client: OkHttpClient = network.client.newBuilder()
    // Limit to 2 requests per second to avoid IP block
    .addInterceptor(RateLimitInterceptor(2, 1, TimeUnit.SECONDS))
    .build()
```

### C. Reading Session Cookies
If the site requires session tokens or cloudflare clearance cookies (`cf_clearance`), read them from the host app's CookieJar or let the user input them via settings.

---

## 8. Decrypting Streams & Third-Party Video Hosts

Many streaming sites embed videos hosted on third-party video hosts (like StreamWish, Filemoon, Doodstream, or Mixdrop).

### Standard Decryption Logic
1. **AES Decryption**: If streams are encrypted using standard AES (common in site-specific players), implement standard Java Cryptography Architecture (JCA) helpers:
   ```kotlin
   import javax.crypto.Cipher
   import javax.crypto.spec.IvParameterSpec
   import javax.crypto.spec.SecretKeySpec

   fun decryptAES(cipherText: ByteArray, key: ByteArray, iv: ByteArray): String {
       val keySpec = SecretKeySpec(key, "AES")
       val ivSpec = IvParameterSpec(iv)
       val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
       cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
       return String(cipher.doFinal(cipherText), Charsets.UTF_8)
   }
   ```
2. **URL Decoding & Base64**: Always resolve standard URL encodings or obfuscations before streaming.

### Standard Video Extractors
The Aniyomi extensions ecosystem contains pre-built extractors for common video hosts. Use these if available, or write simple HTTP scrapers to extract the direct source link from their iframe source.

---

## 9. Bugs Encountered & Solutions

This case study reviews actual bugs encountered during extension development and their root-cause resolutions.

### Bug 1: Subtitle Silent Failure (Loklok Case Study)
* **Root Cause**: Subtitle arrays were encoded as Base64 and embedded into the episode URL via `setUrlWithoutDomain()`. Because there were many subtitle language URLs, the generated string exceeded ~2,700 characters. Tachiyomi's internal SQLite database silently truncated these long strings, corrupting the URL.
* **Solution**: Keep the episode URL under 150 characters (only pass IDs). Fetch subtitles **live** during `getVideoList()` by making a separate API call to `/movieDrama/get`.

### Bug 2: Infinite Search Scroll Loops (Loklok Case Study)
* **Root Cause**: The API uses token cursors (like `sort`), not offset page numbers. When requesting page 2, the scraper requested without a cursor, causing the server to repeat page 1.
* **Solution**: Implemented an in-memory page-to-cursor cache: `private val filterCursorCache = mutableMapOf<Int, String>()`, which is cleared when `page == 1` is requested.

### Bug 3: Background Thread UI Crashes (Loklok Case Study)
* **Root Cause**: Displaying a Toast notice inside network handlers threw a `RuntimeException` because the Toast was initialized outside Android's Main Looper thread.
* **Solution**: Post UI calls to the main looper using a Handler:
  ```kotlin
  Handler(Looper.getMainLooper()).post {
      Toast.makeText(context, message, Toast.LENGTH_LONG).show()
  }
  ```

### Bug 4: Recommended / Related Videos Leak in Scraping (Hstream Case Study)
* **Root Cause**: When parsing series detail/homepage files using standard Jsoup class selectors (e.g. `div.group > a`), recommended or related video cards at the bottom of the page can also be matched. This causes duplicate episodes or completely wrong episodes from other anime to leak into the episode list.
* **Solution**: Always filter elements by checking if the URL path contains the current anime series path segment (e.g. `href.contains("/hentai/$seriesPath-")`). Also, convert JSoup `Elements` to a regular Kotlin list (`.toList()`) first to avoid Jsoup's custom `filter(NodeFilter)` method compilation conflict.

### Bug 5: Duplicate Suffix-Separated Catalogue Listings (Hstream Case Study)
* **Root Cause**: Some streaming sites list each video/episode as a separate entry on their main feeds (popular, latest, search). In Aniyomi, this results in separate cards for every episode (e.g. "Name - 1", "Name - 2") which creates duplicate database items and splinters the user library.
* **Solution**: Override the main page parsers (`popularAnimeParse`, `latestUpdatesParse`, `searchAnimeParse`). Use regular expressions to strip the episode suffixes from both the listing titles (e.g. ` - 1`) and the listing URLs (e.g. `/hentai/name-1` → `/hentai/name`). Deduplicate the parsed list using `.distinctBy { it.url }` to return a clean, consolidated series listing.

### Bug 6: JSON Deserialization Crashes on Missing/Null Fields (Hstream Case Study)
* **Root Cause**: When modeling Player API JSON responses, declaring fields as non-nullable (e.g. `val stream_url: String`) will crash the parser with a deserialization error if the site returns `null` or omits the key in certain circumstances.
* **Solution**: Always declare optional or dynamic response fields as nullable with defaults (e.g., `val stream_url: String? = null`) to ensure graceful parsing.

---

## 10. Universal Diagnostic & Debugging Toolkit

When fixing a broken extension, follow this diagnostic loop:

### Step 1: Capture Traffic (Reverse Engineering)
Before editing code, determine what the official website or app is requesting.
* **On Web**: Inspect network calls via Chrome DevTools (`F12` -> Network tab). Filter by `Fetch/XHR`.
* **On Android**: Capture HTTPS requests using tools like **PCAPDroid**, **HttpCanary**, or **Charles Proxy**.
* **Identify**: Look for base URL, authorization headers, queries, and POST payloads.

### Step 2: Read Live Logs (adb logcat)
Connect your Android test device or emulator via USB and view output logs in real-time.
```bash
# Filter specifically for Aniyomi/Tadami process errors
adb logcat *:E | grep -i "aniyomi"

# Watch extension installation logs
adb logcat -v time | grep -i "ExtensionManager"
```

### Step 3: Decompile classes.jar for Dependency Conflicts
If you encounter runtime crashes such as `NoSuchMethodError` or `ClassNotFoundException`, inspect the actual classes bundled inside the library:
```powershell
# Decompile method signatures to verify parameters
javap -p C:\Users\XIA\AppData\Local\Temp\extensions-lib-14-classes\eu\kanade\tachiyomi\animesource\model\Video.class
```

---

## 11. General Code Best Practices

- **Ignore Unknown JSON Keys**: Always instantiate your JSON builder with `Json { ignoreUnknownKeys = true }`. If you don't, any new key added by the API in the future will immediately crash your parser.
- **Set Init Status**: Ensure `SAnime.initialized = true` is only set after the full details (synopsis, genre, cover) have been completely parsed.
- **Resource Cleanup**: Always close response streams in loops with `response?.close()` to prevent memory leaks and thread exhaustion.
- **Null Safety**: Treat every API field as potentially null or empty. Use Kotlin's safe calls (`?.`) and Elvis operators (`?:`) to provide defaults.

---

*Authoritative manual for compiling, fixing, and verifying Android video extensions inside Tachiyomi-based systems.*
