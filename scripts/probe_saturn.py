import urllib.request
import ssl
import re

ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE
headers = {'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'}

urls = [
    'https://www.animesaturn.net/ongoing?page=1',
    'https://www.animesaturn.net/animeincorso?page=1',
    'https://www.animemars.org/ongoing?page=1',
    'https://www.animemars.org/animeincorso?page=1',
    'https://www.animesaturn.net/animelist?page=1',
    'https://www.animemars.org/animelist?page=1',
    'https://www.animesaturn.tv/ongoing?page=1',
]

for url in urls:
    try:
        req = urllib.request.Request(url, headers=headers)
        with urllib.request.urlopen(req, context=ctx, timeout=8) as resp:
            content = resp.read().decode('utf-8', errors='ignore')
            boxes = len(re.findall(r'class=[\'"][^\'"]*sebox', content))
            anime_links = re.findall(r'href=[\'"](https?://[^\'"]*/anime/[^\'"]+)[\'"]', content)
            print(f"{url} -> status {resp.status}, final: {resp.geturl()}, sebox: {boxes}, anime links: {len(anime_links)}")
            if anime_links:
                print(f"   sample: {anime_links[0]}")
    except Exception as e:
        print(f"{url} -> {e}")
