---
name: stream-extractor-debugger
description: >-
  Diagnostic toolkit for video embed hosts (DoodStream, StreamTape, VidPlay,
  Filemoon, MixDrop, Playmogo, Vidwish, Megaplay, HLS m3u8 streams).
---

# Stream Extractor Debugger Skill

## Overview
This skill provides routines to diagnose and test video stream extractors, decrypt obfuscated player sources, inspect `.m3u8` HLS playlists, and handle multi-hop embed proxies.

## Supported Extractors & Patterns
- **DoodExtractor / Playmogo / Ds2play**: Resolves `/pass_md5/` token and direct streaming links.
- **MixDropExtractor**: Unpacks Packer JS to extract `wurl` MP4 stream.
- **StreamTapeExtractor**: Evaluates inline JavaScript token concatenations.
- **VidPlay / Megaplay / Mewstream**: Resolves AES-encrypted keys and subtitle tracks.
- **HLS Server / Chunked Downloads**: Ensures playlists are parsed without EOF termination errors.

## Request Tagging for Multi-Hop Redirects
```kotlin
private data class VideoTag(val tmdbId: String, val season: String, val episode: String)

val request = GET(url, headers).newBuilder()
    .tag(VideoTag::class.java, VideoTag(tmdbId, season, epNum))
    .build()
```
