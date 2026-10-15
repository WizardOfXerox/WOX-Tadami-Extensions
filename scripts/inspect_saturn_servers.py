import urllib.request, ssl, html as html_lib, json, re

ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE
headers = {'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'}
req = urllib.request.Request('https://www.animesaturn.net/anime/naruto-shippuden-kYJdf/ep-1', headers=headers)
raw_html = urllib.request.urlopen(req, context=ctx).read().decode('utf-8', errors='ignore')

m = re.search(r'watchPage\s*\((.*?)\)\"', raw_html, re.DOTALL)
if m:
    val = html_lib.unescape(m.group(1))
    data = json.loads(val)
    print("watchPage JSON parsed successfully!")
    print("episodeId:", data.get("episodeId"))
    print("initialVideoUrl:", data.get("initialVideoUrl"))
    print("servers:")
    for s in data.get("servers", []):
        print("  ", s)
else:
    print("watchPage regex didn't match")
