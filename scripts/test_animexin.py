import urllib.request, ssl, re
from bs4 import BeautifulSoup

ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE
headers = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
    'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8'
}

base = 'https://animexin.dev'
print("=== Testing AnimeXin on", base, "===")

# 1. Popular / Latest
pop_url = f"{base}/anime/?status=&type=&order=popular"
req = urllib.request.Request(pop_url, headers=headers)
html = urllib.request.urlopen(req, context=ctx).read().decode('utf-8', errors='ignore')
soup = BeautifulSoup(html, 'html.parser')

items = soup.select('div.bsx, div.animposx, article')
print(f"[+] Popular items found: {len(items)}")
if not items:
    print("[-] No items found on popular!")
    exit(1)

sample_a = items[0].select_one('a')
sample_url = sample_a.get('href')
sample_title = sample_a.get('title') or (sample_a.select_one('.tt').text if sample_a.select_one('.tt') else '')
print(f"[+] First show: {sample_title} -> {sample_url}")

# 2. Details and Episodes
req2 = urllib.request.Request(sample_url, headers=headers)
html2 = urllib.request.urlopen(req2, context=ctx).read().decode('utf-8', errors='ignore')
soup2 = BeautifulSoup(html2, 'html.parser')

ep_links = soup2.select('.eplister ul li a, ul.clstyle li a, .episodelst ul li a')
print(f"[+] Episodes found: {len(ep_links)}")
if not ep_links:
    print("[-] No episodes found!")
    exit(1)

sample_ep = ep_links[0].get('href')
print(f"[+] First episode: {sample_ep}")

# 3. Episode video embeds
req3 = urllib.request.Request(sample_ep, headers=headers)
html3 = urllib.request.urlopen(req3, context=ctx).read().decode('utf-8', errors='ignore')
soup3 = BeautifulSoup(html3, 'html.parser')

iframes = soup3.select('iframe[src], select.mirror option[value]')
print(f"[+] Embed sources/options found: {len(iframes)}")
for ifr in iframes[:5]:
    src = ifr.get('src') or ifr.get('value')
    print(f"    Source: {src}")
