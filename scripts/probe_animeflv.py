import urllib.request, re

domains = ['https://www4.animeflv.net', 'https://animeflv.net']
for d in domains:
    try:
        req = urllib.request.Request(d, headers={'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36'})
        with urllib.request.urlopen(req, timeout=10) as resp:
            content = resp.read().decode('utf-8', errors='ignore')
            print(f"[+] {d} -> status {resp.status}, final URL: {resp.geturl()}, len: {len(content)}")
            matches = re.findall(r'ListEpisodios', content)
            print(f"    ListEpisodios occurrences: {len(matches)}")
            # check anime links
            links = re.findall(r'href="(/ver/[^"]+)"', content)
            print(f"    Episode links found: {len(links)}")
            if links:
                print(f"    Sample link: {links[0]}")
    except Exception as e:
        print(f"[-] {d} -> {e}")
