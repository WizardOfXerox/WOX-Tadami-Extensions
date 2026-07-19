# Loklok Extension (Aniyomi / Tadami)

Extension for **Loklok** (also known as **Loktv**; they rebranded in 2024). Streaming for movies, TV series, anime, and dramas. Uses the same API as the Loklok desktop app (`api.chhhn.com`). Use with Aniyomi or Tadami.

## APK

**`Loklok-v14.1-debug.apk`** – install on your device, then in Aniyomi/Tadami go to **Extensions** (or **Sources**) and enable **Loklok**.

## If you see “content not found” or empty lists

The API (`api.chhhn.com`) often returns **403 Forbidden** when requests don’t look like the official app (e.g. missing or different headers). The extension already sends a desktop-style User-Agent; if content still doesn’t load, the server may require extra headers or tokens that only the real app sends.

### Providing the Loklok app to debug

**Yes – if you can share the Loklok app, we can debug it.**

- **Best: Android APK**  
  If you have the **Loklok Android APK** (e.g. from the Play Store, APKMirror, or the official site), you can add it to this project (e.g. put `Loklok.apk` in the project folder and tell me the path). From the APK we can:
  - See the **exact HTTP headers** the app sends (User-Agent, tokens, device id, etc.).
  - See the **exact API endpoints** and request/response format.
  - Adjust the extension so it matches the app and content loads in Tadami.

- **What I’ll do with the APK**  
  I’ll treat it as read-only: inspect the app’s network layer and API usage to replicate the same requests in the extension. I won’t run or modify the app itself.

- **Where to put the XAPK or APK**  
  Put the **XAPK** (or APK) in the project’s **`download`** folder:  
  `C:\Users\XIA\Desktop\Aniyomi Extension\download\`  
  Then say e.g. “The XAPK is in the download folder” or give the exact filename.  
  (XAPK is a zip containing the APK; the assistant can extract and inspect it.)

- **Inspection of LOKTV 1.8.0 (APKPure XAPK)**  
  The XAPK was found in your Downloads, copied to `download/LOKTV_1.8.0.xapk`, and extracted. The app package is **com.novan.morpha** (LOKTV). The API base URL (`api.chhhn.com`) and endpoint names do **not** appear in plain text in the APK (obfuscated / possibly loaded from a config server). The extension already uses **api.chhhn.com** and the same paths as the desktop app; if content still does not load, the server likely requires app-specific headers (device-id, token, or signature).

## Webview (Open in browser)

Opening the source “in webview” goes to **https://www.loklok.com** (the app’s promo/download page). That’s intentional so you don’t get a 404; the real catalog is only in the app/API.

## Build from source

From the **aniyomi-extensions** repo:

```bash
./gradlew :src:all:loklok:assembleDebug
```

APK output: `src/all/loklok/build/outputs/apk/debug/`.
