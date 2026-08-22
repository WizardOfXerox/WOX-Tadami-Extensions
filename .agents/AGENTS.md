# Tadami Extensions Workspace Rules

## Mandatory AI Workflow Checklist for Tadami Extensions

Whenever working inside this workspace:
- **Rule 1: Mandatory Guide References**: Always consult `extension_creation_guide.md` and `extension_upgrade_blueprint.md` for standard architecture, checklists, and diagnostic matrices.
- **Rule 2: Pre-Flight Liveness Check**: Always run domain checks (e.g. testing `baseUrl` and metadata API endpoints) before modifying source code.
- **Rule 3: Player Fallback**: Check for `no_ajax` HTML player containers (`div#source-player-$nume iframe`) if admin AJAX endpoints return `404` or `400`.
- **Rule 4: Redirect Tagging**: Use OkHttp `.tag(VideoTag::class.java)` for multi-server embeds (`vidsrc.me`) to prevent parameter loss across redirects.
- **Rule 5: APK Index Sync**: All distribution APKs in `apks/anime/apk/` must have `-debug` stripped from their filenames to align with `index.min.json`.
- **Rule 6: Compiler Overrides**: Omit `override` from `relatedAnimeListRequest` in extension classes so they compile cleanly under Aniyomi while remaining reflectively callable in Tadami.
