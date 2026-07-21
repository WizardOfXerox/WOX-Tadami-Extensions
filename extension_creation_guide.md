# Aniyomi / Tachiyomi Anime Extension — Creation & Debugging Manual

> **Purpose**: This document is a comprehensive, battle-tested manual for building, fixing, and maintaining anime source extensions for **Aniyomi** (and its forks like **Tadami**). It serves as an authoritative instructions template for AI agents and developers to systematically check domains, verify APIs, diagnose bugs, and resolve all runtime issues.

---

## Table of Contents

1. [Architecture & Lifecycle Overview](#1-architecture--lifecycle-overview)
2. [Project Structure & Setup](#2-project-structure--setup)
3. [Mandatory AI Pre-Flight Verification & Liveness Checklist](#3-mandatory-ai-pre-flight-verification--liveness-checklist)
4. [Core Class API Reference](#4-core-class-api-reference)
5. [Required Overrides Checklist](#5-required-overrides-checklist)
6. [Universal Feature Checklist](#6-universal-feature-checklist)
7. [General HTML Scraping Guidelines (Jsoup)](#7-general-html-scraping-guidelines-jsoup)
8. [Bypassing Cloudflare & CDN Protections](#8-bypassing-cloudflare--cdn-protections)
9. [Decrypting Streams & Third-Party Video Hosts](#9-decrypting-streams--third-party-video-hosts)
10. [Comprehensive Bug Catalog & Solutions (Case Studies)](#10-comprehensive-bug-catalog--solutions-case-studies)
11. [Universal Diagnostic & Debugging Toolkit](#11-universal-diagnostic--debugging-toolkit)
12. [General Code Best Practices](#12-general-code-best-practices)

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

## 3. Mandatory AI Pre-Flight Verification & Liveness Checklist

> [!IMPORTANT]
> Every AI agent working on extension maintenance or bug fixing **MUST** execute the following verification steps prior to declaring an extension healthy or committing changes.

### A. Domain & TLD Liveness Check
- [ ] **DNS & Base URL Resolution**: Test `baseUrl` using automated HTTP requests (e.g. Python `urllib` or PowerShell `Invoke-WebRequest`). Confirm domain TLD is active (e.g. `.cc`, `.ru`, `.net`) and handle decommissioned domains (e.g. dead `.su` domains).
- [ ] **Secondary API Domain Liveness**: Check secondary hostnames used for metadata or decryption (e.g. `themoviedb.hexawatch.cc` replacing dead `hexa.su`).
- [ ] **HTTPS & SSL Redirect Inspection**: Verify HTTPS certificate validity and follow redirect chains to confirm final target URLs.

### B. API & AJAX Endpoint Check
- [ ] **Endpoint Status Code Verification**: Verify endpoints like `/wp-admin/admin-ajax.php` or custom REST endpoints. Test for HTTP `403`, `404`, `400`, or `503` failures.
- [ ] **No-AJAX & DOM Player Fallback**: If an admin AJAX endpoint returns HTTP 404 or 400, inspect page HTML for `no_ajax` theme elements (e.g., `div#source-player-$nume iframe`) and fall back to direct HTML selector extraction.

### C. Third-Party Embed & Host Alias Check
- [ ] **Embed Host Mapping**: Identify custom or proxy domains (e.g. `playmogo.com`, `ds2play.com`, `doodcdn.io`) and map them to their parent video extractors (e.g. `DoodExtractor`, `MixDropExtractor`).

### D. Multi-Hop Redirect Parameter Loss Check
- [ ] **OkHttp Request Tagging**: If multi-hop embeds (e.g. `vidsrc.me`) strip URL parameters like `?tmdb=123` during redirects, attach metadata as an OkHttp tag (`.tag(VideoTag::class.java)`). Retrieve parameters from the tag in `videoListParse` instead of parsing the redirected URL.

### E. Distribution & Index Filename Alignment Check
- [ ] **Index JSON Alignment**: Confirm that the compiled output APK filename (e.g. `aniyomi-en.hstream-v14.13.apk`) matches the `apk` field in `index.min.json`. Strip `-debug` suffixes during sync to prevent HTTP 404 download errors in the extension store.

### F. Namespace & Override Compatibility Check
- [ ] **Non-Standard Interface Overrides**: For methods specific to Tadami/forks (such as `relatedAnimeListRequest`), remove the `override` keyword when building inside the standard Aniyomi Gradle environment to avoid `'relatedAnimeListRequest' overrides nothing` compiler errors.

---

## 4. Core Class API Reference

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

## 5. Required Overrides Checklist

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

## 6. Universal Feature Checklist

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

## 7. General HTML Scraping Guidelines (Jsoup)

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

## 8. Bypassing Cloudflare & CDN Protections

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

## 9. Decrypting Streams & Third-Party Video Hosts

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

## 10. Comprehensive Bug Catalog & Solutions (Case Studies)

This catalog details real-world runtime bugs, crash logs, and their exact solutions.

### Bug 1: Subtitle Silent Failure (Loklok Case Study)
* **Symptom**: Subtitles failed to display in video player without throwing an explicit error.
* **Root Cause**: Subtitle arrays were encoded as Base64 and embedded into the episode URL via `setUrlWithoutDomain()`. Because there were many subtitle language URLs, the generated string exceeded ~2,700 characters. Tachiyomi's internal SQLite database silently truncated these long strings, corrupting the URL.
* **Solution**: Keep the episode URL under 150 characters (only pass IDs). Fetch subtitles **live** during `getVideoList()` by making a separate API call to `/movieDrama/get`.

### Bug 2: Infinite Search Scroll Loops (Loklok Case Study)
* **Symptom**: Scrolling through search results caused page 1 to repeat indefinitely.
* **Root Cause**: The API uses token cursors (like `sort`), not offset page numbers. When requesting page 2, the scraper requested without a cursor, causing the server to repeat page 1.
* **Solution**: Implemented an in-memory page-to-cursor cache: `private val filterCursorCache = mutableMapOf<Int, String>()`, which is cleared when `page == 1` is requested.

### Bug 3: Background Thread UI Crashes (Loklok Case Study)
* **Symptom**: `android.view.ViewRootImpl$CalledFromWrongThreadException` or `RuntimeException`.
* **Root Cause**: Displaying a Toast notice inside network handlers threw a `RuntimeException` because the Toast was initialized outside Android's Main Looper thread.
* **Solution**: Post UI calls to the main looper using a Handler:
  ```kotlin
  Handler(Looper.getMainLooper()).post {
      Toast.makeText(context, message, Toast.LENGTH_LONG).show()
  }
  ```

### Bug 4: Recommended / Related Videos Leak in Scraping (Hstream Case Study)
* **Symptom**: Episode list contained duplicate episodes or wrong shows.
* **Root Cause**: When parsing series detail/homepage files using standard Jsoup class selectors (e.g. `div.group > a`), recommended or related video cards at the bottom of the page can also be matched.
* **Solution**: Always filter elements by checking if the URL path contains the current anime series path segment (e.g. `href.contains("/hentai/$seriesPath-")`). Also, convert JSoup `Elements` to a regular Kotlin list (`.toList()`) first to avoid Jsoup's custom `filter(NodeFilter)` method compilation conflict.

### Bug 5: Duplicate Suffix-Separated Catalogue Listings (Hstream Case Study)
* **Symptom**: Main browse feeds contained separate cards for every episode (e.g. "Title - 1", "Title - 2").
* **Root Cause**: Site lists individual episodes as separate entries on feeds.
* **Solution**: Override main page parsers (`popularAnimeParse`, `latestUpdatesParse`, `searchAnimeParse`). Use regular expressions to strip episode suffixes from titles and URLs (`/hentai/name-1` → `/hentai/name`). Deduplicate the parsed list using `.distinctBy { it.url }`.

### Bug 6: JSON Deserialization Crashes on Missing/Null Fields (Hstream Case Study)
* **Symptom**: `SerializationException: Field 'stream_url' is required`.
* **Root Cause**: Player API returned `null` or omitted keys for unavailable resolutions.
* **Solution**: Always declare optional or dynamic response fields as nullable with defaults (`val stream_url: String? = null`).

### Bug 7: InstantiationError: Exception: Stub! (Dynamic APK Loading)
* **Symptom**: `InstantiationError: Exception: Stub! at eu.kanade.tachiyomi.animesource.online.AnimeHttpSource.<init>`.
* **Root Cause**: `extensions-lib` JARs contain stub constructors that throw `Stub!`. When loaded dynamically via `DexClassLoader`, the extension calls `super()` into the stub.
* **Solution**: Host app must provide concrete non-stub implementations for base classes (`AnimeHttpSource`, `HttpSource`, `ParsedAnimeHttpSource`) and filter out stub class files from linked JARs.

### Bug 8: LinkageError: Failed to resolve PreferenceScreen (DexClassLoader)
* **Symptom**: `LinkageError: While checking class... setupPreferenceScreen(androidx.preference.PreferenceScreen) signature... Failed to resolve arg 0 type androidx.preference.PreferenceScreen`.
* **Root Cause**: The host environment's `DexClassLoader` cannot locate `androidx.preference.PreferenceScreen` at runtime.
* **Solution**: Ensure host application includes `androidx.preference` in its dependencies, or wrap preference calls safely inside reflection blocks.

### Bug 9: QuickJS Promise Resolution Failure (`JsonNull / JsonLiteral is not a JsonArray`) (Novel JS Plugins)
* **Symptom**: `Element class kotlinx.serialization.json.JsonLiteral is not a JsonArray`.
* **Root Cause**: QuickJS engine's microtask queue is not processed automatically during `evaluate()`. Native JS `Promise` objects returned by plugin functions remain unresolved, returning `null`.
* **Solution**: Inject a custom synchronous `SyncedPromise` polyfill in the QuickJS loader that executes `.then()` and `.catch()` callbacks synchronously upon resolution.

### Bug 10: Empty Video Hoster List Due to Redirect Hop Parameter Loss (XPrime Case Study)
* **Symptom**: Video list was empty when resolving multi-server embeds (`vidsrc.me`).
* **Root Cause**: `vidsrc.me` redirects strip query parameters like `?tmdb=123` during redirect hops, causing `response.request.url` parameter parsing in `videoListParse` to fail.
* **Solution**: Attach a private data class `VideoTag` to the request via OkHttp `.tag(VideoTag::class.java)`:
  ```kotlin
  private data class VideoTag(val tmdbId: String, val season: String, val episode: String, val isMovie: Boolean)

  override fun videoListRequest(episode: SEpisode): Request {
      val tag = VideoTag(tmdbId, season, episode, isMovie)
      return GET(url, videoHeaders).newBuilder().tag(VideoTag::class.java, tag).build()
  }

  override fun videoListParse(response: Response): List<Video> {
      val tag = response.request.tag(VideoTag::class.java)
      val tmdbId = tag?.tmdbId ?: ""
      // Metadata preserved across redirects!
  }
  ```

### Bug 11: 404 / 400 AJAX Failure on Theme-Customized Sites (PinoyMoviePedia Case Study)
* **Symptom**: `getPlayerUrl` failed because `/wp-admin/admin-ajax.php` returned HTTP 404 or 400.
* **Root Cause**: Theme uses a `no_ajax` configuration that bypasses WordPress admin AJAX calls, rendering player iframe elements directly into the HTML body.
* **Solution**: Check for `div#source-player-$nume iframe` directly in the HTML document before falling back to `admin-ajax.php`. Map custom clone player domains (e.g. `playmogo.com`) to standard extractors (`DoodExtractor`).

### Bug 12: Repository Index Filename Mismatch Causing 404 Download Failures (Hstream / Custom Extensions)
* **Symptom**: Client fails to download extension APK from repository with HTTP 404.
* **Root Cause**: Compiled output APK was named `aniyomi-en.hstream-v14.13-debug.apk`, while `index.min.json` declared `aniyomi-en.hstream-v14.13.apk`.
* **Solution**: Update distribution sync scripts (`copy_and_clean_apks.ps1`) to strip `-debug` suffixes for all extension APKs to align exactly with `index.min.json`.

### Bug 13: `relatedAnimeListRequest` Compilation Error (`overrides nothing`) (Hexawatch Case Study)
* **Symptom**: `Compilation error: 'relatedAnimeListRequest' overrides nothing`.
* **Root Cause**: `relatedAnimeListRequest` is specific to Tadami and not declared in standard Aniyomi base classes. When compiling under standard Aniyomi `common.gradle`, Kotlin compiler rejects the `override` keyword.
* **Solution**: Remove the `override` keyword (`fun relatedAnimeListRequest(anime: SAnime)`). The method compiles cleanly under standard Aniyomi and remains reflectively invokable by Tadami at runtime.

---

## 11. Universal Diagnostic & Debugging Toolkit

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

## 12. General Code Best Practices

- **Ignore Unknown JSON Keys**: Always instantiate your JSON builder with `Json { ignoreUnknownKeys = true }`. If you don't, any new key added by the API in the future will immediately crash your parser.
- **Set Init Status**: Ensure `SAnime.initialized = true` is only set after the full details (synopsis, genre, cover) have been completely parsed.
- **Resource Cleanup**: Always close response streams in loops with `response?.close()` to prevent memory leaks and thread exhaustion.
- **Null Safety**: Treat every API field as potentially null or empty. Use Kotlin's safe calls (`?.`) and Elvis operators (`?:`) to provide defaults.

---

*Authoritative manual for compiling, fixing, and verifying Android video extensions inside Tachiyomi-based systems.*
