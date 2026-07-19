# Aniwave Extension (Aniyomi / Tadami)

An extension for [Aniwave](https://aniwave.is/) (watch anime free online). Built for **[Aniyomi](https://github.com/aniyomiorg/aniyomi)** and compatible with **[Tadami](https://github.com/tadamiorg/tadami)** (Aniyomi fork with Aurora UI). Use the same APK in either app.

## How to build and use

This extension is meant to be built **inside** the official [aniyomi-extensions](https://github.com/aniyomiorg/aniyomi-extensions) repository.

### 1. Clone aniyomi-extensions

```bash
git clone https://github.com/aniyomiorg/aniyomi-extensions.git
cd aniyomi-extensions
```

(You can use a [sparse checkout](https://github.com/aniyomiorg/aniyomi-extensions/blob/master/CONTRIBUTING.md#cloning-the-repository) to only fetch the modules you need.)

### 2. Copy this extension into the repo

Copy the contents of **`src/all/aniwave`** from this project into **`aniyomi-extensions/src/all/aniwave`** (create the folder if needed).

So you should end up with:

- `aniyomi-extensions/src/all/aniwave/build.gradle`
- `aniyomi-extensions/src/all/aniwave/src/eu/kanade/tachiyomi/animeextension/all/aniwave/Aniwave.kt`
- `aniyomi-extensions/src/all/aniwave/res/...` (icons)

### 3. Add the module to the build

In **`aniyomi-extensions/settings.gradle.kts`** (or `settings.gradle`), add the new module so it is included in the build. For example, if the repo uses:

```kotlin
include(":src:all:aniwave")
```

(or the equivalent for your Gradle setup). Check how other `src/all/...` extensions are included and add the same line for `aniwave`.

### 4. Add extension icons

Under **`src/all/aniwave/res`** you need launcher icons:

- `mipmap-hdpi/ic_launcher.png`
- `mipmap-mdpi/ic_launcher.png`
- `mipmap-xhdpi/ic_launcher.png`
- `mipmap-xxhdpi/ic_launcher.png`
- `mipmap-xxxhdpi/ic_launcher.png`
- `web_hi_res_512.png`

You can generate them with [Android Asset Studio](https://as280093.github.io/AndroidAssetStudio/icons-launcher.html) (upload a 512×512 image and download the pack), or copy the `res` folder from another extension and replace the images.

### 5. Build the extension

From the **aniyomi-extensions** root:

```bash
./gradlew :src:all:aniwave:assembleDebug
```

The APK will be in `src/all/aniwave/build/outputs/apk/`. Install it on a device with **Aniyomi** or **Tadami**; the new source will appear in the app (e.g. **Extensions** or **Sources** → enable Aniwave).

## Tweaking selectors

Aniwave’s HTML may change over time. If something breaks (e.g. no results, wrong titles, no episodes), inspect the site in a browser and update the CSS selectors in **`Aniwave.kt`**:

- `popularAnimeSelector` / `popularAnimeFromElement` – trending list
- `latestUpdatesSelector` / `latestUpdatesFromElement` – recent list
- `searchAnimeSelector` / `searchAnimeFromElement` – search results
- `animeDetailsParse` – anime detail page
- `episodeListSelector` / `episodeFromElement` – episode list
- `getVideoList` – parsing embed/servers on the episode page

See [CONTRIBUTING.md](https://github.com/aniyomiorg/aniyomi-extensions/blob/master/CONTRIBUTING.md) and [extension-docs.aniyomi.org](https://extension-docs.aniyomi.org/) for the full extension API.

---

**Tadami:** Tadami supports the same extension format as Aniyomi. To build from [tadami-extensions-source](https://github.com/tadamiorg/tadami-extensions-source) instead, copy this extension into that repo’s `src/all/aniwave` and build the same way; the resulting APK works in both apps.
