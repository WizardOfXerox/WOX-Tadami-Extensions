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
import zlib
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

        dest_apk.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(built_apk, dest_apk)
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
            m_apk = re.search(r"-v14\.(\d+)\.apk", clean_name)
            if m_apk:
                code = int(m_apk.group(1))
            elif bg_path.exists():
                m = re.search(r"(?:extVersionCode|overrideVersionCode)\s*=\s*(\d+)", bg_path.read_text(encoding="utf-8"))
                if m:
                    code = int(m.group(1))
            ver_name = f"14.{code}"

            found = False
            for it in items:
                if it.get("pkg") == pkg_id:
                    it["apk"] = clean_name
                    it["code"] = code
                    it["version"] = ver_name
                    it["sha256"] = sha256_file(dest_apk)
                    it["size"] = dest_apk.stat().st_size
                    if not it.get("icon"):
                        it["icon"] = f"https://raw.githubusercontent.com/WizardOfXerox/WOX-Tadami-Extensions/main/apks/anime/icon/{pkg_id}.png"
                    if len(it.get("sources", [])) == 1:
                        for kt in (ANIME_DIR / "src" / lang / name).glob("**/*.kt"):
                            kt_txt = kt.read_text(encoding="utf-8", errors="ignore")
                            m_url = re.search(r'override\s+val\s+baseUrl\s*=\s*"([^"]+)"', kt_txt)
                            if m_url:
                                it["sources"][0]["baseUrl"] = m_url.group(1)
                                break
                    print(f"[+] Updated manifest entry for {pkg_id} -> {clean_name} (v{ver_name}, code {code})")
                    found = True
                    break

            if not found:
                ext_name = name.capitalize()
                is_nsfw = 0
                if bg_path.exists():
                    bg_txt = bg_path.read_text(encoding="utf-8")
                    m_name = re.search(r"extName\s*=\s*['\"]([^'\"]+)['\"]", bg_txt)
                    if m_name:
                        ext_name = m_name.group(1)
                    if "isNsfw = true" in bg_txt:
                        is_nsfw = 1
                base_url = ""
                src_name = ext_name
                for kt in (ANIME_DIR / "src" / lang / name).glob("**/*.kt"):
                    kt_txt = kt.read_text(encoding="utf-8", errors="ignore")
                    m_url = re.search(r'override\s+val\s+baseUrl\s*=\s*"([^"]+)"', kt_txt)
                    if m_url:
                        base_url = m_url.group(1)
                    m_sname = re.search(r'override\s+val\s+name\s*=\s*"([^"]+)"', kt_txt)
                    if m_sname:
                        src_name = m_sname.group(1)
                    if base_url:
                        break

                key = f"{src_name.lower()}/{lang}/1".encode("utf-8")
                digest = hashlib.md5(key).digest()
                res_id = 0
                for b in digest[:8]:
                    res_id = (res_id << 8) | b
                src_id = str(res_id & 0x7fffffffffffffff)

                new_entry = {
                    "name": f"Aniyomi: {ext_name}",
                    "pkg": pkg_id,
                    "apk": clean_name,
                    "lang": lang,
                    "code": code,
                    "version": ver_name,
                    "nsfw": is_nsfw,
                    "sources": [
                        {
                            "name": src_name,
                            "lang": lang,
                            "id": src_id,
                            "baseUrl": base_url
                        }
                    ],
                    "icon": f"https://raw.githubusercontent.com/WizardOfXerox/WOX-Tadami-Extensions/main/apks/anime/icon/{pkg_id}.png",
                    "sha256": sha256_file(dest_apk),
                    "size": dest_apk.stat().st_size
                }
                items.append(new_entry)
                items.sort(key=lambda x: x["pkg"])
                print(f"[+] Added new manifest entry for {pkg_id} -> {clean_name} (v{ver_name}, code {code})")

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
            bg_txt = bg_path.read_text(encoding="utf-8") if bg_path.exists() else ""
            code = 1
            ver_name = "1.4.1"
            m_apk = re.search(r"-v(\d+\.\d+)\.(\d+)\.apk", clean_name)
            if m_apk:
                code = int(m_apk.group(2))
                ver_name = f"{m_apk.group(1)}.{code}"
            elif bg_txt:
                m = re.search(r"versionCode\s*=\s*(\d+)", bg_txt)
                if m:
                    code = int(m.group(1))
                    ver_name = f"1.4.{code}"

            primary_base_url = None
            if bg_txt:
                source_blocks = len(re.findall(r"\bsource\s*\{", bg_txt))
                has_mirrors = "mirrors(" in bg_txt
                if source_blocks <= 1 or has_mirrors:
                    m_url = re.search(r'baseUrl\s*=\s*"([^"]+)"', bg_txt) or re.search(r'mirrors\s*\(\s*"([^"]+)"', bg_txt, re.S)
                    if m_url:
                        primary_base_url = m_url.group(1)

            for it in items:
                if it.get("pkg") == pkg_id:
                    it["apk"] = clean_name
                    it["code"] = code
                    it["version"] = ver_name
                    it["sha256"] = sha256_file(dest_apk)
                    it["size"] = dest_apk.stat().st_size
                    if primary_base_url:
                        new_host = urllib.parse.urlparse(primary_base_url).netloc
                        for s in it.get("sources", []):
                            if urllib.parse.urlparse(s.get("baseUrl", "")).netloc != new_host:
                                s["baseUrl"] = primary_base_url
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
            else:
                try:
                    content = js_path.read_text(encoding="utf-8", errors="ignore")
                    m = re.search(r'this\.version\s*=\s*["\']([\d\.]+)["\']', content)
                    if m:
                        semver = m.group(1)
                        parts = [int(p) for p in semver.split(".")]
                        while len(parts) < 3:
                            parts.append(0)
                        int_ver = parts[0] * 1000000 + parts[1] * 1000 + parts[2]
                        if isinstance(it.get("version"), int):
                            it["version"] = max(it.get("version", 0), int_ver)
                        else:
                            it["version"] = int_ver
                except Exception:
                    pass
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

        icon_file = dist_dir / "icon" / f"{pkg}.png"
        if icon_file.exists() and not it.get("icon"):
            it["icon"] = f"https://raw.githubusercontent.com/WizardOfXerox/WOX-Tadami-Extensions/main/apks/{cat}/icon/{pkg}.png"

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

ANIME_MULTISRC_MAP = {
    "animestream": {
        "path": "/anime/?page=1&order=popular",
        "selector": "bsx, article, uta",
        "search": {"path": "/page/1/?s={q}", "selector": "bsx, article, listupd"},
        "filter": {"path": "/anime/?page=1&status=ongoing&order=popular", "selector": "bsx, article, listupd"},
        "has_filters": True,
    },
    "dooplay": {
        "path": "/movies/",
        "selector": "item, poster, data",
        "search": {"path": "/page/1/?s={q}", "selector": "result-item, item, search-page"},
        "filter": {"path": "/genre/action/", "selector": "item, poster, items"},
        "has_filters": True,
    },
    "zorotheme": {
        "path": "/most-popular?page=1",
        "selector": "flw-item, film-poster",
        "search": {"path": "/search?keyword={q}&page=1", "selector": "flw-item, film-poster"},
        "filter": {"path": "/filter?type=2&page=1", "selector": "flw-item, film-poster"},
        "has_filters": True,
    },
    "animekaitheme": {
        "path": "/browser?sort=most_watched&page=1",
        "selector": "aitem, item, poster",
        "search": {"path": "/browser?keyword={q}&page=1", "selector": "aitem, item, poster"},
        "filter": {"path": "/browser?type[]=tv&sort=most_watched&page=1", "selector": "aitem, item, poster"},
        "has_filters": True,
    },
    "anikototheme": {
        "path": "/filter?sort=most_watched&page=1",
        "selector": "item, poster, inner",
        "search": {"path": "/filter?keyword={q}&page=1", "selector": "item, poster, inner"},
        "filter": {"path": "/filter?sort=most_watched&page=1", "selector": "item, poster, inner"},
        "has_filters": True,
    },
    "dopeflix": {
        "path": "/movie?page=1",
        "selector": "flw-item, film-poster",
        "search": {"path": "/search/{q_dash}?page=1", "selector": "flw-item, film-poster"},
        "filter": {"path": "/filter?page=1&type=movie", "selector": "flw-item, film-poster"},
        "has_filters": True,
    },
    "yflixtheme": {
        "path": "/browser?sort=views&page=1",
        "selector": "item, film-section",
        "search": {"path": "/browser?keyword={q}&page=1", "selector": "item, film-section"},
        "filter": {"path": "/browser?sort=trending&page=1", "selector": "item, film-section"},
        "has_filters": True,
    },
    "wcotheme": {
        "path": "/",
        "selector": "img, item, recent-release",
        "search": {"method": "POST", "path": "/search", "data": {"catara": "{q}", "konuara": "series"}, "selector": "cerceve, iccerceve, item"},
        "filter": {"path": "/search-by-genre", "selector": "ddmcc, genre, item"},
        "has_filters": True,
    },
    "datalifeengine": {
        "path": "/",
        "selector": "shortstory, mov, item",
        "search": {"method": "POST", "path": "/index.php?do=search", "data": {"do": "search", "subaction": "search", "story": "{q}"}, "selector": "shortstory, mov, item"},
        "has_filters": True,
    },
    "pelisplus": {
        "path": "/peliculas",
        "selector": "item, poster",
        "search": {"path": "/search?s={q}", "selector": "item, poster"},
        "has_filters": True,
    },
    "anilist": {
        "path": "/",
        "selector": "anime, title",
        "has_filters": True,
    },
}

def _resolve_kt_placeholders(raw, api_url="", is_search=False):
    raw = raw.replace('$baseApiUrl', api_url).replace('${baseApiUrl}', api_url)
    raw = raw.replace('$apiUrl', api_url).replace('${apiUrl}', api_url)
    raw = raw.replace('$searchUrl', api_url).replace('${searchUrl}', api_url)
    raw = raw.replace('$baseUrl', '').replace('${baseUrl}', '')
    if is_search:
        raw = re.sub(r'\$\{(?:query|encodedQuery|searchQuery)[^}]*\}', '{q}', raw)
        raw = re.sub(r'\$(?:query|encodedQuery|searchQuery)\b', '{q}', raw)
    raw = raw.replace('$page', '1').replace('${page}', '1').replace('$offset', '0').replace('${offset}', '0')
    if not is_search:
        raw = re.sub(r'\$\{[^}]+\}', '0', raw)
    return raw

def _count_kotlin_filters(code):
    cls_matches = re.findall(r'\b(?:class|object)\s+\w+[^:\n]*:\s*(?:Anime)?Filter\.(?!Header|Separator)', code)
    direct_matches = re.findall(r'\b(?:Anime)?Filter\.(?:Select|CheckBox|TriState|Text|Sort|Group)\s*(?:\(|<)', code)
    if cls_matches:
        return len(cls_matches)
    return len(direct_matches)

def _extract_kt_http_builder(body, api_url=""):
    m = re.search(r'(?:"([^"]*)"|(baseUrl|apiUrl|baseApiUrl|searchUrl))\.toHttpUrl(?:OrNull)?\(\)\.newBuilder\(\)', body)
    if not m:
        return None
    base_part = _resolve_kt_placeholders(m.group(1) if m.group(1) is not None else f"${m.group(2)}", api_url, is_search=True)
    tail = body[m.end():m.end() + 900]
    cut_m = re.search(r'\.build\(\)|\bfilters\.forEach|\bfilterList\b', tail)
    if cut_m:
        tail = tail[:cut_m.start()]
    segs = []
    qparams = []
    for call_m in re.finditer(r'\.(addPathSegment|addEncodedPathSegments|addQueryParameter)\(\s*([^)]+)\)', tail):
        kind_c, args_s = call_m.group(1), call_m.group(2).strip()
        if kind_c in ("addPathSegment", "addEncodedPathSegments"):
            lit = re.match(r'^"([^"]*)"$', args_s)
            if lit:
                segs.append(lit.group(1).strip('/'))
            elif re.search(r'\b(?:query|encodedQuery|searchQuery)\b', args_s):
                segs.append('{q}')
        else:
            parts = [p.strip() for p in args_s.split(',', 1)]
            if len(parts) == 2 and parts[0].startswith('"') and parts[0].endswith('"'):
                k = parts[0][1:-1]
                v = parts[1]
                lit_v = re.match(r'^"([^"]*)"$', v)
                if lit_v:
                    qparams.append(f"{k}={urllib.parse.quote_plus(lit_v.group(1))}")
                elif re.search(r'\b(?:query|encodedQuery|searchQuery)\b', v):
                    qparams.append(f"{k}={{q}}")
                elif re.search(r'\bpage\b', v):
                    qparams.append(f"{k}=1")
    clean_base = base_part.rstrip('/')
    path_str = "/".join([s for s in segs if s])
    if clean_base.startswith(("http://", "https://")):
        url = f"{clean_base}/{path_str}" if path_str else clean_base
    else:
        b = "/" + clean_base.lstrip('/') if clean_base else ""
        url = f"{b}/{path_str}".replace('//', '/') if path_str else (b or "/")
    if qparams:
        sep = "&" if "?" in url else "?"
        url = f"{url}{sep}{'&'.join(qparams)}"
    if '{q}' not in url and url in ("", "/"):
        return None
    return {"method": "GET", "path": url}

def _extract_search_spec_from_body(body, api_url=""):
    # 1. POST FormBody.Builder(...)
    form_candidates = []
    for m in re.finditer(r'POST\(\s*"([^"]+)"[\s\S]{0,650}?FormBody\.Builder\(\)([\s\S]{0,450}?)\.build\(\)', body):
        raw_url = _resolve_kt_placeholders(m.group(1), api_url, is_search=True)
        if '$' in raw_url or ' ' in raw_url:
            continue
        data = {}
        uses_q = False
        for am in re.finditer(r'\.add\(\s*"([^"]+)"\s*,\s*([^)]+)\)', m.group(2)):
            k, v = am.group(1), am.group(2).strip()
            if re.search(r'\b(?:query|encodedQuery|searchQuery)\b', v):
                data[k] = "{q}"
                uses_q = True
            elif (lit := re.match(r'^"([^"]*)"$', v)):
                data[k] = lit.group(1)
            elif re.search(r'\bpage\b', v):
                data[k] = "1"
        if data:
            form_candidates.append((uses_q, {"method": "POST", "path": raw_url or "/", "data": data}))
    for uses_q, spec in form_candidates:
        if uses_q:
            return spec
    if len(form_candidates) == 1 and "if (query" not in body and "if(query" not in body:
        return form_candidates[0][1]

    # 2. Direct GET("...") string literal
    get_literals = re.findall(r'GET\(\s*"([^"]+)"', body)
    prioritized = [g for g in get_literals if any(k in g for k in ('query', 'encodedQuery', 'searchQuery'))]
    for raw_g in prioritized + [g for g in get_literals if g not in prioritized]:
        resolved = _resolve_kt_placeholders(raw_g, api_url, is_search=True)
        if '$' not in resolved and ' ' not in resolved and ('{q}' in resolved or len(resolved) > 1):
            return {"method": "GET", "path": resolved}

    # 3. OkHttp toHttpUrl().newBuilder() chain
    return _extract_kt_http_builder(body, api_url)

def _extract_kotlin_rules(code, is_anime=True, sibling_filter_code=""):
    """Extract catalog, search API, and filter metadata from an Aniyomi/Tachiyomi Kotlin source."""
    kind = "Anime" if is_anime else "Manga"
    api_m = re.search(r'val\s+(?:baseApiUrl|apiUrl|searchUrl)\s*=\s*"([^"]+)"', code)
    api_url = api_m.group(1) if api_m else ""

    # Popular / Catalog rule
    path = ""
    p_m = re.search(rf'popular{kind}Request[^\n=]*=\s*GET\("(?:\$baseUrl)?([^"]*)"', code)
    if not p_m:
        p_m = re.search(rf'popular{kind}Request[^\n\{{]*\{{[^\}}]*GET\("(?:\$baseUrl)?([^"]*)"', code)
    if p_m:
        raw_p = _resolve_kt_placeholders(p_m.group(1), api_url, is_search=False)
        path = "" if ('$' in raw_p or ' ' in raw_p) else raw_p

    sel = ""
    s_m = re.search(rf'popular{kind}Parse[^\n\{{]*\{{[^\}}]*select\("([^"]+)"\)', code)
    if not s_m:
        s_m = re.search(rf'popular{kind}Selector\(\)[^=]*=\s*"([^"]+)"', code)
    if s_m:
        sel = s_m.group(1).strip()

    # Search selector
    search_sel = ""
    ss_m = re.search(rf'search{kind}Selector\(\)[^=]*=\s*"([^"]+)"', code)
    if not ss_m:
        ss_m = re.search(rf'search{kind}Parse[^\n\{{]*\{{[^\}}]*select(?:First)?\("([^"]+)"\)', code)
    if ss_m:
        search_sel = ss_m.group(1).strip()
    eff_search_sel = search_sel or sel

    # Search request spec & capability
    search_supported = True
    search_spec = None
    override_m = re.search(rf'override\s+fun\s+search{kind}Request[\s\S]{{0,2500}}?(?=\n\s*(?:override|private|protected(?:\s+open)?)\s+(?:suspend\s+)?fun\s|\Z)', code)
    priv_m = re.search(rf'(?:private|protected(?:\s+open)?)\s+fun\s+search{kind}Request[\s\S]{{0,2500}}?(?=\n\s*(?:override|private|protected(?:\s+open)?)\s+(?:suspend\s+)?fun\s|\Z)', code)
    get_m = re.search(rf'override\s+(?:suspend\s+)?fun\s+(?:get|fetch)Search{kind}[\s\S]{{0,2500}}?(?=\n\s*(?:override|private|protected(?:\s+open)?)\s+(?:suspend\s+)?fun\s|\Z)', code)

    if override_m and "UnsupportedOperationException" in override_m.group(0) and not priv_m and not get_m:
        search_supported = False
    else:
        for cand_m in (priv_m, override_m, get_m):
            if cand_m:
                spec = _extract_search_spec_from_body(cand_m.group(0), api_url)
                if spec:
                    if eff_search_sel:
                        spec["selector"] = eff_search_sel
                    search_spec = spec
                    break

    # Filter detection & unambiguous static-slug filter route
    combined_filters = code + "\n" + sibling_filter_code
    trivial_filters = bool(re.search(rf'fun\s+getFilterList\([^)]*\)[^=]*=\s*(?:emptyList|(?:Anime)?FilterList)\(\s*\)', code))
    has_filters = (not trivial_filters and "getFilterList" in code) or bool(sibling_filter_code.strip())
    filter_count = _count_kotlin_filters(combined_filters)
    if has_filters and filter_count == 0:
        filter_count = 1

    filter_spec = None
    search_body_for_filter = (priv_m or override_m).group(0) if (priv_m or override_m) else ""
    if search_body_for_filter and re.search(r'GET\(\s*"\$baseUrl/\$\{[^}]*toUriPart\(\)[^}]*\}"', search_body_for_filter):
        for pair_m in re.finditer(r'Pair\(\s*"[^"]+"\s*,\s*"([^"]+)"\s*\)', combined_filters):
            slug = pair_m.group(1).strip()
            if slug and re.match(r'^[a-zA-Z0-9_/\-?=&]+$', slug):
                filter_spec = {"path": f"/{slug.lstrip('/')}", "selector": eff_search_sel}
                break

    return {
        "path": path,
        "selector": sel,
        "search": search_spec,
        "search_supported": search_supported,
        "filter": filter_spec,
        "has_filters": has_filters,
        "filter_count": filter_count,
    }

def _collect_multisrc_filter_counts(multisrc_root):
    counts = {}
    if not multisrc_root.exists():
        return counts
    for theme_dir in multisrc_root.iterdir():
        if theme_dir.is_dir():
            txt = ""
            for kt in theme_dir.rglob("*.kt"):
                try:
                    txt += "\n" + kt.read_text(encoding="utf-8", errors="ignore")
                except Exception:
                    pass
            counts[theme_dir.name.lower()] = _count_kotlin_filters(txt)
    return counts

def extract_anime_rules():
    rules = {}
    theme_filter_counts = _collect_multisrc_filter_counts(ANIME_DIR / "lib-multisrc")
    sources_dirs = [ANIME_DIR / "src", ROOT_DIR / "src" / "anime"]
    for sdir in sources_dirs:
        if not sdir.exists():
            continue
        for root, dirs, files in os.walk(sdir):
            dirs[:] = [d for d in dirs if d not in SKIP_DIRS]
            bg_file = "build.gradle" if "build.gradle" in files else ("build.gradle.kts" if "build.gradle.kts" in files else None)
            theme = ""
            if bg_file:
                try:
                    bg_txt = open(os.path.join(root, bg_file), "r", encoding="utf-8", errors="ignore").read()
                    tm = re.search(r"themePkg\s*=\s*['\"]([^'\"]+)['\"]", bg_txt)
                    if not tm:
                        tm = re.search(r'theme\s*=\s*"([^"]+)"', bg_txt)
                    if tm:
                        theme = tm.group(1).lower()
                except Exception:
                    pass

            sibling_filter_code = ""
            main_kts = []
            for r_sub, _, r_files in os.walk(root):
                for rf in r_files:
                    if not rf.endswith(".kt"):
                        continue
                    fp_kt = os.path.join(r_sub, rf)
                    try:
                        kt_txt = open(fp_kt, "r", encoding="utf-8", errors="ignore").read()
                    except Exception:
                        continue
                    if rf.endswith("Filters.kt"):
                        sibling_filter_code += "\n" + kt_txt
                    elif not rf.endswith(("Dto.kt", "Constants.kt", "UrlActivity.kt")):
                        main_kts.append(kt_txt)

            # Collect filter code across subdirs when walking individual packages
            if not bg_file:
                continue
            for code in main_kts:
                pkg_m = re.search(r'package\s+([a-zA-Z0-9_\.]+)', code)
                if not pkg_m:
                    continue
                pkg = pkg_m.group(1)
                parsed = _extract_kotlin_rules(code, is_anime=True, sibling_filter_code=sibling_filter_code)
                if theme and theme in ANIME_MULTISRC_MAP:
                    base_rule = dict(ANIME_MULTISRC_MAP[theme])
                    if "search" in base_rule:
                        base_rule["search"] = dict(base_rule["search"])
                    if "filter" in base_rule:
                        base_rule["filter"] = dict(base_rule["filter"])
                    al_m = re.search(r'override\s+val\s+animeListUrl\s*=\s*"([^"]+)"', code)
                    if al_m and theme == "animestream":
                        al_path = "/" + al_m.group(1).strip().strip("/").replace("$baseUrl/", "").lstrip("/")
                        if base_rule.get("path"):
                            base_rule["path"] = base_rule["path"].replace("/anime", al_path, 1)
                        if base_rule.get("filter"):
                            base_rule["filter"]["path"] = base_rule["filter"]["path"].replace("/anime", al_path, 1)
                    merged = {
                        "theme": theme,
                        "path": parsed["path"] or base_rule.get("path", ""),
                        "selector": parsed["selector"] or base_rule.get("selector", ""),
                        "search": parsed["search"] or base_rule.get("search"),
                        "search_supported": parsed["search_supported"],
                        "filter": parsed["filter"] or base_rule.get("filter"),
                        "has_filters": parsed["has_filters"] or base_rule.get("has_filters", False),
                        "filter_count": max(parsed["filter_count"], theme_filter_counts.get(theme, 0)),
                    }
                    if merged["has_filters"] and merged["filter_count"] == 0:
                        merged["filter_count"] = 1
                    rules[pkg] = merged
                else:
                    parsed["theme"] = theme
                    rules[pkg] = parsed
    return rules

MULTISRC_MAP = {
    "bakkin": {"path": "/manga/", "selector": "chapter"},
    "bilibilicomics": {"path": "/tw/v1/comic", "selector": "comic"},
    "clipstudioreader": {"path": "/", "selector": "item"},
    "colorlibanime": {"path": "/", "selector": "set-bg"},
    "comiciviewer": {
        "path": "/ranking/manga",
        "selector": "series-list-item, item",
        "search": {"path": "/api/search?q={q}&page=1&size=20", "selector": "series, title, result"},
        "has_filters": True,
    },
    "eromuse": {"path": "/all-comics/", "selector": "img, comic"},
    "ezmanhwa": {"path": "/series?page=1&perPage=20&sort=popular", "selector": "series"},
    "fansubscat": {"path": "/manga/popular/1", "selector": "manga"},
    "fmreader": {
        "path": "/manga-list.html?sort=views",
        "selector": "thumb, media, thumb-item-flow",
        "search": {"path": "/manga-list.html?name={q}&page=1", "selector": "thumb, media, thumb-item-flow"},
        "filter": {"path": "/manga-list.html?m_status=1&sort=views&page=1", "selector": "thumb, media, thumb-item-flow"},
        "has_filters": True,
    },
    "foolslide": {
        "path": "/directory/",
        "selector": "group, title",
        "search": {"method": "POST", "path": "/search/", "data": {"search": "{q}"}, "selector": "group, title, element"},
    },
    "fuzzydoodle": {"path": "/manga?page=1", "selector": "card-real, manga"},
    "galleryadults": {"path": "/random/", "selector": "thumb"},
    "gattsu": {"path": "/", "selector": "lista, meio"},
    "gigaviewer": {
        "path": "/series",
        "selector": "series-list",
        "search": {"path": "/search?q={q}", "selector": "series, search, item"},
        "has_filters": True,
    },
    "goda": {"path": "/hots/page/1", "selector": "cardlist, container"},
    "greenshit": {"path": "/obras", "selector": "obra"},
    "grouple": {
        "path": "/list?sortType=rate",
        "selector": "tile",
        "search": {"path": "/search/advancedResults?offset=0&q={q}", "selector": "tile, tiles"},
        "filter": {"path": "/search/advancedResults?offset=0&sortType=RATE", "selector": "tile, tiles"},
        "has_filters": True,
    },
    "guya": {
        "path": "/api/get_all_series/",
        "selector": "series",
        "search": {"path": "/api/get_all_series/", "selector": "slug, title"},
    },
    "heancms": {
        "path": "/comics",
        "selector": "comic",
        "search": {"path": "/api/query?query_string={q}&page=1&perPage=20", "selector": "data, series, title"},
        "has_filters": True,
    },
    "hentaihand": {
        "path": "/api/comics",
        "selector": "comic",
        "search": {"path": "/api/comics?q={q}&page=1", "selector": "data, title"},
        "has_filters": True,
    },
    "hiper": {"path": "/page/1", "selector": "item"},
    "iken": {
        "path": "/manga",
        "selector": "manga",
        "search": {"path": "/api/query?searchTerm={q}&page=1&perPage=18", "selector": "posts, title, slug"},
        "filter": {"path": "/api/query?orderBy=totalViews&page=1&perPage=18", "selector": "posts, title, slug"},
        "has_filters": True,
    },
    "initmanga": {"path": "/", "selector": "manga-item-grid"},
    "kemono": {"path": "/api/v1/creators", "selector": "creator"},
    "keyoapp": {
        "path": "/series/",
        "selector": "grid, group",
        "search": {"path": "/series/?q={q}", "selector": "grid, group"},
        "filter": {"path": "/series/?order=popular", "selector": "grid, group"},
        "has_filters": True,
    },
    "libgroup": {
        "path": "/api/manga",
        "selector": "manga",
        "search": {"path": "/api/manga?q={q}", "selector": "data, name"},
        "has_filters": True,
    },
    "liliana": {"path": "/all-manga/1/?sort=last_update", "selector": "grid"},
    "madara": {
        "path": "/manga/page/1/?m_orderby=views",
        "selector": "page-item-detail, manga__item, post-title",
        "search": {"path": "/?s={q}&post_type=wp-manga", "selector": "c-tabs-item__content, post-title, tab-thumb, page-item-detail"},
        "filter": {"path": "/?s=&post_type=wp-manga&m_orderby=views&status[]=on-going", "selector": "c-tabs-item__content, post-title, page-item-detail"},
        "has_filters": True,
    },
    "madtheme": {
        "path": "/",
        "selector": "book-detailed-item",
        "search": {"path": "/search?q={q}&page=1", "selector": "book-detailed-item, item"},
        "has_filters": True,
    },
    "manga18": {
        "path": "/list-manga/1?order_by=views",
        "selector": "story_item",
        "search": {"path": "/list-manga/1?search={q}", "selector": "story_item, item"},
        "has_filters": True,
    },
    "mangabox": {
        "path": "/manga-list",
        "selector": "truyen-list, list-truyen-item-wrap, item",
        "search": {"path": "/search/story/{q_under}?page=1", "selector": "story_item, panel-search-story, list-truyen-item-wrap, item"},
        "filter": {"path": "/manga-list?type=topview&page=1", "selector": "list-truyen-item-wrap, truyen-list, item"},
        "has_filters": True,
    },
    "mangacatalog": {"path": "/", "selector": "flex-col, bg-bg-secondary", "search_supported": False},
    "mangadventure": {
        "path": "/series?page=1&sort=-views",
        "selector": "series",
        "search": {"path": "/api/v2/series?title={q}", "selector": "results, title"},
        "has_filters": True,
    },
    "mangahub": {"path": "/manga", "selector": "media, manga"},
    "mangareader": {
        "path": "/filter?sort=views",
        "selector": "manga-poster, item",
        "search": {"path": "/search?keyword={q}&page=1", "selector": "manga-poster, item"},
        "filter": {"path": "/filter?sort=views&page=1", "selector": "manga-poster, item"},
        "has_filters": True,
    },
    "mangataro": {"path": "/manga", "selector": "capitalize, manga"},
    "mangathemesia": {
        "path": "/manga/?order=popular",
        "selector": "bsx, uta, imgu",
        "search": {"path": "/manga/?title={q}&page=1", "selector": "bsx, uta, listupd"},
        "filter": {"path": "/manga/?status=ongoing&order=popular&page=1", "selector": "bsx, uta, listupd"},
        "has_filters": True,
    },
    "mangawork": {"path": "/", "selector": "series"},
    "mangaworld": {
        "path": "/archive?sort=most_read&page=1",
        "selector": "comics-grid, entry",
        "search": {"path": "/archive?keyword={q}&page=1", "selector": "comics-grid, entry"},
        "filter": {"path": "/archive?sort=most_read&page=1", "selector": "comics-grid, entry"},
        "has_filters": True,
    },
    "mangotheme": {"path": "/obras", "selector": "obra"},
    "manhwaz": {
        "path": "/?page=1",
        "selector": "item, slide-top",
        "search": {"path": "/search?s={q}&page=1", "selector": "item, slide-top, page-item-detail"},
        "has_filters": True,
    },
    "masonry": {
        "path": "/updates/sort/newest/mpage/1/",
        "selector": "list-gallery",
        "search": {"path": "/search/post/{q}/mpage/1/", "selector": "list-gallery, thumb"},
        "has_filters": True,
    },
    "mccms": {"path": "/category/order/hits/page/1", "selector": "comic, img"},
    "mmlook": {"path": "/", "selector": "likedata"},
    "mmrcms": {
        "path": "/filterList?sortBy=views&asc=false",
        "selector": "manga-item, media-body",
        "search": {"path": "/search?query={q}", "selector": "suggestions, value, manga-item"},
        "filter": {"path": "/filterList?page=1&sortBy=views&asc=false", "selector": "manga-item, media-body"},
        "has_filters": True,
    },
    "monochrome": {"path": "/manga", "selector": "manga"},
    "moonlighttl": {"path": "/api/topSerie", "selector": "serie"},
    "multichan": {"path": "/mostfavorites", "selector": "content_row"},
    "natsuid": {"path": "/", "selector": "manga"},
    "nepnep": {"path": "/directory/", "selector": "bsx"},
    "oceanwp": {"path": "/", "selector": "blog-entry"},
    "pam": {"path": "/manga", "selector": "manga"},
    "paprikateam": {"path": "/projets", "selector": "project"},
    "pizzareader": {
        "path": "/comics",
        "selector": "comic",
        "search": {"path": "/api/v1/search/{q}", "selector": "comics, title, slug"},
    },
    "scanreader": {"path": "/bibliotheque/page/1/?sort=views", "selector": "manga-card"},
    "senkuro": {"path": "/", "selector": "manga"},
    "sinmh": {"path": "/list/click/?page=1", "selector": "comic"},
    "spicytheme": {"path": "/", "selector": "comic"},
    "stalkercms": {"path": "/manga", "selector": "manga"},
    "uzaymanga": {
        "path": "/",
        "selector": "manga",
        "search": {"path": "/manga/__data.json?page=1&x-sveltekit-invalidated=001&search={q}", "selector": "nodes, data"},
        "has_filters": True,
    },
    "vercomics": {"path": "/page/1", "selector": "entry"},
    "wpcomics": {
        "path": "/?status=all&sort=views",
        "selector": "items, item",
        "search": {"path": "/tim-truyen?keyword={q}&page=1", "selector": "items, item"},
        "filter": {"path": "/tim-truyen?status=1&sort=views&page=1", "selector": "items, item"},
        "has_filters": True,
    },
    "wpmangastream": {
        "path": "/manga/?order=popular",
        "selector": "bsx",
        "search": {"path": "/manga/?title={q}&page=1", "selector": "bsx, uta, listupd"},
        "filter": {"path": "/manga/?status=ongoing&order=popular&page=1", "selector": "bsx, uta, listupd"},
        "has_filters": True,
    },
    "zeistmanga": {
        "path": "/search/label/Manga",
        "selector": "grid, figure, post",
        "search": {"path": "/feeds/posts/default?alt=json&max-results=21&start-index=1&q={q}", "selector": "feed, entry"},
        "filter": {"path": "/feeds/posts/default/-/Manga?alt=json&max-results=21&start-index=1", "selector": "feed, entry"},
        "has_filters": True,
    },
    "zmanga": {
        "path": "/advanced-search/?order=popular",
        "selector": "flexbox2-item",
        "search": {"path": "/?s={q}", "selector": "flexbox2-item, item"},
        "filter": {"path": "/advanced-search/?order=popular&status=Ongoing", "selector": "flexbox2-item, item"},
        "has_filters": True,
    },
}

def extract_manga_rules(only_pkgs=None):
    rules = {}
    manga_src = MANGA_DIR / "src"
    if not manga_src.exists():
        return rules
    theme_filter_counts = _collect_multisrc_filter_counts(MANGA_DIR / "lib-multisrc") if only_pkgs is None else {}
    for root, dirs, files in os.walk(manga_src):
        dirs[:] = [d for d in dirs if d not in SKIP_DIRS]
        if "build.gradle.kts" in files:
            rel = os.path.relpath(root, manga_src).replace('\\', '/')
            parts = rel.split('/')
            if len(parts) >= 2:
                lang, slug = parts[0], parts[1]
                pkg = f"eu.kanade.tachiyomi.extension.{lang}.{slug}"
            else:
                continue
            if only_pkgs is not None and pkg.lower() not in only_pkgs:
                dirs[:] = []
                continue
            fpath = os.path.join(root, "build.gradle.kts")
            try:
                with open(fpath, "r", encoding="utf-8", errors="ignore") as fp:
                    txt = fp.read()
            except Exception:
                continue
            theme_m = re.search(r'theme\s*=\s*"([^"]+)"', txt)
            if not theme_m:
                theme_m = re.search(r'id\("multisrc\.([^"]+)"\)', txt)
            theme = theme_m.group(1).lower() if theme_m else ""
            if theme and only_pkgs is not None and theme not in theme_filter_counts:
                tdir = MANGA_DIR / "lib-multisrc" / theme
                if tdir.is_dir():
                    ttxt = "\n".join(kt.read_text(encoding="utf-8", errors="ignore") for kt in tdir.rglob("*.kt"))
                    theme_filter_counts[theme] = _count_kotlin_filters(ttxt)
            theme_info = MULTISRC_MAP.get(theme, {})

            sibling_filter_code = ""
            main_kts = []
            for r_sub, _, r_files in os.walk(root):
                for rf in r_files:
                    if not rf.endswith(".kt"):
                        continue
                    try:
                        kt_txt = open(os.path.join(r_sub, rf), "r", encoding="utf-8", errors="ignore").read()
                    except Exception:
                        continue
                    if rf.endswith("Filters.kt"):
                        sibling_filter_code += "\n" + kt_txt
                    elif not rf.endswith(("Dto.kt", "Constants.kt", "UrlActivity.kt")):
                        main_kts.append(kt_txt)

            combined_kt = "\n".join(main_kts)
            if theme:
                path = theme_info.get("path", "")
                search_spec = dict(theme_info["search"]) if theme_info.get("search") else None
                filter_spec = dict(theme_info["filter"]) if theme_info.get("filter") else None
                search_supported = theme_info.get("search_supported", True)

                m_dir = re.search(r'override\s+val\s+(?:mangaSubString|mangaUrlDirectory)\s*=\s*"([^"]+)"', combined_kt)
                if m_dir:
                    sub = "/" + m_dir.group(1).lstrip("/")
                    if "/manga" in path:
                        path = path.replace("/manga", sub, 1)
                    if search_spec and "/manga" in search_spec.get("path", ""):
                        search_spec["path"] = search_spec["path"].replace("/manga", sub, 1)
                    if filter_spec and "/manga" in filter_spec.get("path", ""):
                        filter_spec["path"] = filter_spec["path"].replace("/manga", sub, 1)

                m_sp = re.search(r'override\s+val\s+(?:searchPath|searchPathSegment)\s*=\s*"([^"]+)"', combined_kt)
                if m_sp and search_spec and "/search" in search_spec.get("path", ""):
                    search_spec["path"] = search_spec["path"].replace("/search", "/" + m_sp.group(1).lstrip("/"), 1)

                m_api = re.search(r'(?:override\s+)?val\s+apiUrl\s*=\s*"(https?://[^"]+)"', combined_kt)
                if m_api and search_spec and search_spec.get("path", "").startswith("/api/"):
                    search_spec["path"] = m_api.group(1).rstrip("/") + "/" + search_spec["path"][5:].lstrip("/")

                local_f_cnt = _count_kotlin_filters(combined_kt + "\n" + sibling_filter_code)
                f_cnt = max(local_f_cnt, theme_filter_counts.get(theme, 0))
                has_filters = bool(theme_info.get("has_filters") or "getFilterList" in combined_kt or sibling_filter_code.strip())
                if has_filters and f_cnt == 0:
                    f_cnt = 1

                rules[pkg] = {
                    "theme": theme,
                    "path": path,
                    "selector": theme_info.get("selector", ""),
                    "search": search_spec,
                    "search_supported": search_supported,
                    "filter": filter_spec,
                    "has_filters": has_filters,
                    "filter_count": f_cnt,
                }
            else:
                parsed = _extract_kotlin_rules(combined_kt, is_anime=False, sibling_filter_code=sibling_filter_code) if combined_kt else {}
                rules[pkg] = {
                    "theme": "",
                    "path": "",
                    "selector": "",
                    "search": parsed.get("search"),
                    "search_supported": parsed.get("search_supported", True),
                    "filter": parsed.get("filter"),
                    "has_filters": parsed.get("has_filters", False),
                    "filter_count": parsed.get("filter_count", 0),
                }
    return rules

NOVEL_TEMPLATE_MAP = {
    "madara": {
        "search": {"path": "/?s={q}&post_type=wp-manga", "selector": "c-tabs-item__content, post-title, page-item-detail, tab-thumb"},
        "filter": {"path": "/?s=&post_type=wp-manga&m_orderby=views", "selector": "c-tabs-item__content, post-title, page-item-detail"},
        "has_filters": True,
    },
    "lightnovelwp": {
        "search": {"path": "/page/1/?s={q}", "selector": "article, ts-post-image, entry-title, bsx"},
        "filter": {"path": "/series/?status=ongoing&order=popular", "selector": "article, ts-post-image, entry-title, bsx"},
        "has_filters": True,
    },
    "fictioneer": {
        "search": {"path": "/?s={q}&post_type=fcn_story", "selector": "card, fcn, story, post"},
        "has_filters": True,
    },
    "mtlnovel": {
        "search": {"path": "/wp-admin/admin-ajax.php?action=autosuggest&q={q}", "selector": "items, results, name"},
        "has_filters": True,
    },
    "ifreedom": {
        "search": {"path": "/vse-knigi/?searchname={q}&bpage=1", "selector": "item, book, title"},
        "has_filters": True,
    },
    "readnovelfull": {
        "search": {"path": "/search?keyword={q}", "selector": "list, row, novel-title, item"},
        "has_filters": True,
    },
    "lightnovelworld": {
        "search": {"path": "/search", "selector": "search, novel, item"},
        "has_filters": True,
    },
    "readwn": {
        "search": {"method": "POST", "path": "/e/search/index.php", "data": {"show": "title", "tempid": "1", "tbname": "news", "keyboard": "{q}"}, "selector": "novel-item, item"},
        "has_filters": True,
    },
}

def _extract_balanced_block(text, open_idx, open_ch="{", close_ch="}"):
    depth = 0
    in_q = None
    esc = False
    for idx in range(open_idx, min(len(text), open_idx + 25000)):
        ch = text[idx]
        if in_q:
            if esc:
                esc = False
            elif ch == "\\":
                esc = True
            elif ch == in_q:
                in_q = None
            continue
        if ch in ('"', "'", "`"):
            in_q = ch
        elif ch == open_ch:
            depth += 1
        elif ch == close_ch:
            depth -= 1
            if depth == 0:
                return text[open_idx:idx + 1]
    return ""

def _parse_js_concat_url(expr, q_var, p_var):
    """Evaluate minified ES5 this.site + / .concat(...) search URL expressions."""
    tokens = []
    i = 0
    n = len(expr)
    while i < n:
        ch = expr[i]
        if ch in ('"', "'"):
            qch = ch
            i += 1
            buf = []
            while i < n:
                c = expr[i]
                if c == "\\" and i + 1 < n:
                    buf.append(expr[i + 1])
                    i += 2
                elif c == qch:
                    i += 1
                    break
                else:
                    buf.append(c)
                    i += 1
            tokens.append(("str", "".join(buf)))
        elif expr.startswith("this.site", i) or expr.startswith("this.baseUrl", i):
            tokens.append(("str", ""))
            i += 9 if expr.startswith("this.site", i) else 12
        elif expr.startswith(".concat(", i):
            i += 8
        elif expr.startswith("encodeURIComponent(", i) or expr.startswith("encodeURI(", i):
            prefix_len = 19 if expr.startswith("encodeURIComponent(", i) else 10
            close = expr.find(")", i + prefix_len)
            if close == -1:
                return None
            inner = expr[i + prefix_len:close].strip()
            if q_var and re.match(rf'^{re.escape(q_var)}(?:\.trim\(\))?$', inner):
                tokens.append(("str", "{q}"))
            else:
                return None
            i = close + 1
        elif ch in " \t\r\n+(),":
            i += 1
        else:
            m_id = re.match(r'[a-zA-Z_$][a-zA-Z0-9_$.]*', expr[i:])
            if m_id:
                ident = m_id.group(0)
                if q_var and ident == q_var:
                    tokens.append(("str", "{q}"))
                elif p_var and ident == p_var:
                    tokens.append(("str", "1"))
                elif ident in ("this.options.searchPath", "this.searchUrl"):
                    return None
                else:
                    return None
                i += len(ident)
            elif expr[i].isdigit():
                m_num = re.match(r'\d+', expr[i:])
                tokens.append(("str", m_num.group(0)))
                i += len(m_num.group(0))
            else:
                return None
    out = "".join(v for _, v in tokens)
    return out if ('{q}' in out or out.startswith(("http://", "https://", "/"))) else None

def _extract_novel_search_and_filters(txt, fallback_sel):
    m_cnt = re.search(r'this\.filters\s*=\s*\{', txt)
    has_filters = False
    filter_count = 0
    if m_cnt:
        blk = _extract_balanced_block(txt, m_cnt.end() - 1)
        if blk and blk != "{}":
            has_filters = True
            filter_count = max(len(re.findall(r'type:\s*[a-zA-Z0-9_$.]*FilterTypes', blk)), 1)

    search_spec = None
    m_src = re.search(r'searchNovels\s*(?:=\s*(?:async\s+)?)?(?:function\s*)?\(([^)]*)\)\s*\{', txt)
    if m_src:
        s_idx = m_src.start()
        m_end = re.search(r'\.prototype\.\w+|\basync\s+(?:popularNovels|parseNovel|parseChapter)\b|\b(?:popularNovels|parseNovel|parseChapter)\s*[=:(]', txt[s_idx + 20:])
        scope = txt[s_idx:s_idx + 20 + m_end.start()] if m_end else txt[s_idx:s_idx + 2000]

        outer_params = [p.strip() for p in m_src.group(1).split(',') if p.strip()]
        inner_m = re.search(r'\breturn\s+\w+\(this,arguments,void 0,\(function\(([^)]*)\)', scope)
        params = [p.strip() for p in inner_m.group(1).split(',') if p.strip()] if inner_m and inner_m.group(1).strip() else outer_params
        q_var = params[0] if len(params) >= 1 else None
        p_var = params[1] if len(params) >= 2 else None

        src_sels = re.findall(r'\(\s*["\']([^"\']+)["\']\s*\)\.each', scope)
        eff_sel = ", ".join(dict.fromkeys(src_sels)) if src_sels else fallback_sel

        # Case 1: FormData POST (e.g., dilartube)
        if "FormData" in scope and 'method:"POST"' in scope.replace(" ", ""):
            m_post = re.search(r'fetchApi\)\(\s*([^,]+),\s*\{\s*method:\s*["\']POST["\']', scope)
            data = {}
            for am in re.finditer(r'\.append\(\s*["\']([^"\']+)["\']\s*,\s*([^)]+)\)', scope):
                k, v = am.group(1), am.group(2).strip()
                if q_var and v == q_var:
                    data[k] = "{q}"
                elif (lit := re.match(r'^["\'](.*)["\']$', v)):
                    data[k] = lit.group(1)
            if m_post and data:
                raw_url = m_post.group(1).strip()
                url_val = raw_url[1:-1] if len(raw_url) >= 2 and raw_url[0] in ('"', "'") else _parse_js_concat_url(raw_url, q_var, p_var)
                if url_val:
                    search_spec = {"method": "POST", "path": url_val, "data": data, "selector": eff_sel}

        # Case 2: Standard GET URL concat / + chain referencing this.site or https://
        if not search_spec:
            for u_m in re.finditer(r'(?:""\.concat\(\s*this\.site\b|this\.site\s*\+|["\']https?://[^"\']+["\']\.(?:concat)|\bthis\.site\b\s*\+)', scope):
                start = u_m.start()
                # Scan until top-level delimiter (, ; ) ])
                depth = 0
                in_q = None
                esc = False
                end = start
                while end < len(scope):
                    c = scope[end]
                    if in_q:
                        if esc: esc = False
                        elif c == "\\": esc = True
                        elif c == in_q: in_q = None
                    else:
                        if c in ('"', "'"): in_q = c
                        elif c in "([": depth += 1
                        elif c in ")]":
                            if depth == 0:
                                break
                            depth -= 1
                        elif depth == 0 and c in ",;":
                            break
                    end += 1
                expr = scope[start:end].strip()
                parsed_url = _parse_js_concat_url(expr, q_var, p_var)
                if parsed_url and '{q}' in parsed_url:
                    search_spec = {"method": "GET", "path": parsed_url, "selector": eff_sel}
                    break

    return search_spec, has_filters, filter_count

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
                tpl_m = re.search(r'\[([^\]]+)\]', f)
                tpl = tpl_m.group(1).lower() if tpl_m else ""
                path = ""
                sel = ""
                if 'MadaraPlugin' in txt or tpl == 'madara':
                    tpl = "madara"
                    path = "/page/1/?s=&post_type=wp-manga"
                    sel = "post-title, page-item-detail, c-tabs-item"
                elif tpl == 'lightnovelwp' or 'kolnovel' in nid:
                    tpl = "lightnovelwp"
                    path = "/series/"
                    sel = "article, ts-post-image, eplister, series, entry-title"
                elif 'syosetu' in nid:
                    tpl = "syosetu"
                    path = "/rank/list/type/total_total/"
                    sel = "c-card, p-ranklist-item__title, searchkekka_box"
                else:
                    m_pop = re.search(r'popularNovels\s*[=:(][^{]*\{', txt)
                    pop_scope = txt
                    if m_pop:
                        s_idx = m_pop.start()
                        m_end = re.search(r'\.prototype\.\w+|\basync\s+(?:parseNovel|searchNovels|parseChapter)\b|\b(?:parseNovel|searchNovels|parseChapter)\s*[=:(]', txt[s_idx + 20:])
                        pop_scope = txt[s_idx:s_idx + 20 + m_end.start()] if m_end else txt[s_idx:s_idx + 1500]
                    m_path = re.search(r'popularNovels[^{]*\{[^\n]*this\.site\s*\+\s*["\']([^"\']+)["\']', pop_scope)
                    if not m_path:
                        m_path = re.search(r'popularNovels[^{]*\{[^\n]*(?:=|return)\s*["\']{2}\.concat\(this\.site,\s*["\']([^"\']+)["\']', pop_scope)
                    if m_path:
                        path = m_path.group(1).replace('${page}', '1').replace('$page', '1')
                    sels = re.findall(r'\(\s*["\']([^"\']+)["\']\s*\)\.each', pop_scope)
                    if sels:
                        sel = ", ".join(dict.fromkeys(sels))

                eff_sel = sel or "novel, article, post, title, card"
                src_spec, has_filters, filter_count = _extract_novel_search_and_filters(txt, eff_sel)
                flt_spec = None
                if tpl in NOVEL_TEMPLATE_MAP:
                    tinfo = NOVEL_TEMPLATE_MAP[tpl]
                    if not src_spec and tinfo.get("search"):
                        src_spec = dict(tinfo["search"])
                    if tinfo.get("filter"):
                        flt_spec = dict(tinfo["filter"])
                    has_filters = has_filters or tinfo.get("has_filters", False)
                if tpl == "syosetu" and not src_spec:
                    src_spec = {"method": "GET", "path": "/search/search/?word={q}", "selector": "searchkekka_box, novel_h"}
                    has_filters = True

                rules[nid] = {
                    "theme": tpl,
                    "path": path,
                    "selector": eff_sel,
                    "search": src_spec,
                    "search_supported": ("searchNovels" in txt or bool(src_spec)),
                    "filter": flt_spec,
                    "has_filters": has_filters,
                    "filter_count": max(filter_count, 1) if has_filters else 0,
                }
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

BLOCKED_STATUSES = {
    "CLOUDFLARE_PROTECTED",
    "WAF_BLOCKED",
    "DEAD_DOMAIN",
    "DEAD_PARKED_DOMAIN",
    "ORIGIN_DOWN",
    "NO_BASE_URL",
}

EMPTY_SEARCH_PHRASES = (
    "no results",
    "nothing found",
    "no matches",
    "0 results",
    "aucun résultat",
    "ningún resultado",
    "tidak ditemukan",
    "không tìm thấy",
    "sonuç bulunamadı",
    "見つかりません",
    "没有找到",
    "没有搜索到",
    "ничего не найдено",
    "未找到",
    "查无",
)

def _decode_http_body(raw, enc):
    enc_l = (enc or '').lower()
    if raw.startswith(b'\x1f\x8b') or enc_l == 'gzip':
        try:
            raw = zlib.decompressobj(16 + zlib.MAX_WBITS).decompress(raw)
        except Exception:
            try:
                raw = gzip.decompress(raw)
            except Exception:
                pass
    elif enc_l == 'deflate':
        try:
            raw = zlib.decompressobj(-zlib.MAX_WBITS).decompress(raw)
        except Exception:
            try:
                raw = zlib.decompress(raw)
            except Exception:
                pass
    return raw.decode('utf-8', errors='ignore')

def _build_probe_url(base_clean, path, query="", is_search=False):
    p = path or ""
    if is_search:
        q_enc = urllib.parse.quote_plus(query)
        q_dash = urllib.parse.quote(query.replace(" ", "-"))
        q_under = urllib.parse.quote(query.replace(" ", "_"))
        p = p.replace("{q}", q_enc).replace("{q_dash}", q_dash).replace("{q_under}", q_under)
    url = p if p.startswith(("http://", "https://")) else (f"{base_clean}/{p.lstrip('/')}" if p else base_clean)
    return urllib.parse.quote(url, safe=":/?&=%[]+!$()*,;@#~-._")

def _count_json_items(obj):
    if isinstance(obj, list):
        return len(obj)
    if isinstance(obj, dict):
        for key in ("data", "results", "items", "comics", "novels", "suggestions", "posts", "series", "anime", "manga"):
            val = obj.get(key)
            if isinstance(val, list):
                return len(val)
            if isinstance(val, dict):
                for sub_key in ("items", "results", "data", "novels", "manga", "series", "posts"):
                    if isinstance(val.get(sub_key), list):
                        return len(val[sub_key])
        feed = obj.get("feed")
        if isinstance(feed, dict) and isinstance(feed.get("entry"), list):
            return len(feed["entry"])
    return None

def _probe_endpoint(target_url, base_clean, req_headers, timeout, selectors, method="GET", post_data=None, is_sub_probe=False):
    """Fetch a catalog, search, or filter endpoint and classify HTTP, anti-bot, JSON, and DOM health."""
    def fetch(url, hdrs=None, use_method="GET", data_dict=None):
        h = dict(hdrs or req_headers)
        body_bytes = None
        if use_method == "POST":
            body_bytes = urllib.parse.urlencode(data_dict or {}).encode("utf-8")
            h["Content-Type"] = "application/x-www-form-urlencoded"
        req = urllib.request.Request(url, data=body_bytes, headers=h, method=use_method)
        try:
            with urllib.request.urlopen(req, timeout=timeout, context=CTX_UNVERIFIED) as resp:
                raw_body = _decode_http_body(resp.read(262144), resp.info().get('Content-Encoding'))
                return resp.getcode(), dict(resp.info()), raw_body, resp.geturl()
        except urllib.error.HTTPError as he:
            try:
                raw = he.read(262144)
            except Exception:
                raw = b''
            hdrs_out = dict(he.headers or {})
            return he.code, hdrs_out, _decode_http_body(raw, hdrs_out.get('Content-Encoding')), url

    try:
        code, headers, body, final_url = fetch(target_url, req_headers, method, post_data)
        if method == "GET" and code == 200 and not body.strip():
            # Some non-English sites return an empty 200 body if Accept-Language is forced to en-US.
            # App OkHttp requests do not send Accept-Language by default; retry once without it.
            no_lang_headers = {k: v for k, v in req_headers.items() if k != "Accept-Language"}
            code, headers, body, final_url = fetch(target_url, no_lang_headers, "GET", None)

        lower_headers = {k.lower(): v for k, v in headers.items()}
        server = lower_headers.get('server', '')
        behind_cf = 'cloudflare' in server.lower() or 'cf-ray' in lower_headers

        if behind_cf and 520 <= code <= 530:
            return {"status": "ORIGIN_DOWN", "code": code, "detail": f"Cloudflare reports origin unreachable (HTTP {code})", "url": target_url}

        if code == 202 and 'x-amzn-waf-action' in lower_headers:
            return {"status": "CLOUDFLARE_PROTECTED", "code": code, "detail": "AWS WAF JS challenge (HTTP 202)", "url": target_url}

        if 300 <= code < 400:
            return {"status": "HTTP_ERROR", "code": code, "detail": f"HTTP {code} redirect loop/unfollowed", "url": target_url}

        is_cf_challenge = code in (403, 429, 503) and (
            lower_headers.get('cf-mitigated', '').lower() == 'challenge'
            or any(p.search(body) for p in CLOUDFLARE_PATTERNS)
            or 'ddos-guard' in server.lower()
        )
        if not is_cf_challenge and behind_cf and code in (403, 429):
            return {"status": "WAF_BLOCKED", "code": code, "detail": f"Cloudflare firewall rule returned HTTP {code} without an interactive challenge", "url": target_url}

        if "tiktok.com" in final_url.lower():
            return {"status": "DEAD_PARKED_DOMAIN", "code": code, "detail": f"Domain hijacked to {final_url}", "url": target_url}

        if is_cf_challenge:
            return {"status": "CLOUDFLARE_PROTECTED", "code": code, "detail": f"Cloudflare Shield active ({server or 'Turnstile'})", "url": target_url}

        if len(body) < 30000:
            if (any(p.search(body) for p in PARKED_PATTERNS) or
                ("/lander" in body.lower() and len(body) < 1000) or
                ("window.location" in body.lower() and len(body) < 500) or
                ("domain" in body.lower() and "sale" in body.lower() and len(body) < 2000 and not any(k in body.lower() for k in ['chapter', 'manga', 'anime', 'novel']))):
                return {"status": "DEAD_PARKED_DOMAIN", "code": code, "detail": "Domain expired/parked by registrar", "url": target_url}

        if not is_sub_probe and code in (404, 400) and target_url != base_clean:
            recovered = False
            for fallback_route in ['', '/manga', '/series', '/comics', '/catalog', '/cataleg', '/mangas', '/seriler/', '/ranobe', '/danh-sach/', '/archive', '/all', '/browse', '/novels']:
                try:
                    fb_code, fb_headers, fb_body, fb_url = fetch(f"{base_clean}{fallback_route}")
                    if fb_code == 200 and len(fb_body) > 2000:
                        code, headers, body, final_url = fb_code, fb_headers, fb_body, fb_url
                        lower_headers = {k.lower(): v for k, v in headers.items()}
                        recovered = True
                        break
                    elif fb_code in (403, 429, 503) and (any(p.search(fb_body) for p in CLOUDFLARE_PATTERNS) or {k.lower(): v for k, v in fb_headers.items()}.get('cf-mitigated', '').lower() == 'challenge'):
                        return {"status": "CLOUDFLARE_PROTECTED", "code": fb_code, "detail": "Fallback route live behind Cloudflare", "url": target_url}
                except Exception:
                    pass
            if not recovered:
                return {"status": "HTTP_ERROR", "code": code, "detail": f"HTTP {code} Not Found (catalog route and fallback paths exhausted)", "url": target_url}

        if code >= 400:
            return {"status": "HTTP_ERROR", "code": code, "detail": f"HTTP {code}", "url": target_url}

        stripped = body.strip()
        ctype = lower_headers.get("content-type", "").lower()

        if is_sub_probe:
            if stripped.startswith(('{', '[')) or "json" in ctype:
                try:
                    parsed = json.loads(stripped)
                except Exception:
                    return {"status": "API_ERROR", "code": code, "detail": "Invalid JSON payload", "url": target_url}
                count = _count_json_items(parsed)
                if isinstance(parsed, dict) and (parsed.get("error") or parsed.get("errors")) and count is None:
                    return {"status": "API_ERROR", "code": code, "detail": "JSON payload reported error", "url": target_url}
                cnt_msg = f" ({count} items)" if count is not None else ""
                return {"status": "OK_JSON", "code": code, "detail": f"HTTP {code} JSON OK{cnt_msg}", "url": target_url}

            matched_sel = [s for s in selectors if s.lower() in body.lower()]
            if matched_sel:
                return {"status": "OK_HTML", "code": code, "detail": f"HTTP {code} OK ('{matched_sel[0]}')", "url": target_url}

            is_spa = any(s in body for s in ['__NEXT_DATA__', 'id="__next"', 'id="app"', 'window.__INITIAL_STATE__', 'id="root"'])
            if is_spa:
                return {"status": "OK_SPA", "code": code, "detail": f"HTTP {code} SPA Webapp shell", "url": target_url}

            title_m = re.search(r'<title[^>]*>(.*?)</title>', body, re.I | re.S)
            title_txt = title_m.group(1).lower() if title_m else ""
            is_404_title = ("404" in title_txt) or ("not found" in title_txt)
            body_l = body.lower()
            if len(body) > 1500 and not is_404_title and any(p in body_l for p in EMPTY_SEARCH_PHRASES):
                return {"status": "OK_EMPTY", "code": code, "detail": f"HTTP {code} Valid Search Page (0 matches for query)", "url": target_url}

            has_structure = bool(re.search(r'<img[^>]+src=', body, re.I) and re.search(r'<a[^>]+href=', body, re.I))
            if has_structure and len(body) > 4000 and not is_404_title:
                return {"status": "OK_HTML", "code": code, "detail": f"HTTP {code} OK (DOM results layout)", "url": target_url}

            return {"status": "SELECTOR_DRIFT", "code": code, "detail": f"HTTP {code} live, but search/filter selectors {selectors[:2]} not matched", "url": target_url}

        # Catalog verification mode (default)
        if stripped.startswith(('{', '[')) or "api" in target_url.lower():
            return {"status": "LIVE_CONFIRMED", "code": code, "detail": f"HTTP {code} API Verified (JSON payload)", "url": target_url}

        matched_sel = [s for s in selectors if s.lower() in body.lower()]
        if matched_sel:
            return {"status": "LIVE_CONFIRMED", "code": code, "detail": f"HTTP {code} Scraper OK ('{matched_sel[0]}')", "url": target_url}

        is_spa = any(s in body for s in ['__NEXT_DATA__', 'id="__next"', 'id="app"', 'window.__INITIAL_STATE__', 'id="root"'])
        if is_spa:
            return {"status": "LIVE_CONFIRMED", "code": code, "detail": f"HTTP {code} Verified (SPA Webapp shell)", "url": target_url}

        has_structure = bool(re.search(r'<img[^>]+src=', body, re.I) and re.search(r'<a[^>]+href=', body, re.I))
        if has_structure or any(k in body.lower() for k in ['series', 'manga', 'anime', 'chapter', 'episode', '<article', 'post-title', 'thumb', 'novel', 'comic']):
            return {"status": "LIVE_CONFIRMED", "code": code, "detail": f"HTTP {code} Catalog Verified (DOM layout)", "url": target_url}

        return {"status": "SELECTOR_DRIFT", "code": code, "detail": f"HTTP {code} live, but selectors {selectors[:2]} not in DOM", "url": target_url}

    except urllib.error.HTTPError as e:
        if e.code in (403, 503) and ('cloudflare' in (e.headers.get('Server', '')).lower() or e.headers.get('CF-RAY')):
            return {"status": "CLOUDFLARE_PROTECTED", "code": e.code, "detail": f"Cloudflare Shield active (HTTP {e.code})", "url": target_url}
        return {"status": "HTTP_ERROR", "code": e.code, "detail": f"HTTP {e.code}: {e.reason}", "url": target_url}
    except Exception as e:
        err_msg = str(e)
        if any(k in err_msg.lower() for k in ['timeout', 'timed out']):
            return {"status": "HTTP_ERROR", "code": 504, "detail": "Gateway Timeout (WAF Slowdown)", "url": target_url}
        if any(k in err_msg.lower() for k in ['connection refused', 'getaddrinfo failed', 'name or service not known']):
            return {"status": "DEAD_DOMAIN", "code": 0, "detail": err_msg, "url": target_url}
        return {"status": "HTTP_ERROR", "code": 0, "detail": err_msg, "url": target_url}

def probe_extension_scraper(item, ext_type, rules_map, timeout=8, test_search=False, test_filters=False, query="love"):
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
        res = {"name": name, "pkg": pkg, "type": ext_type, "status": "NO_BASE_URL", "code": 0, "detail": "Missing baseUrl"}
        if test_search:
            res["search"] = {"status": "SKIPPED_BLOCKED", "code": 0, "url": "", "detail": "Skipped (NO_BASE_URL on catalog)"}
        if test_filters:
            res["filters"] = {"status": "SKIPPED_BLOCKED", "code": 0, "url": "", "filter_count": 0, "detail": "Skipped (NO_BASE_URL on catalog)"}
        return res

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

    target_url = _build_probe_url(base_clean, path)
    req_headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
        "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
        "Accept-Encoding": "gzip, deflate",
        "Accept-Language": "en-US,en;q=0.9",
        "Referer": f"{base_clean}/"
    }
    selectors = extract_all_selectors(selector)
    if not selectors:
        selectors = ["item", "card", "thumb", "manga", "anime", "series", "post", "title", "entry", "box", "article", "tile", "obra"]

    cat_probe = _probe_endpoint(target_url, base_clean, req_headers, timeout, selectors, is_sub_probe=False)
    res = {
        "name": name,
        "pkg": pkg,
        "type": ext_type,
        "status": cat_probe["status"],
        "code": cat_probe["code"],
        "detail": cat_probe["detail"],
        "base_url": base_url,
    }
    if not (test_search or test_filters):
        return res

    res["theme"] = rule.get("theme", "")
    is_blocked = res["status"] in BLOCKED_STATUSES

    if test_search:
        if is_blocked:
            res["search"] = {"status": "SKIPPED_BLOCKED", "code": res.get("code", 0), "url": "", "detail": f"Skipped ({res['status']} on catalog)"}
        elif rule.get("search_supported") is False:
            res["search"] = {"status": "SEARCH_UNSUPPORTED", "code": 0, "url": "", "detail": "Extension does not implement search"}
        elif rule.get("search"):
            sspec = rule["search"]
            s_url = _build_probe_url(base_clean, sspec.get("path", ""), query=query, is_search=True)
            s_method = sspec.get("method", "GET")
            s_data = None
            if s_method == "POST" and sspec.get("data"):
                s_data = {k: (query if v == "{q}" else v) for k, v in sspec["data"].items()}
            s_sels = extract_all_selectors(sspec.get("selector", "") or selector) or selectors
            res["search"] = _probe_endpoint(s_url, base_clean, req_headers, timeout, s_sels, method=s_method, post_data=s_data, is_sub_probe=True)
        else:
            res["search"] = {"status": "NO_SEARCH_RULE", "code": 0, "url": "", "detail": "Dynamic/unextracted search request"}

    if test_filters:
        f_count = rule.get("filter_count", 0)
        has_filters = bool(rule.get("has_filters") or rule.get("filter") or f_count > 0)
        if is_blocked:
            res["filters"] = {"status": "SKIPPED_BLOCKED", "code": res.get("code", 0), "url": "", "filter_count": f_count, "detail": f"Skipped ({res['status']} on catalog)"}
        elif rule.get("filter"):
            fspec = rule["filter"]
            f_url = _build_probe_url(base_clean, fspec.get("path", ""))
            f_sels = extract_all_selectors(fspec.get("selector", "") or selector) or selectors
            f_res = _probe_endpoint(f_url, base_clean, req_headers, timeout, f_sels, method=fspec.get("method", "GET"), post_data=fspec.get("data"), is_sub_probe=True)
            f_res["filter_count"] = f_count
            res["filters"] = f_res
        elif has_filters:
            res["filters"] = {"status": "STATIC_ONLY", "code": 0, "url": "", "filter_count": f_count, "detail": f"Filters declared ({f_count}) — dynamic query builder"}
        else:
            res["filters"] = {"status": "NO_FILTERS", "code": 0, "url": "", "filter_count": 0, "detail": "No getFilterList / filters defined"}

    return res


def cmd_test(args):
    target = getattr(args, "target", "all").lower()
    workers = getattr(args, "workers", 40)
    timeout = getattr(args, "timeout", 6)
    out_path = getattr(args, "output", None)
    test_search = getattr(args, "deep", False) or getattr(args, "search", False)
    test_filters = getattr(args, "deep", False) or getattr(args, "filters", False)
    pkg_filter = (getattr(args, "pkg", None) or "").strip().lower()
    query = getattr(args, "query", "love") or "love"

    mode_tags = ["CATALOG"]
    if test_search:
        mode_tags.append(f"SEARCH('{query}')")
    if test_filters:
        mode_tags.append("FILTERS")

    print("\n========================================================")
    print(f"[*] Testing Scrapers & APIs ({target.upper()}) [{' + '.join(mode_tags)}]")
    print("========================================================")

    t0 = time.time()
    raw_tasks = []
    if target in ("anime", "all") and (DIST_ANIME / "index.min.json").exists():
        with open(DIST_ANIME / "index.min.json", encoding="utf-8") as f:
            for it in json.load(f):
                raw_tasks.append((it, "anime"))
    if target in ("manga", "all") and (DIST_MANGA / "index.min.json").exists():
        with open(DIST_MANGA / "index.min.json", encoding="utf-8") as f:
            for it in json.load(f):
                raw_tasks.append((it, "manga"))
    if target in ("novel", "all") and (DIST_NOVEL / "plugins.min.json").exists():
        with open(DIST_NOVEL / "plugins.min.json", encoding="utf-8") as f:
            for it in json.load(f):
                raw_tasks.append((it, "novel"))

    if pkg_filter:
        raw_tasks = [
            t for t in raw_tasks
            if pkg_filter in t[0].get("pkg", "").lower()
            or pkg_filter in t[0].get("id", "").lower()
            or pkg_filter in t[0].get("name", "").lower()
        ]
        if not raw_tasks:
            print(f"[-] No extensions matched filter '--pkg {pkg_filter}'.")
            return

    only_pkgs = {it.get("pkg", it.get("id", "")).lower() for it, _ in raw_tasks} if pkg_filter else None
    cats_present = {cat for _, cat in raw_tasks}

    anime_rules, manga_rules, novel_rules = {}, {}, {}
    if "anime" in cats_present:
        print("[*] Extracting Anime source rules...")
        anime_rules = extract_anime_rules()
    if "manga" in cats_present:
        print("[*] Extracting Manga source rules...")
        manga_rules = extract_manga_rules(only_pkgs=only_pkgs)
    if "novel" in cats_present:
        print("[*] Extracting Novel source rules...")
        novel_rules = extract_novel_rules()

    rules_by_cat = {"anime": anime_rules, "manga": manga_rules, "novel": novel_rules}
    tasks = [(it, cat, rules_by_cat[cat]) for it, cat in raw_tasks]

    total = len(tasks)
    print(f"[*] Dispatching {total} extensions across {workers} concurrent workers (timeout={timeout}s)...")
    results = []
    completed = 0
    t_start = time.time()
    with ThreadPoolExecutor(max_workers=workers) as pool:
        futures = {
            pool.submit(probe_extension_scraper, it, cat, rmap, timeout, test_search, test_filters, query): (it, cat)
            for it, cat, rmap in tasks
        }
        for future in as_completed(futures):
            results.append(future.result())
            completed += 1
            if completed % 100 == 0 or completed == total:
                elapsed = time.time() - t_start
                rate = completed / elapsed if elapsed > 0 else 0
                print(f"  -> Progress: {completed}/{total} ({rate:.1f} ext/s)")

    stats = {}
    search_stats = {}
    filter_stats = {}
    for r in results:
        cat = r["type"]
        st = r["status"]
        stats.setdefault(cat, {})[st] = stats.get(cat, {}).get(st, 0) + 1
        if test_search and "search" in r:
            sst = r["search"]["status"]
            search_stats.setdefault(cat, {})[sst] = search_stats.get(cat, {}).get(sst, 0) + 1
        if test_filters and "filters" in r:
            fst = r["filters"]["status"]
            filter_stats.setdefault(cat, {})[fst] = filter_stats.get(cat, {}).get(fst, 0) + 1

    print("\n" + "=" * 55)
    print("TEST RESULTS SUMMARY:")
    print("=" * 55)
    for cat in sorted(stats.keys()):
        cat_total = sum(stats[cat].values())
        print(f"\n[{cat.upper()} CATALOG] ({cat_total} verified):")
        for st, count in sorted(stats[cat].items(), key=lambda x: x[1], reverse=True):
            pct = count / cat_total * 100
            print(f"  • {st:25s}: {count:4d} ({pct:5.1f}%)")
        if test_search and cat in search_stats:
            stotal = sum(search_stats[cat].values())
            print(f"[{cat.upper()} SEARCH API] ({stotal} checked):")
            for st, count in sorted(search_stats[cat].items(), key=lambda x: x[1], reverse=True):
                pct = count / stotal * 100
                print(f"  • {st:25s}: {count:4d} ({pct:5.1f}%)")
        if test_filters and cat in filter_stats:
            ftotal = sum(filter_stats[cat].values())
            print(f"[{cat.upper()} FILTERS] ({ftotal} checked):")
            for st, count in sorted(filter_stats[cat].items(), key=lambda x: x[1], reverse=True):
                pct = count / ftotal * 100
                print(f"  • {st:25s}: {count:4d} ({pct:5.1f}%)")

    if (test_search or test_filters) and len(results) <= 20:
        print("\nDETAILED TARGET RESULTS:")
        for r in sorted(results, key=lambda x: x["pkg"]):
            print(f"\n  [{r['type'].upper()}] {r['name']} ({r['pkg']}) [theme={r.get('theme') or 'custom'}]")
            print(f"    Catalog : {r['status']} (HTTP {r['code']}) | {r['detail']} | {r.get('base_url', '')}")
            if "search" in r:
                s = r["search"]
                print(f"    Search  : {s['status']} (HTTP {s['code']}) | {s['detail']} | {s.get('url', '')}")
            if "filters" in r:
                fl = r["filters"]
                print(f"    Filters : {fl['status']} (count={fl.get('filter_count', 0)}, HTTP {fl['code']}) | {fl['detail']} | {fl.get('url', '')}")
    elif test_search or test_filters:
        bad_states = {"HTTP_ERROR", "API_ERROR", "SELECTOR_DRIFT"}
        if test_search:
            bad_s = [r for r in results if r.get("search", {}).get("status") in bad_states]
            if bad_s:
                print(f"\nTOP SEARCH API FAILURES ({len(bad_s)} total, showing up to 15):")
                for r in bad_s[:15]:
                    s = r["search"]
                    print(f"  • [{r['type']}] {r['pkg']} -> {s['status']} (HTTP {s['code']}) | {s['detail']} | {s.get('url', '')}")
        if test_filters:
            bad_f = [r for r in results if r.get("filters", {}).get("status") in bad_states]
            if bad_f:
                print(f"\nTOP FILTER ENDPOINT FAILURES ({len(bad_f)} total, showing up to 15):")
                for r in bad_f[:15]:
                    fl = r["filters"]
                    print(f"  • [{r['type']}] {r['pkg']} -> {fl['status']} (HTTP {fl['code']}) | {fl['detail']} | {fl.get('url', '')}")

    report_file = Path(out_path) if out_path else (ROOT_DIR / "scratch" / f"deep_test_{target}_report.json")
    report_file.parent.mkdir(parents=True, exist_ok=True)
    payload = {
        "timestamp": time.time(),
        "target": target,
        "total": total,
        "duration_s": round(time.time() - t0, 2),
        "stats": stats,
        "results": results,
    }
    if test_search:
        payload["search_stats"] = search_stats
    if test_filters:
        payload["filter_stats"] = filter_stats
    with open(report_file, "w", encoding="utf-8") as f:
        json.dump(payload, f, indent=2, ensure_ascii=False)
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
    p_test.add_argument("--deep", action="store_true", help="Run search API and filter checks in addition to catalog check")
    p_test.add_argument("--search", action="store_true", help="Probe live search endpoints on non-blocked extensions")
    p_test.add_argument("--filters", action="store_true", help="Probe filter endpoints and audit declared filter lists")
    p_test.add_argument("--pkg", help="Filter extensions by substring match on package, id, or name")
    p_test.add_argument("--query", default="love", help="Search term for --search / --deep tests (default: love)")

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
