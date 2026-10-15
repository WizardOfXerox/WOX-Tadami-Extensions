import urllib.request, ssl, re
from bs4 import BeautifulSoup

ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE
headers = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
    'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8'
}

base = 'https://tioanime.com'
print("=== Testing TioAnime on", base, "===")

# 1. Popular / Directorio
pop_url = f"{base}/directorio"
req = urllib.request.Request(pop_url, headers=headers)
html = urllib.request.urlopen(req, context=ctx).read().decode('utf-8', errors='ignore')
soup = BeautifulSoup(html, 'html.parser')

articles = soup.select('article.anime')
print(f"[+] Popular articles found: {len(articles)}")
if not articles:
    print("[-] No anime found!")
    exit(1)

sample_a = articles[0].select_one('a')
sample_url = base + sample_a.get('href')
sample_title = sample_a.select_one('h3.title').text if sample_a.select_one('h3.title') else ''
print(f"[+] First anime: {sample_title} -> {sample_url}")

# 2. Details and Episodes
req2 = urllib.request.Request(sample_url, headers=headers)
html2 = urllib.request.urlopen(req2, context=ctx).read().decode('utf-8', errors='ignore')

# TioAnime embeds episodes in a JS variable: var episodes = [...];
ep_match = re.search(r'var\s+episodes\s*=\s*(\[[^;]+\]);', html2)
if ep_match:
    print(f"[+] Found episodes JS variable length: {len(ep_match.group(1))}")
    import json
    eps = json.loads(ep_match.group(1))
    print(f"[+] Total episodes count: {len(eps)}")
    if eps:
        first_ep_num = eps[0]
        # Episode URL structure: /ver/<slug>-<epNum>
        slug = sample_a.get('href').replace('/anime/', '')
        ep_url = f"{base}/ver/{slug}-{first_ep_num}"
        print(f"[+] Testing episode: {ep_url}")
        
        # 3. Episode player servers: var videos = [...];
        req3 = urllib.request.Request(ep_url, headers=headers)
        html3 = urllib.request.urlopen(req3, context=ctx).read().decode('utf-8', errors='ignore')
        vid_match = re.search(r'var\s+videos\s*=\s*(\[[^;]+\]);', html3)
        if vid_match:
            print(f"[+] Found video servers JS: {vid_match.group(1)[:200]}")
            vids = json.loads(vid_match.group(1))
            for v in vids:
                print(f"    Server: {v[0]} -> URL: {v[1][:60]}...")
        else:
            print("[-] No videos JS found on episode page!")
else:
    print("[-] No episodes JS found!")
