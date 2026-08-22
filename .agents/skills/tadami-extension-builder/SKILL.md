---
name: tadami-extension-builder
description: >-
  Automates building Aniyomi/Tadami extension APKs, stripping '-debug' suffixes,
  aligning version codes, calculating SHA-256 hashes, and syncing index manifests.
---

# Tadami Extension Builder Skill

## Overview
This skill provides automated workflows to compile Kotlin/Java extension modules into release/debug Android APKs, sanitize distribution filenames, and synchronize repository manifests (`index.min.json` and `index.json`).

## Key Workflows

### 1. Building Extension Modules
```bash
./gradlew :src:<lang>:<extension-name>:assembleRelease
```

### 2. Stripping Distribution Filenames
All distribution APKs in `apks/anime/apk/` and `apks/manga/apk/` must follow the pattern:
```
aniyomi-<lang>.<extension-name>-v<versionName>.apk
```
*Note: Always strip `-debug.apk` and `-release.apk` suffixes when copying to `apks/*/apk/`.*

### 3. Updating Repository Index Manifests
Ensure `index.min.json` and `index.json` are updated with:
- `pkg`: Full package name (e.g. `eu.kanade.tachiyomi.animeextension.all.animexin`)
- `name`: Display name with prefix (e.g. `Aniyomi: AnimeXin`)
- `version`: Version string (e.g. `14.28`)
- `code`: Version code integer (e.g. `28`)
- `apk`: Clean APK filename
- `icon`: Raw GitHub URL for icon PNG
- `sources`: Array of source IDs, languages, and `baseUrl`s
