import urllib.request, ssl, re
from bs4 import BeautifulSoup

ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE
headers = {'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36', 'Referer': 'https://animesogo.to/'}

url = 'https://animesogo.to/anime/solo-leveling-season-2-arise-from-the-shadow-v52j'
req = urllib.request.Request(url, headers=headers)
html = urllib.request.urlopen(req, context=ctx).read().decode('utf-8', errors='ignore')
soup = BeautifulSoup(html, 'html.parser')

print("Title:", soup.find('h1'))
eps = soup.select('ul.episodes > li > a, a.item')
print(f"Episodes count: {len(eps)}")
if eps:
    first_ep = eps[0]
    print(f"First ep: {first_ep.get('href')} | text: {first_ep.get_text(strip=True)}")
    ep_url = 'https://animesogo.to' + first_ep.get('href')
    
    req_ep = urllib.request.Request(ep_url, headers=headers)
    html_ep = urllib.request.urlopen(req_ep, context=ctx).read().decode('utf-8', errors='ignore')
    soup_ep = BeautifulSoup(html_ep, 'html.parser')
    
    servers = soup_ep.select('.servers .server, .server-item, [data-id]')
    print(f"Servers found: {len(servers)}")
    for s in servers[:5]:
        print("  Server:", s.get_text(strip=True), s.attrs)
