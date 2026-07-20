# WOX Tadami Extensions 🚀

Welcome to the **Wizard Of Xerox Tadami Extensions** repository! This is a self-hosted custom extension repository for **Tadami**, **Aniyomi**, **Mihon**, and compatible forks. It contains compiled extension APKs/plugins (Anime, Manga, and Novels) and their respective source codes.

---

## 📺 How to Add to Your App

To install extensions from this repository directly inside your app, copy the appropriate store/repository URL below:

### 1. Anime Extensions Store (Tadami / Aniyomi)
Copy this link:
```text
https://raw.githubusercontent.com/WizardOfXerox/WOX-Tadami-Extensions/main/apks/anime/index.min.json
```

### 2. Manga Extensions Store (Tadami / Mihon / Tachiyomi)
Copy this link:
```text
https://raw.githubusercontent.com/WizardOfXerox/WOX-Tadami-Extensions/main/apks/manga/index.min.json
```

### 3. Novel Plugins Store (Tadami / LNReader)
Copy this link:
```text
https://raw.githubusercontent.com/WizardOfXerox/WOX-Tadami-Extensions/main/apks/novel/plugins.min.json
```

---

## ⚙️ Steps to Add in Tadami (v0.56.1+ "Extension Stores")

1. Open **Tadami** and navigate to the **Browse** -> **Extensions** tab.
2. Select the media type (**Anime**, **Manga**, or **Novel**) at the top.
3. Click on **Extension stores** (or click the gear icon/settings to manage stores).
4. Under the **Custom stores** section, tap **Add**.
5. Paste the copied URL matching the media type and tap **OK**.
6. Refresh the list to view and install available extensions.

### For Older Versions / Aniyomi / Mihon / LNReader:
* **Anime & Manga:** Go to `Settings -> Browse -> Extension repos` (or `Manage repositories`), tap **Add**, and paste the URL.
* **Novels:** Go to `Settings -> Plugins` (or `Repos`), tap the **+** icon, paste the URL, and click **Add`.

---

## 🛠️ Included Custom Builds (Pre-configured)

This repository includes our custom-built, fully-working versions of extensions that resolve common crash/reflection bugs:

| Custom Extension | Type | Version | Status | Key Fix |
|------------------|------|---------|--------|---------|
| **Pornhub** | Anime | `v14.1-debug` | ✅ Active | Fixed reflection reflection/Safe-call crashes in search |
| **XNXX** | Anime | `v14.4-debug` | ✅ Active | Updated popular list queries and layout selectors |
| **Hstream** | Anime | `v14.12-debug` | ✅ Active | Restored playback and queries |
| **Loklok** | Anime | `v14.1-debug` | ✅ Active | Custom multi-source support |

---

## 📂 Repository Structure

- **`/src`**: Full Kotlin and TypeScript source codes for all extensions.
  - `/anime`: Source code for all anime extensions.
  - `/manga`: Source code for all manga extensions.
  - `/novel`: Source code for all novel extensions.
- **`/apks`**: Prebuilt and custom distribution APKs/JS files + JSON indexes.
  - `/anime`: Custom and official anime APK files.
  - `/manga`: Official manga APK files.
  - `/novel`: Self-hosted novel `.js` plugins and manifest.
