# Walkthrough — Pornhub Extension Creation

We have successfully created, configured, and compiled the new **Pornhub** extension for Tachiyomi/Aniyomi.

## Changes Made

### 1. Project Registration
* Modified [settings.gradle.kts](file:///H:/Ideas/Tadami-Extensions/Aniyomi%20Extension/aniyomi-extensions/settings.gradle.kts) to include the new `:src:all:pornhub` project.
* Created [build.gradle](file:///H:/Ideas/Tadami-Extensions/Aniyomi%20Extension/aniyomi-extensions/src/all/pornhub/build.gradle) registering the name `Pornhub`, class path `.Pornhub`, version `1`, and settings `isNsfw = true`.

### 2. Branding Assets
* Downloaded the high-resolution Pornhub logo from `https://www.pornhub.com/apple-touch-icon.png`.
* Resized and generated all standard resolution mipmaps (`mdpi` (48x48), `hdpi` (72x72), `xhdpi` (96x96), `xxhdpi` (144x144), `xxxhdpi` (192x192), and `web_hi_res_512.png` (512x512)) and configured them as the extension's icon resources.

### 3. Filter Implementation
* Created [PornhubFilters.kt](file:///H:/Ideas/Tadami-Extensions/Aniyomi%20Extension/aniyomi-extensions/src/all/pornhub/src/eu/kanade/tachiyomi/animeextension/all/pornhub/PornhubFilters.kt) including:
  * **Sort Order**: Most Viewed (default), Most Recent, Top Rated, and Hot.
  * **Category Filter**: A select filter mapped to all main Pornhub category paths (e.g. `Amateur`, `Anal`, `Asian`, `MILF`, `POV`, `Hentai`, etc.).

### 4. Main Extension Logic
* Created [Pornhub.kt](file:///H:/Ideas/Tadami-Extensions/Aniyomi%20Extension/aniyomi-extensions/src/all/pornhub/src/eu/kanade/tachiyomi/animeextension/all/pornhub/Pornhub.kt) implementing standard parser lifecycle:
  * **Listing Requests**: Mapped to standard browse and search feeds with pagination and sorting.
  * **Listing Selectors**: Parsed using `.pcVideoListItem` and `div.videoBox` selectors.
  * **Details Page**: Read structured `<script type="application/ld+json">` metadata blocks to obtain title, description, and thumbnail stably. Selects categories and tags from wrappers for genres.
  * **Episode List**: Instantiates a single video entry mapped to the parent URL, parsing upload dates from JSON-LD.
  * **Video Extractor**: Scans video HTML scripts for player `mediaDefinitions`. Supports direct HLS `.m3u8` master playlists, direct progressive MP4 links, and remote `/video/get_media` JSON fetches. Attach required Origin and Referer headers to avoid error 412/403.
  * **Cookie Autoloading**: Injects standard age verification cookies on all requests.

---

## Verification Results

### Automated Compilation
* Gradle built the module successfully:
  ```
  BUILD SUCCESSFUL in 29s
  ```
* Compiled APK path: [builds/aniyomi-all.pornhub-v14.1-debug.apk](file:///H:/Ideas/Tadami-Extensions/builds/aniyomi-all.pornhub-v14.1-debug.apk)
