import urllib.request, ssl, json, re, base64

ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE
headers = {'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'}

def get_html(url, referer=None):
    h = headers.copy()
    if referer:
        h['Referer'] = referer
    req = urllib.request.Request(url, headers=h)
    with urllib.request.urlopen(req, context=ctx, timeout=8) as r:
        return r.read().decode('utf-8', errors='ignore')

def test_anime(slug, ep_num=1):
    print(f"\n--- Testing Anime: {slug} Ep: {ep_num} ---")
    stream_url = f"https://www.animesaturn.net/anime/{slug}/ep-{ep_num}"
    html = get_html(stream_url)
    
    # 1. find embed URL
    m = re.search(r'https://play\.saturncdn\.net/embed/\d+\?[^"\'<>\s]+', html)
    if not m:
        m = re.search(r'initialVideoUrl["\']\s*:\s*["\'](https:[^"\']+)["\']', html)
        if m:
            embed_url = m.group(1).replace(r'\/', '/')
        else:
            print("[-] No embed url found!")
            return
    else:
        embed_url = m.group(0).replace('&amp;', '&')
    print(f"[+] Found embed URL: {embed_url}")

    # 2. Extract id, token, expires
    ep_id = re.search(r'/embed/(\d+)', embed_url).group(1)
    token = re.search(r'token=([^&]+)', embed_url).group(1)
    expires = re.search(r'expires=([^&]+)', embed_url).group(1)

    # 3. Request playlist
    playlist_url = f"https://play.saturncdn.net/embed/{ep_id}/playlist?token={token}&expires={expires}"
    resp_text = get_html(playlist_url, referer=embed_url)
    data = json.loads(resp_text)
    b64_d = data.get('d')
    if not b64_d:
        print("[-] No 'd' in playlist response:", data)
        return
    
    # 4. Decrypt XOR
    raw_bytes = base64.b64decode(b64_d)
    key_bytes = token.encode('latin1')
    decrypted = bytes([b ^ key_bytes[i % len(key_bytes)] for i, b in enumerate(raw_bytes)]).decode('utf-8', errors='ignore')
    print(f"[+] Decrypted video source: {decrypted}")

    # 5. Check if stream is live
    req = urllib.request.Request(decrypted, headers={'User-Agent': headers['User-Agent'], 'Referer': 'https://play.saturncdn.net/'})
    with urllib.request.urlopen(req, context=ctx, timeout=8) as r:
        print(f"[+] Stream accessible! Status: {r.status}, Content-Type: {r.headers.get('Content-Type')}, Size: {r.headers.get('Content-Length')}")

for slug in ['naruto-shippuden-kYJdf', 'i-m-looking-for-a-zombie-EZBpn', 'toaru-anbu-no-item-rsAFX']:
    test_anime(slug)
