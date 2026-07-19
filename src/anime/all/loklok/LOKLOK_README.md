# Loklok Extension (Aniyomi / Tadami)

Extension for **Loklok** (also known as **Loktv**; rebranded in 2024). Streaming for movies, TV series, anime, and dramas. Uses the same API as the Loklok desktop app (`api.chhhn.com`).

---

## 📂 Directory Layout

- **Source Code**: [src/anime/all/loklok/](file:///H:/Ideas/Tadami-Extensions/src/anime/all/loklok)
- **Prebuilt APK**: [apks/anime/aniyomi-all.loklok-v14.1-debug.apk](file:///H:/Ideas/Tadami-Extensions/apks/anime/aniyomi-all.loklok-v14.1-debug.apk)

---

## 🛠️ Build from Source

To compile this extension from source inside the Gradle build environment (`Aniyomi Extension/aniyomi-extensions`):

```bash
./gradlew :src:anime:all:loklok:assembleDebug
```

The compiled APK will be generated under:
`src/anime/all/loklok/build/outputs/apk/debug/`

---

## 🔍 Technical Details & API Analysis

- **API Endpoint**: `https://api.chhhn.com`
- **Promo Page (Webview)**: `https://www.loklok.com` (re-routes the in-app browser to the home download page).
- **Security & Headers**: The API requires desktop-style or app-specific User-Agents to avoid **403 Forbidden** errors. The extension is preconfigured with the necessary headers to pass basic server-side validation.
- **Reversing Notes**: Analysis of the LOKTV `1.8.0` APK (`com.novan.morpha`) shows that the API domain and endpoints are obfuscated. The extension replicates the communication patterns of the desktop client to query metadata and streaming URLs.
