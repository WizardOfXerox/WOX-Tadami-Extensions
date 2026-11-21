# Loklok Extension (Aniyomi / Tadami)

Extension for **Loklok** supporting movies, TV series, anime, and dramas via Loklok's active mobile API (`https://ga-mobile-api.loklok.tv/cms/app`).

---

## Directory Layout

- **Source Code**: [src/anime/all/loklok/](file:///H:/Ideas/Tadami-Extensions/src/anime/all/loklok)
- **Aniyomi Mirror**: [aniyomi-extensions/src/all/loklok/](file:///H:/Ideas/Tadami-Extensions/aniyomi-extensions/src/all/loklok)
- **Distribution APK**: [apks/anime/apk/aniyomi-all.loklok-v14.13.apk](file:///H:/Ideas/Tadami-Extensions/apks/anime/apk/aniyomi-all.loklok-v14.13.apk)

---

## Technical Details & Rate-Limit Protection

- **Base URL**: `https://www.loklok.com`
- **Mobile API Endpoint**: `https://ga-mobile-api.loklok.tv/cms/app`
- **Active Version Header**: `clienttype: android_tem3`, `versioncode: 36` (bypasses `A0001` version termination).
- **Video Rate-Limiting Resolution**:
  - `getVideoList` prioritizes the user's preferred quality (1 request) instead of making 4 immediate burst requests for all definitions.
  - Subsequent qualities are fetched with a 600ms pacing delay to avoid HTTP 429 ("Too Many Requests").
  - Automatic backoff retry on HTTP 429 in OkHttpClient interceptor.
- **In-Memory Subtitle Caching**:
  - Subtitle tracks are parsed during `episodeListParse` and cached per episode, avoiding unnecessary HTTP calls inside `getVideoList`.
- **Clean Video Stream Headers**:
  - Stream chunks (`.m3u8` and `.ts`) are fetched with clean Dalvik UA headers without `Referer` or `Origin`, avoiding HTTP 403 Forbidden on Akamai CDN edges (`akm-cdn-play.subplayts.com`).
- **Geo-Protection & Spoofing**:
  - Attaches Southeast Asian client IP headers (`X-Forwarded-For: 120.28.0.1`, `clientip: 120.28.0.1`, `X-Real-IP: 120.28.0.1`) strictly to API requests to prevent CDN blocks.
