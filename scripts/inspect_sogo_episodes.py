import urllib.request, ssl
from bs4 import BeautifulSoup

ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE
headers = {'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36', 'Referer': 'https://animesogo.to/'}
url = 'https://animesogo.to/watch/solo-leveling-season-2-arise-from-the-shadow-3eukp/ep-1'
req = urllib.request.Request(url, headers=headers)
html = urllib.request.urlopen(req, context=ctx).read().decode('utf-8')
soup = BeautifulSoup(html, 'html.parser')

print("All elements with 'episode' in class or id:")
for el in soup.find_all(attrs={'class': True}):
    if any('episode' in c.lower() for c in el['class']):
        print(f"<{el.name} class='{el['class']}'> -> links: {len(el.find_all('a'))}")

print("\nAll a tags linking to /ep-:")
ep_links = soup.select('a[href*="/ep-"]')
print(f"Total a[href*='/ep-']: {len(ep_links)}")
for a in ep_links[:5]:
    print(' ', a.get('href'), '| text:', a.get_text(strip=True))

print("\nEpisode ranges / dropdowns / tabs:")
for el in soup.select('.ranges, .range, .ep-range, .tabs'):
    print(el.get_text(strip=True)[:100])
