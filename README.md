# WOX Tadami Extensions 🚀

Welcome to the **Wizard Of Xerox Tadami Extensions** repository! This is a self-hosted custom extension repository for **Tadami**, **Aniyomi**, **Mihon**, and compatible forks. It contains compiled extension APKs/plugins (Anime, Manga, and Novels) and their respective source codes.

---

## 📺 How to Add to Your App

To install extensions from this repository directly inside your app, copy the appropriate repository URL below and add it to your app's extension repository list:

### 1. Anime Extensions Repo (Tadami / Aniyomi)
Copy this link:
```text
https://raw.githubusercontent.com/WizardOfXerox/WOX-Tadami-Extensions/main/apks/anime/index.min.json
```

### 2. Manga Extensions Repo (Tadami / Mihon / Tachiyomi)
Copy this link:
```text
https://raw.githubusercontent.com/WizardOfXerox/WOX-Tadami-Extensions/main/apks/manga/index.min.json
```

### 3. Novel Plugins Repo (Tadami / LNReader)
Copy this link:
```text
https://raw.githubusercontent.com/WizardOfXerox/WOX-Tadami-Extensions/main/apks/novel/plugins.min.json
```

---

## ⚙️ Steps to Add the Repo In-App

### For Tadami / Aniyomi / Mihon (Anime & Manga):
1. Open your app.
2. Navigate to the **Browse** tab.
3. Click on **Extensions** and select **Extension repos** (or **Manage repositories** in settings).
4. Select **Add repository**.
5. Paste the copied URL (Anime or Manga) and click **Add**.

### For Tadami / LNReader (Novels):
1. Open your app.
2. Navigate to **Settings** -> **Plugins** (or **Repos**).
3. Select **Add repository** (or click the **+** icon).
4. Paste the copied **Novel Plugins Repo** URL and click **Add**.
5. Refresh the list to view all available novel plugins.

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
