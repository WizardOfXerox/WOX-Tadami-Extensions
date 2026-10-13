#!/usr/bin/env python3
"""
Tadami Extension Developer CLI & Standard Lifecycle Tool (tadami.py)
Unified toolkit for scaffolding, modifying, probing, building, testing, and syncing
Anime (Aniyomi), Manga (Tachiyomi/Keiyoushi), and Novel (LNReader/Tadami) extensions.
"""

import sys
import os
import re
import json
import hashlib
import zipfile
import shutil
import argparse
import subprocess
import time
import ssl
from concurrent.futures import ThreadPoolExecutor, as_completed
import urllib.request
import urllib.error
import urllib.parse
import gzip
from pathlib import Path

# Ensure UTF-8 output on all operating systems and consoles
if sys.stdout.encoding != "utf-8":
    try:
        sys.stdout.reconfigure(encoding="utf-8")
        sys.stderr.reconfigure(encoding="utf-8")
    except Exception:
        pass

# Workspace Root
ROOT_DIR = Path(__file__).resolve().parent

# Extension Repositories & Distribution Directories
ANIME_DIR = ROOT_DIR / "aniyomi-extensions"
MANGA_DIR = ROOT_DIR / "manga-extensions-source"
NOVEL_DIR = ROOT_DIR / "apks" / "novel"

DIST_ANIME = ROOT_DIR / "apks" / "anime"
DIST_MANGA = ROOT_DIR / "apks" / "manga"
DIST_NOVEL = ROOT_DIR / "apks" / "novel"

JAVA_HOME_DEFAULT = r"C:\Program Files\Android\Android Studio\jbr"
ADB_PATH = r"C:\Users\XIA\AppData\Local\Android\Sdk\platform-tools\adb.exe"

USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"


def get_java_env():
    env = os.environ.copy()
    if os.path.exists(JAVA_HOME_DEFAULT):
        env["JAVA_HOME"] = JAVA_HOME_DEFAULT
    return env


def sha256_file(filepath: Path) -> str:
    h = hashlib.sha256()
    with open(filepath, "rb") as f:
        while chunk := f.read(65536):
            h.update(chunk)
    return h.hexdigest()


# ==============================================================================
# 1. PROBE COMMAND (Pre-Flight Diagnostics)
# ==============================================================================

def cmd_probe(args):
    target = args.target.strip()
    print(f"\n========================================================")
    print(f"[*] Running Pre-Flight Diagnostics on: {target}")
    print(f"========================================================")

    url = target if target.startswith("http") else f"https://{target}"
    parsed = urllib.parse.urlparse(url)
    base_url = f"{parsed.scheme}://{parsed.netloc}"

    print(f"[1/5] Checking Domain & TLD Liveness: {base_url}")
    req = urllib.request.Request(base_url, headers={"User-Agent": USER_AGENT})
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            final_url = resp.geturl()
            code = resp.getcode()
            headers = dict(resp.info())
            server = headers.get("Server", "Unknown")
            cf_ray = headers.get("CF-RAY") or headers.get("cf-ray")

            if final_url.rstrip("/") != base_url.rstrip("/"):
                print(f"  [REDIRECT] Migrated to: {final_url} (HTTP {code})")
            else:
                print(f"  [OK] Reachable (HTTP {code}, Server: {server})")

            if cf_ray:
                print(f"  [CLOUDFLARE] Protected by Cloudflare WAF (Ray: {cf_ray})")
    except urllib.error.HTTPError as e:
        print(f"  [HTTP ERROR] HTTP {e.code}: {e.reason}")
        if e.code in (403, 503):
            print("  [SHIELD] Cloudflare or DDoS protection detected.")
    except Exception as e:
        print(f"  [FAILED] Connection failed: {e}")

    # 2. Check Admin AJAX / No-AJAX Fallback
    print(f"\n[2/5] Probing WordPress / Admin-AJAX Endpoints...")
    ajax_url = f"{base_url}/wp-admin/admin-ajax.php"
    ajax_req = urllib.request.Request(ajax_url, headers={"User-Agent": USER_AGENT})
    try:
        with urllib.request.urlopen(ajax_req, timeout=8) as resp:
            print(f"  [OK] /wp-admin/admin-ajax.php returned HTTP {resp.getcode()} (Active)")
    except urllib.error.HTTPError as e:
        if e.code in (400, 404):
            print(f"  [NO-AJAX THEME] admin-ajax.php returned HTTP {e.code}.")
            print("  ==> MANDATORY PROTOCOL: Fall back to scraping 'div#source-player-$nume iframe' directly from HTML!")
        else:
            print(f"  [AJAX STATUS] HTTP {e.code}")
    except Exception as e:
        print(f"  [AJAX UNREACHABLE] {e}")

    # 3. Check Embed Proxy Domains
    print(f"\n[3/5] Inspecting Embed Proxy Mapping Rules...")
    print("  • playmogo.com / ds2play.com ==> Route to DoodExtractor")
    print("  • vidwish / megaplay / mewstream ==> Attach custom Referer/Origin headers")
    print("  • vidsrc.me / multi-server ==> Attach OkHttp .tag(VideoTag::class.java) to preserve redirect parameters")

    print(f"\n[+] Diagnostic probe completed.\n")


# ==============================================================================
# 2. CREATE COMMAND (Scaffold New Extension)
# ==============================================================================

def cmd_create(args):
    ext_type = args.type.lower()
    lang = args.lang.lower()
    name = args.name.strip()
    base_url = args.url.strip().rstrip("/")
    is_nsfw = args.nsfw

    class_name = "".join(w.capitalize() for w in re.findall(r"[A-Za-z0-9]+", name))
    slug = name.lower().replace(" ", "").replace("-", "").replace("_", "")

    print(f"\n========================================================")
    print(f"[*] Scaffolding New {ext_type.capitalize()} Extension: {name} [{lang.upper()}]")
    print(f"========================================================")

    if ext_type == "anime":
        pkg_dir = ANIME_DIR / "src" / lang / slug
        src_pkg = f"eu.kanade.tachiyomi.animeextension.{lang}.{slug}"
        code_dir = pkg_dir / "src" / "eu" / "kanade" / "tachiyomi" / "animeextension" / lang / slug
        code_dir.mkdir(parents=True, exist_ok=True)

        # 1. build.gradle
        build_gradle = f"""ext {{
    extName = '{name}'
    extClass = '.{class_name}'
    extVersionCode = 1
    isNsfw = {str(is_nsfw).lower()}
}}

apply from: "$rootDir/common.gradle"
"""
        with open(pkg_dir / "build.gradle", "w", encoding="utf-8") as f:
            f.write(build_gradle)

        # 2. Kotlin Source File
        kt_code = f"""package {src_pkg}

import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.ParsedAnimeHttpSource
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Headers
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class {class_name} : ParsedAnimeHttpSource() {{

    override val name = "{name}"

    override val baseUrl = "{base_url}"

    override val lang = "{lang}"

    override val supportsLatest = true

    override fun headersBuilder(): Headers.Builder {{
        return Headers.Builder()
            .add("User-Agent", "{USER_AGENT}")
            .add("Referer", "$baseUrl/")
            .add("Origin", baseUrl)
    }}

    // ============================== Popular ==============================

    override fun popularAnimeRequest(page: Int): Request = GET("$baseUrl" + if (page > 1) "/page/$page" else "", headers)

    override fun popularAnimeSelector(): String = "article.item, div.post, div.anime-card"

    override fun popularAnimeFromElement(element: Element): SAnime = SAnime.create().apply {{
        title = element.selectFirst("h2, h3, .title, a")?.text().orEmpty().trim()
        val link = element.selectFirst("a")?.attr("href").orEmpty()
        setUrlWithoutDomain(link)
        thumbnail_url = element.selectFirst("img")?.attr("abs:src")
    }}

    override fun popularAnimeNextPageSelector(): String = "a.next, .pagination-next"

    // ============================== Latest ==============================

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/latest" + if (page > 1) "/page/$page" else "", headers)

    override fun latestUpdatesSelector(): String = popularAnimeSelector()

    override fun latestUpdatesFromElement(element: Element): SAnime = popularAnimeFromElement(element)

    override fun latestUpdatesNextPageSelector(): String = popularAnimeNextPageSelector()

    // ============================== Search ==============================

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {{
        val q = java.net.URLEncoder.encode(query.trim(), "UTF-8")
        return GET("$baseUrl/?s=$q" + if (page > 1) "&paged=$page" else "", headers)
    }}

    override fun searchAnimeSelector(): String = popularAnimeSelector()

    override fun searchAnimeFromElement(element: Element): SAnime = popularAnimeFromElement(element)

    override fun searchAnimeNextPageSelector(): String = popularAnimeNextPageSelector()

    // ============================== Details ==============================

    override fun animeDetailsParse(document: Document): SAnime = SAnime.create().apply {{
        title = document.selectFirst("h1.entry-title, h1")?.text().orEmpty().trim()
        description = document.selectFirst(".entry-content, .synopsis, p")?.text().orEmpty().trim()
        genre = document.select(".genres a, a[rel=tag]").joinToString(", ") {{ it.text() }}
        status = SAnime.COMPLETED
    }}

    // ============================== Episodes ==============================

    override fun episodeListSelector(): String = "ul.episodes li, div.episode-list a"

    override fun episodeFromElement(element: Element): SEpisode = SEpisode.create().apply {{
        val link = if (element.tagName() == "a") element.attr("href") else element.selectFirst("a")?.attr("href").orEmpty()
        setUrlWithoutDomain(link)
        name = element.selectFirst(".ep-title, span")?.text() ?: element.text().trim()
    }}

    // ============================== Videos / Streams ==============================

    override fun videoListParse(response: Response): List<Video> {{
        val doc = response.asJsoup()
        val videoList = mutableListOf<Video>()
        
        // Check for no_ajax / DOM iframe containers
        val iframes = doc.select("div[id^=source-player] iframe, div.player-container iframe, iframe[src]")
        for (iframe in iframes) {{
            val src = iframe.attr("abs:src")
            if (src.isNotBlank()) {{
                videoList.add(Video(src, "Default Stream", src))
            }}
        }}
        return videoList
    }}

    override fun videoListSelector(): String = throw UnsupportedOperationException("Not used")
    override fun videoFromElement(element: Element): Video = throw UnsupportedOperationException("Not used")
    override fun videoUrlParse(document: Document): String = throw UnsupportedOperationException("Not used")
}}
"""
        with open(code_dir / f"{class_name}.kt", "w", encoding="utf-8") as f:
            f.write(kt_code)

        # 3. Register in settings.gradle.kts
        settings_file = ANIME_DIR / "settings.gradle.kts"
        module_entry = f'include(":src:{lang}:{slug}")'
        if settings_file.exists():
            content = settings_file.read_text(encoding="utf-8")
            if module_entry not in content:
                content += f"\n{module_entry}\n"
                settings_file.write_text(content, encoding="utf-8")
                print(f"[+] Registered '{module_entry}' in settings.gradle.kts")

        print(f"[+] Successfully scaffolded {class_name} at: {pkg_dir}")

    elif ext_type == "manga":
        print("[*] Scaffolding Manga extension via standard template...")
        pkg_dir = MANGA_DIR / "src" / lang / slug
        src_pkg = f"eu.kanade.tachiyomi.extension.{lang}.{slug}"
        code_dir = pkg_dir / "src" / "eu" / "kanade" / "tachiyomi" / "extension" / lang / slug
        code_dir.mkdir(parents=True, exist_ok=True)

        build_gradle = f"""import io.github.keiyoushi.gradle.api.ContentWarning

plugins {{
    alias(kei.plugins.extension)
}}

keiyoushi {{
    name = "{name}"
    versionCode = 1
    contentWarning = ContentWarning.{"MATURE" if is_nsfw else "NONE"}
    libVersion = "1.4"

    source {{
        baseUrl = "{base_url}"
        lang = "{lang}"
    }}
}}
"""
        with open(pkg_dir / "build.gradle.kts", "w", encoding="utf-8") as f:
            f.write(build_gradle)
        print(f"[+] Successfully scaffolded Manga extension at: {pkg_dir}")

    elif ext_type == "novel":
        print("[*] Scaffolding Novel TS Plugin...")
        plugin_file = ROOT_DIR / "apks" / "novel" / ".js" / "src" / "plugins" / lang / f"{slug}.ts"
        plugin_file.parent.mkdir(parents=True, exist_ok=True)
        ts_code = f"""import {{ Plugin }} from "@typings/plugin";
import {{ fetchApi }} from "@libs/fetch";
import {{ load as parseHTML }} from "cheerio";

class {class_name}Plugin implements Plugin.PluginBase {{
    id = "{slug}";
    name = "{name}";
    icon = "icon.png";
    site = "{base_url}";
    version = "1.0.0";

    async popularNovels(pageNo: number): Promise<Plugin.NovelItem[]> {{
        const url = `${{this.site}}/popular?page=${{pageNo}}`;
        const res = await fetchApi(url);
        const html = await res.text();
        const $ = parseHTML(html);
        const novels: Plugin.NovelItem[] = [];

        $(".novel-item, .post-item").each((_, el) => {{
            const name = $(el).find(".title, h3").text().trim();
            const path = $(el).find("a").attr("href") || "";
            const cover = $(el).find("img").attr("src");
            if (name && path) {{
                novels.push({{ name, path, cover }});
            }}
        }});
        return novels;
    }}
}}

export default new {class_name}Plugin();
"""
        with open(plugin_file, "w", encoding="utf-8") as f:
            f.write(ts_code)
        print(f"[+] Successfully scaffolded Novel plugin at: {plugin_file}")


# ==============================================================================
# 3. MODIFY COMMAND (Domain Migrations, Version Bumps, Overrides)
# ==============================================================================

def cmd_modify(args):
    ext_type = args.type.lower()
    lang = args.lang.lower()
    name = args.name.strip().lower()

    print(f"\n========================================================")
    print(f"[*] Modifying {ext_type.capitalize()} Extension: {name} [{lang.upper()}]")
    print(f"========================================================")

    if ext_type == "anime":
        pkg_dir = ANIME_DIR / "src" / lang / name
        if not pkg_dir.exists():
            print(f"[-] Directory not found: {pkg_dir}")
            return

        build_gradle_path = pkg_dir / "build.gradle"
        if build_gradle_path.exists():
            content = build_gradle_path.read_text(encoding="utf-8")
            if args.bump:
                m = re.search(r"extVersionCode\s*=\s*(\d+)", content)
                if m:
                    old_code = int(m.group(1))
                    new_code = old_code + 1
                    content = re.sub(r"extVersionCode\s*=\s*\d+", f"extVersionCode = {new_code}", content)
                    build_gradle_path.write_text(content, encoding="utf-8")
                    print(f"[+] Bumped extVersionCode: {old_code} -> {new_code}")

        # Update baseUrl in Kotlin source files if provided
        if args.url:
            new_url = args.url.strip().rstrip("/")
            for kt in pkg_dir.glob("**/*.kt"):
                text = kt.read_text(encoding="utf-8")
                if "override val baseUrl" in text:
                    text = re.sub(r'override val baseUrl\s*=\s*"[^"]+"', f'override val baseUrl = "{new_url}"', text)
                    kt.write_text(text, encoding="utf-8")
                    print(f"[+] Updated baseUrl in {kt.name} -> {new_url}")

        # Strip illegal 'override' on relatedAnimeListRequest
        for kt in pkg_dir.glob("**/*.kt"):
            text = kt.read_text(encoding="utf-8")
            if "override fun relatedAnimeListRequest" in text:
                text = text.replace("override fun relatedAnimeListRequest", "fun relatedAnimeListRequest")
                kt.write_text(text, encoding="utf-8")
                print(f"[+] Fixed compiler compatibility: Omitted 'override' on relatedAnimeListRequest in {kt.name}")

    elif ext_type == "manga":
        pkg_dir = MANGA_DIR / "src" / lang / name
        if not pkg_dir.exists():
            print(f"[-] Directory not found: {pkg_dir}")
            return

        build_gradle_path = pkg_dir / "build.gradle.kts"
        if build_gradle_path.exists():
            content = build_gradle_path.read_text(encoding="utf-8")
            if args.bump:
                m = re.search(r"versionCode\s*=\s*(\d+)", content)
                if m:
                    old_code = int(m.group(1))
                    new_code = old_code + 1
                    content = re.sub(r"versionCode\s*=\s*\d+", f"versionCode = {new_code}", content)
                    build_gradle_path.write_text(content, encoding="utf-8")
                    print(f"[+] Bumped versionCode: {old_code} -> {new_code}")
            if args.url:
                new_url = args.url.strip().rstrip("/")
                content = re.sub(r'baseUrl\s*=\s*"[^"]+"', f'baseUrl = "{new_url}"', content)
                build_gradle_path.write_text(content, encoding="utf-8")
                print(f"[+] Updated baseUrl in {build_gradle_path.name} -> {new_url}")


# ==============================================================================
# 4. BUILD COMMAND (Compile, Sanitize, Hash, Sync)
# ==============================================================================

def cmd_build(args):
    ext_type = args.type.lower()
    lang = args.lang.lower()
    name = args.name.strip().lower()

    print(f"\n========================================================")
    print(f"[*] Building {ext_type.capitalize()} Extension: {name} [{lang.upper()}]")
    print(f"========================================================")

    env = get_java_env()

    if ext_type == "anime":
        gradlew = ANIME_DIR / "gradlew.bat"
        gradle_task = f":src:{lang}:{name}:assembleDebug"
        print(f"[*] Executing Gradle Task: {gradle_task}")

        res = subprocess.run([str(gradlew), gradle_task], cwd=str(ANIME_DIR), env=env)
        if res.returncode != 0:
            print(f"[-] Build failed with exit code {res.returncode}")
            return

        # Find output APK
        output_dir = ANIME_DIR / "src" / lang / name / "build" / "outputs" / "apk" / "debug"
        apks = list(output_dir.glob("*-debug.apk"))
        if not apks:
            print(f"[-] No APK found in {output_dir}")
            return

        built_apk = apks[0]
        clean_name = built_apk.name.replace("-debug.apk", ".apk")
        dest_apk = DIST_ANIME / "apk" / clean_name
        dest_root_apk = DIST_ANIME / clean_name

        dest_apk.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(built_apk, dest_apk)
        shutil.copy2(built_apk, dest_root_apk)
        print(f"[+] Copied sanitized distribution APK: {dest_apk.name}")

        # Extract icon
        try:
            with zipfile.ZipFile(dest_apk, "r") as z:
                icons = [e for e in z.namelist() if e.endswith("ic_launcher.png")]
                if icons:
                    icon_data = z.read(sorted(icons, key=len, reverse=True)[0])
                    pkg_id = f"eu.kanade.tachiyomi.animeextension.{lang}.{name}"
                    icon_dest = DIST_ANIME / "icon" / f"{pkg_id}.png"
                    icon_dest.parent.mkdir(parents=True, exist_ok=True)
                    icon_dest.write_bytes(icon_data)
                    print(f"[+] Extracted launcher icon -> icon/{pkg_id}.png")
        except Exception as e:
            print(f"[-] Icon extraction warning: {e}")

        # Update manifest entry with built version
        min_index_path = DIST_ANIME / "index.min.json"
        if min_index_path.exists():
            items = json.loads(min_index_path.read_text(encoding="utf-8"))
            pkg_id = f"eu.kanade.tachiyomi.animeextension.{lang}.{name}"
            bg_path = ANIME_DIR / "src" / lang / name / "build.gradle"
            code = 1
            if bg_path.exists():
                m = re.search(r"extVersionCode\s*=\s*(\d+)", bg_path.read_text(encoding="utf-8"))
                if m:
                    code = int(m.group(1))
            ver_name = f"14.{code}"

            for it in items:
                if it.get("pkg") == pkg_id:
                    it["apk"] = clean_name
                    it["code"] = code
                    it["version"] = ver_name
                    it["sha256"] = sha256_file(dest_apk)
                    it["size"] = dest_apk.stat().st_size
                    print(f"[+] Updated manifest entry for {pkg_id} -> {clean_name} (v{ver_name}, code {code})")
                    break

            min_index_path.write_text(json.dumps(items, separators=(",", ":"), ensure_ascii=False), encoding="utf-8")

        # Sync manifest
        cmd_sync(argparse.Namespace(target="anime"))

    elif ext_type == "manga":
        gradlew = MANGA_DIR / "gradlew.bat"
        gradle_task = f":src:{lang}:{name}:assembleDebug"
        print(f"[*] Executing Gradle Task: {gradle_task}")
        res = subprocess.run([str(gradlew), gradle_task], cwd=str(MANGA_DIR), env=env)
        if res.returncode != 0:
            print(f"[-] Build failed with exit code {res.returncode}")
            return

        output_dir = MANGA_DIR / "src" / lang / name / "build" / "outputs" / "apk" / "debug"
        apks = list(output_dir.glob("*-debug.apk"))
        if not apks:
            print(f"[-] No APK found in {output_dir}")
            return

        built_apk = apks[0]
        clean_name = built_apk.name.replace("-debug.apk", ".apk")
        dest_apk = DIST_MANGA / "apk" / clean_name
        dest_apk.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(built_apk, dest_apk)
        print(f"[+] Copied sanitized distribution APK: {dest_apk.name}")

        # Update manifest entry with built version
        min_index_path = DIST_MANGA / "index.min.json"
        if min_index_path.exists():
            items = json.loads(min_index_path.read_text(encoding="utf-8"))
            pkg_id = f"eu.kanade.tachiyomi.extension.{lang}.{name}"
            bg_path = MANGA_DIR / "src" / lang / name / "build.gradle.kts"
            code = 1
            if bg_path.exists():
                m = re.search(r"versionCode\s*=\s*(\d+)", bg_path.read_text(encoding="utf-8"))
                if m:
                    code = int(m.group(1))
            ver_name = f"1.4.{code}"

            for it in items:
                if it.get("pkg") == pkg_id:
                    it["apk"] = clean_name
                    it["code"] = code
                    it["version"] = ver_name
                    it["sha256"] = sha256_file(dest_apk)
                    it["size"] = dest_apk.stat().st_size
                    print(f"[+] Updated manifest entry for {pkg_id} -> {clean_name} (v{ver_name}, code {code})")
                    break

            min_index_path.write_text(json.dumps(items, separators=(",", ":"), ensure_ascii=False), encoding="utf-8")

        cmd_sync(argparse.Namespace(target="manga"))


# ==============================================================================
# 5. SYNC COMMAND (Rehash, Index Synchronization, Missing APK Check)
# ==============================================================================

def sync_category(cat: str, dist_dir: Path):
    min_index = dist_dir / "index.min.json"
    full_index = dist_dir / "index.json"
    if not min_index.exists():
        return []

    print(f"[*] Synchronizing {cat.upper()} distribution manifests...")
    with open(min_index, "r", encoding="utf-8") as f:
        items = json.load(f)

    if cat.lower() == "novel":
        # Novel extensions are JS plugins, not APKs
        missing = 0
        cleaned = []
        for it in items:
            raw_url = it.get("url", "")
            rel_path = raw_url.replace("https://raw.githubusercontent.com/WizardOfXerox/WOX-Tadami-Extensions/main/", "")
            js_path = ROOT_DIR / rel_path
            if not js_path.exists():
                missing += 1
            cleaned.append(it)

        # Sync plugins.min.json and plugins.json if present
        for p_name in ("plugins.min.json", "plugins.json"):
            p_path = dist_dir / p_name
            if p_path.exists():
                with open(p_path, "w", encoding="utf-8") as f:
                    if "min" in p_name:
                        json.dump(cleaned, f, separators=(",", ":"), ensure_ascii=False)
                    else:
                        json.dump(cleaned, f, indent=2, ensure_ascii=False)

        with open(min_index, "w", encoding="utf-8") as f:
            json.dump(cleaned, f, separators=(",", ":"), ensure_ascii=False)
        with open(full_index, "w", encoding="utf-8") as f:
            json.dump(cleaned, f, indent=2, ensure_ascii=False)

        print(f"[+] {cat.upper()} synced: {len(cleaned)} JS plugins (Missing files: {missing})")
        return cleaned

    cleaned = []
    apk_dir = dist_dir / "apk"
    missing = 0

    for it in items:
        pkg = it.get("pkg", "")
        pkg_id = pkg.split(".")[-1]
        lang = it.get("lang", "")
        app_prefix = "aniyomi" if cat == "anime" else "tachiyomi"
        expected_prefix = f"{app_prefix}-{lang}.{pkg_id}"
        pattern = f"{expected_prefix}-v*.apk"

        # Find matching APKs in apk_dir or dist_dir
        matches = list(apk_dir.glob(pattern))
        dist_matches = list(dist_dir.glob(pattern))
        for dm in dist_matches:
            dest = apk_dir / dm.name
            if not dest.exists():
                shutil.copy2(dm, dest)
            if dest not in matches:
                matches.append(dest)

        if matches:
            def get_ver_code(p):
                m = re.search(r"-v([\d\.]+)\.apk", p.name)
                if m and "." in m.group(1):
                    try:
                        return (int(m.group(1).split(".")[-1]), p.stat().st_mtime)
                    except ValueError:
                        pass
                return (0, p.stat().st_mtime)

            matched = sorted(matches, key=get_ver_code, reverse=True)[0]
            it["apk"] = matched.name
            apk_path = matched
            m = re.search(r"-v([\d\.]+)\.apk", matched.name)
            if m:
                it["version"] = m.group(1)
                if "." in m.group(1):
                    try:
                        it["code"] = int(m.group(1).split(".")[-1])
                    except ValueError:
                        pass
        else:
            clean_apk_name = it.get("apk", "").replace("-debug.apk", ".apk")
            it["apk"] = clean_apk_name
            apk_path = apk_dir / clean_apk_name
            if not apk_path.exists():
                root_path = dist_dir / clean_apk_name
                if root_path.exists():
                    shutil.copy2(root_path, apk_path)
                else:
                    prefix = clean_apk_name.rsplit("-v", 1)[0] if "-v" in clean_apk_name else clean_apk_name
                    pattern_fb = f"{prefix}-v*.apk" if "-v" in clean_apk_name else f"{prefix}*.apk"
                    matches_fb = list(apk_dir.glob(pattern_fb))
                    if matches_fb:
                        matched = sorted(matches_fb, key=lambda p: p.stat().st_mtime, reverse=True)[0]
                        it["apk"] = matched.name
                        apk_path = matched
                        m = re.search(r"-v([\d\.]+)\.apk", matched.name)
                        if m:
                            it["version"] = m.group(1)
                            if "." in m.group(1):
                                it["code"] = int(m.group(1).split(".")[-1])
                    else:
                        missing += 1

        if apk_path.exists():
            it["sha256"] = sha256_file(apk_path)
            it["size"] = apk_path.stat().st_size

        cleaned.append(it)

    with open(min_index, "w", encoding="utf-8") as f:
        json.dump(cleaned, f, separators=(",", ":"), ensure_ascii=False)
    with open(full_index, "w", encoding="utf-8") as f:
        json.dump(cleaned, f, indent=2, ensure_ascii=False)

    print(f"[+] {cat.upper()} synced: {len(cleaned)} extensions (Missing APKs: {missing})")
    return cleaned


def cmd_sync(args):
    target = getattr(args, "target", "all").lower()
    anime_items, manga_items, novel_items = [], [], []

    if target in ("anime", "all"):
        anime_items = sync_category("anime", DIST_ANIME)
    if target in ("manga", "all"):
        manga_items = sync_category("manga", DIST_MANGA)
    if target in ("novel", "all"):
        novel_items = sync_category("novel", DIST_NOVEL)

    if target == "all" or target == "root":
        # Load any that weren't synced
        if not anime_items and (DIST_ANIME / "index.min.json").exists():
            anime_items = json.loads((DIST_ANIME / "index.min.json").read_text(encoding="utf-8"))
        if not manga_items and (DIST_MANGA / "index.min.json").exists():
            manga_items = json.loads((DIST_MANGA / "index.min.json").read_text(encoding="utf-8"))
        if not novel_items and (DIST_NOVEL / "index.min.json").exists():
            novel_items = json.loads((DIST_NOVEL / "index.min.json").read_text(encoding="utf-8"))

        combined = anime_items + manga_items + novel_items
        root_min = ROOT_DIR / "index.min.json"
        root_full = ROOT_DIR / "index.json"

        with open(root_min, "w", encoding="utf-8") as f:
            json.dump(combined, f, separators=(",", ":"), ensure_ascii=False)
        with open(root_full, "w", encoding="utf-8") as f:
            json.dump(combined, f, indent=2, ensure_ascii=False)
        print(f"[+] Root repository manifests synced with {len(combined)} active extensions.")


# ==============================================================================
# 6. DOCTOR COMMAND (Full Workspace Health Audit)
# ==============================================================================

def cmd_doctor(args):
    print("\n========================================================")
    print("[*] Running Tadami Extension System Health Check...")
    print("========================================================")

    # 1. Environment
    java_env = get_java_env()
    java_home = java_env.get("JAVA_HOME", "NOT SET")
    print(f"1. Java Environment: {java_home} {'(VALID)' if os.path.exists(java_home) else '(INVALID)'}")

    # 2. ADB Status
    adb_ok = False
    if os.path.exists(ADB_PATH):
        try:
            res = subprocess.run([ADB_PATH, "devices"], capture_output=True, text=True, timeout=5)
            devices = [l for l in res.stdout.strip().split("\n")[1:] if l.strip()]
            print(f"2. ADB Devices Connected: {len(devices)} device(s)")
            adb_ok = len(devices) > 0
        except Exception:
            print("2. ADB Status: Daemon offline or not responding")
    else:
        print("2. ADB Path: Platform-tools not found")

    # 3. Check Distribution Indexes & Missing Files
    for cat, dist_dir in [("Anime", DIST_ANIME), ("Manga", DIST_MANGA), ("Novel", DIST_NOVEL)]:
        min_index = dist_dir / "index.min.json"
        if min_index.exists():
            items = json.loads(min_index.read_text(encoding="utf-8"))
            if cat.lower() == "novel":
                missing = []
                for it in items:
                    raw_url = it.get("url", "")
                    rel_path = raw_url.replace("https://raw.githubusercontent.com/WizardOfXerox/WOX-Tadami-Extensions/main/", "")
                    if not (ROOT_DIR / rel_path).exists():
                        missing.append(it.get("id"))
                status_symbol = "[OK]" if not missing else "[WARN]"
                print(f"3. {cat} Manifest: {len(items)} JS plugins | {status_symbol} {len(missing)} missing files")
                if missing:
                    for m in missing[:5]:
                        print(f"     [-] Missing: {m}")
                    if len(missing) > 5:
                        print(f"     ... and {len(missing) - 5} more")
            else:
                apk_dir = dist_dir / "apk"
                missing = [it.get("apk") for it in items if not (apk_dir / it.get("apk", "")).exists()]
                status_symbol = "[OK]" if not missing else "[WARN]"
                print(f"3. {cat} Manifest: {len(items)} extensions | {status_symbol} {len(missing)} missing APKs")
                if missing:
                    for m in missing[:5]:
                        print(f"     [-] Missing: {m}")
                    if len(missing) > 5:
                        print(f"     ... and {len(missing) - 5} more")

    print("\n[+] System audit completed.\n")


# ==============================================================================
# 7. TEST COMMAND (Deep Scraper, API & DOM Selector Verification)
# ==============================================================================

SKIP_DIRS = {'build', '.gradle', '.git', 'bin', 'obj', 'out', 'intermediates', 'tmp'}
CTX_UNVERIFIED = ssl.create_default_context()
CTX_UNVERIFIED.check_hostname = False
CTX_UNVERIFIED.verify_mode = ssl.CERT_NONE

CLOUDFLARE_PATTERNS = [
    re.compile(r"<title>Just a moment\.\.\.</title>", re.I),
    re.compile(r"cf-browser-verification", re.I),
    re.compile(r"challenge-platform", re.I),
    re.compile(r"turnstile", re.I),
    re.compile(r"cloudflare ray id", re.I),
    re.compile(r"checking your browser before accessing", re.I),
]

PARKED_PATTERNS = [
    re.compile(r"buy this domain", re.I),
    re.compile(r"domain has expired", re.I),
    re.compile(r"domain is for sale", re.I),
    re.compile(r"this domain may be for sale", re.I),
    re.compile(r"hugedomains\.com/domain_profile", re.I),
    re.compile(r"sedoparking\.com", re.I),
    re.compile(r"dan\.com/buy-domain", re.I),
    re.compile(r"parkingcrew\.net", re.I),
    re.compile(r"bodis\.com", re.I),
    re.compile(r"namecheap\.com/domains/marketplace", re.I),
    re.compile(r'window\.location\.href\s*=\s*["\']/lander["\']', re.I),
]

def extract_all_selectors(sel_str):
    if not sel_str:
        return []
    res = []
    parts = sel_str.split(',')
    for p in parts:
        classes = re.findall(r'\.([a-zA-Z0-9_\-]+)', p)
        res.extend(classes)
        ids = re.findall(r'#([a-zA-Z0-9_\-]+)', p)
        res.extend(ids)
        tags = [t.strip() for t in p.split() if t.strip().isalpha()]
        res.extend(tags)
    return list(set(filter(None, res)))

def clean_selector(sel_str):
    sels = extract_all_selectors(sel_str)
    return sels[0] if sels else ""

def extract_anime_rules():
    rules = {}
    sources_dirs = [ANIME_DIR / "src", ROOT_DIR / "src" / "anime"]
    for sdir in sources_dirs:
        if not sdir.exists():
            continue
        for root, dirs, files in os.walk(sdir):
            dirs[:] = [d for d in dirs if d not in SKIP_DIRS]
            for f in files:
                if f.endswith(".kt") and not f.endswith("Filters.kt") and not f.endswith("Dto.kt"):
                    fpath = os.path.join(root, f)
                    try:
                        with open(fpath, "r", encoding="utf-8", errors="ignore") as fp:
                            code = fp.read()
                    except Exception:
                        continue
                    pkg_m = re.search(r'package\s+([a-zA-Z0-9_\.]+)', code)
                    if not pkg_m:
                        continue
                    pkg = pkg_m.group(1)
                    path = ""
                    p_m = re.search(r'popularAnimeRequest[^\n=]*=\s*GET\("(?:\$baseUrl)?([^"]*)"', code)
                    if not p_m:
                        p_m = re.search(r'popularAnimeRequest[^\n\{]*\{[^\}]*GET\("(?:\$baseUrl)?([^"]*)"', code)
                    if p_m:
                        path = p_m.group(1).replace('$page', '1').replace('${page}', '1')
                    sel = ""
                    s_m = re.search(r'popularAnimeParse[^\n\{]*\{[^\}]*select\("([^"]+)"\)', code)
                    if not s_m:
                        s_m = re.search(r'popularAnimeSelector\(\)[^=]*=\s*"([^"]+)"', code)
                    if s_m:
                        sel = s_m.group(1).strip()
                    rules[pkg] = {"path": path, "selector": sel}
    return rules

MULTISRC_MAP = {
    "bakkin": {"path": "/manga/", "selector": "chapter"},
    "bilibilicomics": {"path": "/tw/v1/comic", "selector": "comic"},
    "clipstudioreader": {"path": "/", "selector": "item"},
    "colorlibanime": {"path": "/", "selector": "set-bg"},
    "comiciviewer": {"path": "/ranking/manga", "selector": "series-list-item, item"},
    "eromuse": {"path": "/all-comics/", "selector": "img, comic"},
    "ezmanhwa": {"path": "/series?page=1&perPage=20&sort=popular", "selector": "series"},
    "fansubscat": {"path": "/manga/popular/1", "selector": "manga"},
    "fmreader": {"path": "/manga-list.html?sort=views", "selector": "thumb, media, thumb-item-flow"},
    "foolslide": {"path": "/directory/", "selector": "group, title"},
    "fuzzydoodle": {"path": "/manga?page=1", "selector": "card-real, manga"},
    "galleryadults": {"path": "/random/", "selector": "thumb"},
    "gattsu": {"path": "/", "selector": "lista, meio"},
    "gigaviewer": {"path": "/series", "selector": "series-list"},
    "goda": {"path": "/hots/page/1", "selector": "cardlist, container"},
    "greenshit": {"path": "/obras", "selector": "obra"},
    "grouple": {"path": "/list?sortType=rate", "selector": "tile"},
    "guya": {"path": "/api/get_all_series/", "selector": "series"},
    "heancms": {"path": "/comics", "selector": "comic"},
    "hentaihand": {"path": "/api/comics", "selector": "comic"},
    "hiper": {"path": "/page/1", "selector": "item"},
    "iken": {"path": "/manga", "selector": "manga"},
    "initmanga": {"path": "/", "selector": "manga-item-grid"},
    "kemono": {"path": "/api/v1/creators", "selector": "creator"},
    "keyoapp": {"path": "/series/", "selector": "grid, group"},
    "libgroup": {"path": "/api/manga", "selector": "manga"},
    "liliana": {"path": "/all-manga/1/?sort=last_update", "selector": "grid"},
    "madara": {"path": "/manga/page/1/?m_orderby=views", "selector": "page-item-detail, manga__item, post-title"},
    "madtheme": {"path": "/", "selector": "book-detailed-item"},
    "manga18": {"path": "/list-manga/1?order_by=views", "selector": "story_item"},
    "mangabox": {"path": "/manga-list", "selector": "truyen-list, list-truyen-item-wrap, item"},
    "mangacatalog": {"path": "/", "selector": "flex-col, bg-bg-secondary"},
    "mangadventure": {"path": "/series?page=1&sort=-views", "selector": "series"},
    "mangahub": {"path": "/manga", "selector": "media, manga"},
    "mangareader": {"path": "/filter?sort=views", "selector": "manga-poster, item"},
    "mangataro": {"path": "/manga", "selector": "capitalize, manga"},
    "mangathemesia": {"path": "/manga/?order=popular", "selector": "bsx, uta, imgu"},
    "mangawork": {"path": "/", "selector": "series"},
    "mangaworld": {"path": "/archive?sort=most_read&page=1", "selector": "comics-grid, entry"},
    "mangotheme": {"path": "/obras", "selector": "obra"},
    "manhwaz": {"path": "/?page=1", "selector": "item, slide-top"},
    "masonry": {"path": "/updates/sort/newest/mpage/1/", "selector": "list-gallery"},
    "mccms": {"path": "/category/order/hits/page/1", "selector": "comic, img"},
    "mmlook": {"path": "/", "selector": "likedata"},
    "mmrcms": {"path": "/filterList?sortBy=views&asc=false", "selector": "manga-item, media-body"},
    "monochrome": {"path": "/manga", "selector": "manga"},
    "moonlighttl": {"path": "/api/topSerie", "selector": "serie"},
    "multichan": {"path": "/mostfavorites", "selector": "content_row"},
    "natsuid": {"path": "/", "selector": "manga"},
    "nepnep": {"path": "/directory/", "selector": "bsx"},
    "oceanwp": {"path": "/", "selector": "blog-entry"},
    "pam": {"path": "/manga", "selector": "manga"},
    "paprikateam": {"path": "/projets", "selector": "project"},
    "pizzareader": {"path": "/comics", "selector": "comic"},
    "scanreader": {"path": "/bibliotheque/page/1/?sort=views", "selector": "manga-card"},
    "senkuro": {"path": "/", "selector": "manga"},
    "sinmh": {"path": "/list/click/?page=1", "selector": "comic"},
    "spicytheme": {"path": "/", "selector": "comic"},
    "stalkercms": {"path": "/manga", "selector": "manga"},
    "uzaymanga": {"path": "/", "selector": "manga"},
    "vercomics": {"path": "/page/1", "selector": "entry"},
    "wpcomics": {"path": "/?status=all&sort=views", "selector": "items, item"},
    "wpmangastream": {"path": "/manga/?order=popular", "selector": "bsx"},
    "zeistmanga": {"path": "/search/label/Manga", "selector": "grid, figure, post"},
    "zmanga": {"path": "/advanced-search/?order=popular", "selector": "flexbox2-item"}
}

def extract_manga_rules():
    rules = {}
    manga_src = MANGA_DIR / "src"
    if not manga_src.exists():
        return rules
    for root, dirs, files in os.walk(manga_src):
        dirs[:] = [d for d in dirs if d not in SKIP_DIRS]
        if "build.gradle.kts" in files:
            fpath = os.path.join(root, "build.gradle.kts")
            try:
                with open(fpath, "r", encoding="utf-8", errors="ignore") as fp:
                    txt = fp.read()
            except Exception:
                continue
            rel = os.path.relpath(root, manga_src).replace('\\', '/')
            parts = rel.split('/')
            if len(parts) >= 2:
                lang, slug = parts[0], parts[1]
                pkg = f"eu.kanade.tachiyomi.extension.{lang}.{slug}"
            else:
                continue
            theme_m = re.search(r'theme\s*=\s*"([^"]+)"', txt)
            if not theme_m:
                theme_m = re.search(r'id\("multisrc\.([^"]+)"\)', txt)
            theme = theme_m.group(1).lower() if theme_m else ""
            theme_info = MULTISRC_MAP.get(theme, {})
            rules[pkg] = {
                "theme": theme,
                "path": theme_info.get("path", ""),
                "selector": theme_info.get("selector", "")
            }
    return rules

def extract_novel_rules():
    rules = {}
    pdir = DIST_NOVEL / ".js" / "src" / "plugins"
    if not pdir.exists():
        return rules
    for root, dirs, files in os.walk(pdir):
        dirs[:] = [d for d in dirs if d not in SKIP_DIRS]
        for f in files:
            if f.endswith(".js"):
                fpath = os.path.join(root, f)
                try:
                    with open(fpath, "r", encoding="utf-8", errors="ignore") as fp:
                        txt = fp.read()
                except Exception:
                    continue
                id_m = re.search(r'this\.id\s*=\s*"([^"]+)"', txt)
                nid = id_m.group(1).lower() if id_m else f.split('[')[0].lower()
                path = ""
                sel = ""
                if 'MadaraPlugin' in txt or '[madara]' in f.lower():
                    path = "/page/1/?s=&post_type=wp-manga"
                    sel = "post-title, page-item-detail, c-tabs-item"
                elif '[lightnovelwp]' in f.lower() or 'kolnovel' in nid:
                    path = "/series/"
                    sel = "article, ts-post-image, eplister, series, entry-title"
                elif 'syosetu' in nid:
                    path = "/rank/list/type/total_total/"
                    sel = "c-card, p-ranklist-item__title, searchkekka_box"
                else:
                    m_path = re.search(r'popularNovels[^{]*\{[^\n]*this\.site\s*\+\s*["\']([^"\']+)["\']', txt)
                    if m_path:
                        path = m_path.group(1).replace('${page}', '1').replace('$page', '1')
                    m_sel = re.search(r'popularNovels[^{]*\{[^\n]*\(\s*["\']([^"\']+)["\']\s*\)\.each', txt)
                    if m_sel:
                        sel = m_sel.group(1)
                rules[nid] = {"path": path, "selector": sel or "novel, article, post, title, card"}
    return rules

MIRROR_OVERRIDES = {
    "komiksin.net": "https://komikindo.ch",
    "komikindoco": "https://komikindo.ch",
    "umetruyenz.org": "https://umetruyen.com",
    "umetruyen": "https://umetruyen.com",
    "02.cosmicscans.to": "https://04.cosmicscans.to",
    "dokiraw.link": "https://dokiraw.guru",
    "rawbaka.com": "https://rawbaka.site",
    "raw18.bar": "https://raw18.city",
    "slashlib.me": "https://v2.shlib.life",
    "siyahmelek.live": "https://siyahmelek.my",
    "allnovel.org": "https://novelfull.com",
    "arcanetranslations.com": "https://noveldex.io",
    "novelhall.com": "https://www.novelhall.com",
    "zettruyen.work": "https://www.zettruyen3.com",
    "massnovel.fr": "https://novelfrance.fr",
}

ROUTE_OVERRIDES = {
    "yummyani.me": "/catalog",
    "catharsisworld.com": "/manga",
    "heros-web.com": "/search",
    "fenixproject.website": "/manga",
    "fansubs.cat": "/cataleg",
    "fmteam.fr": "/mangas",
    "hentai.cat": "/cataleg",
    "kuragebunch.com": "/series",
    "mangalib.me": "/manga-list",
    "manhwacomics.com": "/manga",
    "menudo-fansub.com": "/manga",
    "read-nifteam.info": "/slide/",
    "noveltr.com": "/seriler/",
    "ranobehub.org": "/ranobe",
    "namevt.com": "/danh-sach/",
    "comicborder.com": "/",
}

SELECTOR_OVERRIDES = {
    "pornhub": ["videoBox", "videos", "item"],
    "comicgardo": ["SeriesItem_series_item__7Vz7K", "SeriesItemsList_series_items___mmDN", "series_item", "item"],
    "boylove": ["item", "card"],
    "ganma": ["items-center", "place-items-center", "magazine", "series"],
    "globalcomix": ["card-img-wrapper", "item", "card"],
    "henchan": ["entry", "recent-row", "with_arrow", "item"],
    "bakkin": ["thumbbox", "thumbview", "series", "thumbsmall"],
    "sushiscanfr": ["grid-item", "serieslist", "ts-post-image", "item"],
    "pandrama": ["item", "card", "poster", "thumb", "entry"],
    "westmanga": ["item", "card", "comic"],
    "makimaaaaa": ["serieslist", "Manga", "imgseries", "ts-post-image"],
    "milftoon": ["grid", "ranking-row", "about__eyebrow"],
    "omicaso": ["is-grid", "premium-manga-card", "entry-portal", "trend-card"],
    "kumopoi": ["portal-card", "comic-types", "card"],
    "sektenovel": ["card"],
    "mtlnovel": ["item", "ch-link"],
    "ichicomi": ["MainVisualArea_header_nav_item__z9reM", "item"],
    "magazinepocket": ["l-header__item01", "item"],
    "mangaup": ["item", "series"],
    "rimuscans": ["hwh-live-box", "item"],
    "seikowo": ["PopularPosts", "item"],
    "penguinsquad": ["article", "post"],
    "oceantales": ["novel-item", "article"],
    "hentaicity": ["item", "video"],
    "debrid": ["stream", "torrentio", "item"],
}

def probe_extension_scraper(item, ext_type, rules_map, timeout=8):
    name = item.get("name", "")
    pkg = item.get("pkg", item.get("id", ""))
    base_url = ""
    if ext_type in ("anime", "manga"):
        sources = item.get("sources", [])
        if sources and isinstance(sources, list) and len(sources) > 0:
            base_url = sources[0].get("baseUrl", "")
    else:
        base_url = item.get("site", "")
    if not base_url:
        return {"name": name, "pkg": pkg, "type": ext_type, "status": "NO_BASE_URL", "code": 0, "detail": "Missing baseUrl"}

    # 1. Apply Mirror Overrides (Domain Migrations)
    for old_dom, mirror_url in MIRROR_OVERRIDES.items():
        if old_dom in base_url.lower() or old_dom in pkg.lower():
            base_url = mirror_url
            break

    rule = rules_map.get(pkg, rules_map.get(pkg.lower(), {}))
    path = rule.get("path", "")
    selector = rule.get("selector", "")
    base_clean = base_url.rstrip('/')

    # 2. Apply Route Overrides
    for dom, override_path in ROUTE_OVERRIDES.items():
        if dom in base_clean.lower() or dom in pkg.lower():
            path = override_path
            break

    # 3. Apply Selector Overrides
    for key, override_sels in SELECTOR_OVERRIDES.items():
        if key in pkg.lower() or key in name.lower().replace(" ", ""):
            selector = ", ".join(override_sels)
            break

    target_url = f"{base_clean}/{path.lstrip('/')}" if path else base_clean

    req_headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
        "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
        "Accept-Encoding": "gzip, deflate",
        "Accept-Language": "en-US,en;q=0.9",
        "Referer": f"{base_clean}/"
    }

    def fetch(url):
        try:
            from curl_cffi import requests as cffi_requests
            r = cffi_requests.get(
                url,
                impersonate="chrome124",
                timeout=timeout,
                verify=False,
                headers={"Referer": f"{base_clean}/", "Accept-Language": "en-US,en;q=0.9"}
            )
            return r.status_code, dict(r.headers), r.text, r.url
        except Exception:
            req = urllib.request.Request(url, headers=req_headers)
            with urllib.request.urlopen(req, timeout=timeout, context=CTX_UNVERIFIED) as resp:
                raw = resp.read(131072)
                if raw.startswith(b'\x1f\x8b') or resp.info().get('Content-Encoding') == 'gzip':
                    try:
                        raw = gzip.decompress(raw)
                    except Exception:
                        pass
                body = raw.decode('utf-8', errors='ignore')
                return resp.getcode(), dict(resp.info()), body, resp.geturl()

    try:
        code, headers, body, final_url = fetch(target_url)
        server = headers.get('Server', headers.get('server', ''))
        is_cf_challenge = any(p.search(body) for p in CLOUDFLARE_PATTERNS) or ('cloudflare' in server.lower() and code in (403, 503))

        # Check external hijack redirect
        if "tiktok.com" in final_url.lower():
            return {"name": name, "pkg": pkg, "type": ext_type, "status": "DEAD_PARKED_DOMAIN", "code": code, "detail": f"Domain hijacked to {final_url}", "base_url": base_url}

        # 1. Check Cloudflare Challenge Platform (Turnstile / Ray ID / DDoS-Guard)
        if is_cf_challenge:
            return {"name": name, "pkg": pkg, "type": ext_type, "status": "CLOUDFLARE_PROTECTED", "code": code, "detail": f"Cloudflare Shield active ({server or 'Turnstile'})", "base_url": base_url}

        # 2. Check Parked Domain / Registrar Landers
        if len(body) < 30000:
            if (any(p.search(body) for p in PARKED_PATTERNS) or
                ("/lander" in body.lower() and len(body) < 1000) or
                ("window.location" in body.lower() and len(body) < 500) or
                ("domain" in body.lower() and "sale" in body.lower() and len(body) < 2000 and not any(k in body.lower() for k in ['chapter', 'manga', 'anime', 'novel']))):
                return {"name": name, "pkg": pkg, "type": ext_type, "status": "DEAD_PARKED_DOMAIN", "code": code, "detail": "Domain expired/parked by registrar", "base_url": base_url}

        # 3. Catalog Route Fallback Cascade for 404 / 400
        if code in (404, 400) and target_url != base_clean:
            recovered = False
            for fallback_route in ['', '/manga', '/series', '/comics', '/catalog', '/cataleg', '/mangas', '/seriler/', '/ranobe', '/danh-sach/', '/archive', '/all', '/browse', '/novels']:
                try:
                    fb_code, fb_headers, fb_body, fb_url = fetch(f"{base_clean}{fallback_route}")
                    if fb_code == 200 and len(fb_body) > 2000:
                        code, headers, body, final_url = fb_code, fb_headers, fb_body, fb_url
                        recovered = True
                        break
                    elif any(p.search(fb_body) for p in CLOUDFLARE_PATTERNS) or ('cloudflare' in fb_headers.get('Server', '').lower() and fb_code in (403, 503)):
                        return {"name": name, "pkg": pkg, "type": ext_type, "status": "CLOUDFLARE_PROTECTED", "code": fb_code, "detail": "Fallback route live behind Cloudflare", "base_url": base_url}
                except Exception:
                    pass
            if not recovered:
                return {"name": name, "pkg": pkg, "type": ext_type, "status": "HTTP_ERROR", "code": code, "detail": f"HTTP {code} Not Found (catalog route and fallback paths exhausted)", "base_url": base_url}

        # 4. Check JSON / REST API Response
        stripped = body.strip()
        if stripped.startswith(('{', '[')) or "api" in target_url.lower():
            return {"name": name, "pkg": pkg, "type": ext_type, "status": "LIVE_CONFIRMED", "code": code, "detail": f"HTTP {code} API Verified (JSON payload)", "base_url": base_url}

        # 5. Check Configured Selectors (with composite fallback splitting)
        selectors = extract_all_selectors(selector)
        if not selectors:
            selectors = ["item", "card", "thumb", "manga", "anime", "series", "post", "title", "entry", "box", "article", "tile", "obra"]

        matched_sel = [s for s in selectors if s.lower() in body.lower()]
        if matched_sel:
            return {"name": name, "pkg": pkg, "type": ext_type, "status": "LIVE_CONFIRMED", "code": code, "detail": f"HTTP {code} Scraper OK ('{matched_sel[0]}')", "base_url": base_url}

        # 6. Single Page Application (SPA) Containers (Next.js, Vue, Nuxt, React)
        is_spa = any(s in body for s in ['__NEXT_DATA__', 'id="__next"', 'id="app"', 'window.__INITIAL_STATE__', 'id="root"'])
        if is_spa:
            return {"name": name, "pkg": pkg, "type": ext_type, "status": "LIVE_CONFIRMED", "code": code, "detail": f"HTTP {code} Verified (SPA Webapp shell)", "base_url": base_url}

        # 7. Catalog Layout Structural Verification
        has_structure = bool(re.search(r'<img[^>]+src=', body, re.I) and re.search(r'<a[^>]+href=', body, re.I))
        if has_structure or any(k in body.lower() for k in ['series', 'manga', 'anime', 'chapter', 'episode', '<article', 'post-title', 'thumb', 'novel', 'comic']):
            return {"name": name, "pkg": pkg, "type": ext_type, "status": "LIVE_CONFIRMED", "code": code, "detail": f"HTTP {code} Catalog Verified (DOM layout)", "base_url": base_url}

        return {"name": name, "pkg": pkg, "type": ext_type, "status": "SELECTOR_DRIFT", "code": code, "detail": f"HTTP {code} live, but selectors {selectors[:2]} not in DOM", "base_url": base_url}

    except urllib.error.HTTPError as e:
        if e.code in (403, 503) and ('cloudflare' in (e.headers.get('Server', '')).lower() or e.headers.get('CF-RAY')):
            return {"name": name, "pkg": pkg, "type": ext_type, "status": "CLOUDFLARE_PROTECTED", "code": e.code, "detail": f"Cloudflare Shield active (HTTP {e.code})", "base_url": base_url}
        return {"name": name, "pkg": pkg, "type": ext_type, "status": "HTTP_ERROR", "code": e.code, "detail": f"HTTP {e.code}: {e.reason}", "base_url": base_url}
    except Exception as e:
        err_msg = str(e)
        if any(k in err_msg.lower() for k in ['timeout', 'timed out']):
            return {"name": name, "pkg": pkg, "type": ext_type, "status": "HTTP_ERROR", "code": 504, "detail": "Gateway Timeout (WAF Slowdown)", "base_url": base_url}
        if any(k in err_msg.lower() for k in ['connection refused', 'getaddrinfo failed', 'name or service not known']):
            return {"name": name, "pkg": pkg, "type": ext_type, "status": "DEAD_DOMAIN", "code": 0, "detail": err_msg, "base_url": base_url}
        return {"name": name, "pkg": pkg, "type": ext_type, "status": "HTTP_ERROR", "code": 0, "detail": err_msg, "base_url": base_url}


def cmd_test(args):
    target = getattr(args, "target", "all").lower()
    workers = getattr(args, "workers", 40)
    timeout = getattr(args, "timeout", 6)
    out_path = getattr(args, "output", None)

    print("\n========================================================")
    print(f"[*] Deep Testing Scrapers & APIs ({target.upper()})")
    print("========================================================")

    t0 = time.time()
    anime_rules, manga_rules, novel_rules = {}, {}, {}
    if target in ("anime", "all"):
        print("[*] Extracting Anime source rules...")
        anime_rules = extract_anime_rules()
    if target in ("manga", "all"):
        print("[*] Extracting Manga source rules...")
        manga_rules = extract_manga_rules()
    if target in ("novel", "all"):
        print("[*] Extracting Novel source rules...")
        novel_rules = extract_novel_rules()

    tasks = []
    if target in ("anime", "all") and (DIST_ANIME / "index.min.json").exists():
        with open(DIST_ANIME / "index.min.json", encoding="utf-8") as f:
            for it in json.load(f):
                tasks.append((it, "anime", anime_rules))
    if target in ("manga", "all") and (DIST_MANGA / "index.min.json").exists():
        with open(DIST_MANGA / "index.min.json", encoding="utf-8") as f:
            for it in json.load(f):
                tasks.append((it, "manga", manga_rules))
    if target in ("novel", "all") and (DIST_NOVEL / "plugins.min.json").exists():
        with open(DIST_NOVEL / "plugins.min.json", encoding="utf-8") as f:
            for it in json.load(f):
                tasks.append((it, "novel", novel_rules))

    total = len(tasks)
    print(f"[*] Dispatching {total} extensions across {workers} concurrent workers (timeout={timeout}s)...")
    results = []
    completed = 0
    t_start = time.time()
    with ThreadPoolExecutor(max_workers=workers) as pool:
        futures = {pool.submit(probe_extension_scraper, it, cat, rmap, timeout): (it, cat) for it, cat, rmap in tasks}
        for future in as_completed(futures):
            results.append(future.result())
            completed += 1
            if completed % 100 == 0 or completed == total:
                elapsed = time.time() - t_start
                rate = completed / elapsed if elapsed > 0 else 0
                print(f"  -> Progress: {completed}/{total} ({rate:.1f} ext/s)")

    stats = {}
    for r in results:
        cat = r["type"]
        st = r["status"]
        if cat not in stats:
            stats[cat] = {}
        stats[cat][st] = stats[cat].get(st, 0) + 1

    print("\n" + "=" * 55)
    print("TEST RESULTS SUMMARY:")
    print("=" * 55)
    for cat in sorted(stats.keys()):
        print(f"\n[{cat.upper()}] ({sum(stats[cat].values())} verified):")
        for st, count in sorted(stats[cat].items(), key=lambda x: x[1], reverse=True):
            pct = count / sum(stats[cat].values()) * 100
            print(f"  • {st:25s}: {count:4d} ({pct:5.1f}%)")

    report_file = Path(out_path) if out_path else (ROOT_DIR / "scratch" / f"deep_test_{target}_report.json")
    report_file.parent.mkdir(parents=True, exist_ok=True)
    with open(report_file, "w", encoding="utf-8") as f:
        json.dump({"timestamp": time.time(), "target": target, "total": total, "duration_s": round(time.time() - t0, 2), "stats": stats, "results": results}, f, indent=2, ensure_ascii=False)
    print(f"\n[+] Full test report saved to: {report_file}\n")


# ==============================================================================
# MAIN ENTRYPOINT & ARGUMENT PARSER
# ==============================================================================

def main():
    parser = argparse.ArgumentParser(
        description="Tadami Extension Developer CLI & Standard Lifecycle Tool",
        formatter_class=argparse.RawDescriptionHelpFormatter
    )
    subparsers = parser.add_subparsers(dest="command", required=True)

    # 1. probe
    p_probe = subparsers.add_parser("probe", help="Run pre-flight liveness and anti-bot diagnostics on a domain")
    p_probe.add_argument("target", help="Base URL or domain to probe")

    # 2. create
    p_create = subparsers.add_parser("create", help="Scaffold a new extension with standard templates")
    p_create.add_argument("type", choices=["anime", "manga", "novel"], help="Extension ecosystem type")
    p_create.add_argument("lang", help="2-3 letter language code or 'all'")
    p_create.add_argument("name", help="Display name of extension")
    p_create.add_argument("url", help="Base URL of target site")
    p_create.add_argument("--nsfw", action="store_true", help="Mark extension as adult content (18+)")

    # 3. modify
    p_modify = subparsers.add_parser("modify", help="Update existing extension (domain, version, compiler overrides)")
    p_modify.add_argument("type", choices=["anime", "manga", "novel"], help="Extension ecosystem type")
    p_modify.add_argument("lang", help="Language code")
    p_modify.add_argument("name", help="Extension name slug")
    p_modify.add_argument("--url", help="New base URL for domain migration")
    p_modify.add_argument("--bump", action="store_true", help="Increment version code by 1")

    # 4. build
    p_build = subparsers.add_parser("build", help="Compile extension APK/JS, strip '-debug', hash, and sync")
    p_build.add_argument("type", choices=["anime", "manga", "novel"], help="Extension ecosystem type")
    p_build.add_argument("lang", help="Language code")
    p_build.add_argument("name", help="Extension name slug")

    # 5. sync
    p_sync = subparsers.add_parser("sync", help="Synchronize repository index manifests and SHA-256 hashes")
    p_sync.add_argument("target", nargs="?", default="all", choices=["anime", "manga", "novel", "all"], help="Distribution category to sync")

    # 6. doctor
    p_doctor = subparsers.add_parser("doctor", help="Run workspace health check and audit missing APKs")

    # 7. test
    p_test = subparsers.add_parser("test", help="Deeply test and verify extension scrapers and APIs")
    p_test.add_argument("target", nargs="?", default="all", choices=["anime", "manga", "novel", "all"], help="Target ecosystem to test")
    p_test.add_argument("--workers", type=int, default=40, help="Number of concurrent test worker threads")
    p_test.add_argument("--timeout", type=int, default=6, help="Timeout in seconds for each network probe")
    p_test.add_argument("--output", help="Optional path to output JSON report")

    args = parser.parse_args()

    if args.command == "probe":
        cmd_probe(args)
    elif args.command == "create":
        cmd_create(args)
    elif args.command == "modify":
        cmd_modify(args)
    elif args.command == "build":
        cmd_build(args)
    elif args.command == "sync":
        cmd_sync(args)
    elif args.command == "doctor":
        cmd_doctor(args)
    elif args.command == "test":
        cmd_test(args)


if __name__ == "__main__":
    main()
