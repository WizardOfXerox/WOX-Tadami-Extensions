---
name: extension-liveness-prober
description: >-
  Automates high-throughput concurrent domain liveness testing, TLD redirects,
  SSL validation, and Cloudflare/WAF classification across Anime, Manga, and Novel sources.
---

# Extension Liveness Prober Skill

## Overview
This skill provides automated diagnostic tooling to test domain health, detect domain migrations/redirects, identify Cloudflare challenges, and flag dead/decommissioned domains across Anime, Manga, and Novel ecosystems.

## Classification System
- 🟢 **Healthy (HTTP 200 OK)**: Direct reachability and normal operations.
- 🔄 **Migrated / 301 Redirect**: The domain moved to a new hostname or TLD (e.g. `.su` ➔ `.cc`). Update `baseUrl` in source and index.
- 🛡️ **Cloudflare / DDoS Shield (HTTP 403 / 503)**: Protected by WAF or Turnstile. Accessible via in-app WebView or modern browser user-agent headers.
- 🔴 **Dead / Unreachable (NXDOMAIN / 404 / 521 / Connection Timeout)**: Expired or decommissioned domain requiring pruning or replacement.

## Probing Script Execution
```bash
python H:/Ideas/Tadami-Extensions/scratch/run_deep_scan.py
```
