# Loklok Extension (Aniyomi / Tadami)

Extension for **Loklok** (`loklok.fun`) supporting movies, TV series, anime, and dramas via Loklok's signed web API (`https://api.loklok.fun`).

---

## Directory Layout

- **Source Code**: [src/anime/all/loklok/](file:///H:/Ideas/Tadami-Extensions/src/anime/all/loklok)
- **Aniyomi Mirror**: [aniyomi-extensions/src/all/loklok/](file:///H:/Ideas/Tadami-Extensions/aniyomi-extensions/src/all/loklok)
- **Distribution APK**: [apks/anime/apk/aniyomi-all.loklok-v14.12.apk](file:///H:/Ideas/Tadami-Extensions/apks/anime/apk/aniyomi-all.loklok-v14.12.apk)

---

## Technical Details & Rate-Limit Protection

- **Web Base URL**: `https://www.loklok.fun`
- **API Endpoint**: `https://api.loklok.fun` (`clientType = web-loklok`, `versionCode = 32`)
- **Per-Request Signing & Response Decryption**:
  - Implements the exact Nuxt 1.0.1 client crypto:
    - `aesKey`: 16-hex-char random AES key, RSA-1024 encrypted with Loklok's public key -> `aesKey` header.
    - `sign`: AES-128-ECB encryption of `${payload}${currentTime}` with `aesKey`, then MD5 lower-case -> `sign` header.
    - `usertype`: AES-128-ECB encryption of `deviceId` with the public key prefix, then HMAC-SHA256 with `aesKey` -> `usertype` header.
    - Automatic AES-128-ECB payload decryption when `ecy: 1` is returned.
- **Background Thread Safety**:
  - Completely removed off-thread `CookieManager.getInstance()` calls that caused `AndroidRuntimeException` on background OkHttp dispatcher threads.
  - Device IDs are generated and cached in memory and `SharedPreferences`. Auth tokens are read safely via `network.client.cookieJar`.
- **Reliable Episode Extraction**:
  - `episodeListRequest` routes directly to `/cms/pc/movieDrama/queryEpisodeList`, which returns live episode arrays with `code: 00000` without returning decoy payloads.
- **Graceful Error Handling**:
  - Catalog parsers (`popularAnimeParse`, `latestUpdatesParse`, `searchAnimeParse`, `animeDetailsParse`) handle non-fatal edge responses gracefully rather than throwing unhandled exceptions that produce `"Couldn't connect to the source"`.
- **Geo-Protection & Akamai Bypass**:
  - Attaches Southeast Asian client IP headers (`X-Forwarded-For: 120.28.0.1`, `clientip: 120.28.0.1`, `X-Real-IP: 120.28.0.1`) strictly to API requests to prevent HTTP 403 blocks from Akamai Edgesuite outside SE Asia.
  - Media stream headers (`Video(...)`) remain clean without IP headers.
- **Playback Authentication (`A0230`)**:
  - `/media/pc/getPlayInfo` requires a signed-in Loklok session. Open **Loklok in WebView** inside Tadami/Aniyomi to sign in (`loklok_token` cookie is picked up automatically), or configure Email/Password or Session Token in Extension Settings.
