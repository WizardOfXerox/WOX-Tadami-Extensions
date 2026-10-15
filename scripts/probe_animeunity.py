import urllib.request, re

embed_url = "https://vixcloud.co/embed/304645?token=10a2a70fc31f00a853e641a171da68b5&expires=1797134058&canPlayFHD=1"
req = urllib.request.Request(embed_url, headers={
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36',
    'Referer': 'https://www.animeunity.so/'
})
try:
    with urllib.request.urlopen(req, timeout=10) as resp:
        html = resp.read().decode('utf-8', errors='ignore')
        print(f"Embed page status {resp.status}, length: {len(html)}")
        if "masterPlaylist" in html:
            print("Found masterPlaylist!")
            m_url = re.search(r"url:\s*'([^']+)'", html)
            m_exp = re.search(r"'expires':\s*'([^']+)'", html)
            m_tok = re.search(r"'token':\s*'([^']+)'", html)
            if m_url and m_exp and m_tok:
                pl_url = m_url.group(1)
                exp = m_exp.group(1)
                tok = m_tok.group(1)
                sep = '&' if '?' in pl_url else '?'
                master_pl_url = f"{pl_url}{sep}h=1&token={tok}&expires={exp}"
                print(f"Master playlist url: {master_pl_url}")
                # test fetching playlist
                req_pl = urllib.request.Request(master_pl_url, headers={'User-Agent': 'Mozilla/5.0', 'Referer': 'https://vixcloud.co/'})
                with urllib.request.urlopen(req_pl, timeout=10) as resp_pl:
                    pl_content = resp_pl.read().decode()
                    print("Playlist lines:\n", pl_content[:400])
        else:
            print("No masterPlaylist found in embed page!")
            print(html[:500])
except Exception as e:
    print("Embed request failed:", e)
