# Loklok Extension (Aniyomi / Tadami)

Extension for **Loklok** (`loklok.fun`) supporting movies, TV series, anime, and dramas via Loklok's signed web API (`https://api.loklok.fun`).

---

## Directory Layout

- **Source Code**: [src/anime/all/loklok/](file:///H:/Ideas/Tadami-Extensions/src/anime/all/loklok)
- **Aniyomi Mirror**: [aniyomi-extensions/src/all/loklok/](file:///H:/Ideas/Tadami-Extensions/aniyomi-extensions/src/all/loklok)
- **Distribution APK**: [apks/anime/apk/aniyomi-all.loklok-v14.11.apk](file:///H:/Ideas/Tadami-Extensions/apks/anime/apk/aniyomi-all.loklok-v14.11.apk)

---

## Technical Details & Rate-Limit Protection

- **Web Base URL**: `https://www.loklok.fun`
- **API Endpoint**: `https://api.loklok.fun` (`clientType = web-loklok`, `versionCode = 32`)
- **Per-Request Signing & Response Decryption**: Uses per-request RSA/AES/HMAC-SHA256/MD5 request signing and automatic AES-128-ECB payload decryption when `ecy: 1` is returned.
- **Session Warm-Up**: Automatically initializes `deviceId` and warms up navigation/home endpoints so title details and episodes do not return decoy `FDLSxl1000129` responses.
- **429 / Too Many Requests Hardening**:
  - Defaults to fetching **only the preferred quality** (1 `/media/pc/getPlayInfo` call per episode instead of 5 back-to-back calls); enable **Fetch all video qualities** in extension settings if you want all qualities loaded.
  - Enforces minimum request spacing and retries `HTTP 429` (`Retry-After` aware) and `5xx` responses with exponential backoff and freshly signed headers.
  - Strips API signing headers and spoofed IP headers from ExoPlayer `Video` requests.
- **Playback Authentication (`A0230`)**: `/media/pc/getPlayInfo` requires a signed-in Loklok session. Open **Loklok in WebView** inside Tadami/Aniyomi to sign in (`loklok_token` cookie is picked up automatically), or configure Email/Password or Session Token in Extension Settings.
